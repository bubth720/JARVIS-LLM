# LAB-ADMISSION-01 — revue seulement

Base PR #14 : `2500a123a69b059bf21ac6bec24be5a52b54a9c5`. Branche `codex/llm-lab-admission-review`. L'accord utilisateur autorise **la préparation du diff**, pas son application.

- [Architecture, autorité et ordre des preuves](ARCHITECTURE.md)
- [Patch exact NON APPLIQUÉ](LAB-ADMISSION.patch), [SHA256](PATCH.sha256), [préimages/postimages et modes](PREIMAGES.json)
- [Contrats proposés](CONTRACTS.md), [matrice de risques](RISK-MATRIX.md)
- [Plan de tests et qualification réelle](TEST-PLAN.md), [rollback](ROLLBACK.md)
- [Stratégie CORE réelle, documentaire](CORE-INTEGRATION-STRATEGY.md)
- [Rapport de portée et résultats](REPORT.md)

Les postimages sont des données sous `labadmission/postimages/`, jamais des sources du build :app. Le banc JVM les compile isolément ; il ne fait aucun `git apply` autre que `--check`. Les 457 fichiers historiques sont figés, y compris tout le JNI C++ approuvé. Aucun modèle n'est téléchargé ou chargé, aucune APK installée, aucun dépôt CORE/INTERFACE modifié, aucune fusion ou dépense de service.

Aucune clé d'opérateur ni signature utilisable n'est fournie. Les fixtures de signature/mémoire/native servent seulement aux tests de composants. GATE-B, génération réelle, performance p95 et Android demeurent **NOT_RUN / NON VALIDÉS**. Le SHA de PATCH.sha256 est celui à examiner avant toute nouvelle autorisation ; le moindre changement du patch requiert une nouvelle validation.

La CI dédiée publie les résultats du commit exact. Une CI de revue verte ne corrige pas le FAIL historique `Protected baseline drift: app/src/main/AndroidManifest.xml`, qui reste inchangé. Les liens et chiffres définitifs de la campagne sont complétés dans la PR après lecture des artefacts, sans les présumer PASS à partir du workflow.
