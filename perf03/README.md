# JARVIS PERF-03A — dossier de revue native

**Patch non appliqué. Arrêt obligatoire avant GATE-A.** Module LLM, dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03a-native-review`, référence `09e278b54b8be58a3d665d2d91de10f8869e40bb`, tree `975fc71395ac7758678cb5af1b76858ee614c9b2`.

- [Rapport central et acceptation](REPORT.md), [référence exacte](BASELINE.md).
- [Résultats locaux et empreintes](LOCAL-RESULTS.json), [revue indépendante](INDEPENDENT-REVIEW.md).
- [Audit](AUDIT.md), [architecture](ARCHITECTURE.md), [compatibilité CORE/INTERFACE](CORE-INTERFACE-COMPAT.md).
- [Diff JNI exact](NATIVE-STREAMING.patch), [préimages et SHA256](PREIMAGES.json), [revue JNI](JNI-REVIEW.md), [risques](RISK-MATRIX.md).
- [Tests et phases suivantes](TEST-PLAN.md), [plan de benchmark](BENCHMARK-PLAN.json), [preuves CI et sauvegarde](CI-EVIDENCE.md), [retour arrière](ROLLBACK.md).

## Vérifications non productives

```bash
python3 perf03/check.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s perf03 -p 'test_*.py' -v
bash perf03/tests/run.sh
bash tools/cloud-env.sh ./gradlew -I perf03/review.init.gradle :perf03-review:test --rerun-tasks --no-daemon --max-workers=2
```

`make_patch.py --check` reconstitue le diff en mémoire et vérifie sa concordance puis utilise uniquement `git apply --check`. Ne pas appliquer le patch dans 03A. La CI compile directement la copie C++ de revue avec `-fsyntax-only`, et le projet JVM séparé compile les copies Kotlin et ses tests à NativeApi simulée. Aucun de ces tests n'atteste une vraie génération, le rendu Android ou les courses du JNI modifié.

Les signatures historiques, permissions, contrats, budgets et code productif restent inchangés. Le nouveau chemin proposé est interne, texte P2 seul et explicitement opt-in, sans implémentation productive d'autorité CORE. Aucun modèle, réseau applicatif, moteur supplémentaire ou connexion VOICE/NEXUS/SERVICES n'est ajouté.

**TTFT <3 s : NOT_QUALIFIED. Génération réelle et Android physique : NOT_RUN.** Les résultats de CI exacts sont liés dans la PR brouillon et ses artefacts, avec commit/tree, XML et SHA256SUMS. Ne pas assimiler une CI verte aux tests historiques rouges ou à la qualification des phases B/C.
