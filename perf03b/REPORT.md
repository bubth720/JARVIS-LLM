# JARVIS PERF-03B — bilan de qualification expérimentale

Module : **LLM**. Mission : **PERF-03B**. Dépôt : `bubth720/JARVIS-LLM`.
Branche isolée : `codex/llm-perf-03b-native-stream`.
Base autorisée : **`ca9d8f060856e134d793d0c23764fae45ce2cc53`**, tree **`75798737dfc4a618f213ae299b0e467e7547032a`**.
Le commit/tree final, le lien de PR brouillon et les résultats définitifs sont ceux du complément de livraison de la PR et des marqueurs de CI au head exact. Le présent document ne s'auto-attribue pas un commit encore à publier.

## Autorisation et modification exacte

L'utilisateur a explicitement validé GATE-A pour l'application expérimentale du seul patch PERF-03A SHA256 :

```text
747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460
```

[APPLICATION.json](APPLICATION.json) conserve le contrôle du worktree propre, de cette empreinte, des quatre préimages Git/SHA256/modes, du dry-run, de l'application et des postimages. Il indique `apply_exit=0`, `postimages_verified=true` et l'absence d'application du patch F3B. Toute modification ultérieure de ces postimages nécessite une nouvelle approbation ; une erreur de qualification n'autorise pas à corriger silencieusement le diff natif.

| Source appliquée | Modification autorisée |
|---|---|
| `app/src/main/cpp/bridge.cpp` | Export additionnel `generateStream`, callback synchrone, handles opaques monotones et pin exclusif ; anciennes signatures maintenues |
| `app/src/main/cpp/stream_support.h` | Assembleur UTF-8 incrémental et limites candidates |
| `app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt` | Chemin interne `generateOwnedStream` explicitement opt-in, même backend/worker et ressources existantes |
| `app/src/main/java/fr/jarvis/llm/engine/OwnedStreamContract.kt` | Contrat, corrélation, révocation, file bornée et clôture des callbacks |

Les autres changements de cette mission sont des ajouts de banc, laboratoire, CI et documentation sous `perf03b/`, plus le workflow dédié. Le garde de portée produit la liste exhaustive et les empreintes au commit testé. Les anciens gardes, les politiques thermiques/batterie/RAM, la qualité, les contextes, les modèles et les contrats CORE/INTERFACE restent inchangés. Aucune fusion, activation productive, permission sensible, migration ou installation personnelle n'est autorisée par cette livraison.

## Résultats : lire la preuve correspondant au bon périmètre

Les statuts ci-dessous décrivent le périmètre préparé avant la campagne finale. **PENDING** n'est pas un PASS. Les chiffres et verdicts finaux sont publiés dans la PR avec les artefacts bruts du commit exact ; un fichier de workflow ne constitue pas un test exécuté.

| Campagne | Résultat avant CI finale | Limite de preuve |
|---|---|---|
| Vérification/application exacte | PASS selon APPLICATION.json | Identité du diff appliqué ; pas sûreté ou génération attestées |
| Compilation JNI hôte appliqué | PENDING | Compiler le vrai bridge et la source llama.cpp épinglée, sans poids |
| Callbacks JNI réels sans modèle | PENDING | ERROR, descripteurs, exceptions, références locales, réentrance et concurrence ; aucun ACK/TOKEN réel possible sans modèle |
| Handles et nettoyage d'un moteur vide | PENDING | Création/annulation/déchargement vide/destruction ; pas libération de poids/KV jamais chargés |
| ASan/UBSan et tests natifs | PENDING | Distinguer helpers et bibliothèque JNI réellement instrumentée ; toute indisponibilité reste explicite |
| Régression historique fraîche | PENDING | Baseline 03A et candidat sur même outillage ; identités de tests et XML frais conservés |
| Anciennes signatures | PENDING | Comparer les exports historiques et vérifier l'ajout attendu de generateStream ; nom inchangé seul insuffisant pour compatibilité comportementale |
| APK Android ARM64 distincte | PENDING | Construction/manifestes/permissions/signature/alignement ; pas génération ni mesure matérielle |
| Modèle autorisé disponible | **NONE_FOUND** | Scan workspace décrit ci-dessous, aucun téléchargement |
| Génération réelle, vrais ACK/TOKEN/COMPLETED, Unicode llama et comparaison de contenu | **NOT_RUN** | Modèle absent et propriétaire/admission live non qualifiés |
| Stop pendant vrai prefill/décodage et déchargement d'un modèle résident | **NOT_RUN** | Un callback d'erreur bloqué n'est pas un prefill ou un token réel |
| APK candidate sur émulateurs API29/35 | **NOT_RUN** avant campagne explicite | Un build ARM64 ne remplace pas l'exécution Android ; ne pas substituer des fixtures aux mesures LLM |
| POCO X8 Pro / Redmi Pad SE | **NOT_RUN** | Aucune installation/campagne personnelle autorisée |
| TTFT p50/p95/max et comparaison avant/après | **NOT_RUN**, valeurs `null` | Cible p95<3000ms **NOT_QUALIFIED** |

Les campagnes anciennes de PERF-03A restent des preuves de revue historique : 357 tests de régression à la base comme au candidat, 33 tests Kotlin de revue et 31 helpers C++ réussis. Elles ne deviennent pas automatiquement les résultats du JNI appliqué. Les cas réexécutés dans plusieurs bancs ne sont pas additionnés comme tests distincts.

## Modèles, CORE et INTERFACE

Une recherche de noms `*.gguf` sous `/workspace`, hors `.git`, `.tools`, `.deps`, `.gradle` et `gradle-cache`, n'a trouvé aucun fichier. Aucun contenu de poids ni de données privées n'a été inspecté. Ce résultat ne prétend pas examiner d'autres volumes ou appareils ; la présence future d'un fichier ne suffira pas à prouver son autorisation, sa licence, son empreinte ou son adéquation matérielle. Aucun modèle n'est téléchargé ou embarqué dans l'APK.

L'interface `StreamOwnerAuthority` n'a pas d'implémentation productive qualifiée. Les mandats CORE, l'identité de bail attestée, le lien au modèle réellement vérifié et le mapping entre empreinte logique et préparation exacte restent des prérequis. Les contrats historiques continuent d'annoncer `nativeReady=false` et le tokenizer structuré `UNSUPPORTED`. L'adaptateur INTERFACE de référence accepte une provenance laboratoire ; aucun vrai événement LLM ne doit être déguisé en fixture pour franchir cette frontière. Voir [compatibilité de référence](../perf03/CORE-INTERFACE-COMPAT.md) et [matrice 03B](QUALIFICATION-MATRIX.md).

Le laboratoire indépendant utilise `:perf03b-lab` via `lab.init.gradle`, applicationId `fr.jarvis.llm.perf03b.lab`, `testOnly=true`, ARM64, min29/target36. Son écran annonce l'absence de modèle et la génération réelle NOT_RUN. Le test de cycle de vie appelle le vrai `LlamaBackend` unique sans charger de poids ; aucun grant CORE ni faux flux de tokens n'est fabriqué. Un succès de ce bouton ne prouve ni un modèle déchargé, ni DRAINED, ni une communication CORE–LLM–INTERFACE.

## Sécurité, blocages et décision

L'application du patch est autorisée ; **GATE-B reste incomplet** tant qu'une génération réelle et les contrôles de contenu/sûreté associés n'ont pas été exécutés. Des callbacks d'erreur réels, même réussis, ne prouvent pas le chemin de sampling, les limites de sortie d'un modèle ou le Stop pendant calcul. Le déchargement d'un moteur vide ne prouve pas la libération de poids/KV ou une baisse de PSS/RSS.

Un échec critique arrête la qualification et les étapes dépendantes ; garder journal, signal, code retour, commit/tree, bibliothèque/APK et empreintes. Aucun retry automatique de génération et aucune modification du patch, des protections ou des reçus pour obtenir un PASS. Une correction sensible exige un nouveau diff à approuver.

Le FAIL historique `Protected baseline drift: app/src/main/AndroidManifest.xml` doit rester consigné aux références pertinentes, avec garde inchangé. Les gardes PERF-02/03A ont leur propre portée : leur échec après application native ne doit pas être maquillé ni corrigé ; le garde 03B vérifie exactement les quatre postimages autorisées et fige tout le reste. Une CI 03B verte n'affirme pas que toutes les CI anciennes sont vertes.

La [préparation des benchmarks et de PERF-03C](BENCHMARK-PREP.md) fixe les cellules froid/chaud, caps8/24, horloges et conditions comparables. Le [rollback](ROLLBACK.md) n'accorde aucune preuve fictive de ressources libérées. Prochaine décision : examiner les résultats réels 03B et les blocages ; aucune campagne personnelle, activation productive, modification CORE/INTERFACE ou fusion ne découle automatiquement de GATE-A.
