# PERF-03A — architecture proposée

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche de revue `codex/llm-perf-03a-native-review`. Base `09e278b54b8be58a3d665d2d91de10f8869e40bb`, tree `975fc71395ac7758678cb5af1b76858ee614c9b2`. **Patch préparé seulement : aucun nouveau JNI productif appliqué.** Les résultats de tests et l'identité finale du dossier se lisent dans le rapport central/artefacts ; cette architecture n'est pas une qualification d'exécution native.

## Graphe et propriété

```mermaid
flowchart LR
  I[INTERFACE future approuvée] --> C[CORE propriétaire de la session]
  C --> G[Mandat et préparation exacts à qualifier]
  G --> B[LlamaBackend existant opt-in]
  B --> R[ProcessResources existant]
  B --> W[Worker unique existant]
  W --> J[JNI synchrone candidat]
  J --> E[Engine et llama.cpp existants]
  E --> K[Callback local borné]
  K --> Q[File côté Kotlin]
  Q --> F[Adaptateur UI par frame]
```

Les flèches CORE/INTERFACE sont **futures et bloquées** par les contrats actuels, détaillés dans [CORE-INTERFACE-COMPAT.md](CORE-INTERFACE-COMPAT.md). La file, l'observateur et l'adaptateur ne possèdent aucun modèle, worker de génération ou bail indépendant. Le callback n'accorde aucune admission. Les anciennes signatures et le chemin `generate → ByteArray → String` restent disponibles ; le streaming doit être activé explicitement dans une composition de laboratoire après accord.

## Port exact du candidat

Le diff propose `LlamaBackend.generateOwnedStream(request, contextLimit, authority, buffer)`, méthode interne opt-in, avec `StreamOwnerAuthority`, `OwnedStreamBuffer` et `OwnedStreamCallback`. Le premier périmètre est **P2 texte seul : un message user et un system prompt explicite**. L'historique multi-tours et les documents ne sont pas aplatis ou tronqués pour y entrer ; ils restent sur les chemins historiques ou refusés par cette entrée. Ce patch ne suffit donc pas à convertir tout le chat de production.

Le candidat réemploie la même instance `LlamaBackend`, le même CAS `generating`, le même worker et les mêmes baux MODEL/HEAVY. Il remet à l'autorité une vue locale contenant **l'instance réelle du ResourceLease** détenue par le backend, le propriétaire et des incarnations worker/résidence ; aucune copie d'UUID ne remplace cet objet. Cette vue n'est pas une attestation du broker. L'autorité doit lier ce fait local au mandat CORE, modèle vérifié, requête/session/epoch/génération et bail approuvés. **Aucune implémentation productive de cette autorité n'est fournie**, ce qui interdit toute activation live automatique.

La mesure canonique existante `measureFixtureP2` est réutilisée pour préparer les hashes du template et du rendu, le compte de tokens et l'empreinte cadrée de la demande. La génération refait cette préparation et `onNativePrepared` doit exiger l'égalité exacte **avant ACK et prefill**. La double préparation reste donc présente dans ce premier patch : aucune optimisation de tokenisation ou cache à usage unique n'est prétendue appliquée. L'empreinte locale `StreamPrepared.requestSha256` n'est pas assimilée sans contrôle à `GenerateRequest.reference.requestSha256` ; l'autorité future doit lier explicitement les deux objets et leur contenu.

## Choix transport local

| Option | Propriétés | Contraintes |
|---|---|---|
| **Callback JNI synchrone sur le worker existant : candidat privilégié** | Pièce transmise dès sa production ; aucun thread producteur supplémentaire ; pas de polling au repos | Callback doit être borné, interrompable et sans appel UI direct. Exceptions Java/OOM traitées explicitement, aucune réentrance load/unload/destroy. La latence du consommateur peut bloquer ce même worker. |
| File native bornée + lecture contrôlée | Découple la cadence du consommateur et la production ; lecture non bloquante possible | Le worker occupé par generate ne peut pas aussi poller. Une lecture sur autre thread impose une nouvelle discipline de propriété/références/drain et davantage de copies ; un step API serait une autre ABI sensible. Pas de second moteur et pas de boucle idle. Alternative non retenue pour ce premier patch. |

Pour un callback strictement synchrone, reçu comme paramètre Java→JNI, appelé sur ce même thread et jamais retenu après retour, **la référence locale JNI est valide pendant l'appel**. Un NewGlobalRef n'est pas nécessaire par principe. L'implémentation doit borner/libérer ses autres références locales par événement. Si une version asynchrone retient le callback, elle exige explicitement NewGlobalRef/DeleteGlobalRef, gestion d'attachement JVM et une autre revue de durée de vie ; elle n'est pas couverte ici. Ce point précise la formulation indicative de PERF-02.

## Flux de contenu et backpressure

Un ACK corrélé de contrôle précède le contenu et ne constitue pas un token. Le producteur observe un vrai token non-EOG à la sortie de l'échantillonneur llama ; il convertit ses octets avec un scratch borné et reconstruit UTF-8 strictement avant d'émettre un chunk utilisable. Des token pieces peuvent couper un caractère : les1à3octets incomplets restent dans un carry, sans caractère de remplacement inventé. Un chunk peut représenter plusieurs token pieces nécessaires au même caractère ; son identité de séquence et ses compteurs ne doivent pas faire passer un fragment arbitraire de String final pour un token.

Les contrôles de validité du flux refusent une sortie invalide ou hors limites ; ils ne la corrigent ni ne la filtrent silencieusement. L'identité ancien/nouveau doit être démontrée sur les sorties valides avec le même modèle et les mêmes paramètres. Un refus candidat face à des octets que l'ancien chemin acceptait est une différence à consigner, pas une comparaison de contenu réussie.

Les limites expérimentales sont **16événements en attente,16Kio de texte en attente,4096octets par chunk et64Kio cumulés**. Ce sont des bornes logiques de revue, pas un budget Android validé : objets JNI/JVM, empreintes, scratch et overhead de collection s'y ajoutent. Une réserve de terminal doit empêcher qu'une file pleine supprime l'information de fin.

BACKPRESSURE ne valide ni ne consomme la séquence du chunk refusé. Le producteur conserve exactement ses octets et réessaie seulement ce chunk après attente interruptible et bornée, avec le flag Stop et la deadline existants contrôlés. Aucun spin ni production anticipée illimitée. Après acceptation, chaque octet est livré une fois et dans l'ordre. Une duplication exacte suit la règle d'idempotence bornée ; conflit, trou ou identité différente ferme le chemin en UNKNOWN/ERROR, sans afficher le texte ambigu.

Il n'est permis d'appeler ni UI/CORE ni callbacks sous `handleLock`/verrou du broker. L'ordre de synchronisation doit laisser `requestStop` atteindre le drapeau natif même si le consommateur ralentit. Une fermeture du consommateur déclenche l'arrêt ; elle ne décharge pas le modèle. Aucune reprise sur un nouveau moteur n'est automatique.

## États à conserver distincts

| État | Signification exacte | Ce qu'il n'autorise pas |
|---|---|---|
| `CONTENT_DONE` | Terminal logique cohérent COMPLETED/CANCELLED/ERROR pour l'opération ; aucun nouveau texte accepté | Ne prouve ni sortie du callback, retour JNI, file/import drainés, ni bail fermé |
| `DRAINED` | Preuve exhaustive corrélée de fin réelle : aucun appel/callback/tâche/import/flux de l'opération encore actif ou en file, références de callback non utilisées | Ne signifie pas modèle déchargé ; le bail MODEL peut rester résident |
| `UNLOADED` | Après drain attesté, modèle/contexte libérés et baux concernés fermés sous la même incarnation/propriétaire ; reçu valide | Ne signifie jamais PSS/RSS zéro ni admission globale inter-APK |
| `UNKNOWN` | Identité, retour, cleanup ou preuve manquants/contradictoires, mort de processus ou observation perdue | Aucune réadmission ni remise à zéro par ACK, timeout, coroutine annulée, nouveau PID ou file vide |
| `CLEANUP_FAILED` | Nettoyage demandé mais exception/échec observé ; quarantaine conservée | Aucun transfert de propriété, destruction répétée aveugle ou libération supposée |

Les contrats gelés ne publient aujourd'hui que `UNATTESTED` et `mayAdmitAnotherEngine=false`. Ces définitions exposent les exigences du futur port ; le patch de contenu ne doit pas les promouvoir par un booléen de fin. Le patch F3B historique de preuve de ressources reste séparé et non appliqué, SHA256 `be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374`.

## Arrêt et mémoire

Stop doit être idempotent et corrélé. Avant entrée, durant prefill, decode, callback bloqué ou attente de capacité : empêcher de nouvelles publications, demander le cancel natif existant, puis attendre la fin réelle sous le propriétaire. La mort du processus n'autorise pas à fabriquer un reçu UNLOADED. Le terminal producteur peut déjà être en file lorsque Stop survient : le texte non rendu doit quand même pouvoir être supprimé, comme le correctif PERF-02 final l'a établi.

Le runtime continue d'acquérir MODEL/HEAVY via son broker existant. Aucun seuil RAM, contexte, batterie, thermique, sampler, modèle, qualité ou durée de résidence n'est changé. Réutilisation du scratch, préparation exacte à usage unique et rendu différentiel sont des candidats séparés ; toute conservation d'optimisation exige comparaison avant/après et non-régression. Pas de KV-cache partagé approximativement entre sessions.

## Mesure et phases suivantes préparées

Les horloges doivent être monotones et dans un domaine qualifié : Android `elapsedRealtimeNanos` peut être rapproché de CLOCK_BOOTTIME **après vérification**, jamais de `steady_clock` arbitraire sans calibration. `NATIVE_FIRST_TOKEN`, premier UTF-8 utilisable, remise à TextView, commit de frame et réponse complète sont séparés. Le commit de frame est une définition logicielle de rendu, pas une mesure physique de photons.

PERF-03B exige accord du diff exact, compilation C++/JNI/ARM64, signatures anciennes, alignement16Kio, sanitizers disponibles, fautes JNI et génération réelle volontaire pour GATE-B. PERF-03C prépare laboratoire indépendant testOnly et matrice API29/35 ; aucune APK candidate n'est déclarée qualifiée avant build et vérification. Les campagnes physiques POCO/Redmi requièrent un second accord. TTFT p95<3s reste NOT_QUALIFIED sans≥20essais physiques comparables par cellule, refus/erreurs conservés.
