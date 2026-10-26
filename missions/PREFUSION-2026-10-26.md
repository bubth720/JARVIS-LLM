# MISSION CODEX LLM-PF-01 — Qualification moteur natif et contrat consommateur CORE

Dépôt : bubth720/JARVIS-LLM
Chemin : missions/PREFUSION-2026-10-26.md
Branche de distribution : mission/prefusion-gates-20261026
Base Git vérifiée lors de la préparation : f147236820e0633b0c88f6f1725e828f71167b27

## Règles obligatoires
Codex lit d'abord AGENTS.md si présent, la CI au SHA exact, les PR ouvertes et le code réel. Créer sa propre branche de développement à partir du HEAD de cette branche mission; préserver contrats, historique, données et tests. 100 % local hors ligne au runtime, sobriété Android, minSdk stable. Aucun merge main, changement architecture majeur, installation utilisateur, activation sensible, suppression de preuve ni prise de permission implicite. Chaque phase est un commit testable et réversible.

## Phases

### 1. État réel

Lire docs/fusion-02/REPORT.md, CORE-CONSUMER.md, NATIVE-REVIEW.md, GenerationClientContract.kt, ClientStreamMonitor.kt et NativeBackendLifecycleTest.kt. Contrôler SHA, branches, CI/JNI et minSdk29. Ne jamais annoncer fini un unload natif sur simple arrêt logique.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 2. Moteur et budget

Qualifier chargement réel GGUF, tokeniseur exact, types de refus, mise hors mémoire, streaming, annulation et native UNKNOWN sur émulateur/API29+ puis ARM64 si disponible. Les budgets 0.6B/1.7B restent prudents; FULL_POWER OFF sur Android. Pas de téléchargement implicite.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 3. Compatibilité CORE

Tester contrat P2/GenerationClientContract, epoch/generation, refus des faux ACK, trous de séquence, replay, injection de documents, UNKNOWN sticky et budgets de contexte. Offrir port offline OFF par défaut sans nouveau Binder/public service sans revue de signature UID/certificat.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 4. Concurrence VOICE

Décrire et tester protocole de réservation d'une seule opération lourde, libération native prouvée et arbitrage CORE. Absence de preuve => refus; aucun accès au coffre vocal depuis LLM.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 5. Livraison

Exécuter tests domain/app/native réellement disponibles et les workflows GitHub Actions du dépôt. Contrôler ABI, 16Kio si applicable, mode avion, thermiques, no INTERNET. PR brouillon, preuve exacte et rollback.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

## Livrables et blocages
PR brouillon sur base correspondante, changelog technique, fichiers concernés, matrice dépendances, commandes et résultats tests (succès/échecs/skips/non exécutés), artefacts et SHA du commit, risques, retour arrière, prochaine action. Une réussite JVM ne constitue pas une preuve physique ni un GO Android. Aucun merge dans main.
