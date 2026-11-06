# PERF-03B — JNI hôte réellement chargé, sans modèle

Le harness Java déclare les mêmes exports JNI et charge le **bridge approuvé** compilé avec les sources llama.cpp épinglées. Il n'utilise ni faux llama ni injection de texte. Les callbacks ERROR/CANCELLED exécutés par JNI sont réels ; aucun ACK/TOKEN, prefill, decode, modèle chargé ou TTFT n'est revendiqué sans GGUF autorisé.

```bash
bash tools/cloud-env.sh bash tools/test-native.sh
bash tools/cloud-env.sh bash -c 'python3 perf03b/native-tests/run_jni_host.py \
  --library build/native-tests/libjarvis_llm.so --jdk "$JAVA_HOME" --lane plain'
bash tools/cloud-env.sh bash perf03b/native-tests/run_sanitized_host.sh
```

Pour un shell qui n'a pas déjà `JAVA_HOME`, sourcer d'abord `tools/cloud-env.sh`, ou passer explicitement le JDK21 configuré. Les scripts ne téléchargent pas de modèle. `tools/test-native.sh` peut préparer les sources de bibliothèque vérifiées selon l'outillage historique ; aucune génération GGUF n'est effectuée ici.

22 cas vérifient le lifecycle de handles vides, les signatures historiques refusant l'absence de modèle, les erreurs de descripteur, le callback synchrone sur l'appelant, la conservation exacte d'une exception Java et d'un OOM **injecté**, les bindings invalides, les handles périmés/non réutilisés, les doubles annulations, les appels de cleanup refusés pendant un callback, la concurrence et la libération du pin après le vrai retour. 512 invocations bornées exercent les références locales sous `-Xcheck:jni`. Le test de callback bloqué est relâché explicitement par le harness : une annulation n'est jamais interprétée comme son retour ou un unload.

L'absence de modèle oblige `generateStream` à émettre un terminal ERROR ou CANCELLED sans contenu. Cela teste le transport JNI de contrôle ; ce n'est pas un streaming de tokens, un test de backpressure TOKEN, ni une preuve de performance. Le nettoyage d'un modèle réellement chargé et les reçus globaux DRAINED/UNLOADED restent NOT_RUN/UNKNOWN. Les refus d'un GGUF absent n'attestent pas un déchargement de poids résidents.

Le runner vérifie le patch et les quatre postimages avant chaque lane, enregistre SHA256 des sources et du binaire, vrai commit/tree, état worktree et logs. Toute erreur du harness, diagnostic `-Xcheck:jni`, ASan/UBSan ou timeout arrête la qualification et crée `build/perf03b/CRITICAL-STOP.json`. Aucun retry ni réparation du diff approuvé n'est automatique.

La lane séparée ASan/UBSan instrumente les vrais fichiers C/C++ et lance aussi CTest. LSan est actif pour le test CTest natif autonome ; il est explicitement **NOT_RUN pour la JVM**, dont les allocations et la gestion mémoire ne constituent pas un test LSan fiable. ASan puis `libstdc++` du même compilateur CXX sont préchargés, dans cet ordre, uniquement dans le processus Java. Leurs SHA256 sont enregistrés. Cette disposition permet à ASan de résoudre `__cxa_throw` dès le démarrage, avant le chargement tardif du JNI par la JVM ; le contrôle d'ordre ASan reste activé et aucun diagnostic n'est masqué. Les options SIGSEGV sont adaptées au fonctionnement de la JVM ; aucun seuil ni budget Android n'est modifié. Les petites limites heap du harness n'ont aucune valeur de benchmark de modèle ou d'admissibilité matérielle.

Les résultats sont écrits dans `build/perf03b/jni-host-plain/` et `jni-host-sanitized/`, ainsi que `build/perf03b/sanitized-build/` (logs de compilation/CTest et copie de `LastTest.log`, même en cas d'échec). Les objets et bibliothèques intermédiaires restent dans `build/perf03b-native-sanitized/`, hors du dossier d'artefact ; le runner conserve le SHA256 de la bibliothèque réellement chargée. Une campagne réelle exige un modèle déjà autorisé, les protections inchangées et les prérequis d'autorité documentés par la mission. Aucun fichier productif ou patch approuvé n'est modifié par ces bancs.
