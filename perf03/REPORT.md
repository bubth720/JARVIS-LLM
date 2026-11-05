# JARVIS PERF-03A — rapport central

Module : **LLM**. Dépôt : `bubth720/JARVIS-LLM`. Mission : **PERF-03A**, préparation des phases 03B/03C. Branche : **`codex/llm-perf-03a-native-review`**. Base exacte : **`09e278b54b8be58a3d665d2d91de10f8869e40bb`**, tree **`975fc71395ac7758678cb5af1b76858ee614c9b2`**. Publication en PR brouillon vers la branche PERF-02, sans fusion. Les SHAs/tree exacts du candidat et les résultats finaux de CI figurent dans le complément de livraison de la PR et les rapports générés de son run exact, afin de ne pas inscrire un SHA auto-référent dans son propre contenu.

**JNI productif inchangé. Patch NON APPLIQUÉ. Objectif p95 <3000 ms : NOT_QUALIFIED.** Génération avec modèle, streaming JNI réel, Android API29/35 du nouveau moteur et appareils physiques : **NOT_RUN**. Les phases 03B/03C ne sont pas déclarées achevées.

## Livraison de revue

Le [patch exact](NATIVE-STREAMING.patch) propose un export JNI additionnel, un callback synchrone sur le worker existant, un tampon Kotlin borné et une liaison explicite aux identités d'opération, au mandat CORE, au bail et à la demande préparée. [PREIMAGES.json](PREIMAGES.json) contient son SHA256, toutes les préimages/postimages et leurs modes. Aucun fichier runtime cible n'est modifié par 03A.

Le chemin expérimental proposé est **interne, opt-in, P2 texte seul**. L'ancien `LanguageModel.generate` et les signatures natives complètes restent disponibles dans le diff proposé. Les chunks proviendraient de la boucle réelle de sampling, avant la fin de la réponse ; aucun découpage d'un résultat final n'est présenté comme streaming. Le premier token natif, le premier UTF-8 utilisable et le premier affichage sont distincts.

Le patch propose aussi une protection de durée de vie par handles opaques monotones et pin exclusif des appels natifs sensibles. Cette évolution touche les chemins historiques après une éventuelle application : elle est incluse explicitement dans le diff sensible à approuver et doit subir sa propre qualification. Le maintien des noms de signatures n'est pas une preuve suffisante de compatibilité comportementale.

Les bornes candidates sont 16 événements (slot terminal réservé), 16 Kio de texte en attente, 4096 octets/chunk et 64 Kio cumulés. Le texte UTF-8 incomplet est conservé puis reconstitué ; une entrée invalide produit une erreur explicite. Ces valeurs logiques ne remplacent pas les budgets RAM existants ni une mesure PSS Android. Aucun seuil thermique, batterie, contexte, qualité, modèle, sampler ou paramètre de génération n'est abaissé pour fabriquer un gain.

## Documents et fichiers

[Audit étape par étape](AUDIT.md), [architecture comparée callback/file native](ARCHITECTURE.md), [revue JNI](JNI-REVIEW.md), [risques](RISK-MATRIX.md), [compatibilité CORE/INTERFACE](CORE-INTERFACE-COMPAT.md), [référence](BASELINE.md), [tests](TEST-PLAN.md), [preuves CI](CI-EVIDENCE.md), [rollback](ROLLBACK.md).

Les ajouts sont limités à `perf03/` et `.github/workflows/perf03.yml`. Ils comprennent les postimages de revue, le patch/manifeste, les helpers et tests C++/Kotlin/Python, les scripts de compilation isolée et de préservation, le plan de benchmark et les rapports. La liste exhaustive des fichiers, modes, empreintes, commit et tree est produite dans `build/perf03/preservation.json` et les marqueurs de campagne. Tous les fichiers historiques de PERF-02 et du runtime sont gelés.

## Résultats et frontières de preuve

| Contrôle | Résultat disponible avant publication |
|---|---|
| Référence GitHub PERF-02 | PASS : PR #10 fermée sans fusion, commit/tree exacts ; export local reconstituant le même tree |
| Patch / préimages | Contrôles `make_patch.py --check` et garde 03A ; le dry-run vérifie l'applicabilité, pas le JNI réel |
| Helpers C++ | 31 cas PASS ; même suite ASan/UBSan PASS localement avec LSan explicitement désactivé |
| LeakSanitizer local | BLOCKED par le sandbox sous ptrace ; premier échec conservé. CI conserve LSan activé et ne réessaie pas silencieusement |
| Outils Python 03A / PERF-02 | 20 / 34 PASS localement ; périmètre, routes, préimages, preuves et banc historique |
| Kotlin candidat | 3 compilations isolées PASS ; 178 tests PASS = 145 domaine + 6 anciens backend + 11 nouveaux backend + 16 tampon/callback ; NativeApi simulée |
| Baseline PERF-02 déjà publiée | CI PASS [41907076112](https://github.com/bubth720/JARVIS-LLM/actions/runs/41907076112), 357 tests JVM dans sa qualification |
| Nouvelle CI PERF-03A | Statut à consulter dans le complément de livraison de la PR et le run du head exact ; la présence du workflow n'est pas un PASS |
| JNI appliqué, ARM64 candidat, modèle réel et identité ancien/nouveau | NOT_RUN : arrêt GATE-A / modèle non disponible |
| Nouveau laboratoire Android API29/35 | NOT_RUN : JNI non appliqué, raccordement non validé |
| Appareils POCO X8 Pro / Redmi Pad SE | NOT_RUN ; aucune autorisation d'installation/campagne personnelle |

Les [données locales et empreintes des sources testées](LOCAL-RESULTS.json) sont des résultats d'export de worktree ; elles ne prétendent pas provenir d'un checkout propre du commit distant. Le domaine, la revue Kotlin et les tests du domaine ont été compilés en modules distincts, sans accès interne du candidat au domaine. La CI reconstruit la référence exacte puis le candidat exact, lie les XML frais aux sources et conserve les identités des 357 cas JVM. Elle compile séparément le C++ de revue en mode syntaxique et le projet Kotlin de revue, sans lier ni charger le JNI candidat. Les APK historiques sont inspectées pour les permissions, signatures, ARM64/16 Kio et l'absence du nouveau symbole JNI/classes de revue.

Le garde historique reste **FAIL** avec `Protected baseline drift: app/src/main/AndroidManifest.xml`. Ce résultat existe déjà à la référence et reste conservé séparément. Une CI PERF-03A verte ne signifie pas que toutes les CI historiques sont vertes. Aucun garde n'est corrigé ou affaibli dans cette mission.

## Performance et risques

| Mesure de génération réelle, caps 8/24, tous profils/états froid/chaud | Statut | Valeur |
|---|---|---|
| TTFT p50/p95/max et première frame | NOT_RUN | null |
| Réponse complète p50/p95 et chargement | NOT_RUN | null |
| Débit réel, PSS/RSS/pic, CPU, énergie | UNKNOWN | null |
| Comparaison avant/après sur même matériel/modèle | NOT_RUN | null |

Aucune accélération du calcul n'est revendiquée. Réutilisation du scratch token uniquement dans le nouveau chemin proposé ; le double formatage/tokenizer et les load idempotents de sûreté sont conservés. Toute optimisation supplémentaire exige une comparaison isolée. [BENCHMARK-PLAN.json](BENCHMARK-PLAN.json) prépare deux variantes, trois états de résidence, caps 8/24, ≥20 essais comparables/cellule, mêmes paramètres et conditions, sans boucle matérielle automatique.

Les risques JNI (UAF, ABA, exceptions/OOM callback, backpressure, arrêt pendant prefill/decode/callback, nettoyage et mort de processus) restent à éprouver sur le binaire appliqué après accord. Les tests de helper et les NativeApi simulées vérifient les composants isolés ; ils ne démontrent pas la sûreté d'une bibliothèque native modifiée.

## Acceptation et blocages

| Critère | État |
|---|---|
| Patch exact, manifeste et dossier de risques | Livrés pour revue ; aucune application |
| Préservation du runtime, des protections et des dépôts complémentaires | Imposée par le garde complet et revue du diff |
| Tests non sensibles et CI dédiée | Préparés/exécutés selon les preuves du run exact ; tout échec reste visible |
| Mandat CORE / bail natif attesté / adaptateur INTERFACE réel | **BLOCKED** : contrats live non raccordés, propositions seulement |
| GATE-A autorisant l'application | **PENDING_USER_APPROVAL** |
| GATE-B avec génération réelle | **NOT_RUN**, non atteint |
| GATE-C et p95 physique <3000 ms | **NOT_RUN / NOT_QUALIFIED** |
| Fusion main / installation personnelle | Non effectuées, autorisations séparées requises |

L'autorité candidate n'a aucune implémentation productive ; les métadonnées de grant ne valent pas preuve CORE/broker. Le hash local de préparation est distinct de l'empreinte du contrat CORE actuel, et le mapping doit être vérifié. Les reçus globaux DRAINED/UNLOADED restent inconnus ; un retour JNI n'autorise pas une nouvelle résidence. Aucun changement d'un autre dépôt ni connexion VOICE/NEXUS/SERVICES n'est inclus.

## Décision précise à GATE-A et suite

Après lecture du diff lié à **son SHA256 exact et au commit de PR**, autoriser ou refuser son application sur `codex/llm-perf-03b-native-stream`, en laboratoire seulement. Cette autorisation couvrirait les quatre cibles recensées dans PREIMAGES, dont la modification sensible du registre de handles. Elle ne vaudrait ni approbation de F3B, ni activation productive, ni modification CORE/INTERFACE, ni fusion, ni campagne personnelle.

Après accord : appliquer uniquement le patch approuvé, qualifier la bibliothèque JNI hôte/ARM64, exceptions, sanitizers et identité sur modèle volontairement disponible ; préparer ensuite l'adaptateur Android distinct et les émulateurs. En l'absence de modèle, GATE-B restera incomplet. L'accord matériel personnel ne sera demandé qu'avec une APK de laboratoire réellement compilée/contrôlée, son SHA256 et un protocole précis. Préserver les artefacts de CI et leurs SHA256 avant expiration selon [CI-EVIDENCE.md](CI-EVIDENCE.md).
