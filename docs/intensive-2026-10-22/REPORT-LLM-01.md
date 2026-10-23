# LLM-01 — Catalogue local expérimental

Base vérifiée : `design/003f-f3b-worker-connection`, `8f1c2f5a936d6c01874d7be6bb987f4459b83374`.
Fiche lue : planification `fdb71605d91b69484a38fcf0df283365a2493f38`.
Branche : `feat/llm-01-runtime`. Le SHA livré est celui de cette branche, pas celui du présent document.

## Fonction réellement programmée

Catalogue SQLite additionnel `verified-models-v1.db` : identité SHA, taille, référence opaque,
provenance/licence déclarées, architecture/quantification déclarées, capacités, date et sélection.
Deux identités se sélectionnent/désélectionnent après réouverture. Un doublon conserve la provenance initiale.

Écran « Modèles locaux (catalogue expérimental) » accessible après libération par le chemin existant
`releaseForCatalogue`. Import SAF explicite : une copie privée sous `models/catalog-v1/<sha>.gguf`,
`VerifiedCopy.install` existant (taille, magie, SHA, espace libre, renommage atomique), même broker
`ProcessResources`, annulation et seuils import thermique/batterie identiques au chemin historique.
Aucun téléchargement, nouveau moteur, worker ou contexte. Suppression volontaire limitée à cette copie ;
le document original et les fichiers historiques `ModelStore` restent intacts.

La sélection concerne **le catalogue**. Elle ne modifie pas le choix automatique historique du chat.
Le raccordement de ces identités à l'inférence n'est pas livré : ne pas annoncer un modèle interchangeable
qualifié sur Android. Les métadonnées GGUF sont déclaratives ; seule l'intégrité de la copie est vérifiée.
L'état `EXECUTABLE_UNQUALIFIED` ne vaut ni mesure de tokenizer ni preuve d'inférence réelle.

## Préservation et limites

Le moteur, `ModelStore`, `VerifiedCopy`, `ChatViewModel`, les budgets, `SafetyPolicy`, les ressources et toutes
les fixtures restent octet pour octet identiques à la base (garde `tools/check-intensive-llm.py`).
Base SQLite additionnelle, aucune migration des bases existantes, aucune nouvelle permission réseau
ou service exporté. Une interruption brutale peut laisser un `.part` ou une copie orpheline : aucun effacement
automatique ; inspection/reprise explicite encore à compléter. L'atomicité réutilisée ne prouve pas la résistance
à une coupure électrique. Aucun modèle réel, appareil personnel, voix privée ou conversation utilisé.

Les anciennes gardes F3A/F3B figent historiquement tout `app`/`domain` : cette mission autorisant des ajouts
applicatifs, leur incompatibilité de périmètre est rapportée, sans réécrire ni neutraliser leurs assertions.
Une CI de campagne exécute la nouvelle garde et les suites réelles. Le job historique de téléchargement de
modèles est désormais strictement optionnel, `workflow_dispatch` avec `run_model_probe=true` ; jamais au push.

## Démonstration reproductible

Ouvrir le nouvel écran pour lire le refus de qualification, sans bibliothèque native initialisée.
Les tests synthétiques importent une fausse signature GGUF et vérifient sélection persistante, déduplication,
SHA invalide, troncature, annulation, conservation de la copie précédente et suppression volontaire.
Ces octets ne sont pas un modèle et ne prouvent aucune génération.

Bilan exécuté : **240 tests JVM/Robolectric réussis, zéro échec/erreur/saut**, CTest Linux natif **1/1**,
APK app et fixtures compilés, Lint sans erreur, contrôle APK signé ARM64/16KiB/sans réseau réussi.
Les 8 nouveaux cas API29/35 ne constituent pas un test de modèle.

Résultats et commandes réellement exécutés : `VALIDATION-LLM-01.json`.
Prochaine mission : LLM-02 sur branche fille, après validation de cette livraison.
LLM-03 dépend aussi d'UNI-01 et reste soumis aux autorisations natives distinctes.
