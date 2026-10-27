# LLM-PF-01 — lot 1, acheminement CI et gel natif

Date : 2026-10-27 UTC. Base mission `12fc58a81ceee70eedbba2d0b187b1294bae6283` vérifiée par Git ; parent de code `f147236820e0633b0c88f6f1725e828f71167b27`. Branche `codex/prefusion-20261026-lot1`, PR brouillon vers `mission/prefusion-gates-20261026`. SHA publié : voir HEAD de cette PR.

## Diagnostic

Au SHA mission, [40412770792](https://github.com/bubth720/JARVIS-LLM/actions/runs/40412770792) échoue au vieux garde 003E (`Protected baseline drift: app/src/main/AndroidManifest.xml`) avant build/tests natifs. Reproduit localement ; ce garde vérifie une révision antérieure aux extensions FUSION, pas une régression JNI démontrée. Les workflows FUSION-01/02 [40412770819](https://github.com/bubth720/JARVIS-LLM/actions/runs/40412770819) et [40412770975](https://github.com/bubth720/JARVIS-LLM/actions/runs/40412770975) sont SKIPPED à cause de leur filtre de branche. Le garde FUSION-02 au HEAD mission refuse aussi le document de mission additionnel. Aucun de ces résultats n'est un succès actuel.

## Correction bornée

Une campagne PRE-FUSION reprend toutes les étapes FUSION-02 : contrôle de préservation, vecteurs/recovery, vérification APK, revue native sèche, tests négatifs de preuves, CTest sans modèle, tests frais domaine/app/fixtures, baseline exacte de 285 cas, lint/ARM64 et audit APK. L'ancienne route Android est exclue uniquement pour la branche de ce lot et sa base mission ; elle est remplacée par cette campagne complète, sans suppression de test ou changement des anciens gardes. Le téléchargement optionnel de modèle reste réservé au dispatch manuel explicite existant.

`check-prefusion.py` refuse toute modification des sources, contrats, tests historiques, JNI ou patch sensible depuis le SHA mission. Il permet seulement le garde, six tests, ce rapport, le nouveau workflow et la transformation exacte de la route Android. Les gardes FUSION-02/FUSION-01/intensive d'origine sont exécutés dans un clone local temporaire au parent f147236… ; leur succès historique n'est jamais attribué au nouveau HEAD. Les résultats CI nouveaux sont des artefacts de la nouvelle exécution ; les reçus Git historiques restent inchangés.

## Validation locale

| Commande | Résultat |
|---|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/check-prefusion.py` | PASS gel mission + gardes historiques au parent exact |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tests/prefusion -v` | 6/6 PASS, 0 échec/skip |
| `python3 tools/check-preservation-003e.py` | FAIL historique décrit ci-dessus, conservé |
| `python3 tools/check-fusion-02.py` | FAIL périmètre mission, conservé sans affaiblissement |
| `python3 tools/review-fusion-02-native.py` | PASS 7 préimages + git apply --check, patch non appliqué |
| `python3 -m unittest discover -s tools/tests/fusion02 -v` | 11/11 PASS de refus des preuves fausses/périmées/ignorées, Linux Python3.12 |
| `node tools/test-apk-verifier.cjs` | PASS des cas d'intégrité ; pas navigateur Android |
| Gradle domaine/app, CTest, ARM64/lint | NOT_RUN local, outillage/cache/SDK absents et proxy shell indisponible ; exiger CI au SHA publié |
| GGUF réel/API29+/ARM64 physique, mode avion/batterie/thermique | NOT_RUN |

Des pyc générés par les premières vérifications ont été refusés comme fichiers additionnels ; retirés des seuls répertoires de cache générés, puis exécution sans bytecode Python. Aucun test source supprimé.

## Contrats et décisions réservées

Relus : `GenerationClientContract.kt`, `ClientStreamMonitor.kt`, `NATIVE-REVIEW.md`, `CORE-CONSUMER.md` et tests de cycle de vie existants. Corrélation epoch/session/request/generation, séquences, ACK de fixture refusé en live et UNKNOWN collant sont inchangés. Le client n'est pas une autorité native. Le tokenizer structuré reste UNSUPPORTED, l'admission /2 fermée ; stop logique ne prouve pas unload.

Patch sensible toujours **NON APPLIQUÉ**, SHA256 `be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374`, dossier `docs/mission-003f-f3b/DIFF-A-APPROUVER.md`. Son éventuelle application exige accord spécifique avant correction JNI/ABI. Aucun téléchargement modèle, nouveau Binder, import voix privée ou FULL_POWER.

Compatibilité gelée : minSdk29/ARM64, target/compile36, AGP8.10.1/Kotlin2.1.21, Gradle8.11.1, JDK21 et bytecode17, NDK28.1.13356709. CORE API24 ne peut pas charger ce runtime en process sur API24. Licences/ABI restent à qualifier pour assemblage et redistribution ; aucun changement de dépendance ici.

**BLOCKED** pour JNI GGUF/budgets/unload Android et arbitrage LLM/VOICE interprocessus ; absence de preuve de libération => refus. Prochaine étape : observer la nouvelle CI exacte, puis décision distincte sur le patch natif et qualification autorisée. Rollback : revert uniquement ce lot d'outillage/route/rapport. Aucun stockage, moteur ou contrat changé. Mission non terminée.
