# Validation 003F-F3A

Date : 17 octobre 2026. Cloud Linux, aucune installation ni mesure physique.
Base de comparaison :003F-R1 `1ce2440` (185 JUnit rapportés, CI39058623037).
Identité du code et statut CI publiés en fin de document après exécution.

## Résultats locaux réellement obtenus

| Vérification | Résultat | Nature / limite |
|---|---|---|
| Domaine JVM | 87 réussis | Suite historique intacte |
| App JVM/Android simulé | 27 réussis | 21 Robolectric +6 backend/fixture budget, sans appareil |
| Fixture | 101 réussis | 71 historiques +30 nouveaux ; tous tests antérieurs inchangés |
| Ensemble JUnit | **215**, zéro échec/erreur/saut | +30 tests, aucune inférence de gain matériel |
| CTest native_safety | 1/1 réussi | C++ Linux, pas F3 post-native |
| ARM64 app + endpoint + clients | Compilation réussie | Pas installation |
| Lint | 0 erreur/fatale | Avertissements app32, endpoint10, client autorisé6 ; mêmes nombres que base |
| APK historique | signature, permissions, ARM64, ZIP/ELF16K vérifiés | Vérificateur existant ; clés locales ignorées |
| APK fixtures | Quatre APK vérifiées, runtime partagé exact | Signature commune test + client mauvaise signature distinct ; identité historique distincte ; aucun APK/keystore/modèle publié |
| Préservation F3A | 101 fichiers byte-identiques | app/domain/fixtures existants de la base |
| Préservation 003E/003F/D4E1 | Réussie | Contrats, catalogue, seuils, moteur, manifests et anciens tests |
| Contrats/vecteurs | Réussis | /1 et /2 figés, 22 contrôles Python approuvés + vecteurs SHA réel Kotlin/Python |
| Vérificateur SHA APK offline | Réussi | Banc Node, pas navigateur Android physique |

Le passage de185 à215 provient exclusivement des30 nouveaux cas. Les valeurs PSS,
RSS, batterie, température et performances physiques ne sont pas mesurées ici.
Le catalogue historique (dont jeu synthétique1500) est rejoué sans charger le LLM ;
les mesures de son banc Robolectric ne sont pas des gains Android.

## Nouvelles assertions

- ObservationTest9 : port absent, chaque provenance manquante/divergente, compteurs
  inconnus/négatifs, identité clé/bail, états handle/model/context, séquences,
  process recréé, STOP/DRAINED/CLEANUP_FAILED/UNLOADED distincts.
- BarrierTest7 : registre incomplet, toutes admissions bloquées par freeze, import
  en cours et cleanup en file, tickets obsolètes, recréation/quarantaine, course
  freeze/admission24 fois (threads JVM réels), plafond128 sans éviction.
- ReviewJournalTest6 : absence/enrôlement, aller-retour review-only UNKNOWN, corruption,
  perte record/tête/marker et sidecars, **10 frontières** d'interruption pour un append,
  échec directory-fsync injecté, plafond64 sans reset. fsync fichier réel Linux,
  DirectorySync factice ; pas de durabilité Android qualifiée.
- F3WorkerTest8 : vrai LlamaBackend partagé, fakeJNI injecté : échec create/load/capteur,
  panique retournée en JVM, arrêt répété et annulation coroutine pendant appel bloqué,
  double génération/import refusés par le vrai coordinator, unload sérialisé sur le
  même worker, unload/close ratés avec MODEL conservé, retry explicite même instance,
  retour effectif avant fermeture du bail, capteur indisponible distinct de zéro.

Les scénarios historiques de recréation service/process, reçu isolé non probant,
clé ancienne, journal corrompu, COMMIT/ACK perdu et44 frontières durables sont conservés.
Les tests contrôlent des ordres via latches ; un timeout est une limite du test,
jamais une preuve de libération. Le test de « panique » est une AssertionError
**rendue à Java par le fake**, pas un abort natif ni un crash OS réel.

Les essais intermédiaires ont détecté des erreurs du banc ajouté (codec Android
hors Robolectric, limite de checkpoints et exceptions de test/nettoyage). Elles ont
été corrigées ; aucune assertion historique ni politique n'a été retirée ou modifiée.

## Reproduction

```sh
python3 tools/check-preservation-003e.py
python3 tools/check-preservation-003f.py
python3 tools/check-preservation-d4e.py
python3 tools/check-preservation-f3a.py
python3 tools/check-recovery-contract.py
python3 tools/recovery-vectors.py
python3 docs/mission-003f-correction/reference/verify_vectors.py
node tools/test-apk-verifier.cjs
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
./tools/cloud-env.sh python3 tools/check-fixtures.py
python3 tools/publish-validation-003f.py
python3 tools/publish-validation-f3a.py
```

Le rapport JSON build/mission003f-f3a-validation.json indique le SHA testé, le détail
des30 tests et tous les flags de preuve natif/physique à false. Il est collecté par
l'artefact GitHub Actions jarvis-llm-validation, avec les XML JUnit et Lint.
La documentation est aussi collectée par jarvis-llm-003f-contract-candidate,
sans changer le bundle normatif.

## Non exécuté / bloqué

- Pas de port F3 branché au backend, pas de reçu terminal authentique.
- Pas de crash/abort natif réel ni de mort de processus Android.
- Pas d'émulateur/Binder entre APK ni appareil POCO/Redmi ; aucune qualification physique.
- Pas de nouveau modèle complet local disponible pour un test JNI/GGUF local ;
  la CI historique dispose de sa propre étape bornée Linux avec GGUF public vérifié.
- Aucun benchmark intensif, déploiement, fusion, admission native /2 ou CORE004D.

## Clôture CI et publication

Code testé/publié : **`1abf3264a7051cf4b8f25976b301938fa728af5a`**.

[GitHub Actions39072313347](https://github.com/bubth720/JARVIS-LLM/actions/runs/39072313347)
: **SUCCESS**, statut et conclusions de chaque étape consultés sur GitHub.
Détail machine conservé dans [CI.json](CI.json).

- verify117512278835 : tous contrôles de préservation, CTest, Gradle/tests/ARM64/Lint,
  APK historique/fixtures, génération des rapports et uploads réussis.
- model-probe117512279005 : tests natifs, comparaison courte bornée GGUF public
  vérifié et script JNI P2 Linux existant réussis. Le script exige p2-jni.json ;
  cette preuve du chemin historique n'est pas une preuve F3 post-native.
- memory-result117512782491 : succès ; mesure Linux uniquement, pas PSS Android.

Les215 JUnit sont décomptés localement dans les XML (87+27+101), avec0 échec/erreur/saut.
La CI froide réexécute les suites et les scripts vérifient les minimums et l'absence
d'échecs/erreurs/sauts avant l'upload. Les étapes publiques sont observées ; les XML
produits par GitHub sont dans jarvis-llm-validation, pas prétendument téléchargés ici.
Les logs locaux sont ignorés sous /tmp/f3a-* et les résultats de build sous build/.

Le commit de clôture suivant ne modifie que cette documentation et utilise [skip ci]
pour conserver ce run comme preuve du code exact. Aucun changement applicatif n'est
ajouté après la validation. La branche main distante est restée917e335.
Les empreintes APK/certificats locales ne sont pas des pins universels de laboratoire ;
aucune clé et aucun APK/GGUF ne sont ajoutés à Git ou aux artefacts de cette mission.
