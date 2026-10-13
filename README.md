# JARVIS LLM — Mission 002

Prototype Android textuel, autonome et hors ligne, branche `mission-002-prototype`.
Phases 1–3 autorisées dans la conversation ; aucune fusion dans main. CORE et VOICE restent inchangés.

## Périmètre

- Kotlin, adaptateur `LanguageModel` interchangeable, llama.cpp CPU/JNI épinglé.
- Android 10/API 29 minimum, cible API 36, ARM64 uniquement.
- Qwen3 0.6B / 1.7B GGUF Q4_K_M importés manuellement avec SHA-256 attendu.
- Conversation en RAM, contexte tokenisé, ressources bornées.
- Protections batterie/thermique/mémoire/PSS, annulation native et deadline.
- ÉCO, ÉQUILIBRÉ, AUTO ; PLEINE PUISSANCE refusée tant que non qualifiée.
- Aucune permission réseau ou d'accès aux données, aucun microphone, télémétrie, action ou connexion CORE/VOICE.

Catalogue, documents, mémoire persistante, voix, actions et IPC appartiennent aux phases suivantes.

## Construire

JDK 21, SDK 36, NDK 28.1.13356709, CMake 3.22.1, Git et outils C/C++ requis.
Gradle Wrapper 8.11.1 et empreinte de distribution inclus.

```sh
./tools/prepare-native.sh
./gradlew :domain:test :app:lintDebug :app:assembleDebug
./tools/test-native.sh
python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
```

Cloud Linux x86_64 :

```sh
./tools/setup-cloud.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:lintDebug :app:assembleDebug
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
```

Les outils sont installés dans `.tools/`, les sources moteur dans `.deps/`, sans modèles.
Utiliser le checkout cloud existant ; ne pas créer de worktree pour les tâches ordinaires. Aucun serveur à démarrer.
La compilation requiert GitHub/Google/Maven ; l'APK fonctionne indépendamment de ces accès.

## Installation

APK debug : `app/build/outputs/apk/debug/app-debug.apk`, destiné aux essais contrôlés, pas à la distribution publique.
Au démarrage : diagnostic passif uniquement, aucun modèle chargé.
Ni le POCO ni la Redmi ne sont qualifiés par une compilation cloud.

- [Installation](docs/INSTALLATION.md)
- [Qualification matérielle](docs/DEVICE_QUALIFICATION.md)
- [Protections](docs/DEVICE_SAFETY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Validation](docs/VALIDATION.md)
- [Dépendances](docs/THIRD_PARTY.md)
