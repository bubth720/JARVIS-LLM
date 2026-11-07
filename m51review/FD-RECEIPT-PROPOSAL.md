# Proposition distincte de chargement par FD / reçu fort — NON APPLIQUÉE

Le patch 8df826… reste inchangé. Ce document n'est pas un patch compilable ni une nouvelle signature JNI approuvée. Il décrit le prochain diff à préparer si l'hypothèse de snapshot coopératif est refusée.

## Problème observé

Le chemin Java vérifie SHA et métadonnées d'un pathname, puis le loader JNI historique rouvre ce chemin. Des permissions courantes et un inode/mtime stables ne démontrent pas les octets réellement consommés pendant load/mmap ; un FD d'écriture préexistant reste utilisable après chmod0400. Les tests synthétiques le reproduisent sans modèle.

## Contrat candidat à revoir

- Une API native additionnelle ouvrirait le fichier une seule fois avec flags sûrs, identifierait le FD et vériferait taille/hash exacts sur ce FD sous une autorité de chargement préexistante. Aucune route load/generate ne contournerait le scope.
- Il faut vérifier les capacités de la version llama.cpp épinglée : support réel du chargement depuis FD ou map immuable, propriété/durée du FD, stabilité des pages, coût de copies et intégrité jusqu'à fin de résidence. Un simple `/proc/self/fd/N` ne crée pas une immutabilité contre les autres écrivains de l'inode.
- Si un snapshot scellé Linux est retenu, comparer memfd/seals/stockage privé et leurs coûts à budget constant ; Android ne doit pas hériter automatiquement d'une solution hôte. Aucune duplication durable de modèle ni second moteur.
- Le reçu natif devrait lier scope, identité de requête, modèle/taille/hash mesurés, configuration exacte, handle/incarnation, baux broker observés et résultat réel de load. Il ne vaut pas mandat CORE ni preuve d'unload.
- L'échec d'identité, scellage, hash, métadonnées, préparation ou cleanup ferme l'admission. Conserver les anciennes signatures et le rollback.

## Préconditions et tests du futur diff

Étude de compatibilité pinned llama.cpp/NDK/ABI, préimages précises des fichiers bridge/NativeApi/contrat, refus truncate/swap/in-place-write, mmap après fermeture, annulation pendant hash/load, erreurs JNI/callback, cleanup échoué, fuites FD, ASan/UBSan, ARM64 et comparaison de contenu réel sous autorité approuvée. Toute modification JNI/ABI et tout changement de budget nécessitent une nouvelle validation du diff exact ; aucun achat ni acquisition de modèle n'est impliqué par cette proposition.
