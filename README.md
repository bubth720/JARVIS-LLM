# JARVIS LLM — Mission 003B

Prototype Android textuel, autonome et hors ligne, branche `mission-003b-resources`, issue de `mission-003-catalogue`.
Diagnostic mémoire volontaire et coordination des ressources dans le processus ; budget d’admission inchangé. Lire [l’analyse 003B](docs/MISSION_003B.md), [le protocole tablette](docs/ESSAIS_MEMOIRE_003B.md) et [la validation](docs/VALIDATION_003B.md).
Phases 1–3 autorisées dans la conversation ; aucune fusion dans main. CORE et VOICE restent inchangés.
Catalogue privé SQLite, 8 réponses de démonstration, variantes facultatives et import/export JSON versionné. Recherche structurée avant LLM, clarifications en cas de conflit. Embeddings désactivés ; interface conservée pour une qualification future.
Lire [CATALOGUE_003.md](docs/CATALOGUE_003.md) et [les instructions d'essai](docs/TEST_CATALOGUE_003.md). Les profils et protections Mission 002C sont conservés ; aucune baisse du budget LLM.

## Périmètre

- Kotlin, adaptateur `LanguageModel` interchangeable, llama.cpp CPU/JNI épinglé.
- Android 10/API 29 minimum, cible API 36, ARM64 uniquement.
- Qwen3 0.6B / 1.7B GGUF Q4_K_M importés manuellement avec SHA-256 attendu.
- Conversation en RAM, contexte tokenisé, ressources bornées.
- Protections batterie/thermique/mémoire/PSS, annulation native et deadline.
- TABLETTE ÉCO, ÉCO, ÉQUILIBRÉ, AUTO ; PLEINE PUISSANCE refusée tant que non qualifiée.
- Aucune permission réseau ou d'accès aux données, aucun microphone, télémétrie, action ou connexion CORE/VOICE.

Catalogue éditorial local livré. Documents/RAG, mémoire de conversation persistante, voix, actions et IPC appartiennent aux phases suivantes.

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

Mission 003 **0.3.0** publiée : [CI réussie](https://github.com/bubth720/JARVIS-LLM/actions/runs/38583319277), [APK, SHA-256, guides et fixtures](https://github.com/bubth720/JARVIS-LLM/actions/runs/38583319277/artifacts/11903829000), [rapports de validation](https://github.com/bubth720/JARVIS-LLM/actions/runs/38583319277/artifacts/11904785815). Les artefacts précédents restent historiques.
Premier essai : [catalogue en mode avion sans modèle](docs/TEST_CATALOGUE_003.md). Un changement de clé debug peut empêcher la mise à jour : sauvegarder les fichiers originaux avant toute désinstallation.

APK debug : `app/build/outputs/apk/debug/app-debug.apk`, destiné aux essais contrôlés, pas à la distribution publique.
Au démarrage : diagnostic passif uniquement, aucun modèle chargé.
Ni le POCO ni la Redmi ne sont qualifiés par une compilation cloud.

- [Installation](docs/INSTALLATION.md)
- [Qualification matérielle](docs/DEVICE_QUALIFICATION.md)
- [Protections](docs/DEVICE_SAFETY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Validation](docs/VALIDATION.md)
- [Dépendances](docs/THIRD_PARTY.md)
