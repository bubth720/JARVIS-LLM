# PERF-03A — audit du chemin réel

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche de revue `codex/llm-perf-03a-native-review`.
Base demandée et retenue : commit `09e278b54b8be58a3d665d2d91de10f8869e40bb`, tree `975fc71395ac7758678cb5af1b76858ee614c9b2`. La PR PERF-02 #10 est fermée sans fusion ; son contenu n'est pas présumé présent dans main. Le commit/tree final et les preuves exécutées sont consignés dans le rapport central et les artefacts de CI.

## Périmètre vérifié

Les documents PERF-02 `REPORT.md`, `PROTOCOL.md`, `NATIVE-STREAMING-PROPOSAL.md`, `MEASUREMENT-TEMPLATE.json`, `README.md`, `check.py`, puis `docs/fusion-02/NATIVE-REVIEW.md` ont été lus. Tous les fichiers historiques restent préservés : ce dossier prépare un patch à approuver, sans l'appliquer au runtime. Aucun modèle n'est importé/téléchargé et aucun appareil physique n'est testé. **Toutes les durées du moteur réel ci-dessous sont NOT_RUN.** Les coûts sont déduits des sources, sans mesure CPU/PSS ni estimation de gain chiffrée.

Le graphe productif observé est `MainActivity → ChatViewModel → LlamaBackend → NativeBridge → llama.cpp`, dans l'application LLM. Le graphe cible `INTERFACE → CORE → admission LLM → …` n'est pas raccordé. Les fixtures M50/PERF ne constituent pas cette intégration.

## Étapes et candidats

Les chemins Kotlin de l'application ci-dessous sont relatifs à `app/src/main/java/fr/jarvis/llm/` ; les fonctions C++ sont dans `app/src/main/cpp/bridge.cpp`.

| Étape | Fonction / fichier | Durée réelle | Coût visible dans les sources | Risque et optimisation candidate | Impact attendu |
|---|---|---|---|---|---|
| Envoi UI | `MainActivity.onCreate`, listener `send`, ligne103 | NOT_RUN | Lecture et conversion de l'EditText ; appel du ViewModel sur le thread UI | Chronométrer après validation d'envoi ; aucun ACK/spinner ne vaut premier texte | Hypothétique : attribution du délai, sans accélération en soi |
| Validation et catalogue | `ChatViewModel.send`, ligne217 ; `router.route` ligne231 | NOT_RUN | Validation taille/état, routage et stats catalogue sur IO ; potentielle attente de dispatcher | Conserver provenance catalogue/synthétique ; mesurer séparément, ne pas compter cette réponse dans le TTFT modèle | Hypothétique : distinguer routage et inférence |
| Admission | `ChatViewModel.sendLlm`, ligne258 ; `domain/.../SafetyPolicy.kt:decide` | NOT_RUN | Échantillonnage capteurs/mémoire, choix du profil, contrôle foreground/ABI/modèle | Ne pas retirer de garde pour réduire le délai ; mandat CORE live absent | Aucune réduction autorisée des protections |
| Construction du moteur | `ChatViewModel.engine`, ligne145 ; constructeur `engine/LlamaBackend.kt`, `handle = native.create()` | NOT_RUN | Exécution sur le thread appelant ; `create` fait `call_once`, init backend puis allocation Engine | Un déplacement de lifecycle peut créer courses/cancel/UAF ; instrumenter avant modification | Hypothétique : éviter blocage UI lors de la première construction ; non appliqué |
| Vérification du fichier | `ChatViewModel.sendLlm`, ligne286 ; `ModelStore.verify` sous bail MODEL_IMPORT | NOT_RUN | Lecture SHA-256 en IO, coût linéaire dans la taille du fichier ; enveloppe déclarée distincte du PSS | Ne pas remplacer la vérification par un cache de confiance arbitraire | Hypothétique : attribution du coût froid, aucun contournement |
| Chargement et réutilisation | `LlamaBackend.load`, ligne56 ; JNI `NativeBridge_load` ligne86 | NOT_RUN | Worker unique, déchargement préalable, lecture metadata, mmap poids, création contexte/KV ; budgets existants | `loaded` compare `(path, RuntimeConfig)` complet : changer le cap de sortie peut provoquer une recharge. Séparer une future identité de résidence exige preuve et étude ; aucun changement ici | Hypothétique : éviter uniquement des recharges démontrées inutiles |
| Revalidation idempotente | Deux appels `model.load` dans `ChatViewModel`, lignes292/298 | NOT_RUN | Le second appel devient no-op natif si paire déjà identique ; contrôlé sur le même worker | Il protège contre un unload idle/background déjà en file. **Ne pas le supprimer** | Pas de gain supposé par suppression |
| Attente worker et entrée Kotlin | `LlamaBackend.generate`, ligne87 | NOT_RUN | CAS `generating`, dispatch mono-worker, acquisition HEAVY du même propriétaire | Mesurer dispatch+admission+encodage ; ne pas qualifier ce total d'attente pure de file | Attribution seulement |
| Préparation JVM → JNI | `LlamaBackend.generate`, lignes94–98 | NOT_RUN | Nouvelles listes/arrays UTF-8 des rôles et textes ; JNI recopie dans strings C++ | Préparation consommable exacte candidate, liée au modèle/template/configuration/opération/bail ; pas de cache global approximatif | Hypothétique : moins de copies, à mesurer |
| Template et tokenisation | `format`, ligne118 ; `generate`, boucle ligne169 ; fixture `measureFixtureP2`, ligne236 | NOT_RUN | Buffer initial16Kio, éventuel resize ; tokenizer réel ; legacy peut retirer des paires historiques puis reformater/retokeniser. Fixture compte puis régénère le même rendu | Préparer une fois sous identité exacte ; préserver SYSTEM, contrôles injection, politique de contexte et question entière | Hypothétique : éliminer une double préparation démontrée, pas abaisser la qualité |
| Prefill | `generate`, lignes178–183 | NOT_RUN | `llama_memory_clear(..., true)` puis decode par batch : poids chauds ne signifient pas KV chaud | Arrêt vérifié entre batches et callback abort llama existant ; aucun KV approximativement partagé entre sessions | Streaming ne supprime pas ce coût ; gain TTFT calcul non garanti |
| Échantillonnage | `llama_sampler_init_greedy` et `llama_sampler_sample`, lignes184/188 | NOT_RUN | Même sampler déterministe, un vrai token non-EOG par itération | Observer à cet endroit le premier token réel ; distinguer token natif, UTF-8 utilisable et rendu | Permettre une mesure authentique, sans faux texte |
| Pièces et buffers | `llama_token_to_piece`, lignes190–194 | NOT_RUN | Allocation d'un `vector<char>(256)` à chaque token, resize éventuel ; append dans `std::string output` | Réutilisation bornée d'un scratch, contrôles de longueur et reconstruction UTF-8 stricte ; pas de remplacement U+FFFD inventé | Hypothétique : baisse d'allocations ; non mesurée |
| Decode suivant | `generate`, lignes195–198 | NOT_RUN | Decode du token échantillonné pour le prochain pas ; calcul sérialisé | Stop pendant decode/callback à tester ; ne pas créer un deuxième worker pour contourner l'attente | Aucun débit garanti par streaming |
| Retour JNI historique | `generate`, lignes201–203 | NOT_RUN | Une NewByteArray et copie de **tout** le texte seulement après la boucle | Nouveau callback synchrone opt-in proposé ; signatures historiques conservées ; références et exceptions à qualifier | Hypothétique : premier affichage plus tôt pour réponses longues |
| Conversion et diagnostics | `LlamaBackend.generate`, lignes99–101 ; `ChatViewModel.sendLlm`, ligne303 | NOT_RUN | Décodage String complet, `.trim()`, rafraîchissement allocations et snapshots | Mesurer séparément. Ne pas mélanger buffers llama/PSS/RSS ni déplacer des contrôles sans étude | Hypothétique : attribution des copies et du coût diagnostic |
| Publication et historique | `ChatViewModel.sendLlm`, réponse complète puis update | NOT_RUN | Liste des messages et String final ; aucune publication partielle actuellement | Adaptateur candidat versionné avec historique logique unique ; traitement différentiel et batch de vrais fragments | Hypothétique : réduire travail UI répété sans perte |
| Rendu Android | `MainActivity`, collecteur ligne105, `conversation.text = messages.joinToString` ligne110 | NOT_RUN | Reconstruction de toute la conversation pour chaque état, y compris diagnostic seul | Observer frame commit ; renderer de laboratoire incrémental, une fois par frame, sans changer l'ancienne application | Hypothétique : moins de reconstructions ; TTFT physique non mesuré |
| Arrêt et fermeture | `ChatViewModel.stop` ligne181, `onCleared` ligne354 ; `LlamaBackend.requestStop/unload/close` | NOT_RUN | Flag atomique natif, attente worker puis cleanup ; MODEL reste détenu jusqu'au cleanup réel | Annulation coroutine ≠ retour JNI ≠ DRAINED ≠ UNLOADED ; exception cleanup conserve quarantaine | Sécurité à préserver, aucun raccourci de temps |

## Protections conservées

`SafetyPolicy` refuse FULL_POWER, les capteurs requis invalides, l'arrière-plan, ABI non ARM64, batterie<10%, thermalStatus≥3, headroom≥1 lorsqu'il est valide, lowMemory et marge RAM insuffisante. Repli ÉCO si thermalStatus≥1, batterie<20%, économie d'énergie ou headroom≥0,8. Réserve RAM : max(1Gio, RAM totale/5, seuil low-memory Android+256Mio), crédit PSS de résidence borné.

| Profil actuel | Configuration nominale | Budget estimé | Idle unload |
|---|---|---|---|
| TABLET_ECO | SMALL, contexte512, sortie max64, thread1 | 2Gio | 10s |
| ECO / repli | SMALL, contexte768, sortie max96, thread1 | 2Gio | 30s |
| BALANCED/AUTO admissible | MAIN, contexte2048, sortie max256, threads1..4 | 4Gio | 120s |

Ce sont des limites et estimations existantes, jamais des pics mesurés. Aucune quantification, poids, précision, sampler, batch/micro-batch, GPU layers, SYSTEM, seuil thermique, batterie, RAM ou contexte n'est modifié par le dossier 03A.

## Résultats et limites

Le banc PERF-02 et ses résultats restent historiques. Les nouvelles preuves 03A indiquent séparément les tests exécutés au commit exact ; un dry-run de patch ne compile ni n'exécute le JNI candidat. Aucune latence modèle avant/après, valeur p50/p95, mesure PSS/RSS native ou énergie physique n'est disponible : **NOT_RUN / null**, jamais zéro. Cible TTFT<3s : **NOT_QUALIFIED**.

Le garde historique `tools/check-preservation-003e.py` échoue déjà avec `Protected baseline drift: app/src/main/AndroidManifest.xml` ; il n'est ni modifié ni masqué par une CI dédiée verte. La correction légitime de cet écart reste un travail distinct à valider.

Prochaine action : revue GATE-A du patch exact et de ses risques, puis uniquement après accord explicite, application/compilation/qualification native dans PERF-03B. CORE/INTERFACE et campagne matérielle nécessitent leurs validations de périmètre propres.
