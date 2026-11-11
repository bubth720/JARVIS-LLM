# A02 — contrat v1 : chaîne d'intégrité jusqu'au chargeur natif

Statut : **implémenté et testé sur hôte Linux ; Android = suite native sur émulateurs (adb shell) ; contexte applicatif et appareil NOT_RUN**. Aucune admission A01 (autorité opérateur) n'est accordée par A02.

## Invariants

| # | Invariant | Où il est appliqué |
|---|---|---|
| I1 | Le moteur ne reçoit **aucun chemin fourni par l'hôte**. L'unique chemin est `/proc/self/fd/<memfd scellé>`. | `a02_bridge.cpp` : `loadSealedFd` ; le chargeur historique est renommé à la compilation et **n'a plus de nom JNI** (`Java_fr_jarvis_llm_engine_NativeBridge_load` absent, vérifié par `nm`). |
| I2 | La source est un **descripteur régulier** choisi par l'hôte (lecture seule), jamais rouvert par chemin ; lu par `pread`. | `sealed_snapshot()` : `fstat` → `S_ISREG`, taille exacte. |
| I3 | **Budget mémoire réservé avant allocation** : `budget ≥ taille + 64 MiB`, taille ≤ 2 GiB. Refus avant création du memfd. | `require_budget()` → `A02_MEMORY_BUDGET_REFUSED`. |
| I4 | SHA-256 calculé sur **les octets copiés** dans le memfd, comparé au condensé de confiance (64 hex minuscules). | `A02_DIGEST_REFUSED`, `A02_DIGEST_FORMAT_REFUSED`. |
| I5 | Quatre sceaux `F_SEAL_WRITE|GROW|SHRINK|SEAL` posés puis revérifiés par `duplicate_verified_fd` (réutilisé de `perf03/candidate/native/a02_sealed_fd.h`). | `A02_SEALS_REFUSED`, `A02_UNSEALED_REFUSED`. |
| I6 | Les **octets scellés sont re-hachés** avant chargement. | `A02_SEALED_DIGEST_REFUSED`. |
| I7 | Avant l'appel au chargeur, `stat(/proc/self/fd/N)` doit désigner le **même inode/périphérique/taille** que le memfd vérifié. | `proc_path_for()` → `A02_PROC_FD_IDENTITY_REFUSED`. |
| I8 | Après chargement, `/proc/self/maps` doit montrer le mapping du **memfd scellé** et **aucun** mapping de l'inode source ; sinon déchargement et refus. | `inspect_maps()` → `A02_SOURCE_MAPPED_REFUSED`. |
| I9 | Annulation : vérifiée avant et entre chaque bloc de copie et de re-hachage ; le chargeur protégé conserve sa propre annulation. | `A02_CANCELLED`, `Calcul natif arrêté`. |
| I10 | Ownership FD : le descripteur source reste à l'hôte ; le memfd est fermé après chargement (le mapping maintient l'inode), ou immédiatement sur tout refus. | tests « no-fd-leak ». |
| I11 | Le chargeur **protégé et inchangé** (`app/src/main/cpp/bridge.cpp`) applique ensuite ses validations historiques (métadonnées GGUF, Q4_K_M, qwen3, nombre de paramètres). | inclusion textuelle, fichier protégé non modifié. |

## API JNI (`fr.jarvis.llm.a02.SealedNativeBridge`, `libjarvis_llm_a02.so`)

`create, begin, cancel, loadSealedFd(h, sourceFd, exactSize, sha256Bytes, memoryBudgetBytes, context, threads, small) → trace JSON, generateP2, generateStream(h, system, question, maxTokens, contextLimit, binding64hex, callback) → terminal (2 terminé, 3 annulé, 4 erreur), countP2Tokens, allocations, unload, destroy`.

`generateStream` est un simple renvoi vers le `SynchronousStream` protégé (callback `onNativePrepared([B[BI)Z` / `onNativeEvent([BII[BIJI)I`).

## Politique protégée du moteur (inchangée, respectée)

Pour Qwen3 0.6B (`native_configuration.h`) : contexte 512 ou 768, **exactement 1 thread**, sortie ≤ 64 (ctx 512) ou ≤ 96 (ctx 768) jetons. Toute autre configuration est refusée (`Configuration native refusée`) ; A02 et l'hôte V1-A s'y conforment au lieu de la contourner.

La trace ne contient ni chemin ni contenu : taille, condensés copiés/scellés, sceaux, `sealedInodeMapped`, `sourceInodeMapped=false`, `pathLoaderExported=false`.

## Coût mémoire

Le memfd est de la mémoire partagée anonyme (shmem) **non récupérable sous pression** tant que le modèle est chargé, contrairement au page-cache d'un fichier mappé. `llama.cpp` mappe ce memfd (mmap), sans seconde copie : coût résident ≈ taille du GGUF (≈ 397 Mio pour Qwen3-0.6B Q4_K_M) + KV/compute. Pendant la copie, le page-cache de la source s'ajoute temporairement (récupérable). L'hôte Android doit donc fournir `memoryBudgetBytes` à partir de `ActivityManager.MemoryInfo.availMem` moins son seuil `lowMemory` ; le profil ÉCO doit refuser plutôt que forcer.

## Limites connues

- Contexte applicatif Android (`untrusted_app`, seccomp/SELinux) **NOT_RUN** : la suite émulateur s'exécute en `adb shell`. À prouver par test instrumenté dans l'hôte V1 (CORE-01).
- POCO X8 Pro : NOT_RUN (DEVICE-01, action humaine).
- A01 (racine d'autorité opérateur) reste fermée : A02 prouve l'intégrité des octets, pas l'autorisation d'un modèle.
- **Modèle réel autorisé le 2026-11-11 par l'utilisateur** : `unsloth/Qwen3-0.6B-GGUF`, révision `50968a4468ef4233ed78cd7c3de230dd1d61a56b`, `Qwen3-0.6B-Q4_K_M.gguf`, 396 705 472 octets, SHA-256 `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a` (manifeste `a02/models/qwen3-0.6b-q4km.json`). Le dépôt officiel `Qwen/Qwen3-0.6B-GGUF` ne publie pas de Q4_K_M. Toute autre révision, taille, empreinte ou licence déclarée ≠ apache-2.0 est refusée ; aucune source de repli. Périmètre : CI cloud et laboratoire V1-A ; installation sur le POCO = autorisation distincte.
- Correctif de CI : les étapes `| tee` s'exécutaient sans `pipefail` ; un BLOCKED apparaissait vert. Le workflow impose désormais `shell: bash` (pipefail) et isole le modèle réel dans son propre job.
