# Preuves 003F

## Vérifications locales réellement exécutées — 17 octobre 2026

Les premiers essais ont révélé deux API Android non publiques, puis deux échecs
ciblés (précondition propriétaire et SNAPSHOT incomplet). Les corrections sont
décrites dans RAPPORT.md : aucune substitution de fausse preuve pour rendre vert.

Résultats finaux sur l'arbre de travail livrable :

| Vérification | Résultat |
|---|---|
| Domaine JVM | 87 tests, 0 échec/erreur/ignoré |
| App JVM/Robolectric historique | 27 tests, 0 échec/erreur/ignoré |
| Fixture JVM/Robolectric | 41 tests : 19 conservés, 22 nouveaux ; 0 échec/erreur/ignoré |
| CTest natif Linux | 1/1 réussi |
| Compilation Android ARM64 | App historique et endpoint réussis ; trois clients de sonde compilés |
| Lint | 0 erreur ; app 32 avertissements, endpoint 10, client authorized 6 |
| APK | Signatures vérifiées, permission signature fixture, aucune permission Internet, alignements ELF/ZIP 16 Kio |
| Signatures | Certificat des fixtures autorisée/endpoint commun ; client erroné distinct ; historique distinct |
| Runtime partagé | Sources du backend copiées à l'identique depuis app ; pas de moteur parallèle |
| Préservation | 68 fichiers historiques byte-identiques à 003E ; tous les tests hérités conservés |
| Contrat | Digest /1 exact, préfixes ABI inchangés, tables fermées et vecteur Python/Kotlin vérifiés |
| Catalogue | Tests historiques SQLite/import/variantes/échelle conservés et rejoués |
| Vérificateur APK JS | Tous les cas hors ligne passent |

Total JUnit : **155**, contre 133 dans la référence 003E ; cela compte des tests,
**pas** un gain de mémoire, sécurité matérielle ou performance.

### Reproduction cloud

Depuis la racine de ce dépôt, avec l'environnement décrit par `tools/setup-cloud.sh` :

```sh
python3 tools/check-preservation-003e.py
python3 tools/check-preservation-003f.py
python3 tools/check-recovery-contract.py
node tools/test-apk-verifier.cjs
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
./tools/cloud-env.sh python3 tools/check-fixtures.py
python3 tools/publish-validation-003f.py
python3 tools/publish-catalogue-evidence.py
```

Les rapports `build/mission003f-{preservation,apks,validation}.json` et les XML JUnit/Lint
sont conservés comme artefacts CI. Ils portent explicitement
`bootstrapProductionReady=false`, `physicalQualification=false`,
`androidCrossApkExecuted=false`, `core004DStarted=false`.
Le bundle candidat public est un artefact de revue séparé ; aucune clé ni modèle.

## Empreintes

Version : `jarvis.fixture.p2/2` **candidate bloquée pour consommation CORE**.

Bundle candidat (CONTRACT.txt + TABLES.json + AIDL triés) :

`4b4129b595b78481d846c521993026fe2477c64fccfe4259ba9a5e30f9d68e62`

Bundle /1 figé :

`fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9`

Vecteur DISCOVER : voir `examples/discover.sha256`, contrôlé dans le test Kotlin.
Les signatures de fixture étant propres à l'environnement, les SHA des APK et du
certificat local ne doivent pas être utilisés pour une paire construite ailleurs.
Le rapport APK de chaque CI enregistre ses empreintes réelles distinctes.

## Ce qui n'a pas été exécuté localement

- Aucune installation ni transaction interprocessus sur Android/émulateur.
- Aucun essai physique POCO ou Redmi ; aucune donnée matérielle inventée.
- Aucun chargement/import de modèle pour le bootstrap.
- Aucun harnais JNI Linux avec un vrai modèle local : seul le vocabulaire des sources
  llama.cpp est présent, ce n'est pas un modèle de génération. Les accès API GitHub
  et Hugging Face sont refusés par le proxy local ; aucun contournement tenté.
- Aucun arbitre CORE ni mission CORE 004D.

Le workflow conserve le harnais Linux existant dans son job `model-probe` avec
modèles publics vérifiés ; un résultat CI doit être rapporté séparément lorsqu'il
est effectivement disponible. Il ne démontre ni le bootstrap /2 ni une qualification
Android physique. Aucun benchmark intensif sur appareil n'est lancé.

## Publication

Branche dédiée uniquement, sans fusion. Les identifiants définitifs des commits et
l'état CI sont ajoutés après publication. Une CI verte ne vaut pas acceptation
003F : F1/F2 et les preuves F3 restent bloquants.
