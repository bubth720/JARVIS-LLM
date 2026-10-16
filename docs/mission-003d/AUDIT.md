# Audit et préservation 003C

Base exacte : `43503f59f530f2bb48955bce9d7d71e2470fb668` ; branche dédiée, sans main.

Conversation.kt définit les demandes et l'interface interchangeable. LlamaBackend utilise un worker unique, les baux internes ProcessResources et le JNI réel. bridge.cpp valide Qwen3/Q4_K_M, applique les configurations natives, le template officiel et le callback d'arrêt. ResourceCoordinator ne connaît que son processus et ses baux HEAVY/MODEL.

Changements examinables : paramètre SYSTEM facultatif, validation stricte P2, JNI distinct sans troncature, refus de génération concurrente, conservation du bail en cas d'échec de nettoyage et blocage jusqu'au déchargement. L'ancien appel generate, les réglages du modèle et le catalogue restent conservés.

Contrôle automatisé : `python3 tools/check-preservation-003d.py`. Tous les fichiers existants hors exceptions nominatives sont comparés octet pour octet. Les tests existants restent identiques ; aucun seuil n'est réécrit. Le rapport JSON distingue les changements autorisés de la préservation des politiques. Il ne prétend plus que tout le runtime est inchangé.

Sources CORE/UNIFIED consultées en lecture seule : SOURCES.json et matrice de compatibilité. Aucun fichier de ces dépôts n'est modifié. Aucun modèle GGUF ni donnée privée n'est inclus dans l'APK ou les artefacts.
