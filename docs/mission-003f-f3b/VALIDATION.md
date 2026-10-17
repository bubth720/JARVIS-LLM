# F3B — tests exécutés et limites des preuves

## Résultats locaux

| Vérification | Résultat | Périmètre réel |
|---|---|---|
| Domaine JVM | 87 réussis | Suite historique |
| App JVM/Robolectric | 27 réussis | Suite historique, sans appareil |
| Fixture JVM/Robolectric | 118 réussis | 101 hérités +17 nouveaux candidats |
| Total JUnit | **232**, zéro échec/erreur/saut | +17 à la référence F3A215 ; aucun test historique retiré |
| CTest native_safety | 1/1 réussi | Code CPP historique, pas la sonde proposée |
| Gradle ARM64/Lint | Réussi | APK historiques, pas backend patché |
| git apply --check | Réussi | Sept fichiers, aucune application du patch |
| Préservation | 108 fichiers byte-identiques à aa85ad4 | Tout app/domain/fixtures, F3A inclus |
| Compilation candidats | F3Trace et F3NativeApi réussis | test sourceSet uniquement, sources compilées comparées aux candidates |
| Absence candidats des APK | Vérifiée dans les dex | Aucun F3Trace ajouté aux APK app/fixture |

Le DTO F3BackendObservation, LlamaBackend avec nouveaux hooks, ResourceCoordinator
modifié, AndroidRuntime modifiée et la sonde JNI proposée ne sont **pas** compilés.
Le contrôle d'applicabilité du patch ne remplace pas leur validation.

## Les17 tests F3B

- Nouveau tracer vide, STOP avec appel bloqué, abandon avant worker, tâches/imports
  séparés de JNI, cleanup en file et inspection ne masquant pas ce cleanup.
- Unload rendu sans sonde reste UNKNOWN ; sonde périmée/absente/malformée refusée ;
  modèle/contexte indépendants ; absence d'une source de baux ou incohérence = UNKNOWN.
- Échec de nettoyage conservé jusqu'à retry explicite rendu et sonde ; mauvaise clé,
  token étranger et mauvais worker refusés/UNKNOWN.
- Deux tests utilisent le **vrai backend et worker historiques** avec le décorateur
  candidat et un **NativeApi factice** : arrêt ne fermant pas l'appel bloqué, maintien
  des vrais baux ProcessResources, unload raté puis retry explicite.
- Deux fautes de l'observateur sont injectées dans le traceur synthétique : succès
  destroy non transformé en exception de cleanup, exception originale non masquée.
- create factice renvoyant0 : aucun état de naissance sain.

La génération de preuve n'est pas testée : il n'y en a aucune. Les valeurs UNLOADED
obtenues sur des flags/événements synthétiques sont des résultats d'algorithme,
pas des observations JNI. Le test du vrai worker reste UNKNOWN puisque le worker
historique n'a pas les nouveaux hooks de tâches et d'identité : assertion explicite.
Aucun timeout, Binder mort ou processus nouvellement vide ne sert de preuve.

Une validation Gradle intermédiaire a détecté une dépendance manquante entre la
copie des sources de test et la génération du modèle Lint. L'init script déclare
ces dépendances ; la validation complète suivante passe, sans modifier le runtime.

## Commandes reproductibles depuis la racine

```sh
python3 docs/mission-003f-f3b/build_patch.py --check
python3 docs/mission-003f-f3b/verify.py
git apply --check docs/mission-003f-f3b/SENSITIVE.patch
python3 tools/check-preservation-f3a.py --successor-f3b
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew -I docs/mission-003f-f3b/candidate.init.gradle :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
./tools/cloud-env.sh python3 tools/check-fixtures.py
python3 docs/mission-003f-f3b/verify.py --results
```

Les contrôles 003E/003F/D4E1, SHA /1-/2 et vecteurs Python/Kotlin, ainsi que le
vérificateur offline SHA d'APK, sont également conservés et rejoués. La CI archive
build/mission003f-f3b-validation.json, les XML JUnit/Lint et la documentation. Les
rapports machine séparent patchApplied=false, patchedBackendCompiled=false et
newJniProbeExecuted=false du résultat des tests isolés réellement exécutés.

Le switch explicite --successor-f3b du vérificateur F3A élargit uniquement la liste
des documents autorisés à docs/mission-003f-f3b/. Les comparaisons de tous les
fichiers historiques restent identiques ; le nouveau vérificateur gèle aussi les
sept fichiers de code/tests ajoutés par F3A, soit108 fichiers de référence.

## À tester seulement après approbation du patch

Compilation réelle du backend/DTO/coordinateur, tests des révisions de baux/ABA et
des sources complètes, annulation avant/durant dispatch modifié, observer fermé,
race import/stop/close, faute du capteur JNI et durée de vie des pointeurs,
JNI Linux avec GGUF public vérifié si disponible, puis laboratoire Android autorisé.
La CI actuelle peut rejouer le script JNI historique, mais ne peut pas prouver la
sonde inexistante dans son code compilé. Aucun essai physique n'est exécuté.
