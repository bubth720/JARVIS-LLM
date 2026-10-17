# Adaptation sensible proposée — suspendue avant modification

**Décision requise seulement pour une mission suivante.** L'issue2 demande :
« Si une modification native ou production est indispensable : fournir le diff
projeté, l'inventaire du risque de régression et arrêter avant modification sensible ».
L'audit constate ce besoin pour F3 positive. Le code historique est donc préservé
byte pour byte, sans stub de preuve ni activation conditionnelle cachée.

Le fichier BACKEND-PROPOSE.diff.txt expose le diff conceptuel minimal des points
à instrumenter. **Ce n'est pas un patch applicable ou compilé** : types/ABI et
verrouillage restent à valider. Il n'est inclus dans aucun sourceSet. Il n'autorise
ni modification de /2, ni admission, ni CORE004D, ni installation.

| Fichier futur | Changement à approuver | Risque et preuve requise avant usage |
|---|---|---|
| AndroidRuntime | Enregistrer admissions/imports/factory avant allocation ; lier clé complète, owner et baux ; observer l'instance existante | Entrée oubliée, course avec import ou stop ; inventaire exhaustif, tests de toutes les entrées, jamais factory pour inspecter |
| LlamaBackend | Hook optionnel fixture, initialisé avant create ; suivi soumissions/retours worker, begin/cancel compris ; observation cohérente ; séquences corrélées | Double réservation, interblocage handleLock/worker/autorité, fuite sur Throwable, changement d'annulation ; suite historique intacte + worker fakeJNI + JNI réels |
| ProcessResources/ResourceCoordinator | Lecture atomique identité/génération des vrais baux, sans acquisition ni fermeture par observateur | Relâchement du bail d'une autre opération/ABA ; IDs non réutilisés, test retours tardifs et mismatch exact |
| bridge.cpp/NativeApi | Sonde read-only du même Engine sur worker, états pointeurs réels + erreur explicite | Use-after-free, ABI/handle invalide, course cancel/destroy ; observation après drain, mutex/ordre exacts, hôte ASan si disponible, JNI/GGUF et Android dédié |
| autorité fixture | Freeze couvrant tous participants, commandes exactes et reçu durable avant publication | Attente sous verrou, service recréé, réception tardive, corruption ; pas COMMIT=grant, aucun retry implicite |

## Invariants à conserver dans toute proposition suivante

Aucun second backend, aucun bail P2Lifecycle ajouté, aucun changement RAM/thermique/
batterie, threads, batch, contexte ou modèle. Les APIs historiques restent disponibles
et sémantiquement identiques ; l'observateur ne doit pas renforcer une preuve si la
voie historique dans le même processus échappe à son registre. Les chemins CPP
partagés exigent une revue même si le hook est opt-in fixture.

Une erreur de chargement a déjà un nettoyage natif ; ne pas dupliquer cette politique
ni compter deux fois les unload. Distinguer appel JNI revenu en erreur, crash sans
retour, nettoyage tenté, réussi, puis bail réellement fermé et reçu durable. Un arrêt
manuellement demandé n'incrémente jamais unload_seq. Le capteur ne doit ni créer un
Engine ni recharger un modèle, et son échec n'a jamais la valeur zéro.

## Ordre recommandé de validation ciblée

1. Approuver précisément le périmètre ci-dessus et le schéma terminal proposé,
   après revue du diff concret final (à produire dans la mission suivante).
2. Implémenter le port réel opt-in sans ouvrir /2, tester sa provenance et chaque
   transition/failure sur worker réel + fakeJNI puis JNI réel séparé.
3. Qualifier un laboratoire Android autorisé (identités et deux processus), puis
   revoir le bundle /2 exact et les preuves avant une éventuelle mission CORE004D.

Ce rapport demande une validation du périmètre sensible ; il ne considère pas
la simple instruction « reprends » comme l'approbation d'un diff encore non revu.
Aucune question d'approbation ne bloque les composants isolés déjà autorisés.
