# Validation de la branche P2 native

Résultats en cours de collecte. Ne pas réutiliser les preuves CI de la première branche contract-only comme preuves de P2 JNI.

Commandes :

```sh
python3 tools/check-preservation-003d.py
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
```

CI : ajoute le harnais JNI utilisant le vrai GGUF Qwen3 Q4_K_M. La comparaison Linux déjà existante reste limitée à des générations courtes ; aucun essai sur téléphone. Le JSON p2-jni.json indique le commit et le SHA du GGUF. Aucun modèle dans les artefacts.

Qualification physique : **EN ATTENTE UTILISATEUR** pour Redmi Pad SE et POCO X8 Pro. Aucun gain RAM, autonomie, thermique ou latence mobile revendiqué. Aucun déploiement et aucune fusion.
