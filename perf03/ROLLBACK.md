# PERF-03 — retour arrière et arrêt des phases

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03a-native-review`, base `09e278b54b8be58a3d665d2d91de10f8869e40bb` / tree `975fc71395ac7758678cb5af1b76858ee614c9b2`.

## Phase 03A livrée

Le patch JNI est un fichier de revue, pas un changement appliqué. Les seuls ajouts sont le dossier `perf03/` et son workflow. Pour abandonner cette phase : fermer la PR et conserver la branche/les preuves ; si les ajouts avaient été intégrés à une branche de laboratoire par accord distinct, en faire un revert explicite. Aucun store, modèle, permission, signature, schéma ou migration n'est concerné. Ne supprimer aucun historique ni aucune preuve de récupération.

## Après une éventuelle autorisation GATE-A

Créer `codex/llm-perf-03b-native-stream` à partir du commit de revue approuvé. Recontrôler le SHA256 du patch et toutes ses préimages avant son application. Une modification du diff exige une nouvelle décision ; le présent dossier n'autorise aucun patch F3B historique par transitivité.

Si un test critique échoue, fermer l'admission expérimentale, conserver erreur/UNKNOWN et journaux, demander l'arrêt de l'opération exacte, attendre ses retours et les preuves réelles de nettoyage. Ni un timeout, ni une coroutine annulée, ni un retour JNI, ni une file vide, ni un revert ne prouvent DRAINED/UNLOADED. Ne pas démarrer un second moteur pour compenser un retour manquant. Un nettoyage échoué conserve la quarantaine et les baux selon la politique existante.

L'ancien chemin complet reste disponible pour un prochain lancement autorisé après remise en état démontrée. Revenir au code ancien par un commit de revert auditable, sans force-push, modification de protection, migration ou suppression de données. Garder les APK de laboratoire distinctes et les signatures existantes ; une installation ou désinstallation sur appareil personnel demande un accord séparé.

## Points d'arrêt

| Gate | Décision séparée |
|---|---|
| A | Autoriser le **diff et son SHA256 exacts**, sur la seule branche 03B, pour qualification de laboratoire |
| B | Après fonctionnement réel et sûreté démontrés : autoriser les appareils personnels et le modèle volontairement importé |
| C | Après mesures et régressions : proposer un raccordement ultérieur CORE/INTERFACE, dans les périmètres validés |
| Merge | Accord explicite supplémentaire, jamais implicite dans les gates précédents |

03A ne livre pas d'APK de streaming actif. Les assemblages historiques servent à la non-régression ; ils ne doivent pas être proposés comme nouvelle génération native.
