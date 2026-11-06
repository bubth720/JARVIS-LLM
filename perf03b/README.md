# PERF-03B — application expérimentale du patch approuvé

Dépôt : `bubth720/JARVIS-LLM`. Branche : `codex/llm-perf-03b-native-stream`.
Base autorisée : `ca9d8f060856e134d793d0c23764fae45ce2cc53`.
Patch SHA256 : `747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460`.

[Preuve d'application](APPLICATION.json), [rapport](REPORT.md), [matrice de qualification](QUALIFICATION-MATRIX.md), [preuves CI](CI-EVIDENCE.md), [benchmarks / PERF-03C](BENCHMARK-PREP.md), [rollback](ROLLBACK.md).

L'accord GATE-A autorise les quatre postimages exactes dans cette branche isolée. Aucun autre changement du patch, aucun F3B, aucune fusion main, activation productive ou modification CORE/INTERFACE n'est autorisé. Les chemins historiques sont gelés hors ces quatre cibles. Les nouveaux bancs et le laboratoire sont des ajouts séparés.

## Exécution cloud

Le workflow `PERF-03B approved JNI laboratory qualification` est déclenché uniquement par push sur cette branche, afin de ne pas doubler automatiquement les tests natifs lors de la création de la PR. Il compile une référence fraîche, le JNI réellement appliqué, ses tests hôte et sanitizers, les régressions, puis le laboratoire ARM64 distinct. Aucun modèle n'est téléchargé. Un échec critique produit `CRITICAL-STOP.json`, bloque les étapes dépendantes et conserve les preuves ; ne pas relancer pour obtenir un meilleur résultat.

```bash
python3 perf03b/check.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s perf03b -p 'test_*.py' -v
```

Les [tests JNI hôte](native-tests/README.md) chargent le vrai bridge et les sources llama.cpp épinglées, sans faux producteur. Sans GGUF autorisé, ils exercent les callbacks de contrôle/erreur et les handles ; ACK/TOKEN, prefill, décodage et comparaison du texte réel restent `NOT_RUN`. Une exception OOM injectée vérifie sa propagation, pas une saturation mémoire Android.

## APK de laboratoire

Le [module Android indépendant](android-lab/README.md), ajouté uniquement par `lab.init.gradle`, utilise l'applicationId `fr.jarvis.llm.perf03b.lab`, `testOnly`, une signature de laboratoire distincte et zéro permission. Il compile le JNI et les trois sources Kotlin réellement appliquées. Son écran expose seulement un test de cycle de vie sans modèle ; aucun mandat CORE, grant de génération ou faux token n'est fabriqué.

La CI ne place l'APK dans `qualified-apk/` qu'après ses contrôles effectifs. Une configuration Gradle ou un APK compilé mais non contrôlé n'est pas une qualification. Le rapport `lab-apk.json` contient commit/tree, SHA256, manifeste, certificat, ABI/16 Kio et exports JNI. Aucun APK n'est installé sur un appareil personnel.

**GATE-B reste soumis à validation explicite et incomplet sans génération réelle qualifiée.** Les campagnes Android physiques et les métriques TTFT p50/p95/max restent `NOT_RUN` ; les valeurs manquantes sont `null`, jamais zéro. Le garde historique du manifeste et les limites des anciens gardes restent conservés séparément.
