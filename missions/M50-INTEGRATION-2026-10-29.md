# LLM-M50-01 — Un seul LLM réel avec protocole CORE et budget Android

Dépôt : bubth720/JARVIS-LLM
Branche mission : mission/m50-integrated-beta-20261029
SHA base : 6ae8a4f5c2490f6c9814e08957909a8e3a807180

**DOCUMENT DE MISSION UNIQUEMENT — PAS DE CODE MODIFIÉ PAR CE FICHIER.** Objectif : préparer les modules optionnels pour JARVIS M50 tout en respectant indépendance et fonctionnement local. Codex crée une nouvelle branche de développement depuis la branche mission, tests et PR brouillon. Sans fusion main, activation sensible, installation utilisateur, migration ou changement de licence/ABI/minSdk sans validation humaine.

## Fichiers et contrats (vérifier présence)
- `docs/fusion-02/CORE-CONSUMER.md`
- `docs/fusion-02/NATIVE-REVIEW.md`
- `app/src/main/java/fr/jarvis/llm/client/LocalClientReadinessPort.kt`
- `domain/src/main/kotlin/fr/jarvis/domain/client/GenerationClientContract.kt`
- `app/src/test/java/fr/jarvis/llm/NativeBackendLifecycleTest.kt`

## Phases et vérification

### 1 — A — Préservation modèle

Rejouer 303 cas JVM/Robolectric du lot1; confirmer Java/Kotlin/API29, modèle GGUF, budgets charge/tokenizer, contrats P2 et JNI/ARM64 aux SHA exacts. Comparer patch natif non appliqué sans l'activer.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 2 — B — Port CORE versionné, initialement OFF

Produire un client injectable strict qui refuse lorsque le backend natif n'est pas qualifié, quand le modèle manque, lors de contenu non fiable et quand l'admission CORE n'est pas approuvée. CORE doit rester unique orchestrateur; LLM unique moteur et pas de second scheduler.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 3 — C — Moteur réel en laboratoire

Si un modèle licite est déjà disponible dans un environnement autorisé, vérifier concrètement chargement, génération de quelques tokens, streaming, Stop, cancel, JNI crash et libération native. Sinon indiquer NOT_RUN; ne pas télécharger de poids automatiquement ni prétendre valider la génération depuis un mock.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 4 — D — Concurrence et thermique

Mesurer mémoire résidente au pic, retour après unload, time-to-first-token, débit, CPU, chauffe/batterie et concurrence VOICE sur appareil autorisé. UNKNOWN/unload non attesté bloque une deuxième admission; FULL_POWER strictement exclu d'Android, réservé au matériel futur.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 5 — E — Matrice Android et rapport

Séparer API24 de CORE et API29 du moteur LLM, fournir stratégie de modules conditionnels sans augmenter minSdk CORE. CI domain/app/native/ARM64/build/lint au HEAD, documenter limitations et retour arrière avant toute connexion réelle.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

## Conditions communes M50
- Les modules indispensables de l'APK initiale sont CORE, UI et un service local de documents / CSV ; LLM natif devient indispensable pour dire « conversation IA réelle » et doit être qualifié séparément.
- VOICE et EVOLUTION ne doivent pas être des dépendances bloquantes : un échec d'audio ou de diagnostic n'affecte ni chat ni documents.
- Ne jamais présenter un simulateur, une compilation ou un refus correct comme une véritable intégration Android en production.
- Ne pas copier secrets ou modèles protégés dans le dépôt et n'utiliser aucune connexion réseau au runtime.

## Bilan requis
Module, mission, branche, commit 40 caractères, arbre, tests pass/fail/skip/not-run, ABI/API, CI URL, PR brouillon, blocages et prochaine action. Aucun merge main. Toute action sensible nécessite confirmation explicite.
