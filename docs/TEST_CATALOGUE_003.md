# Essais Mission 003 sur Redmi Pad SE

APK 0.3.0 `fr.jarvis.llm`, application unique pour Redmi et POCO. Les premiers essais de catalogue ne nécessitent **aucun GGUF**, aucune connexion et aucun benchmark intensif.

## Installation et sauvegardes

1. Télécharger l'artefact **jarvis-llm-debug** de l'exécution CI réussie indiquée dans VALIDATION.md ; connexion GitHub généralement nécessaire.
2. Extraire et vérifier `SHA256SUMS` : Linux/macOS `shasum -a 256 JARVIS-LLM-debug.apk`, Windows `Get-FileHash .\JARVIS-LLM-debug.apk -Algorithm SHA256`. Le SHA doit correspondre à celui du fichier APK, pas au digest du ZIP GitHub.
3. Copier l'APK localement et installer. Ne pas remplacer CORE, VOICE ou INTERFACE.
4. **La clé debug générée par un nouveau runner peut différer de l'installation 002C.** Si Android refuse la mise à jour, ne pas désinstaller aveuglément : conserver d'abord le GGUF original et son hash hors des données privées de l'application. Une désinstallation efface les modèles et la base privés ; la conversation n'était pas persistante. Une stratégie de signature durable n'est pas encore livrée.
5. Après une installation neuve, vérifier 8 réponses de démonstration dans Catalogue. Les 1 500 réponses synthétiques ne sont pas ajoutées automatiquement.

## Réponses simples en mode avion

1. Activer le mode avion, sélectionner TABLETTE ÉCO et conserver les conditions thermiques normales. Copier le diagnostic initial (RAM, PSS, batterie/thermique).
2. Envoyer `Bonjour` plusieurs fois : les deux variantes doivent alterner ; statut Catalogue ; aucun modèle chargé.
3. Envoyer `Tu marches quand il n'y a pas de connexion ?` : réponse enregistrée sur le fonctionnement local.
4. Envoyer `Tu ne fonctionnes pas hors ligne ?` ou une demande nouvelle : ne pas obtenir automatiquement la réponse positive du catalogue. Le LLM ne sera sollicité que s'il est importé **et** admis par les protections.
5. Envoyer `batterie` : clarification proposée ; répondre `1` ou reformuler. La clarification ne charge pas le LLM.
6. Revenir à l'accueil Android et rouvrir : aucune inférence automatique. Noter le PSS ; un pic échantillonné ne capture pas nécessairement toutes les pointes.

## Gestion du catalogue

- Ouvrir Catalogue : le moteur résident est libéré avant navigation.
- Toucher une réponse, modifier un texte et enregistrer : révision +1. Une variante suffit. Plusieurs variantes doivent exprimer le même sens.
- Ajouter une petite réponse USER avec ID/intention stables et une formulation complète. Vérifier qu'elle répond hors ligne.
- Exporter JSON vers un dossier local, puis réimporter sans changer le contenu : import sans effet. L'export contient les réponses courantes et les suppressions logiques, pas tout l'historique.
- Supprimer la réponse test : elle disparaît de la recherche ; historique conservé. Un ancien export de révision inférieure est refusé **en entier** et ne la ressuscite pas.
- Si un export échoue, supprimer son fichier incomplet ; ne pas l'utiliser comme sauvegarde.

## Variantes et conflits — petits fichiers synthétiques

Les fichiers sont dans `catalogue-fixtures/` du paquet. Importer d'abord `catalogue-variants-test.json`, puis envoyer `Donne une réponse de démonstration` plusieurs fois : rotation entre deux formulations, marque TEST SYNTHÉTIQUE.
Importer `catalogue-conflicts-test.json`, puis envoyer `Le service de test est-il disponible ?` : clarification de conflit ; aucune des assertions opposées ne doit être choisie ni un LLM chargé pour contourner ce conflit.
Ces données sont artificielles. Aucun service réel n'est décrit. Pour retirer les tests, supprimer les entrées concernées dans Catalogue ; les tombstones restent versionnées.

## Passage à l'échelle — facultatif et séparé

`catalogue-synthetic-1500.json` contient uniquement des entrées TEST SYNTHÉTIQUE. Importer une fois, dans de bonnes conditions de batterie/thermique et sans génération simultanée. Ce n'est pas une campagne de stress : arrêter au moindre refus ou comportement anormal.
Vérifier 1 508 réponses actives sur une base contenant uniquement les 8 démonstrations (davantage si d'autres fixtures ont été importées). Envoyer `Test synthétique référence 1499` : seule la réponse synthétique correspondante est pertinente. L'écran ne charge que 50 réponses à la fois.
La suppression en masse et la remise à zéro du catalogue ne sont pas proposées dans cette livraison. Pour rester sur le petit catalogue courant, ne pas importer les 1 500 entrées ; les tests cloud couvrent déjà cette taille.

## LLM et relevé final

Pour une demande non couverte, reprendre un **essai bref** avec votre Qwen3 0.6B déjà qualifié, si l'admission est normale. Exemple : `Explique les aurores boréales en une phrase`. Le chargement peut toujours être refusé faute de RAM, batterie ou capteur ; ne pas forcer ni abaisser les seuils.
Tester Arrêter sur cet unique échange et noter le délai réellement observé ; votre arrêt initial de 1–2 s ne garantit pas celui-ci. Vérifier le déchargement et copier le diagnostic final. Pas de boucle, conversation prolongée, chauffage volontaire ou PLEINE PUISSANCE.

Relever : version APK/firmware, état initial et final, résultat catalogue/clarification/LLM/refus, PSS actuel/pic échantillonné, minimum RAM disponible, délai ressenti de recherche/import/arrêt, anomalie éventuelle. Les temps cloud ne sont pas des objectifs de performance de la tablette.
