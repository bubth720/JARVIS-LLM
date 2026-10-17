# Validation du lot D4-02E.1

Branche `fix/003f-d4-02e1-commit`, base111be993.
**CI [39058623037](https://github.com/bubth720/JARVIS-LLM/actions/runs/39058623037) entièrement réussie**
sur le code `9745599b3894619ae00d57b3aefab2b5ef2b5f35`.
Cette réussite valide le lot logiciel et les non-régressions disponibles, pas F3 ou un E2E Android.

## Vérifications locales dans l'environnement cloud

- Domaine :87 tests JVM ; application :27 tests ; fixture :71 tests, dont28 nouveaux.
  Total185 JUnit vérifié par les rapports, aucun test supprimé/ignoré.
- CTest Linux :1/1 ; aucun modèle chargé pour ces tests.
- Build app/endpoint ARM64 et clients de sonde ; Lint sans erreur.
- Inspection APK : signatures/certificats distincts, permissions, alignements ELF/ZIP16Kio,
  runtime partagé byte-identique, hash du contrat dans le DEX.
- Préservation003E/003F et D4E1 : backend/runtime historique/politiques/AIDL//1 inchangés.
- Vecteurs approuvés :22 contrôles Python ; vecteurs réels Python/Kotlin, dix messages,
  primitives C, Unicode et quatre engagements exacts. Le sentinel n'est pas le pin réel.
- Échecs initiaux de compilation/test dus à l'adaptation des anciennes attentes
  sont corrigés : chaque évolution est expliquée dans RAPPORT, sans ignorer de test.

## Reproduction

```sh
python3 tools/check-preservation-003e.py
python3 tools/check-preservation-003f.py
python3 tools/check-preservation-d4e.py
python3 tools/check-recovery-contract.py
python3 tools/recovery-vectors.py
python3 docs/mission-003f-correction/reference/verify_vectors.py
node tools/test-apk-verifier.cjs
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
./tools/cloud-env.sh python3 tools/check-fixtures.py
python3 tools/publish-validation-003f.py
```

Les rapports build/mission003f-*.json et XML JUnit/Lint sont des artefacts CI ; ils
marquent F3Implemented=false, nativeAdmissionEnabled=false, physicalQualification=false,
androidCrossApkExecuted=false et core004DStarted=false. Le job Linux modèle existant
reste séparé et ne démontre ni le bootstrap Android ni une preuve F3.
Aucun modèle local complet disponible ; les fichiers vocab de llama.cpp ne sont
pas des modèles de génération. Le JNI réel Linux a été rejoué avec succès par le job CI décrit ci-dessous.

## Couverture nouvelle

Négociation positive/écho, UID/cert/binding erronés, broker retiré, date expirée et
service mort ; SNAPSHOT propriétaire LLM, CORE_PREPARE RPC/roles invalides ; mutation
de tête/inventaire, proof discordante, base64/JSON non canoniques/tailles ; appartenance
COMMIT même après recalcul d'une preuve forgée, membre absent/inversé, set arbitraire ;
ACK exact/idempotent, corruption d'outbox, interruption COMMITTED→ACK, perte/expiration
ACK, expiration pendant fsync, seconde décision recalculée ;44 frontières I/O,
réservation de quatre records et hash de record non autoréférentiel.

Les tests injectent canal et inventaire techniques ; ils ne sont pas des tests
Binder multi-APK. Aucun émulateur, installation ou essai physique n'est exécuté.

## Résultat CI observé et publication

Statut global Success, vérifié sur la page publique du run, tous les steps réussis :

- [verify](https://github.com/bubth720/JARVIS-LLM/actions/runs/39058623037/job/117471781624) :
  préservation, tables/vecteurs, CTest, suites JVM/Robolectric, ARM64, Lint,
  APK/signatures/permissions/alignements et artefacts.
- [model-probe](https://github.com/bubth720/JARVIS-LLM/actions/runs/39058623037/job/117471781483) :
  modèle public vérifié et harnais JNI P2 Linux historiques réussis, aucun changement
  du runtime natif ; ce n'est pas un test du nouveau transport /2 ni une preuve F3.
- [memory-result](https://github.com/bubth720/JARVIS-LLM/actions/runs/39058623037/job/117472569605) :
  réussi ; valeurs Linux seulement, aucun gain Android ou budget d'admission modifié.

Artefacts : jarvis-llm-validation, jarvis-llm-model-memory,
jarvis-llm-003f-contract-candidate. Ils contiennent les résultats et empreintes des
APK/certificats propres à ce run et le bundle de revue ; aucune clé/GGUF/donnée privée.
Les limites physiques/Binder/F3 et les flags de blocage restent inscrits dans les JSON.

Commits de code publiés :

| Commit | Objet |
|---|---|
| 40be767f719cb576eb362fd1c413e2dd9bde116a | F1/F2, codec canonique, deux preuves, records et outboxes D4E1 |
| 6eff97c6d1ef281d3b051130c8425076a5a8b523 | Comparaisons par membre et références PREPARED exactes |
| 7fba4f56a4b6ad21667c8ee78bf0e48ad6c61c96 | Contrôle des deux composants épinglés et trois tests PM simulés |
| 9745599b3894619ae00d57b3aefab2b5ef2b5f35 | Quarantaine de liaison après rejet d'identité avant décodage |

Les runs intermédiaires sont remplacés par la validation complète de9745599.
La clôture documentaire est publiée séparément avec [skip ci], sans modification
normative ou du code testé. Le SHA du bundle demeure
`3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef`.
La branche main est restée à917e3351f9b525857b6aa019e98d07872d8f6e76 ; aucune fusion,
installation, migration, modification d'un autre dépôt ou mission CORE004D.
