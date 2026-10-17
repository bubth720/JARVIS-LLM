# Audit 003F-F3A — propriétaire réel et sources manquantes

Date : 17 octobre 2026. Base vérifiée localement et sur origin :
`fix/003f-d4-02e1-commit`, `1ce24405b04a50f97cf30078b9716c7d00c593fc`.
Branche de travail : `feat/003f-f3-native-observation`.
Issue [LLM2](https://github.com/bubth720/JARVIS-LLM/issues/2) lue intégralement,
ainsi que D4-02E.1 au commit UNIFIED `49dd4027d2ccb54bf330f591d3ee17f5dc577132`
et la décision `275eca892c20b6dae3bfa20b241099542fd4ced6`.
Les références/hashes hérités sont dans ../mission-003f-correction/SOURCES.json.
Aucun autre dépôt n'est modifié. L'issue impose de suspendre avant modification
sensible native/production ; cette phase est **suspendue**, pas implémentée.

## Chaîne réelle de propriété

Dans l'APK historique, ChatViewModel possède un LanguageModel paresseux et appelle
prepareOperation, vérification/import sous ProcessResources, load puis generate.
Le nettoyage de onCleared utilise une portée survivant à celle du ViewModel.
Dans la fixture, FixtureApplication construit une seule AndroidRuntime, sans backend
au démarrage ; AndroidRuntime détient le seul LlamaBackend nullable, construit après
la vérification/import et les contrôles de sécurité. AndroidRuntime n'est pas remplacée
par chaque Service. P2EndpointService partage l'autorité Application, contrôle identité,
file de dispatch bornée, liaison Binder et epochs ; une coroutine ou un Service n'est
pas le propriétaire des allocations llama.cpp.

LlamaBackend possède un owner UUID interne, un dispatcher single-thread et le handle.
**create** est toutefois synchrone dans le constructeur, avant tout passage sur ce
worker ; **begin/cancel** s'exécutent sous handleLock sur le thread appelant. Le worker
sérialise load, generate, measure/generateFixtureP2, unload et destroy/close. L'atome
generating interdit deux générations, sans décrire toute sa file. Un futur compteur
ne doit donc pas assimiler « worker inactif » à « aucun appel JNI ».

bridge.cpp possède un Engine avec Cancellation, llama_model*, llama_context*,
contexte/batch/max_output. Engine::unload appelle llama_free puis llama_model_free,
puis remet les pointeurs à null ; destroy delete Engine (destructeur -> unload).
Les buffers temporaires de tokenisation/template/sampler sont locaux aux appels.
Les erreurs C++ standard de load déclenchent déjà e.unload avant propagation Java.
Un crash/abort/OOM natif ne fournit aucun retour Java ni observation de fin.
Les pointeurs et leurs transitions ne sont pas exposés par une API d'inventaire F3.

ProcessResources est uniquement **local au processus APK**. ResourceCoordinator
réserve un HEAVY et un MODEL au plus, avec IDs privés croissants. Les snapshots
exposent les requêtes, pas les IDs/générations de baux ni une clé de requête complète.
Le backend est le détenteur des vrais baux ; le nouveau ledger n'en acquiert aucun.

## Mapping observation -> état (révision actuelle, pas de preuve F3 positive)

| Source réelle | Ce qu'elle établit | État F3 utilisable / manque |
|---|---|---|
| BootstrapRegistry scellé, jamais touché, pas de backend ni bail | Construction contrôlée avant toute activité de cette incarnation | NOT_STARTED dans le périmètre initial existant ; jamais preuve de fin d'une ancienne opération |
| backend == null, PID neuf, Binder mort | Absence de référence ou perte de canal | UNKNOWN pour une opération antérieure ; ni drain ni unload |
| generating true | Une génération admise côté backend | Travail possible ; pas inventaire des load/import/unload/close en file |
| requestStop / Cancellation::request | Drapeau d'arrêt posé | STOP_REQUESTED seulement ; le code JNI peut encore fonctionner |
| Retour de load/generate/measure sur le worker | Cet appel synchrone a rendu la main | Fin de cet appel seulement ; aucun call_end_seq corrélé exporté |
| Fin de coroutine / timeout | Événement de contrôle | UNKNOWN ; ne libère pas les ressources |
| loaded Pair ou null | Cache Kotlin chemin/config | Ne décrit pas seul pointeurs, appels ni imports ; UNKNOWN si utilisé isolément |
| allocations/model/context/compute | Buffers déclarés llama.cpp après appel réussi | Pas PSS/RSS ni inventaire de résidence ; échec capteur = indisponible, pas zéro |
| coordinator.snapshot | Requêtes HEAVY/MODEL actuellement détenues | Protection locale réelle ; IDs et générations privés, pas snapshot atomique avec JNI |
| retour réussi de native.unload | Code existant a parcouru Engine::unload | Transition interne utile, mais ni unload_seq ni reçu durable liés à la clé réelle |
| échec unload/destroy | cleanupFailed, MODEL conservé lorsqu'il existe | CLEANUP_FAILED ; prepare/load/generate bloqués ; aucun effacement supposé |
| fermeture réussie et bail fermé | Chemin de nettoyage backend achevé | Pas preuve réutilisable après interruption : reçu et corrélation manquants |
| ModelStore.import/verify, VerifiedCopy | Flux SAF, copie .part, SHA/renommage, prefs | Activité I/O hors worker ; doit participer à la barrière et au drain |
| fichiers/journal corrompus ou ancien propriétaire perdu | Incertitude persistante | UNKNOWN/JOURNAL_BLOCKED, aucune reconstruction positive |

Le MODEL est retenu si load et son nettoyage échouent. Le HEAVY de l'appel est fermé
à son retour, même si le nettoyage a échoué ; conserver MODEL empêche un autre
propriétaire lourd. unload/close sont sérialisés sur le worker mais n'acquièrent pas
un HEAVY distinct. Ces faits restent inchangés ; un compteur de HEAVY ne suffit pas
pour compter tous les appels ou nettoyages. Une bibliothèque/Engine vide peut rester
alloué sans modèle résident ; UNLOADED ne signifiera jamais RSS/PSS=0.

## Exhaustivité de la barrière future

Avant **toute** entrée participante : factory/constructeur create, prepare/begin,
import/verify/flux ouverts, load/reload, génération standard/P2 et comptage associé,
unload, close/destroy, requestStop/cancel. Les nettoyages déjà admis doivent pouvoir
se terminer sous barrière ; un retry nouveau exige mandat exact explicite. La file
du Service et le passage vers le worker doivent compter dès avant le dispatch,
pas seulement quand JNI commence. Compter les imports jusqu'à fermeture effective
des flux et fin des écritures, même si une coroutine est annulée.

L'APK historique reste hors du périmètre de la fixture expérimentale. Il faut
inventorier aussi les appels directs au backend et au JNI à l'intérieur du processus
participant ; interdire toute preuve COMPLETE si une entrée échappe au registre.
Aucune barrière logicielle de ce dépôt ne peut attester les autres APK/CORE/VOICE.

## Conclusion d'audit

Un vrai adaptateur post-natif nécessite des changements dans LlamaBackend,
ResourceCoordinator/ProcessResources et probablement le bridge pour observer
les pointeurs appartenant réellement au moteur. Ces fichiers sont partagés avec
l'APK historique : **ne pas les modifier dans cette phase**. Voir
ADAPTATION-SENSIBLE.md et BACKEND-PROPOSE.diff.txt pour le périmètre à valider.
Le nouveau code est un socle isolé de revue, sans connexion vers RecoveryInventory,
AndroidRuntime ou ProcessRecoveryAuthority. F3 réelle demeure NON VÉRIFIÉE.
