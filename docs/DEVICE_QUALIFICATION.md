# Qualification matérielle — phase 1

Noms POCO X8 Pro / Redmi Pad SE et Android 16 / 15 : informations utilisateur, non mesurées ici.
Aucun appareil réel accessible dans le cloud ; aucun essai réalisé dessus.
Ne pas compter l'extension mémoire comme RAM physique ni le stockage commercial comme espace allouable.

## Diagnostic passif

Sans chargement ni génération : constructeur, modèle, DEVICE, Android/API, SoC déclaré (API 31+), ABI,
taille de page mémoire, RAM accessible au noyau/disponible, stockage libre, batterie, thermique et PSS.
La RAM accessible exclut certaines réservations ; la rapprocher de la fiche physique.
Pas de mesure de vitesse du stockage ou de capacité GPU/NPU. CPU seul, accélérations non qualifiées.
Un fabricant peut retourner un statut thermique NONE sans capteur suffisamment informatif : sa plausibilité reste à vérifier.

| Champ | POCO X8 Pro | Redmi Pad SE |
|---|---|---|
| Référence/variante/firmware | À relever | À relever |
| RAM physique / extension séparée | À confirmer | À confirmer |
| SoC, ABI, taille de page | À relever | À relever |
| RAM disponible / stockage allouable | À mesurer | À mesurer |
| Capteurs thermiques | À vérifier | À vérifier |
| GPU/NPU | Non qualifiés | Non qualifiés |
| Inférence et arrêt réels | Non testés | Non testés |

## Avant les essais

1. Lire résultats automatisés et protections ; installer sans modèle.
2. Relever le diagnostic et les informations constructeur.
3. Vérifier appareil froid, batterie >=20 %, stockage et réserve mémoire.
4. Après validation des protections, importer le 0.6B vérifié et sélectionner ÉCO.
5. Une question courte puis un arrêt contrôlé ; pas de boucle de benchmark.
6. Vérifier PSS, capteurs et arrêt avant toute session prolongée.
7. Qualifier ensuite 1.7B sur le POCO si admissible.

L'admission automatique ne constitue pas une certification matérielle.
