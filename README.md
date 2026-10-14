# JARVIS LLM — Mission 002C

Prototype Android textuel, autonome et hors ligne, branche `mission-002c-tablet`.
Phases 1–3 autorisées dans la conversation ; aucune fusion dans main. CORE et VOICE restent inchangés.
TABLETTE ÉCO : 512/64, batch 16/4, un thread, déchargement après 10 s ; ÉCO 768/96 conservé. Diagnostics PSS et buffers natifs. Budgets d'admission non abaissés sans preuves.
Lire [TABLET_002C.md](docs/TABLET_002C.md) : à 3,24 Gio disponibles sur 7,51 Gio accessibles, le premier chargement reste refusé (au moins 3,502 Gio requis). Comparaison réelle de GGUF sur Linux dans les rapports CI ; aucune assimilation au PSS Android.

## Périmètre

- Kotlin, adaptateur `LanguageModel` interchangeable, llama.cpp CPU/JNI épinglé.
- Android 10/API 29 minimum, cible API 36, ARM64 uniquement.
- Qwen3 0.6B / 1.7B GGUF Q4_K_M importés manuellement avec SHA-256 attendu.
- Conversation en RAM, contexte tokenisé, ressources bornées.
- Protections batterie/thermique/mémoire/PSS, annulation native et deadline.
- TABLETTE ÉCO, ÉCO, ÉQUILIBRÉ, AUTO ; PLEINE PUISSANCE refusée tant que non qualifiée.
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
La compilation requiert GitHub/Google/Maven ; la comparaison de modèles du workflow requiert aussi Hugging Face. L'APK fonctionne indépendamment de ces accès. Les poids téléchargés pour la comparaison cloud ne sont pas embarqués.

## Installation

La version Mission 002C **0.2.1** est publiée : [CI réussie](https://github.com/bubth720/JARVIS-LLM/actions/runs/38553690120), [APK, SHA-256 et guide](https://github.com/bubth720/JARVIS-LLM/actions/runs/38553690120/artifacts/11887534355), [mesures réelles des GGUF sur Linux](https://github.com/bubth720/JARVIS-LLM/actions/runs/38553690120/artifacts/11889260132).
Le [rapport TABLET_002C](docs/TABLET_002C.md) distingue les économies mesurées des limites Android. Les anciens artefacts 002/002B restent historiques.
Premier essai : [diagnostic passif uniquement](docs/FIRST_DIAGNOSTIC.md), sans importer ni exécuter de modèle.

APK debug : `app/build/outputs/apk/debug/app-debug.apk`, destiné aux essais contrôlés, pas à la distribution publique.
Au démarrage : diagnostic passif uniquement, aucun modèle chargé.
Ni le POCO ni la Redmi ne sont qualifiés par une compilation cloud.

- [Installation](docs/INSTALLATION.md)
- [Qualification matérielle](docs/DEVICE_QUALIFICATION.md)
- [Protections](docs/DEVICE_SAFETY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Validation](docs/VALIDATION.md)
- [Dépendances](docs/THIRD_PARTY.md)
