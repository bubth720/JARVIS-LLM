# PERF-02 — instrumentation isolée et proposition de streaming

Base : `1b62a5c50eb979d82ef8eb0b488ab38c6fe462ce`, branche mission `mission/llm-perf-ttft-3s-20261104`. Travail : `codex/llm-perf-ttft-3s`. Aucun merge main.

**SLA premier texte modèle < 3 s : NON QUALIFIÉ.** Aucun GGUF téléchargé/importé, aucune installation personnelle ni génération réelle. Les résultats Android physique sont `NOT_RUN`.

Les fichiers de ce dossier sont ajoutés aux **sources de test seulement** par `qualification.init.gradle`. Les APK main, le JNI, le tokenizer, les contrats CORE/INTERFACE, le scheduler, les baux, les signatures et les protections mémoire/thermiques restent ceux de la base. La campagne vérifie aussi l’absence des classes PERF dans le DEX de l’APK.

- [Rapport et résultats](REPORT.md), [résultats locaux bruts](LOCAL-RESULTS.json).
- [Protocole reproductible](PROTOCOL.md) et [gabarit sans mesures inventées](MEASUREMENT-TEMPLATE.json).
- [Proposition JNI distincte, NON APPLIQUÉE](NATIVE-STREAMING-PROPOSAL.md).

## Vérification logicielle

Depuis la racine, avec l’outillage vérifié du dépôt, sans modèle :

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s perf02 -p 'test_*.py' -v
python3 perf02/check.py --historical
bash tools/cloud-env.sh ./gradlew -I perf02/qualification.init.gradle :domain:test :app:testDebugUnitTest :p2-endpoint:testDebugUnitTest --rerun-tasks --no-daemon --max-workers=2
```

Le garde nécessite des fichiers indexés avec leurs modes Git ; il refuse toute mutation des fichiers historiques, y compris les workflows. Le workflow PERF-02 réalise une baseline fraîche au commit de référence, puis la variante sur le même runner, conserve les XML JUnit et vérifie les identités des 303 cas historiques. Les anciens gardes conservent leur domaine et sont également exécutés à leurs propres SHAs ; leurs échecs de périmètre hérités à la nouvelle base ne sont pas masqués.

## Lire des traces

```bash
python3 perf02/benchmark.py --baseline baseline.jsonl --candidate candidate.jsonl --output comparaison.json
```

Cet outil **ne lance pas de modèle**. Il valide des JSONL bornés et sans texte/chemin, sépare froid/chaud, modèle, matériel, configuration, corpus et caps de sortie 8/24. L’observateur actuel ne dispose ni d’un premier token JNI réel, ni d’une acceptation CORE raccordée : p50/p95 de génération restent `NOT_RUN`, valeurs `null`. Une source synthétique ne peut jamais qualifier le SLA.

Le microbenchmark `ObserverOverheadTest` mesure le coût du collecteur sur JVM hôte, avec 40 échantillons par taille et par lane ; ce n’est ni un TTFT, ni un débit modèle, ni un résultat Android.
