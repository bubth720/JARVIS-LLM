# Validation du lot D4-02E.1

Branche `fix/003f-d4-02e1-commit`, base111be993. Les identifiants définitifs de
publication et le statut GitHub Actions sont consignés après le push.

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
pas des modèles de génération. Le JNI réel ne sera rapporté que si son job CI réussit.

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
