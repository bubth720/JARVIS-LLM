# R02 A01 — Provisionnement local Ed25519 (préparation uniquement)

**Décision utilisateur :** conserver la clé privée sur son appareil personnel.
**Statut :** APPROBATION DU PRINCIPE UNIQUEMENT. Aucune clé créée, importée, installée ou autorisée. Admission native et génération réelles interdites.

## Frontières de confiance

- La clé privée Ed25519 ne doit jamais être envoyée dans GitHub, la CI, une conversation, un journal, une APK, un fichier de configuration synchronisé ou un artefact.
- Le composant de signature local doit utiliser Android Keystore **si** le fournisseur du terminal permet effectivement Ed25519 et une clé non exportable. Ne pas présumer ce support : le vérifier sur l'appareil réel. Sinon, refuser et proposer un support sécurisé compatible après accord explicite.
- Les tests peuvent créer des clés éphémères **uniquement** dans leur propre fixture, jamais pour activer la racine de production.
- Exporter seulement la clé publique DER SubjectPublicKeyInfo et son SHA-256 ; l'utilisateur doit confirmer l'empreinte par un canal indépendant du composant qui la propose.
- Ne pas accepter une empreinte fournie par le même acteur que le permis sans approbation explicite : cela permettrait l'auto-approbation.
- Une fois approuvée, figer la racine dans un diff minimal, avec vérification des préimages, tests de substitution de clé, révocation, rotation, refus en absence de racine et signatures liées à run/attempt/commit/nonce/TTL.
- Aucun fallback vers variable d'environnement, fichier local modifiable, clé de fixture ou clé générée par CI.
- Le modèle d'autorisation reste *par tentative*, pas par campagne entière. Aucune signature automatique.

## Étapes soumises à approbation séparée

1. Vérifier le fournisseur cryptographique, la non-exportabilité et les capacités de signature Ed25519 sur le téléphone (sans créer de clé).
2. Présenter le résultat, l'emplacement de conservation et le mécanisme de récupération/rotation.
3. Demander explicitement l'autorisation **avant** toute génération de clé.
4. Exporter la clé publique et calculer l'empreinte ; confirmation humaine indépendante.
5. Préparer une PR d'ancrage avec empreinte publique, revue de sécurité et tests de refus.
6. Ne jamais fusionner dans main sans validation explicite.

## Critères d'acceptation

- A01 demeure fail-closed tant que l'empreinte approuvée n'existe pas.
- Une clé privée n'apparaît dans aucun commit, log, rapport ou fichier d'artefact.
- Le test d'absence de racine et celui de clé substituée passent.
- Le mécanisme de signature est compatible avec le matériel réellement vérifié, pas supposé.
