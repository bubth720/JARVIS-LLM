# F3B — diff sensible exact à approuver, NON APPLIQUÉ

Base : `aa85ad4b506bb655dbe5b50ec4b125d19b48e81e`.

SHA-256 du patch :
`be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374`.
Le fichier [SENSITIVE.patch](SENSITIVE.patch) est un patch unifié complet, sans
ellipses ni symboles d'API non définis dans ce patch. [PATCH.json](PATCH.json)
donne son SHA-256 et les empreintes avant/après des sept fichiers proposés.
`git apply --check` réussit. **Aucune application**, même temporaire, à un checkout
historique n'est effectuée dans cette mission. La vérification sèche n'est ni une
compilation du backend modifié ni une preuve JNI.

## Les sept fichiers du patch

| Fichier | Diff précis proposé | Risque à approuver / vérification après accord |
|---|---|---|
| app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt | Tracer optionnel nul par défaut ; décorateur du NativeApi existant ; soumissions avant dispatch, fins dans le corps synchrone worker ; lecture seule sous handleLock sur le worker | Course annulation/dispatch, worker fermé, ordre des verrous, coût du wrapper ; compiler et tester le worker réellement modifié, mêmes comportements legacy |
| app/src/main/java/fr/jarvis/llm/engine/F3Trace.kt (nouveau) | Événements corrélés process/digest/owner, compteurs distincts tâches/imports/JNI, séquences de retour et nettoyage, UNKNOWN conservateur | Exhaustivité des sources, métadonnées en RAM, cap128, Thread objet comme contrôle interne et jamais preuve OS ; tester chaque entrée, erreurs et incertitudes |
| app/src/main/java/fr/jarvis/llm/engine/F3NativeApi.kt (nouveau) | Décorateur délégant les appels au même moteur, y compris create/begin/cancel ; événement terminé après retour synchrone réussi ou en erreur | Ne pas compter un abandon coroutine comme retour ; une panne du compteur après succès natif ne doit pas devenir une erreur de nettoyage et provoquer double destroy ; tests de faute de l'observateur |
| app/src/main/java/fr/jarvis/llm/engine/F3BackendObservation.kt (nouveau) | DTO brut trace + baux réels + cohérence ; aucun ATTEST ou reçu terminal | Ne pas confondre snapshot local avec preuve distribuée ni importer un état d'une autre incarnation |
| app/src/main/cpp/bridge.cpp | Sonde JNI observeOwned : tableau borné [ABI1, handle_existant1, modèle0/1, contexte0/1], lecture des mêmes pointeurs | Mauvais handle/use-after-free, coût/allocation de quatre longs JVM, OOM, sérialisation et ABI ; JNI Linux et tests de durée de vie à exécuter seulement après accord |
| domain/src/main/kotlin/fr/jarvis/domain/resources/ResourceCoordinator.kt | Snapshot atomique lecture seule exposant epoch du coordinateur, ID de bail et révision sur mutation effective ; acquisition/libération inchangées | Changements partagés par l'app historique ; ABA, retours tardifs et coût UUID initial ; tests exacts des IDs/révisions et baux après accord |
| fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/AndroidRuntime.kt | Tracer lié au pilot et à la demande avant import/factory ; suivi jusqu'à retour des I/O ; lecture de l'instance existante ou de son événement destroy retenu | Clé nouvelle refusée sur cet owner, null/absence n'est pas une libération ; tester import interrompu, backend fermé et recréation après accord |

Les fichiers ProcessResources.kt, ModelStore.kt, SafetyPolicy.kt, profils, catalogue,
P2Lifecycle, contrats /1 et /2, manifests, Application et autorité de récupération
ne sont **pas** modifiés par ce patch. La permission/transport IPC reste identique.

## Paramètres et propriété

Un unique LlamaBackend et un unique Engine par owner : le décorateur ne recrée
pas le moteur et n'acquiert/ne ferme aucun bail. Le backend garde tous ses try/catch,
les clôtures de baux existantes et la politique native de cleanup. Import et modèle
ont leurs propriétaires réels distincts ; une observation ne devient propriétaire
d'aucun des deux. Pas de double réservation P2Lifecycle/ProcessResources.

Aucun changement de RAM, réserve système, thermique, batterie, contexte, batch,
threads, modèle ou quantification. Le suivi est facultatif dans le constructeur
historique ; le chemin de fixture en serait le seul utilisateur. Le wrapper worker
reste cependant dans un fichier historique : cette modification demeure sensible
et n'est pas autorisée par le seul fait que le suivi soit nul par défaut.

## Frontières et verrous

Le suivi JNI ne ferme un token qu'après retour du delegate. Les tâches sont suivies
avant withContext, puis entrées/terminées dans le corps synchrone worker. Une tâche
annulée avant entrée reste comptée et marque UNKNOWN : pas de décrément déduit du
finally de l'appelant. INSPECT est exclu du compte de travaux de ressource, sans
masquer les autres tickets. Les imports sont synchrones et leur fin suit les use
fermant flux et baux ; aucun Job annulé n'est utilisé comme preuve.

Ordre de lecture : worker -> handleLock -> coordinateur (lecture courte), puis
sonde JNI et compteurs, puis relecture du coordinateur/compteurs. Les moniteurs
internes du tracer ne font pas d'appel JNI, d'attente ou de callback externe.
Le nativeLock de Runtime est relâché avant toute attente suspendue du worker.
Aucune attente n'est ajoutée sous le verrou de l'autorité de récupération.

Un échec de l'enregistrement **après** retour JNI est mis en quarantaine et ne masque
pas le résultat natif original. Une admission dans un compteur saturé peut refuser
avant tout JNI : refus fermé, pas allocation supplémentaire pour contourner le cap.
Un abort/crash sans retour garde l'opération non terminée ; un nouveau processus
ne récupère aucune preuve de ce seul fait.

## Limites qui restent fermées même si ce patch est approuvé

Le patch fournit une **instrumentation brute**, pas une connexion IPC ou une
qualification F3 complète. Il ne remplace pas UnavailableObservationPort, ne publie
aucun champ natif positif dans RecoveryInventory et ne modifie pas le registre
birth-only. Il ne branche pas encore freezeAdmission à toutes les admissions du
processus : avant cette exhaustivité démontrée, aucun snapshot ne peut servir à
une ATTEST F3 positive. COORD-001 n'est pas présumé disponible ou validé.

Le digest est une corrélation locale calculée sur les champs fermés du Request
historique de fixture, le request_digest et pilot_id enrôlé. Il ne devient pas une
autorisation /2. Le mandat natif /2 n'existe pas ; requireNativeAdmission reste un
refus inconditionnel avant traceFor/import/create/generate. Aucune extension du
contrat partagé n'est implicitement approuvée par ce diff interne.

Après close, le même tracer conservé peut rapporter un **destroy réellement rendu**.
La branche backend==null exige cet événement, jamais son seul vide. Sans événement,
clé, incarnation, baux et registre fiables : UNKNOWN. Il ne s'agit pas de lecture
OS de la mémoire, de RSS/PSS zéro ou de preuve de mort d'un processus Android.

Le port F3A positif, les reçus terminaux durables, les retries exacts sous barrière,
la corrélation finale des IDs de baux internes avec les mandats CORE et le laboratoire
Android restent des étapes distinctes. Le journal review-only F3A ne devient pas
un journal de libération. Aucune admission /2, aucun CORE004D, aucune installation.

## Validation explicite demandée

Autoriser **l'application et la compilation/tests** du patch exact identifié dans
PATCH.json, sur branche dédiée et sans installation, est la prochaine décision.
Les sept fichiers sensibles ne seront modifiés qu'après cette validation.
Cela ne donne aucun accord pour ouvrir /2, publier un reçu natif non vérifié,
changer le bundle, installer sur appareil ou lancer CORE004D.
