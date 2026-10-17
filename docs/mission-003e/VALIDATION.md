# Validation et limites 003E

Code vérifié : **5751646acd4a7bb13caaea4b96a4ab3a68ec6adb** (17 octobre 2026 pour la clôture documentaire). Base : daa46432e847ed9613dc403045498e27281eff42. Branche : fixture/003e-p2-secure-endpoint. Le commit de clôture suivant ne change que les documents/preuves.

**[GitHub Actions 38975009901 : entièrement réussie](https://github.com/bubth720/JARVIS-LLM/actions/runs/38975009901)**. Le run précédent a été remplacé lors du correctif de sécurité ; il n'est pas utilisé comme preuve finale. Aucun résultat 003D antérieur ne remplace cette exécution.

Contrat filaire : jarvis.fixture.p2/1 ; bundle SHA-256 **fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9**. Vecteur de demande synthétique : a52661069b51817183e164e4e9444ed5cbbac5ca0d36a2e6195d768997e89eed.

| Vérification | Résultat réel |
|---|---|
| Domaine | 87 réussis, tests 003D conservés |
| App historique et backend | 27 réussis : 25 de référence + 2 budgets/worker fixture |
| Fixture sous Robolectric API35 | 19 réussis : contrat, digest croisé, identité simulée et pannes/états |
| Total JUnit | **133 réussis, 0 échec, 0 erreur, 0 ignoré** |
| CTest natif | 1/1 réussi |
| JNI Linux avec GGUF public vérifié | Réussi : génération/arrêt, mesure canonique et 512 demandé/768 chargé |
| ARM64 | APK historique et fixture endpoint compilées |
| Lint | 0 erreur ; CI : historique36 avertissements, endpoint10, client authorizedDebug6 |
| APK historique | Signature, permissions et alignements vérifiés ; pas de nouvel endpoint |
| APK fixtures | Signatures, packages, permission signature, service unique protégé, digest dans DEX, ABI/alignment et séparation de clés vérifiés |
| Shared runtime | Sources générées byte-identiques aux sources originales |
| Arbitre CORE réel | **Absent** ; chargement fermé |
| Binder entre APK / matériel | **Non exécuté**, aucune qualification physique |

La compilation des trois variantes clientes est vérifiée ; Lint client porte sur authorizedDebug, source commune aux trois variantes. Les 19 tests de fixture comprennent trois tests du résolveur Android/UID/certificat sous Robolectric et seize tests de contrat/contrôleur, avec faux RuntimePort/Authority explicitement de test. Ils ne sont pas des identités exercées entre processus Android réels.

Preuves : [EVIDENCE.json](EVIDENCE.json), [rapports de validation](https://github.com/bubth720/JARVIS-LLM/actions/runs/38975009901/artifacts/12079350883), [preuve JNI/modèles](https://github.com/bubth720/JARVIS-LLM/actions/runs/38975009901/artifacts/12078503192). ZIP annoncés par GitHub : respectivement 98fbcbc3d407ae3f2c344118905a894b364b2a385732affe7f0f6e10a1729fa3 et 19540f49bd394f450e9e3a37e743ef010bf740e92c26fab71c1b8fb4df511f2f. Les conclusions d'étapes et annotations publiques ont été examinées ; ZIP et logs détaillés non téléchargés.

GGUF réel du harnais : Qwen3 0.6B Q4_K_M public, SHA-256 ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a. Le script existant conserve la provenance/révision et vérifie la taille/SHA avant le test. Le harnais mesure le rendu exact, démontre la présence unique de la politique SYSTEM et refuse un texte qui tient dans768 mais dépasse512 demandé. CPU un thread, sorties courtes ; pas de test de stress Android et pas de gain matériel revendiqué.

APK endpoint CI contrôlée, **non distribuée et non installée** : SHA-256 49b321293e7d14c0195fe1934d1c29687c3b38b6288728dc458197315b51fa48. Certificat de fixture CI : 8d310c7616169a85fab59a7531f8bf2bcb54030f7e3b23e83c6494e5d1959103, identique aux sondes authorized/wrongPackage, différent de wrongSignature et de l'historique. Les APK locales ont une autre clé/empreinte ; ne pas les confondre avec celles du runner. Aucun keystore publié. Les artefacts de ce workflow contiennent uniquement des rapports, pas les APK ou modèles.

## Vérifications reproductibles

```sh
python3 tools/check-preservation-003e.py
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint --no-daemon
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
./tools/cloud-env.sh python3 tools/check-fixtures.py
python3 tools/publish-validation-003e.py
```

Le job model-probe reconstruit le JNI hôte et exécute le harnais Java sur le GGUF Qwen3 0.6B Q4_K_M public dont le SHA est vérifié. Les nouveaux cas comparent le rendu de mesure au rendu réellement partagé, l'unicité SYSTEM et le budget 512 demandé sur contexte768. Aucune simulation ne remplace ce harnais ; son statut est rapporté séparément. Un test de graphe CPU natif existant vérifie aussi l'abort callback.

Niveaux de preuve : domaine JVM ; backend/worker avec API native simulée ; endpoint et identité Android sous Robolectric API35 ; vrai JNI/GGUF Linux ; compilation ARM64 et inspection statique des APK ; Binder inter-APK non exécuté ; Android physique non exécuté.

## Restrictions maintenues

Aucune modification de SafetyPolicy, ResourceCoordinator, ProcessResources, DeviceMonitor, ModelStore, native_configuration, cancellation, model_check ou catalogue ; aucun service ajouté au manifeste historique. Les sources partagées générées sont comparées aux originales. Les tests 003D restent byte-identiques ; les nouveaux tests sont ajoutés séparément. Aucun seuil RAM/thermique/batterie abaissé, aucune activation PLEINE PUISSANCE, aucun benchmark sur appareil.

L'absence d'arbitre réel entraîne ARBITER_ABSENT avant import/chargement. Les faux brokers et moteurs ne sont présents que dans les tests. Une reprise après UNKNOWN/CLEANUP_FAILED n'est pas autorisée par timeout, disparition du client ou relance de l'Activity. Journal privé technique sans prompt, réponse, URI ou profil personnel ; les UUID ne sont pas des permissions.

## Blocages explicites

- Pas d'émulateur installé dans cet environnement : pas d'installation, de signature permission effectivement exercée par le noyau Android ou de Binder entre APK testé.
- CORE004C et son broker réel sont hors périmètre de ce dépôt : pas d'admission interprocessus réelle, pas d'E2E CORE→LLM.
- Pas d'accès physique POCO/Redmi : aucune mesure ni qualification Android physique.
- Clé de fixture commune durable pour une paire future à provisionner par canal privé autorisé ; les clés CI sont éphémères.
- Corpus synthétique volontairement fermé ; pas d'historique, RAG, mémoire ou documents. Le service ne transforme pas sa liaison en foreground ; absence d'Activity réellement reprise entraîne un refus.
- Un redémarrage avec journal non résolu reste UNKNOWN : la réconciliation multi-processus et la preuve de mort sont à réaliser côté CORE selon le contrat, pas un reset serveur.

Aucun APK installé, aucune migration/désinstallation, aucun autre dépôt modifié, aucune fusion. Prochaine action : revue de cette livraison puis mission CORE004C sur autorisation distincte, avant tout essai à deux processus ou sur appareil.
