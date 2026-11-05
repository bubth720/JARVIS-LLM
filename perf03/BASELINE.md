# PERF-03A — référence et préservation

Module : LLM. Mission : PERF-03A. Dépôt : `bubth720/JARVIS-LLM`.
Branche : `codex/llm-perf-03a-native-review` ; cible PR : `codex/llm-perf-ttft-3s`.

Référence exacte : **`09e278b54b8be58a3d665d2d91de10f8869e40bb`**, tree **`975fc71395ac7758678cb5af1b76858ee614c9b2`**. La [PR PERF-02 #10](https://github.com/bubth720/JARVIS-LLM/pull/10) est fermée sans fusion. Ses 23 ajouts sont conservés. Ils ne sont pas supposés présents dans main.

## Vérification de départ

Le connecteur GitHub a confirmé la PR, son head et l'absence de fusion. La comparaison GitHub avec `1b62a5c50eb979d82ef8eb0b488ab38c6fe462ce` ne contient que les 23 ajouts PERF-02. L'export local de ces blobs, ajouté à l'arbre historique existant, produit exactement le tree ci-dessus avec `git write-tree`. Le shell n'a pas pu récupérer le commit distant à cause de son accès réseau restreint. Le travail local est donc déclaré **export d'arbre**, avec HEAD historique distinct ; aucune preuve locale n'affirme un checkout propre de `09e278…`.

La publication utilise les objets GitHub avec le **parent réel `09e278…` et son tree exact**. La CI fait un checkout complet du head exact, vérifie l'ascendance et rejette le mode export local. Aucun reset ou écrasement de travail précédent n'a été effectué ; le workspace PERF-02 antérieur reste conservé séparément.

## CI de la référence

État récupéré via GitHub, pas déduit d'une configuration de workflow :

| Workflow de la référence | Exécution | Résultat |
|---|---|---|
| PERF-02 | [41907076112](https://github.com/bubth720/JARVIS-LLM/actions/runs/41907076112) | PASS |
| Android prototype checks | [41907076010](https://github.com/bubth720/JARVIS-LLM/actions/runs/41907076010) | FAIL historique |
| FUSION-02 | [41907076085](https://github.com/bubth720/JARVIS-LLM/actions/runs/41907076085) | SKIPPED |
| FUSION-01 | [41907076004](https://github.com/bubth720/JARVIS-LLM/actions/runs/41907076004) | SKIPPED |

Le garde `tools/check-preservation-003e.py` conserve son échec **`Protected baseline drift: app/src/main/AndroidManifest.xml`**. Le script, ses préimages et les workflows historiques restent gelés. La CI PERF-03 enregistre cet échec sur la référence et le candidat, avec code retour et journal bruts. Un succès PERF-03 ne rend pas vertes les CI historiques.

## Critères de comparaison

La base possède 357 tests JVM (domain 190, app 49, endpoint 118), dont les 303 cas antérieurs à PERF-02. La CI reconstruit référence puis candidat sur le même runner avec le même outillage épinglé et conserve toutes les identités des cas et les XML frais. Elle compile les sources candidates séparément ; aucun code candidat n'entre dans les APK historiques.

Le garde PERF-03 fige **tous les chemins, contenus et modes** du tree PERF-02. Seuls des ajouts sous `perf03/` et le nouveau `.github/workflows/perf03.yml` sont permis. La liste exacte des nouveaux fichiers, le commit/tree du build et leurs SHA256 figurent dans `build/perf03/preservation.json`, les marqueurs de test et l'artefact de CI. Les quatre cibles du patch sont décrites dans [PREIMAGES.json](PREIMAGES.json) ; elles restent non appliquées.

Toolchain historique conservé : Kotlin 2.1.21, Gradle 8.11.1, JDK 21, cible JVM 17, AGP 8.10.1, Android min 29/compile 36, NDK 28.1.13356709, ARM64 et alignement 16 Kio. La préparation CI télécharge des outils et les sources llama.cpp épinglées, **aucun modèle**.
