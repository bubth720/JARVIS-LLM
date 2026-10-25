FUSION-01 prépare une API cliente locale LLM versionnée sans activer le raccordement natif bloqué. Les requêtes réutilisent le contrat P2, les flux sont corrélés et bornés, et une lecture passive distingue sélection du catalogue et moteur réellement disponible.

Le client refuse toute génération native ; tokenizer exact UNSUPPORTED, preuves de fin/unload UNATTESTED et admission /2 fermée. Le backend, JNI, protections, catalogue, contrats historiques et application autonome restent identiques à LLM-03. Le mock est limité aux tests.

Validation locale :285tests distincts (127domaine,40Robolectric,118fixture), zéro échec/erreur/saut ; CTest1/1, compilation ARM64, Lint sans erreur et contrôles APK réussis. Aucun modèle GGUF exécuté, aucune installation ni mesure physique. Résultats et commandes :docs/fusion-01/REPORT.md.

Statut PARTIAL ; revue native distincte indispensable. PR brouillon uniquement, aucune fusion ni activation sensible.
