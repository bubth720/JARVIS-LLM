# PERF-03A — compatibilité CORE / INTERFACE

Module LLM ; dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03a-native-review`, base `09e278b54b8be58a3d665d2d91de10f8869e40bb`, tree `975fc71395ac7758678cb5af1b76858ee614c9b2`. Aucun fichier des dépôts complémentaires n'est modifié. Cette revue de sources n'atteste pas une intégration live.

## Références complémentaires lues

| Dépôt / référence locale exacte | Fichiers examinés | Observation |
|---|---|---|
| CORE `bubth720/JAVIS-Local-v2`, `5da98383ed12cb5a54e896fbe57560bacc432fb5` | `app/src/main/java/com/stark/jarvislocal/SessionCoordinator.kt`, `events/CoreSessionEvents.kt` | Le coordinator possède l'opération application ; `SessionActions.chat` retourne un ChatMessage complet. Annulation/timeout demandent l'arrêt et attendent le travail effectif. Les IDs d'événements sont métier, pas preuve de bail natif. |
| INTERFACE `bubth720/JARVIS-INTERFACE`, `b9a6c1997710c182dd481b23a47ff7120e3e98a0` | `app/src/main/java/com/jarvis/interfaceapp/ui/chat/core/CoreConversationClientAdapter.kt` | Client prototype UI ; accepte uniquement `CoreTextOrigin.LABORATORY` et état READY, ACK corrélé, déduplication/ordre bornés, refus UNKNOWN. Le laboratoire synthétique M50-S1B reste distinct d'une génération LLM réelle. |
| LLM base PERF-02 | `domain/.../Conversation.kt`, `client/GenerationClientContract.kt`, `generation/P2Contract.kt`, `generation/Core004BMapping.kt`, `resources/ResourceCoordinator.kt`, `runtime/RuntimePolicyProfiles.kt` | Port local gelé, pas de transport/autorisation live, tokenizer structuré UNSUPPORTED, état natif UNATTESTED. |

## Frontières qui doivent rester fermées

- `LanguageModel.generate` reste un `String` complet ; `EngineCapabilities.structuredCoreSession=false` et `interprocessArbiter=false` restent vrais.
- `GenerationClientPort` est un port local sans Binder/socket/composition runtime ; `ClientCapabilities.nativeReady=false`, `exactTokenCount=UNSUPPORTED`. `EventOrigin` n'admet que `FIXTURE` et `UNAVAILABLE` : aucun événement natif ne peut être déguisé en fixture pour passer le garde.
- `Core004BMapping.textOnly` lie `requestId==OperationKey.operationId`, exige contexte vide et aucune source ; il ne peut reconstruire fidèlement des documents depuis le contexte CORE plat. Aucun raccordement NEXUS n'est ajouté.
- `GenerateRequest.reference.requestSha256` couvre la demande logique en framing de longueurs. Il ne prouve pas à lui seul le rendu exact du template/tokenizer natif ni sa consommation sous le bon modèle.
- `ResourceCoordinator` garde ses identités réelles Long privées. Un `OperationKey.leaseId` UUID, `authorizationRef` ou hash fourni par l'appelant ne vaut pas bail attesté. Le singleton `ProcessResources` ne gouverne que son processus, pas les autres APK.
- Un `SessionCoordinator` sans tâche active, un ACK, une fin de contenu, un retour JNI, un nouvel epoch/PID ou un snapshot de file vide ne prouvent pas automatiquement DRAINED/UNLOADED.

## Proposition versionnée de liaison future

Le callback natif candidat est local au backend et ne change aucun contrat CORE/INTERFACE gelé. Sa liaison de revue utilise le domaine versionné `jarvis.llm.stream/1` ; un futur adaptateur doit être explicitement composé sous le propriétaire existant. Ce domaine désigne un candidat, pas une capacité active du client historique. Pas de réseau, IPC, permission, service, scheduler, moteur ou connexion VOICE/NEXUS/SERVICES ajouté.

Le patch proposé expose seulement `generateOwnedStream` interne pour un message user et un system prompt P2. Il ne convertit pas l'historique multi-tours ou les documents en un champ texte et ne qualifie pas une connexion générale du chat. Il réutilise `measureFixtureP2` puis vérifie le rendu/tokenizer exact avant prefill ; cette mesure réelle de formatter n'autorise pas à déclarer `NativeTokenCountPort` structuré disponible. L'autorité candidate obligatoire n'a aucune implémentation productive et ne contourne donc pas les refus des ports existants.

| Champ / fait | Source d'autorité requise | Contrôle avant admission et avant rendu |
|---|---|---|
| epoch, session, request/operation, generation | CORE propriétaire de la session et incarnation courante | Égalité exacte, ancienne génération invalidée, aucune promotion depuis métadonnées seules |
| authorization / mandat CORE | Autorité CORE validée, pas un booléen de fixture | Mandat valide pour cette opération, cancellationId et ressources ; aucun mandat auto-créé par LLM/UI |
| bail et propriétaire | Broker réel sous lecture atomique, relié à MODEL/HEAVY existants | Même propriétaire, identité/révision/incarnation exactes ; ancien bail ne libère ni ne réutilise un nouveau bail |
| modèle et SHA-256 | Import volontaire et vérification modèle existants | Poids/configuration effectivement résidents, capacités admises, pas simple sélection catalogue |
| empreinte demande logique | `GenerateRequest.reference` existante | Framing canonique, limites/profil/target/documents liés sans perte |
| empreinte préparation exacte | Formatter/tokenizer natif qualifié | Même SYSTEM/template/octets rendus/tokens/contexte/modèle ; aucune estimation UTF-8 à la place d'un compte exact. Le candidat local refait la préparation et vérifie son égalité, il ne fournit pas encore un prepare/consume/discard structuré général. |
| séquence et provenance | Backend du worker existant | ACK0, vrais chunks ordonnés, terminal unique, erreurs contradictoires UNKNOWN, aucune substitution du String final découpé |
| preuve de fin / cleanup | Propriétaire natif et broker attestés | CONTENT_DONE, DRAINED, UNLOADED séparés ; nettoyage échoué=CLEANUP_FAILED, preuve absente=UNKNOWN |

Les événements `ACK`, `TOKEN`, `COMPLETED`, `CANCELLED`, `ERROR` proposés restent des événements de contenu et de contrôle. CORE garde l'autorité d'admission et d'annulation. Les références sensibles de mandat/bail ne sont pas des données à afficher ni à logger. Les mesures n'exportent pas de prompt/réponse/chemin privé.

Le `StreamResident` candidat donne à `StreamOwnerAuthority` l'objet `ResourceLease` réellement détenu et l'incarnation locale du worker/modèle. Il ne rend pas publiques les identités internes du broker. Le `StreamPrepared.requestSha256` cadre les octets locaux et la configuration ; il n'est pas identique par définition au fingerprint logique client/1. Le futur mandat doit attester leur correspondance et ne jamais se contenter de recopier un hash fourni par une fixture.

## Adaptateur INTERFACE à préparer après validation de périmètre

Le futur adaptateur différentiel doit vérifier la clé complète, la provenance approuvée, la séquence et les bornes avant toute mutation du texte. Le premier fragment non vide réellement généré peut être remis au rendu à la frame suivante ; l'ACK et les statuts restent exclus du TTFT. La file se draine par batch borné ; un historique logique unique conserve le texte accepté sans reconstruire toute la conversation pour chaque token.

Stop conserve le texte déjà affiché selon une politique explicite, invalide le texte encore en attente et interdit tout nouveau rendu après annulation/terminal/changement de session. Il demande l'arrêt sous la clé exacte et attend le terminal, puis les preuves de ressources séparées. Rotation ne redémarre pas l'inférence ; fermeture force l'arrêt et la désinscription, pas une libération supposée. Tout retour tardif d'une ancienne incarnation est refusé.

Les budgets existants INTERFACE (jusqu'à16Kio/chunk configurables, 256Kio cumulés) ne négocient pas automatiquement les limites expérimentales03A : le laboratoire futur utilisera des limites explicites plus strictes de4096octets/chunk,16Kio en attente,64Kio cumulés et16événements, sans relever les protections d'un composant. Une fenêtre de réordonnancement n'autorise jamais une contradiction de séquence.

## Verdict de compatibilité

| Critère | État03A |
|---|---|
| Sources, contrats et applications historiques préservés | À attester par le garde/CI de préservation au commit exact ; aucune mutation prévue par03A |
| Signatures historiques disponibles dans le patch proposé | À vérifier par la revue exacte et les tests statiques ; compilation JNI candidat après GATE-A |
| Schéma de liaison et adaptateur future définis | PROPOSED, aucune activation |
| CORE → LLM réel avec mandat, bail, préparation attestés | BLOCKED : contrats/attestations et composition manquent |
| INTERFACE affichant de vrais tokens LLM | NOT_RUN, nécessite périmètre et implémentation validés |
| M50-S1B synthétique utilisé comme régression | Référence historique seulement ; ne qualifie pas le LLM réel |

Décision architecturale attendue séparément de l'accord JNI : autoriser ou refuser la préparation de branches complémentaires CORE/INTERFACE pour la liaison exacte des mandats, baux et événements. Aucune modification de ces dépôts n'est implicitement autorisée par ce dossier. Sans cette décision et les preuves requises, l'intégration réelle reste fermée ; aucun garde n'est affaibli.
