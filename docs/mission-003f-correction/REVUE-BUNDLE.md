# Remise du bundle exact pour revue — CORE004D toujours bloquée

Ce document est une liste de contrôle de revue, pas une autorisation d'installation,
de génération native, d'intégration ou de lancement de CORE004D.

## Identité du bundle

- Branche : `fix/003f-d4-02e1-commit`, base111be993.
- Transport : `jarvis.fixture.p2/2`, cible approuvée D4-02E.1.
- Fichiers normatifs : CONTRACT.txt, TABLES.json et les quatre AIDL sous
  fixtures/p2-contract/src/main/aidl/fr/jarvis/fixture/p2/ ; six fichiers au total.
- SHA bundle : `3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef`.
- SHA TABLES seuls : `0bca700a2a926b841896c999f89edc7e1b82fdbbd0ced3f5923e5f4cfbbfbe44`.
  Ils sont byte-identiques à la référence UNIFIED approuvée, pas un substitut au hash du bundle.
- /1 : snapshot et Wire inchangés ; digest fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9.
- AIDL byte-identiques à111be993 ; endpoint1..5, arbiter1..2, callbacks complete1.

## Vérifications de consommation future, après mission et validation séparées

1. Recalculer le digest du bundle depuis les octets exacts, refuser l'ancien candidat
   4b4129b5 et /1 sans downgrade. Le nom /2 seul ne suffit pas.
2. Reproduire exemples/engagements Kotlin/Python et les contrôles de canonicalisation,
   bornes, rôles et appartenance aux preuves locales ; ne pas utiliser les nonces
   synthétiques fixes ou contract64zéros comme des valeurs de session.
3. CORE possède ses propres epochs et journaux. Ses CORE_PREPARE, CORE_PROOF_OUTBOX,
   DECISION_OUTBOX et FINALISED seront à réaliser dans sa mission ; aucun n'est écrit
   pour lui depuis LLM. SNAPSHOT LLM ne prétend jamais lire son journal.
4. Vérifier les UID/certificats/permissions et callbacks sur deux processus Android
   dans un laboratoire autorisé ; ce n'est pas démontré par Robolectric ou la compilation.
5. Livrer et qualifier séparément F3. Les journaux de contrôle/COMMIT ne remplacent
   pas les preuves de retour JNI, drain, unload et conservation/libération des vrais baux.
6. Obtenir une revue réussie de ce bundle et des preuves nécessaires avant CORE004D.

Le contrôle /2 ne délivre aucun mandat natif, même après COMMIT_ACK.
Les anciens journaux /2 ont un format/pin différent : aucun import, reset ou
changement de sandbox pour les faire accepter. Une future nouvelle paire de
laboratoire nécessite une autorisation explicite, des pins APK/certificats exacts
et une clé commune dédiée hors Git ; aucune procédure d'installation n'est lancée ici.

## Exemples de référence

Les deux membres complets et le COMMIT avec le vrai SHA figurent dans examples/vectors.json.
Leurs engagements (synthétiques, sans autorité) sont :

| Engagement | Valeur |
|---|---|
| CORE_PREPARE | 22d33630c291207b39ae9da2eefa7a8f04a8f85069dd1df04a77eb1407d2f019 |
| ATTEST | ef7bb738af48efe6dd8e367dcee33ed79c2ecfeb49d030cc47e64ad64b4ea1f8 |
| prepare_set | 3e26231eb3bffb4c30e816de9e803bf212f8aac4c68c9b475169f7e499f5e77e |
| decision | a93c086ddbb1c87dd41efa159b661c28a39c4bb717b0343b87ef7b6927397ab6 |

La copie immuable des vecteurs approuvés avec le sentinel est dans reference/.
Les métadonnées réelles de CI/APK sont dans VALIDATION.md et les artefacts du run,
aucune empreinte locale de certificat n'est promue en pin universel.
