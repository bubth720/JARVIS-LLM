# PERF-03B — CI et portée des preuves

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03b-native-stream`.
Base autorisée : `ca9d8f060856e134d793d0c23764fae45ce2cc53`, tree `75798737dfc4a618f213ae299b0e467e7547032a`.
Patch unique autorisé SHA256 : `747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460`.

Le garde `check.py` fige tous les fichiers de la base GATE-A, avec les seules quatre postimages exactes approuvées comme exceptions. Les préimages sont relues depuis les objets Git du commit autorisé, les postimages depuis les copies de revue gelées. Le patch, son manifeste et les anciens gardes restent inchangés. Le contrôle sec inverse ne modifie rien. `APPLICATION.json` conserve les vérifications effectuées avant l'application autorisée ; il ne confère aucun mandat CORE.

## Une campagne automatique par publication

Le workflow est déclenché **uniquement par push sur la branche 03B**. Ses checks du commit restent visibles dans la PR brouillon. La création de PR ne relance donc pas la même campagne native après un éventuel échec critique du push. Aucun retry automatique ni `continue-on-error` n'est utilisé. Une modification du patch demande une nouvelle validation utilisateur ; un échec critique doit arrêter la qualification et conserver ses preuves.

Les étapes de qualification cessent au premier échec. Les tests JNI créent en plus `build/perf03b/CRITICAL-STOP.json` et refusent une autre invocation dans le même workspace. Seuls l'archivage et l'upload des résultats bruts utilisent `if: always()`. L'APK de laboratoire n'est copiée dans le lot qualifié qu'après le succès de toutes les étapes précédentes et de son inspecteur. Un APK produit avant un échec ultérieur n'est donc pas livré comme qualifié.

## Comparaison et frontières

La référence GATE-A et le candidat sont reconstruits fraîchement sur le même runner, avec les mêmes versions épinglées. Les clés Android debug/fixture publiques de CI sont préparées par les tâches historiques **avant** le partage de l'outillage, puis les enfants sont liés sous de vrais répertoires ignorés : ni symlink racine dirty, ni modification d'ignore, ni relâchement du contrôle clean. Les clés existantes ne sont jamais remplacées.

La base conserve les 357 identités JVM : domain190, app49, endpoint118. Le candidat doit préserver toutes ces identités sans fail/error/skip. Le module `:perf03b-runtime` compile les fichiers **appliqués** sous `app/src/main/.../engine`, puis réutilise les 27 cas de revue et 6 cas historiques. Il ne compile pas une copie de postimage à la place du backend appliqué. Les marqueurs et XML frais sont liés aux commit/tree et SHA256 de toutes les sources.

Les tests Java hôte chargent le vrai JNI compilé avec llama.cpp épinglé, avec `-Xcheck:jni`, sans modèle. Ils vérifient les chemins de contrôle/erreur et de propriété réellement accessibles sans GGUF. ASan/UBSan s'appliquent à une compilation distincte du JNI ; LSan du processus JVM est explicitement non qualifié, tandis que le CTest autonome peut utiliser LSan. Les helpers et NativeApi simulées sont des preuves complémentaires de composants, jamais des tokens modèle.

Les APK historiques sont inspectées : mêmes identifiants, permissions et certificat ; douze exports JNI hérités conservés, seul l'export `generateStream` approuvé ajouté. Leurs sources UI restent gelées et n'activent pas le streaming silencieusement. L'APK de laboratoire a un applicationId, une signature et un manifeste `testOnly` distincts, aucune permission réseau/sensible et les mêmes sources natives approuvées. ARM64 et alignements ELF/ZIP16Kio sont vérifiés sur les vrais fichiers produits.

Le garde003E historique reste **FAIL** avec `Protected baseline drift: app/src/main/AndroidManifest.xml` aux deux références. Son code, code retour et message restent conservés. Une CI PERF-03B verte ne rend pas les CI historiques vertes ; les anciens gardes de périmètre 03A/02 ne sont pas modifiés pour accepter une application native hors de leur mission.

## Conservation et limites

L'artefact `perf03b-evidence-<commit exact>` est conservé 90 jours : XML, rapports, logs natifs/JNI/sanitizers, marqueurs et sources SHA256, reçu d'application, patch approuvé, inspections APK et `SHA256SUMS`. Les résultats manquants après un arrêt restent NOT_RUN. Télécharger le ZIP avant expiration, conserver son SHA256 et l'URL/ID du run, puis vérifier tous les fichiers extraits avec `sha256sum -c SHA256SUMS`. La signature du ZIP et les empreintes de ses fichiers sont deux vérifications distinctes.

Sans modèle explicitement autorisé déjà disponible : tokens réels, prefill/decode, identité de texte ancien/nouveau, nettoyage d'un modèle chargé, TTFT et performances Android sont NOT_RUN/UNKNOWN. Le chargement réel de la bibliothèque et les callbacks ERROR sans modèle n'établissent pas ces capacités. Aucune campagne physique ni installation personnelle n'est autorisée par cette CI. GATE-B reste soumis à la décision utilisateur.
