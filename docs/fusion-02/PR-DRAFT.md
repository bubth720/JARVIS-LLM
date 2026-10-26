Étend le banc client LLM sans toucher au runtime :18 nouveaux cas couvrent les doubles ACK, identités divergentes, délais, révocation, absence de modèle/permission et courses stop/réception. Un test explore4096 séquences courtes, explicitement TEST-ONLY.

Le nouveau collecteur exige des résultats frais, les285 identités historiques exactes, des sources inchangées et zéro doublon/échec/saut.11 tests Python négatifs vérifient les refus. Le dossier F3B contrôle sept préimages et l'applicabilité sèche du patch ; aucun patch appliqué ni preuve native fabriquée.

Validation locale :303 JVM/Robolectric,11 Python et CTest1/1, zéro échec/saut ; compilation ARM64, Lint et contrôles APK réussis. Tous les fichiers de production, client/1, contrats, protections et catalogue restent identiques à FUSION-01.

Statut PARTIAL : JNI/GGUF, tokenizer exact, unload, CORE004D et appareils restent bloqués. Guides de qualification future et prérequis CORE/VOICE fournis sous docs/fusion-02/. Aucune installation, migration, fusion main ou activation sensible.

Base de revue proposée : feat/fusion-01-llm-integration. PR brouillon uniquement ; ne pas fusionner ni clôturer prématurément la mission directrice.
