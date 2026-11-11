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

`create, begin, cancel, loadSealedFd(h, sourceFd, exactSize, sha256Bytes, memoryBudgetBytes, context, threads, small) → trace JSON, generateP2, countP2Tokens, allocations, unload, destroy`.

La trace ne contient ni chemin ni contenu : taille, condensés copiés/scellés, sceaux, `sealedInodeMapped`, `sourceInodeMapped=false`, `pathLoaderExported=false`.

## Coût mémoire

Le memfd est de la mémoire partagée anonyme (shmem) **non récupérable sous pression** tant que le modèle est chargé, contrairement au page-cache d'un fichier mappé. `llama.cpp` mappe ce memfd (mmap), sans seconde copie : coût résident ≈ taille du GGUF (≈ 397 Mio pour Qwen3-0.6B Q4_K_M) + KV/compute. Pendant la copie, le page-cache de la source s'ajoute temporairement (récupérable). L'hôte Android doit donc fournir `memoryBudgetBytes` à partir de `ActivityManager.MemoryInfo.availMem` moins son seuil `lowMemory` ; le profil ÉCO doit refuser plutôt que forcer.

## Limites connues

- Contexte applicatif Android (`untrusted_app`, seccomp/SELinux) **NOT_RUN** : la suite émulateur s'exécute en `adb shell`. À prouver par test instrumenté dans l'hôte V1 (CORE-01).
- POCO X8 Pro : NOT_RUN (DEVICE-01, action humaine).
- A01 (racine d'autorité opérateur) reste fermée : A02 prouve l'intégrité des octets, pas l'autorisation d'un modèle.
- **Modèle réel : BLOCKED.** Le dépôt officiel `Qwen/Qwen3-0.6B-GGUF` ne publie pas de fichier Q4_K_M (constaté par la CI run 43083191718), or le chargeur protégé n'admet que Q4_K_M. Le repli Unsloth utilisé auparavant par R02 n'est pas une source autorisée ici : aucune génération réelle via A02 n'est donc revendiquée. Décision humaine requise : (a) autoriser `unsloth/Qwen3-0.6B-GGUF` à une révision et un SHA-256 exacts, ou (b) quantifier soi-même les poids officiels Qwen en Q4_K_M avec le llama.cpp épinglé (reproductible, empreinte publiée).
- Correctif de CI : les étapes `| tee` s'exécutaient sans `pipefail` ; un BLOCKED apparaissait vert. Le workflow impose désormais `shell: bash` (pipefail) et isole le modèle réel dans son propre job.
