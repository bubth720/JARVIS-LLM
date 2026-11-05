# PERF-03A — revue du diff natif, arrêt avant application

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03a-native-review`.
Base de revue exacte : commit `09e278b54b8be58a3d665d2d91de10f8869e40bb`, tree `975fc71395ac7758678cb5af1b76858ee614c9b2`. La PR PERF-02 #10 est fermée sans fusion ; son contenu n'est pas supposé présent dans main.

**Statut : patch proposé, NON APPLIQUÉ.** [PREIMAGES.json](PREIMAGES.json) lie le SHA256 du patch, les préimages Git/SHA256/modes et les postimages complètes présentes sous `candidate/`. `make_patch.py` reconstruit exactement ce diff puis lance uniquement `git apply --check`. La vérification sèche ne charge aucun JNI modifié et ne prouve ni sa sûreté ni une génération réelle.

## Contenu exact à examiner

| Cible du patch | Proposition | Effet sur le chemin historique après une éventuelle approbation |
|---|---|---|
| `app/src/main/cpp/bridge.cpp` | Export additionnel `NativeBridge_generateStream`; callback synchrone du même worker pendant la vraie boucle de sampling/décodage ; registre de handles opaques monotones et pin de streaming | Les anciens noms, paramètres et types JNI restent présents. Les handles deviennent des identifiants opaques non réutilisés, sans changement de signature. Les erreurs/stale handles sont refusés ; cette évolution est sensible et exige la revue du diff. |
| `app/src/main/cpp/stream_support.h` | Assembleur UTF-8 incrémental indépendant, bornes avant allocation, erreurs persistantes | Nouveau header utilisé uniquement par le chemin stream proposé ; aucune source productive n'inclut ce fichier dans 03A. |
| `app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt` | Méthode interne explicitement opt-in `generateOwnedStream`, même CAS `generating`, dispatcher/worker, handle, baux MODEL/HEAVY ; Stop invalide la file active et le drapeau natif | `LanguageModel.generate` reste un résultat complet. Aucun client historique n'appelle la nouvelle méthode, aucune capacité CORE live n'est activée. |
| `app/src/main/java/fr/jarvis/llm/engine/OwnedStreamContract.kt` | Autorisation/grant explicites, empreintes exactes, file bornée, révocation atomique, événements typés | Aucune implémentation productive de `StreamOwnerAuthority` n'est fournie. Le simple DTO, UUID ou SHA ne confère aucun droit CORE/broker. |

Le CMake, les flags de compilation, la quantification, le greedy sampler, le prompt fixe, le template officiel, les contextes, les batch/microbatch, les threads, les budgets et les seuils de sûreté ne sont pas modifiés. L'alignement Android 16 Kio demeure une propriété à revérifier sur le futur binaire appliqué. Aucun autre dépôt n'est modifié.

## ABI et flux proposé

```text
NativeApi.generateStream(handle, systemUtf8, questionUtf8, outputCap,
                         contextLimit, bindingSha256Ascii, OwnedStreamCallback) -> terminal Int
onNativePrepared(actualRenderedUtf8, actualTemplateUtf8, actualInputTokens) -> Boolean
onNativeEvent(bindingSha256Ascii, kind, sequence, utf8, sampledTokens,
              firstNativeTokenNanos, nativeClockDomain) -> ACCEPTED | RETRY | CANCEL | ERROR
```

Types wire : ACK=0, TOKEN=1, COMPLETED=2, CANCELLED=3, ERROR=4 ; ACCEPTED=0, RETRY=1, CANCEL=2, ERROR=3. Le callback est final et ne délègue à aucune UI, aucun transport, aucun arbitre CORE. `authorize` est appelé une fois **avant** le JNI, hors `handleLock` et hors verrou du broker. L'adaptateur d'autorité devra être local, borné, sans réentrance ou attente de travail sur le même worker. Aucun de ces raccordements n'est présumé qualifié.

La première version proposée est **texte seulement : un message user et un système P2 séparé**, conforme à la voie fixture existante. Elle n'étend pas silencieusement le streaming aux historiques multitours, documents ou contrats NEXUS. Les signatures historiques continuent de traiter leurs cas existants.

1. Le backend conserve son propriétaire et ses baux réels. Il mesure le rendu/template/tokenizer canonique via `measureFixtureP2` sur le worker existant.
2. Une empreinte length-framed SHA256 couvre les octets système/question/rendu/template, le modèle logique, le contexte demandé et résident, le cap et les threads. Elle est **distincte** du hash de `GenerateRequest.reference` du contrat gelé ; le futur adaptateur doit démontrer leur liaison exacte.
3. L'autorité explicitement fournie doit vérifier le mandat CORE, la copie modèle volontairement vérifiée/SHA, le vrai bail broker et sa révocation. Le grant lie request/session/epoch/generation/leaseRef/modelSha/mandateSha, la préparation exacte, l'incarnation worker et l'incarnation de résidence. `ownerSequence` est un ordinal monotone propre au propriétaire, distinct de la génération CORE ; il empêche une reconsommation locale sans imposer un ordre global aux sessions CORE.
4. Le backend exige les mêmes instances `prepared` et `resident`, une révocation non déclenchée et un nouvel ordinal. Une ancienne préparation/grant ne peut être réutilisée. L'instance de `ResourceLease` sert de corrélation locale ; elle ne publie pas l'identité privée du broker. L'autorité **ne doit jamais fermer, recréer ou transférer ce bail**. Aucune autorité concrète qualifiée n'existe dans ce lot.
5. Avant ACK/prefill, `onNativePrepared` compare de nouveau le vrai rendu natif, le template et le nombre de tokens à la préparation autorisée. Les doublons de formatage/tokenization sont donc conservés pour la sûreté. Aucune optimisation prepare/consume native à usage unique n'est revendiquée ici.
6. La même boucle llama.cpp échantillonne chaque token non-EOG. Elle marque le véritable premier sampling avant conversion. Les pièces brutes passent immédiatement dans l'assembleur UTF-8 puis dans le callback ; aucune découpe du résultat final n'est utilisée.
7. Un retour de contenu ne ferme ni modèle, ni bail, ni scope d'autorité CORE. L'adaptateur d'affichage futur devra aussi annuler sa propre file et ses callbacks de frame lors d'un changement de session.

## UTF-8, ordre et texte

L'assembleur conserve au plus trois octets de caractère incomplet, refuse surlongs, continuations invalides, substituts et scalaires hors plage. Les chunks complets ont au plus 4096 octets. Le texte accepté conserve exactement ses octets et son ordre ; rien n'est remplacé par U+FFFD. Un suffixe incomplet à la fin devient ERROR.

La restriction expérimentale sur NUL/DEL et contrôles ASCII hors TAB/LF/CR est un **refus explicite**, jamais une suppression de token. Elle peut rendre une réponse admissible historiquement non terminable sur le candidat ; les tests de comparaison de contenu réel devront le révéler. Elle n'autorise pas à déclarer l'identité de toutes les sorties avant qualification. Le premier token natif peut être vide ou ne former qu'une partie d'un caractère ; il est distinct du premier texte utilisable et du premier commit de frame.

Le compteur `sampledTokens` observe les vrais tokens non-EOG. Le cap demandé n'est pas substitué au compte réellement produit. La séquence est stricte ; seul le dernier événement identique peut être accusé de nouveau sans affichage, les contradictions/gaps/changements de binding/clock deviennent UNKNOWN. Le tampon ne constitue ni un transport IPC fiable ni un nouveau `GenerationClientPort` productif.

## Bornes et backpressure

Les limites candidates restent logiques : **16 événements maximum, dont un slot réservé au terminal ; 16 Kio de texte en attente ; 4096 octets par chunk ; 64 Kio cumulés**. Elles ne constituent pas une mesure de PSS/RSS ou un budget Android validé. Les copies JNI temporaires, les objets JVM, la dernière émission pour déduplication, le scratch token natif, les empreintes et métadonnées sont des coûts additionnels à mesurer.

RETRY conserve strictement le même payload et la même séquence : aucun token accepté n'est perdu, aucune génération suivante n'est anticipée. Le même worker attend par tranches de 10 ms sans verrou du registre, du handle ou du broker. Il vérifie le cancel existant et sa deadline ; aucun second worker/moteur n'est créé et aucune deadline n'est remise à zéro pour maintenir artificiellement le modèle résident. Un consommateur absent finit par arrêt/erreur, jamais par une file illimitée.

Stop révoque le grant actif, efface le texte en attente non affiché et demande le véritable cancel natif. Le texte déjà affiché appartient à l'historique UI et devra être marqué partiel selon la politique approuvée. Les terminaux CANCELLED/ERROR restent des métadonnées ; ils n'émettent plus de texte. Stop après terminal producteur peut encore effacer la file UI non rendue. Une file vide ne vaut pas un drain.

## Références JNI, exceptions et durée de vie

Le callback est l'argument `jobject` local de l'appel Java→native synchrone. Il reste enraciné pendant cet appel et n'est **jamais retenu au-delà de son retour**, ni appelé depuis un autre thread. Il ne nécessite donc ni `NewGlobalRef`, ni attachement d'un nouveau thread JNI. Toute future variante asynchrone changerait cette preuve et exigerait une revue distincte. Ce point précise la mention conservatrice d'une global ref dans la proposition PERF-02.

Chaque callback utilise un `PushLocalFrame` borné. Les allocations/lookup/calls JNI sont contrôlés ; une exception Java de callback est conservée, et aucun autre callback n'est lancé sous une exception JVM en attente. Les opérations JNI de nettoyage autorisées (`PopLocalFrame`, `DeleteLocalRef`) libèrent les références temporaires. L'erreur native tente un terminal métadonnées ; si cette tentative échoue, le résultat demeure ERROR/UNKNOWN et ne devient jamais succès. Aucun retry automatique de génération n'est réalisé.

Le registre proposé remplace les adresses réutilisables par des `jlong` opaques monotones, refuse l'épuisement et les IDs absents. Un pin exclusif protège toute la durée de chaque appel sensible (historique comme streaming), et le nouvel appel reste épinglé jusqu’au dernier callback synchrone : `begin`, `load`, les voies de mesure/génération, `unload` et `destroy` refusent un moteur occupé. Le pin de load reste détenu pendant son cleanup en erreur ; un refus BUSY ne décharge jamais le moteur d’une autre opération. `cancel` conserve seulement une courte section atomique sous registre ; aucun callback/llama_free n'est exécuté sous ce verrou. Le cleanup de destroy se fait après retrait du handle, hors verrou.

**Limite essentielle :** les voies historiques conservent la discipline sérialisée existante du backend. Le registre ajoute un refus explicite des appels natifs concurrents et une protection de durée de vie ; il n’est pas un nouvel ordonnanceur autorisant des appels arbitraires simultanés à NativeBridge. Les tests de courses begin/cancel/destroy, réentrance, exceptions, UAF et ABA sur le binaire candidat restent obligatoires après GATE-A. Une compilation syntaxique ou des tests de helper ne les attestent pas.

## États de contenu et ressources

| État | Signification exigée | Preuve disponible ici |
|---|---|---|
| CONTENT_DONE | Terminal de contenu accepté pour l'opération exacte | Prototype / résultat de contenu ; pas une libération |
| DRAINED | Barrière exhaustive : appels, callbacks, tâches, files et imports de la même incarnation terminés | UNKNOWN : non fourni par le simple retour `generateStream` |
| UNLOADED | En plus, modèle/contexte réellement libérés et baux exacts fermés avec reçus corrélés | UNKNOWN : le modèle reste résident après génération |
| UNKNOWN | Identité/retour/preuve absente, process death, contradiction ou saturation | Admission fermée ; aucun lancement d'un autre moteur par compensation |
| CLEANUP_FAILED | Échec de nettoyage observé par le propriétaire | Quarantaine historique conservée ; aucune libération fictive du bail |

`jniInvocationFinished` observe seulement la fin de l'invocation Kotlin, y compris une erreur de liaison ou de callback. Il n'affirme même pas à lui seul que le JNI a été entré. Un `OwnedStreamOutcome.nativeReturned=true` n'est produit qu'après un retour terminal normal de la méthode native ; ce retour ne prouve toujours ni drain ni unload. Une mort de processus ne peut produire un reçu après coup. Le retour terminal natif doit correspondre au terminal effectivement accepté par le tampon ; COMPLETED exige aussi la préparation canonique validée. À toute erreur d’invocation/callback ou contradiction, le tampon devient UNKNOWN et efface le texte non rendu. Le callback est clôturé de façon monotone en finally ; un appel tardif est refusé avant toute mutation du tampon ou du résultat déjà établi.

## Horloges et qualification non acquise

Le natif propose CLOCK_BOOTTIME sur Android et CLOCK_MONOTONIC sur hôte ; le domaine est transporté explicitement dans chaque événement. L'adaptateur futur doit vérifier le boot/domaine et calibrer toute frontière avant soustraction avec une trace UI/CORE. Aucun chiffre d'hôte n'est assimilé à `elapsedRealtimeNanos` Android. Les marqueurs ACK, catalogue, spinner et pièces UTF-8 incomplètes ne qualifient pas un premier affichage.

Les seuls tests indépendants non sensibles peuvent couvrir le helper, les buffers et le backend JVM avec NativeApi simulé. Les JNI hôte réel, ASan/UBSan du bridge modifié, compilation ARM64 appliquée, comparaison ancien/nouveau sur même GGUF, mémoire réelle, Android API29/35 et appareils physiques restent distincts. Leurs statuts effectifs sont consignés dans le rapport central, sans assimilation de fixtures à de vrais tokens modèle.

## Décision attendue à GATE-A

Examiner **le SHA256 exact de `NATIVE-STREAMING.patch` référencé par le manifeste et le commit de la PR**, puis autoriser ou refuser son application sur la branche distincte `codex/llm-perf-03b-native-stream`, en laboratoire seulement. Cette décision ne vaut ni fusion main, ni activation productive, ni autorisation de modifier CORE/INTERFACE, ni campagne personnelle, ni validation de l'objectif p95 <3000 ms.

Le patch F3B historique `be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374` reste distinct et non appliqué ; PERF-03A n'accorde aucune approbation implicite à ce diff. Les lacunes d'autorité réelle, tokenizer structuré, reçus de drain/unload et compatibilité client/2 restent à résoudre selon les validations prévues.
