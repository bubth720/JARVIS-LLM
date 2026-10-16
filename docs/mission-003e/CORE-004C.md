# Guide de consommation pour CORE 004C — sans modification de CORE ici

## Artefacts à figer

Prendre un commit précis de cette branche, les AIDL sous `fixtures/p2-contract/src/main/aidl/fr/jarvis/fixture/p2/`, CONTRACT.txt et l'empreinte de bundle donnée dans VALIDATION.md. Le namespace filaire est **jarvis.fixture.p2/1**, distinct de P2/1.0, engine/1.0 et UNIFIED v1. Générer les mêmes interfaces AIDL (mêmes packages, ordre de méthodes et paramètres). Le manifeste de fixture CORE doit utiliser la permission signature `fr.jarvis.fixture.llm.permission.BIND_P2` et déclarer la visibilité du package LLM. Ne pas importer l'APK LLM comme une bibliothèque.

La sonde authorized de ce dépôt n'est pas CORE. Elle ne doit pas être confondue avec la future application fr.jarvis.fixture.core et ne contient pas de broker. Aucun historique, warm-up CORE, VOICE ou repli automatique ne participe à ce pilote.

## Séquence exigée

1. Vérifier le package serveur fr.jarvis.fixture.llm, son UID, son certificat courant unique appairé et l'absence de rotation. Lier explicitement `fr.jarvis.fixture.llm/fr.jarvis.fixture.llm.P2EndpointService` ; aucun Intent implicite.
2. `hello`: objet fermé de trois chaînes version/contract/session_id, plus IBinder de liveness appartenant au client. Vérifier version/digest, conserver le nouvel epoch serveur réel. Ne jamais fabriquer un epoch depuis le compteur Long de CORE. Les capacités disent que l'autorité CORE externe est requise et qu'aucune qualification physique/E2E n'est acquise.
3. Le **vrai broker CORE** doit être implémenté côté fixture CORE, en service explicite `fr.jarvis.fixture.core.P2ArbiterService` protégé par la même permission. Il doit authentifier l'UID/package/certificat de LLM, journaliser la réservation exclusive, vérifier génération/expiration/digest/autorisations et callbacks authentifiés. Son AIDL IP2Arbiter est fourni ici, mais aucune autorité prête à approuver n'est fournie dans LLM.
4. Construire une demande nouvelle, complète et versionnée. Le [vecteur synthétique](examples/p2-request.json) n'est pas un mandat ni un epoch utilisable : remplacer les IDs par ceux enregistrés dans CORE, epoch par hello et SHA par celui du GGUF public réellement préparé dans la fixture. Nombres transmis comme chaînes décimales canoniques. SYSTEM reste séparé, éventuellement vide explicite.
5. `submit` renvoie AUTHORIZING/état courant, **pas une preuve d'admission**. LLM interroge le broker réel avec la clé, digest, UID réels, modèle et plafonds. Voir CONTRACT.txt pour les champs exacts. Le broker répond à IP2AuthorizationCallback.complete avec l'écho exact plus approved et expires_elapsed_ms absolu selon elapsedRealtime Android. Un UUID d'autorisation seul est sans valeur.
6. Interroger `status` avec la clé complète inchangée. Refus de transport/ACK perdu : consulter l'état, jamais rejouer submit ou démarrer un autre moteur. `cancel` avec la même clé est idempotent ; STOP_REQUESTED ne signifie ni nativeDone ni unloaded.
7. Vérifier clé, request_digest, modèle, limites et reçus de fin. Accepter un texte seulement après UNLOADED, effective_end/model_unloaded/release_confirmed explicites, sans annulation/expiration. CORE renseigne son nativeDone depuis ce reçu, pas depuis un défaut true. Conserver sa propre quarantaine après UNKNOWN/CLEANUP_FAILED ; un retry ou nouveau contrôleur ne la supprime pas.

## Sérialisation et digest

JSON UTF-8 strict, un objet plat de **chaînes**, <=24576 octets ; champs inconnus/manquants/dupliqués, nombres JSON natifs, rôles, historique/documents/mémoire/sources et chemins sont refusés. Voir CONTRACT.txt pour les bornes. Aucun découpage de contexte aplati CORE et aucune suppression silencieuse.

Digest indépendant de l'ordre/espaces JSON : uint32 big-endian du nombre de champs, puis pour chaque clé ASCII triée, longueur uint32BE/UTF-8 de clé puis longueur/UTF-8 de valeur. Aucune normalisation Unicode. `examples/p2-request.sha256` est un vecteur croisé Python/Kotlin testé. Le digest du bundle de contrat se calcule séparément à partir de CONTRACT.txt + AIDL triés, chemin relatif UTF-8, NUL, octets exacts, NUL (LF imposés par .gitattributes).

Pilote volontairement fermé à deux questions et trois SYSTEM synthétiques listés dans CONTRACT.txt. Ajouter un autre corpus nécessite une évolution concertée du contrat, pas l'activation de requêtes documentaires réelles. Le système reste configurable parmi ces valeurs synthétiques ; aucune donnée personnelle n'est requise.

## Refus et suite de validation

UNAUTHORIZED_UID/CERTIFICATE, INCOMPATIBLE, INVALID_P2, SYNTHETIC_ONLY, UNKNOWN_OR_DUPLICATE_FIELD, INVALID_TYPE/UTF8, INVALID_LIMIT, STALE_SESSION/OPERATION, REPLAY, BUSY, SESSION_QUOTA, ARBITER_ABSENT/TIMEOUT/LOST, UNVERIFIED_LEASE/EXPIRED_LEASE, SAFETY_REFUSAL, MODEL_UNAVAILABLE_OR_MISMATCH, CONTEXT_EXCEEDED, NATIVE_FAILURE, JOURNAL_FAILED, CLEANUP_FAILED, UNKNOWN_STATE, CLOSED : conserver le code et la phase. Ne pas parser les messages d'exception JNI.

Contrôles de contrat/identité sont préparés dans ce dépôt, mais **Binder entre deux APK et E2E CORE→vrai LLM Android non exécutés**. Émulateur absent et CORE 004C/broker non livrés. Prochaine validation : mission CORE autorisée distinctement, puis essai isolé à deux processus avec certificat/contrat/commits figés et protocole de panne. Installation sur Redmi/POCO et qualification physique restent interdites sans autorisation dédiée.
