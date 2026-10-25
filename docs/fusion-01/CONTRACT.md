# L1 — API cliente locale, non native

Révision `jarvis.llm.client/1`. Le descripteur exact est [CLIENT-CONTRACT.json](CLIENT-CONTRACT.json),
avec [empreinte SHA-256](CLIENT-CONTRACT.sha256). Il ne modifie ni P2/1.0, ni les
contrats Binder expérimentaux /1 et /2, ni les contrats partagés UNIFIED.
**Aucun transport, endpoint, worker ou générateur natif n'est raccordé.**

GenerateRequest contient P2Request validée et immuable, priorité INTERACTIVE/BACKGROUND,
profil existant et cible explicite ANDROID_LOCAL/LINUX_LOCAL. Une priorité est une
métadonnée pour une future file, jamais une préemption ou une permission. Le décodeur
Fields est fermé, refuse inconnus/doublons/versions/types invalides et réutilise le
validateur P2. Ce n'est pas un parseur JSON, AIDL ou un format exportable tel quel.
Un futur transport devra borner octets/profondeur **avant** désérialisation.

OperationKey conserve epoch/session/opération/corrélation/annulation/autorisation/bail/
génération. RequestReference ajoute SHA-256 de tous les champs pertinents (ordre et
encodage dans le descripteur). Le digest n'est **ni une signature ni un grant** ;
ClientAuthorizationGate appartient à l'hôte, refuse par défaut et reçoit la requête
exacte, priorité incluse. Une exception d'autorisation ferme la demande. Même avec
un gate favorable, UnavailableGenerationClient retourne NATIVE_UNQUALIFIED, jamais
une réponse. FULL_POWER Android retourne UNSUPPORTED_PROFILE. Linux n'a pas de backend.

SYSTEM reste distinct. Les documents sont non fiables ; aucune conversion en SYSTEM,
autorisation ou commande. Les limites P2 (SYSTEM/question8192 octets chacun, historique
≤6 messages alternés/4096 chacun, ≤4 documents4096 chacun, agrégat≤inputBytes≤65536)
sont conservées. Contexte demandé512..2048 multiple256, sortie1..256, délai1..90000ms
ne valent pas admission : SafetyPolicy et contrôles SMALL/MAIN historiques restent
obligatoires avant toute future exécution. L1 n'en lance aucune. TokenCountStatus est
**UNSUPPORTED**, sans nombre approximatif. ContextPlanner existant reste l'autorité
de planification P2 : refus si tokenizer exact indisponible, pas d'élagage SYSTEM/question.

## Flux borné et annulation

ClientStreamMonitor est une projection RAM d'une seule requête ; pas une nouvelle
machine de résidence. En mode live, seul Error/UNAVAILABLE est accepté. En mode
fixture explicite, Ack0 → StreamChunk1.. → Completed est affiché comme
**FIXTURE — sans moteur LLM**. Le producteur synthétique n'existe qu'en sources de test.
Chunks≤4096 octets UTF-8, total≤65536, maximum258 événements (séquences0..257).
Un chunk n'est pas un token ; cette borne de transport n'est pas un budget modèle.

Rejeu identique avant état terminal : DUPLICATE, sans texte ajouté. Contradiction,
séquence sautée, mauvais digest ou mauvaise origine : UNKNOWN et contenu effacé.
Autre OperationKey : STALE, sans perturber l'opération actuelle. Après état terminal,
les événements sont ignorés comme TERMINAL. Aucun ACK n'est une autorisation native.
Annulation exacte : STOP_REQUESTED, texte effacé ; répétition idempotente. Completed
tardif n'atteste aucun déchargement. Délai monotone expiré ou horloge régressive :
UNKNOWN ; aucun reset, aucune réadmission. L'hôte doit fournir une horloge monotone,
non une heure issue du client distant. Les appels au moniteur sont synchronisés.

NativeResourceState reprend une observation passive optionnelle de l'unique broker ;
termination/unload sont exclusivement UNATTESTED et mayAdmitAnotherEngine est toujours
false. Un broker vide est EMPTY_UNATTESTED. CONTENT_DONE décrit du contenu seulement.
Cancel côté port indisponible retourne une observation inconnue, sans promesse JNI.

LocalClientReadinessPort emprunte l'instance VerifiedModelCatalog fournie ; aucun
second catalogue, handle, worker, bail, modèle ou sondage périodique. Son observation
est ponctuelle et non une décision atomique d'admission. Pas de composition dans
MainActivity, de modification du parcours autonome ou de lecture d'un autre package.

## Compatibilité avec CORE et les autres modules

| Besoin consommateur | Correspondance LLM | Disponibilité |
|---|---|---|
| session/request/correlation/generation | OperationKey, digest de requête | Domaine testé, mapping futur explicite requis |
| prompt approuvé | P2Request.system | Validation locale ; pas de transport/auth distante |
| RAG NEXUS | DocumentExcerpt.reference/revision/text | Données non fiables bornées ; provenance complète doit rester côté CORE/NEXUS |
| mémoire confirmée | Aucun champ dédié | NON REPRÉSENTABLE : ne pas dégrader en SYSTEM silencieusement |
| priorité/deadline | Priority, remainingMillis | Métadonnées locales, pas de scheduler natif |
| streaming UI | Ack/StreamChunk/Completed/Error | FIXTURE seulement ; aucun token JNI streamé |
| stop | Cancel avec RequestReference exact | Demande locale ; fin JNI non attestée |
| fin de résidence/réadmission VOICE | NativeResourceState | UNATTESTED, admission /2 fermée |
| budget exact | TokenCountStatus | UNSUPPORTED ; pas d'estimation NEXUS substituée |
| CORE004D / protocole /2 | Contrats historiques inchangés | BLOCKED ; pas de GO ni raccordement CORE |
| CORE API24/25/26 | app LLM min29/ARM64 | In-process Android incompatible ; pas de hausse de minSdk CORE |
| mini-PC | ExecutionTarget.LINUX_LOCAL | Port conceptuel local ; pas de serveur/backend activé |

Aucune preuve de contrat ne qualifie le transport, une identité Binder, une autorisation
CORE, le JNI Android ou un appareil physique. Un consommateur doit conserver le refus
et ses capacités désactivées ; ne pas traiter ce lot comme un modèle disponible.
