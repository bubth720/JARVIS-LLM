# Matrice B01–B18 — preuves et limites

Les noms Bxx des tests ne sont pas une certification complète du scénario D4-02D.
Les inventaires et epochs injectés sont explicitement des fixtures hôtes.
Aucun émulateur ou couple CORE–LLM réel n'est exécuté.

| Cas | Ce qui est exercé | Ce qui reste à prouver |
|---|---|---|
| B01 | Application réelle sous Robolectric sans provisionnement, registre scellé, aucun backend/modèle ; refus faute d'epochs CORE | Bootstrap positif de la paire : bloqué F1/F2 |
| B02 | Journal relu à génération 7, sans remise à zéro ; refus INSPECT incomplet | Réconciliation fraîche des deux journaux |
| B03 | Phase ADMITTED héritée ne donne aucune ATTEST positive | Ancienne clé complète et vrai worker interrompu |
| B04 | Tests 003E d'arrêt/drain/conservation des baux préservés | Observation corrélée de ce worker dans /2 |
| B05 | Corruption de record/tête/intention/marqueur, fichiers .new/.bak/pending ; fsync injecté en échec ; conservation des fichiers | Coupure réelle du système de fichiers Android |
| B06 | Expiration de la fenêtre, epochs de service périmés, COMMIT contradictoire | Replays Binder entre deux processus |
| B07 | Tests 003E UID/certificat/shared UID/rotation conservés ; permission/build pins inspectés dans le code/APK | Client authentifié/refusé et callbacks entre APK ; remplacement de package réel |
| B08 | Empreinte TERMINAL seule rejetée ; chemins JNI/natifs historiques inchangés | Attestation /2 après fin/unload réel et journal durable |
| B09 | File BUSY empêche ATTEST ; toute admission native reste fermée | Instrumentation complète après activité native |
| B10 | Même autorité/process_epoch après renouvellement service_epoch ; ancien service refusé ; nouveau processus a un autre epoch | Destruction/recréation Android du Service avec vrai worker |
| B11 | COMMIT identique retourne ACK durable exact ; autre décision refusée ; échecs aux quatre frontières fsync de répertoire bloquent | Perte du callback réel et interruptions Android à toutes les frontières fichier |
| B12 | CLEANUP_REQUEST refusé, aucune attente du worker sous la barrière | Retry explicite même propriétaire après fin native réelle : F3 |
| B13 | Mélanges /1↔/2, digest/version erronés, champs/nonce/UTF-8 invalides ; ABI préfixe inchangée ; vecteur Python/Kotlin identique | Appel UNKNOWN_TRANSACTION vers ancienne APK sur Android |
| B14 | PREPARED empêche autre CHALLENGE ; admission native refusée, y compris après COMMIT | Course réelle snapshot/load lorsque le nouveau port de grant sera câblé |
| B15 | Ancien défi préparé refusé après nouveau process/service ; fenêtre bornée | Reboot Android et journal des deux pairs |
| B16 | Tête ancienne, marqueur absent et incohérences de séquences bloquent | Rollback coordonné des deux sandboxes : non démontrable par checksum |
| B17 | Registre non scellé/étranger/touché ne prouve jamais NOT_STARTED ; second propriétaire interdit | Reçu opérationnel exact après une activité native passée |
| B18 | 128 tours persistés ne sont pas évincés ; refus QUEUE_FULL ; exécuteur 1+1, callback hors verrou | Saturation Binder et latence réelle sous charge : non exécutées |

Les tests historiques d'EndpointController restent conservés comme non-régressions
003E ; le nouveau Service ne l'emploie pas pour annoncer une récupération /2.
Le harnais JNI Linux séparé, lorsqu'exécuté, ne certifie aucune ligne Binder ci-dessus.
