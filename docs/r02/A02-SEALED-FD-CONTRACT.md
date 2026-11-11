# R02 A02 — Source GGUF immuable : contrat d'intégration

Statut : **prototype Linux en tests, PAS une admission native qualifiée**.

## Risque réel
`LabFileProof.verify` valide actuellement un chemin, un inode, la taille, les permissions et SHA-256, puis `requireStable` recontrôle des métadonnées. Un processus disposant des droits adéquats peut modifier les octets entre ces vérifications et la lecture du backend. Ce mécanisme n'est pas une preuve d'immuabilité.

## Architecture cible
1. Après validation d'une source privée, copier les octets vers un `memfd_create(MFD_ALLOW_SEALING|MFD_CLOEXEC)` privé, avec comptage strict et SHA-256 calculé **sur les octets copiés**.
2. Avant admission, imposer les quatre seals `F_SEAL_WRITE|F_SEAL_GROW|F_SEAL_SHRINK|F_SEAL_SEAL` et vérifier `F_GET_SEALS`. Rejeter tout mmap writable ou FD non scellé.
3. Transmettre le **même descripteur** au backend natif, sans rouvrir le chemin original. L'API native doit accepter un FD avec propriété de durée de vie explicite. Si le moteur exige un chemin, un chemin `/proc/self/fd/N` ne doit être utilisé qu'après vérification du FD et de sa stabilité jusqu'à fin de lecture.
4. Fermer tous les FD et supprimer toute résidence à la révocation ; échec ou état inconnu => fail closed.
5. Contrôler l'empreinte et la taille depuis le FD scellé avant chargement. Ne jamais charger le modèle depuis le chemin initial.
6. Tester remplacement du chemin, changement de permissions, hardlinks, write/truncate, modification pendant la copie, FD fermé, fuite de FD et lecture concurrente.
7. Tenir compte du coût mémoire : le modèle de 396704416 octets peut doubler temporairement son empreinte. Réserver cette mémoire avant la copie ; sinon refuser. Sur Android, vérifier la disponibilité réelle de `memfd`/seals et la compatibilité du moteur. Pas de fallback silencieux vers un chemin mutable.

## État d'avancement
- Prototype Linux de copie scellée et tests adversariaux : `tools/test-a02-sealed-snapshot.py`.
- CI : exécution ajoutée avant compilation Android.
- **Non réalisé** : passage du FD scellé au moteur JNI, admission de modèle réelle, mesures de pic mémoire, qualification Android.
- **A02 reste BLOQUANTE** tant que ces éléments ne sont pas qualifiés.

## Critères de passage
- Aucune lecture native depuis un chemin mutable.
- SHA-256 vérifié sur le FD scellé ; toutes les tentatives d'écriture échouent.
- Le moteur utilise ce FD précis pendant toute la consommation.
- Tests adversariaux et tests natifs/Android verts au commit exact.
- Aucun téléchargement de modèle ni inférence sans les autres barrières de confiance.
