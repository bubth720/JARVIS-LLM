# Dépendances et intégrité

- llama.cpp : https://github.com/ggml-org/llama.cpp, MIT, `1b70350c92563449576bcbbf552994b1d1ed847d`.
- GGML inclus à cette révision ; mentions upstream conservées dans le checkout de dépendance.
- Kotlin 2.1.21, AndroidX, coroutines : Apache 2.0 ; notices transitives à assembler avant distribution.
- Gradle 8.11.1 : distribution et wrapper vérifiés par SHA-256.
- Temurin 21.0.12.1+1 cloud : archive vérifiée par SHA-256 publié ; licences livrées avec le JDK.
- SDK/NDK Google : vérifications sdkmanager et licences de développement.
- Qwen3 : Apache 2.0 annoncée par le projet officiel ; poids non inclus, licence de conversion à vérifier séparément.

Sources moteur dans `.deps/`, ignorées. Script refuse autre révision ou modifications suivies, ne remplace pas un checkout utilisateur.
Aucun modèle, secret ou identifiant stocké dans Git.
Avant distribution publique : notices complètes, signature release stable et audit des obligations transitives.
La clé debug n'est pas une clé de production.
