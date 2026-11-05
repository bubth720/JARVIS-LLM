# CI et conservation des preuves PERF-03A

La CI dédiée `PERF-03A native review, frozen runtime and isolated tests` utilise le **SHA de tête exact**, jamais le merge temporaire d'une PR. Son garde lie la référence PERF-02 `09e278b54b8be58a3d665d2d91de10f8869e40bb` au tree `975fc71395ac7758678cb5af1b76858ee614c9b2`, vérifie tous les fichiers historiques et leurs modes Git, puis contrôle les préimages et `git apply --check` du patch proposé. Il n'applique pas le patch.

Le workflow compile les helpers de revue, exécute leurs tests et ASan/UBSan/LSan, et vérifie la syntaxe de la traduction C++ candidate directement depuis le dossier de revue, sans la lier ni la charger. Un projet JVM séparé `:perf03-review`, ajouté uniquement par `perf03/review.init.gradle`, compile les deux fichiers Kotlin candidats et teste leurs contrats avec un `NativeApi` injecté ; il ne remplace aucune source de l'app et ne charge aucun JNI. Un marqueur frais lie ces résultats à leurs sources exactes. Le runtime historique ARM64 est compilé séparément ; les tests JVM PERF-02 sont exécutés fraîchement à la base exacte puis au candidat, sur le même runner/outillage. Le comparateur exige la conservation des **identités** des 357 cas de la base (domain190, app49, endpoint118), sans compter comme succès les erreurs, les skips ou les XML périmés. Le compilateur et les scripts de préparation sont ceux du dépôt ; aucune génération GGUF n'est lancée.

Les APK compilées dans ce workflow sont les applications historiques de référence, avec leurs identifiants et frontières vérifiés. Ce ne sont pas des APK de laboratoire streaming à livrer ou installer. Les exports JNI historiques sont comparés entre les deux builds. Aucun nouveau symbole `generateStream` et aucune classe PERF test-only ne doivent y apparaître. Les contrôles existants vérifient permissions, certificats, ABI ARM64, alignement ELF/ZIP 16 Kio et contrats des fixtures.

Le garde historique `tools/check-preservation-003e.py` reste inchangé et **FAIL** avec :

```text
Protected baseline drift: app/src/main/AndroidManifest.xml
```

Cette sortie et son code d'échec sont conservés pour la base et le candidat dans `inherited-003e-*.log/json`. Un autre échec ou une disparition inattendue de cet échec arrête la CI de revue. La qualification de la revue ne requalifie pas le workflow historique et ne corrige pas implicitement ce drift.

L'artefact `perf03a-evidence-<SHA exact>` conserve pendant **90 jours** les XML bruts, rapports Gradle/lint, logs du build natif et des sanitizers, résultats comparatifs, manifeste des préimages, patch proposé et `SHA256SUMS`. Les rapports exposent commit, tree, entrées SHA256, états NOT_APPLIED/NOT_RUN et l'absence de qualification du TTFT. Les XML des campagnes ayant échoué sont également collectés lorsque disponibles ; aucun retry automatique de test n'est prévu.

Avant expiration, télécharger l'artefact depuis la page GitHub Actions du run exact et conserver son ZIP avec les rapports dans une sauvegarde choisie par l'utilisateur. Après extraction, exécuter `sha256sum -c SHA256SUMS` dans le dossier de preuves. Conserver aussi le SHA256 du ZIP téléchargé et l'URL/ID du run : `SHA256SUMS` contrôle les fichiers extraits, pas le conteneur ZIP lui-même. Le code, le patch et les documents de revue restent versionnés dans la branche/PR ; leur conservation ne dépend pas de la rétention Actions.

Les campagnes physiques, le JNI streaming réel, l'identité de contenu sur modèle, l'APK de laboratoire streaming et les percentiles Android restent **NOT_RUN/BLOCKED par GATE-A** tant que l'application du patch exact n'est pas approuvée. Une syntaxe compilée et des helpers de revue réussis ne constituent pas une preuve d'absence d'UAF JNI, de drain natif ou de déchargement réel.

Pour un environnement où les sockets IPC de Gradle sont indisponibles, `perf03/tests/run_kotlin_review.py` réutilise uniquement un JDK21 et les JAR déjà présents dans un cache Gradle. Il ne télécharge rien et exécute directement `K2JVMCompiler` Kotlin2.1.21 puis `JUnitCore`4.13.2. Les paramètres `--jdk` et `--cache` sont obligatoires. L'option `--historical` ajoute les145 tests domain et6 tests historiques de backend injecté aux tests candidats. Le domaine et la revue sont compilés comme modules séparés ; seuls les tests historiques du domaine reçoivent les friend-paths de leur propre module. Les SHA256 des sources/JAR, le vrai HEAD/tree, l'état worktree et le résultat exact sont conservés sous `build/perf03/kotlin-direct/`. Un résultat local dirty n'est jamais présenté comme une attestation de commit propre.
