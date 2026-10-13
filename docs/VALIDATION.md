# Validation Mission 002 — 13 octobre 2026

## Périmètre réalisé

Branche dédiée `mission-002-prototype`, application indépendante `fr.jarvis.llm`.
Diagnostic passif, import offline, conversation textuelle, adaptateur JNI CPU et protections de départ.
Aucune modification de CORE/VOICE, aucun modèle inclus, aucun test intensif sur appareil.
La phase 1 matérielle réelle et la phase 3 sur POCO/Redmi restent **non terminées** faute d'appareils accessibles.

## Résultats

| Vérification | Résultat |
|---|---|
| Tests JVM métier/sécurité/import | 22 exécutés, réussis |
| Test natif CTest | 1 exécuté, réussi : arrêt inter-thread, deadline, callback de chargement, abandon/reprise d'un graphe CPU réel, métadonnées modèles |
| Tests Android simulés Robolectric API 35 | 2 exécutés, réussis : démarrage sans activation JNI, refus sans modèle |
| Compilation ARM64 / APK debug | Réussie sur les sources finales |
| Lint debug | Dernière exécution réussie : 0 erreur, 11 avertissements |
| Permissions, signature, ELF/ZIP 16 Kio | Inspection réussie sur l'APK final |
| Qualification POCO / Redmi | Non exécutée |
| Génération avec un vrai Qwen GGUF | Non exécutée : destination Hugging Face refusée par le proxy cloud |
| GPU / NPU, consommation, chauffe, p50/p95 | Non mesurés |
| CI GitHub | Workflow fourni ; résultat distant non observé |

Le test natif n'est pas un benchmark ni une preuve de délai d'arrêt d'un vrai Qwen sur Android.
SHA-256 de l'APK inspecté : `6e0c86a672a22b282dce825d35c088dcd15b4fbdc192801a16f4ae3a60e165f1`.
Robolectric ne valide ni le firmware, ni les pilotes, ni Android 16 sur appareil.
L'APK peut être construit sans poids ; une compilation seule ne prouve pas la qualité ou le fonctionnement de l'inférence.
Les avertissements Lint portent sur le périmètre ARM64, l'icône absente, une suggestion KTX et des textes UI non localisés.
Ils ne sont ni supprimés par configuration ni présentés comme des erreurs corrigées.

## Reproduire dans ce cloud

```sh
./tools/setup-cloud.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
```

SDK/NDK et JDK vérifiés, sources llama.cpp épinglées. Le setup a été réexécuté avec succès sur les outils installés.
Les caches sont conservés ; aucun processus applicatif serveur n'est nécessaire.

## Suite autorisée des phases 1–3

1. Renseigner les fiches matérielles et le diagnostic passif.
2. Valider la plausibilité des capteurs et les budgets sur appareil avant essais prolongés.
3. Premier essai court ÉCO/0.6B : chargement, réponse, arrêt manuel et passage en arrière-plan.
4. Mesurer PSS, arrêt natif, récupération et température ; abandonner le profil si inadmissible.
5. Comparer ensuite 1.7B sur le téléphone, avec entrée/sortie standardisées.
6. Mesurer démarrage froid/résident, premier token, tokens/s, p50/p95, pic PSS, état thermique et batterie.
7. Déclarer les configurations réellement qualifiées et conserver les replis.

Critères provisoires : arrêt <=2 s, premier token p95 <=10 s téléphone /20 s tablette, réponse courte p95 <=60 s.
Ces objectifs ne sont pas atteints par les tests cloud présentés. Ne pas forcer une configuration qui les manque ou dépasse les protections.
RAG, catalogue massif, voix, actions, IPC et PLEINE PUISSANCE sur station restent hors de cette mission limitée.
