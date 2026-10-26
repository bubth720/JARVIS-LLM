# L4 — prérequis consommateur CORE, aucune liaison

Le contrat [jarvis.llm.client/1](../fusion-01/CONTRACT.md) est inchangé, SHA-256
`f29f8a9f3e10645fe1f25ea01913ccc8cf2ee883cbc45f7adca18645ac322b0f`.
P2/1.0, Binder expérimental /1 et bundle /2 restent distincts et exacts. Aucun
amendement des contrats UNIFIED ni import de leurs DTO sans revue.

| Besoin CORE | Port/type local existant | Condition avant consommation réelle |
|---|---|---|
| Demande approuvée | GenerateRequest(P2Request, priorité, profil, cible) | SYSTEM approuvé distinct ; documents non fiables ; gate hôte exact |
| Identité | OperationKey epoch/session/request/correlation/cancel/authorization/lease/generation + digest | UUID/digest identifient, n'authentifient ni n'autorisent |
| Événements | Ack, StreamChunk, Completed, Error | Origine FIXTURE rejetée en live ; CONTENT_DONE n'est pas un reçu natif |
| Annulation | Cancel(RequestReference exacte) | STOP_REQUESTED seulement ; pas de réadmission ou retry automatique |
| Ressources | NativeResourceState, projection ProcessResources | UNATTESTED, observation null inconnue, aucun arbitre global |
| Budget exact | TokenCountStatus / ContextPlanner | UNSUPPORTED fermé ; jamais estimation NEXUS |
| Erreurs validation | P2Exception/P2Error | Version/roles/tailles/UTF-8/refus de contexte restent explicites |
| Erreurs client | UNAUTHORIZED, NATIVE_UNQUALIFIED, UNSUPPORTED_PROFILE, TOKENIZER_UNAVAILABLE, UNKNOWN_STATE | Indisponibilité honnête, jamais réponse inventée |
| Sans LLM | Outils NEXUS/SERVICES sous autorité CORE | Le pilote document/CSV doit continuer sans dépendance artificielle à LLM |

La priorité ne confère pas de préemption ni de permission. Le consommateur fournit
l'horloge monotone et traite UNKNOWN comme fermé. L'idempotence RAM du moniteur ne
suffit pas après crash ; aucune réexécution sur perte d'ACK. La mémoire confirmée
n'a pas de champ spécialisé ici : pas de conversion silencieuse en SYSTEM.

## Transport à revoir séparément

Pas de service/socket ajouté. Un futur Binder signé devra conserver UID Binder réel,
package/certificat et permission de signature épinglés, pas une identité déclarée
par payload. Clés de test dédiées privées, pas de remplacement des signatures des
APK personnelles. Version/capacités, taille/profondeur avant parsing, délais, session,
replay, révocation, mort du client, processus et reprise doivent être revus avec
CORE/UNIFIED. Les fixtures existantes ne donnent pas un GO de production.

CORE minSdk24 et LLM Android minSdk29/ARM64 ne sont pas compatibles in-process sur
API24 ; aucune hausse de minSdk CORE proposée. Le domaine JVM ne qualifie pas à lui
seul un transport Android24 ou un AAR intégrable.

## Concurrence VOICE

ProcessResources protège seulement ce processus LLM. Il ne voit ni ONNX/VOICE ni
TTS d'un autre APK. Un snapshot vide ou fin audio ne prouve aucun unload de l'autre
moteur. Avant nouvelle résidence lourde : propriétaire unique, arbitre interprocessus
à concevoir/revoir, mandat/bail/incarnation exacts et preuve de fin native qualifiée.
Faute de preuve, refus conservateur ; aucune double réservation par le client ou
P2Lifecycle. Pas de connexion CORE004D, de coffre vocal partagé, de nouveau modèle
ou d'activation FULL_POWER. L'orientation Linux reste locale et non activée.
