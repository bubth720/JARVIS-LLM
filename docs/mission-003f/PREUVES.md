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
| Fixture JVM/Robolectric | 43 tests : 19 conservés, 24 nouveaux ; 0 échec/erreur/ignoré |
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

Total JUnit : **157**, contre 133 dans la référence 003E ; cela compte des tests,
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

## GitHub Actions effectivement réussie

[Exécution 39031742868](https://github.com/bubth720/JARVIS-LLM/actions/runs/39031742868),
statut global **Success**, commit source
`b2f6bc052871a2e4f604d9170f385b71c241837b`.

- [verify](https://github.com/bubth720/JARVIS-LLM/actions/runs/39031742868/job/117393460505) :
  préservation, vérificateur hors ligne, CTest, tests JVM/Robolectric, ARM64, Lint,
  APK/signatures/permissions/alignements, rapports et artefact du contrat réussis.
- [model-probe](https://github.com/bubth720/JARVIS-LLM/actions/runs/39031742868/job/117393460634) :
  modèle public vérifié, harnais JNI P2 Linux réel et publication des preuves réussis.
  Ce job conserve le script historique sans modification ; il ne teste pas le bootstrap /2.
- [memory-result](https://github.com/bubth720/JARVIS-LLM/actions/runs/39031742868/job/117394097385) :
  réussi, résultat Linux uniquement, aucune qualification physique Android.

Artefacts sur la page de cette exécution : `jarvis-llm-validation`,
`jarvis-llm-model-memory`, `jarvis-llm-003f-contract-candidate`.
Les JSON contiennent les empreintes APK/certificats propres à cette exécution,
ainsi que la provenance du modèle public Linux. Aucune clé, aucun GGUF ni donnée
personnelle n'est publié. Aucun APK n'est installé sur un appareil.

Le premier run `39031584293` de `ac678de` a été annulé par le mécanisme de
concurrence après le correctif `b2f6bc0` ; il est remplacé par le run réussi ci-dessus.
Une CI verte ne vaut pas acceptation 003F : F1/F2 et les preuves F3 restent bloquants.

## Publication

Branche : `fixture/003f-bootstrap-attestation`, sans fusion.

- `ac678de9b8c04f913b117d2d6bda034108523756` : contrat candidat, codec, autorité,
  registre de naissance, journal durable, contrôles et tests.
- `b2f6bc052871a2e4f604d9170f385b71c241837b` : refus des compteurs backend
  inconnus/multiples et propriétaires contradictoires, deux tests supplémentaires.
- Clôture documentaire sur la même branche, message `[skip ci]` : aucune modification
  du code testé ni du bundle normatif ; les preuves exécutées restent celles de b2f6bc0.

La branche main distante est restée à `917e3351f9b525857b6aa019e98d07872d8f6e76`.
Aucune branche n'a été fusionnée et CORE 004D n'a pas été lancé.
