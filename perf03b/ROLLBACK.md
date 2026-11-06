# PERF-03B — arrêt et retour arrière

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche isolée `codex/llm-perf-03b-native-stream`.
Base autorisée immuable : `ca9d8f060856e134d793d0c23764fae45ce2cc53`, tree `75798737dfc4a618f213ae299b0e467e7547032a`.
Patch appliqué autorisé SHA256 : `747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460`.

## Arrêt sur échec critique

1. Arrêter la qualification et ses étapes dépendantes. Ne pas relancer automatiquement le test ou la génération pour fabriquer un succès.
2. Fermer l'admission expérimentale, conserver l'opération et son état UNKNOWN/CLEANUP_FAILED selon le fait observé. Demander Stop pour l'identité exacte sans inventer de fin ou de libération.
3. Conserver commit/tree, patch/préimages/postimages, code retour/signal, logs bruts, traces sans contenu privé, rapports, JNI/APK construits et SHA256. Ne pas supprimer les preuves d'un premier échec ou réécrire les reçus.
4. Attendre les retours réels disponibles. Une file vide, un terminal, une coroutine annulée, un retour JNI, un PID neuf ou un revert ne prouvent pas DRAINED/UNLOADED. Ne pas démarrer un second moteur pour compenser un retour manquant.
5. Ne pas modifier les quatre sources approuvées, les budgets, seuils, modèles ou gardes pour faire réussir la campagne. Tout correctif JNI/ABI exige un nouveau diff exact et une validation distincte.

Les tests sans modèle peuvent montrer un moteur vide détruit et un handle refusé ensuite ; ils n'attestent pas le nettoyage de poids/KV jamais chargés, une résidence nulle ou un PSS/RSS à zéro. Un échec de cleanup conserve la quarantaine et les ressources selon la politique existante.

## Retour de code contrôlé

La base03A et sa branche restent intactes. L'ancien chemin complet demeure présent dans le patch appliqué ; il n'autorise pas à réadmettre une nouvelle opération avant la preuve de remise en état. Si la branche expérimentale doit être abandonnée, fermer sa PR et conserver branche/artefacts. Si un revert est nécessaire sur une branche de laboratoire validée, créer un commit auditable restaurant les quatre cibles à leurs préimages et supprimant seulement les deux fichiers créés par le patch, sans force-push ni suppression d'historique.

Ne jamais exécuter un `git reset --hard` sur un workspace partagé contenant du travail utilisateur. Vérifier worktree et diff avant toute restauration. Les ajouts de banc/rapports peuvent rester pour documenter l'échec ; une suppression de code ne doit pas supprimer les preuves.

L'APK de laboratoire possède un applicationId distinct et `testOnly`. Elle ne remplace pas les applications historiques et ne fait aucune migration. Aucune installation ou désinstallation sur un appareil personnel n'est autorisée par GATE-A. Si une installation est autorisée plus tard, son guide devra préciser l'identifiant exact et préserver données/modèles externes ; une désinstallation locale ne doit pas être présentée comme un nettoyage natif attesté.

## Gates conservés

| Décision | Portée |
|---|---|
| GATE-A accordé | Application expérimentale du seul patch exact sur03B, préimages revérifiées |
| Nouveau diff sensible | Nouvelle approbation avant toute modification du patch |
| GATE-B | Validation explicite après les preuves logicielles réelles ; incomplet si modèle réel absent |
| Matériel personnel / PERF-03C physique | Accord séparé, APK vérifiée et modèle volontairement autorisé |
| CORE/INTERFACE | Aucun autre dépôt modifié ; périmètre et contrats à valider séparément |
| Merge / activation productive | Accord supplémentaire explicite, jamais automatique |

Le patch F3B historique reste non autorisé et non appliqué. Le FAIL historique du garde sur `AndroidManifest.xml` reste documenté ; ce rollback ne le corrige pas implicitement.
