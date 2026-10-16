# Disponibilité et limites natives

Livré : système distinct via generateP2, règles fixes, template réel Qwen3, préremplissage vide, comptage tokenizer réel, refus du dépassement sans troncature, validation JNI indépendante, génération et arrêt sur le moteur existant. Configuration mémoire/threads/KV/batch inchangée.

Tests hôte : même bridge.cpp, JNI Java, GGUF public avec SHA contrôlé. Les fonctions d'inspection de prompt sont sous JARVIS_HOST_TESTS, compilées uniquement sur l'hôte. Aucun contenu de prompt personnel publié.

À valider avant raccordement CORE : adaptateur du port structuré avec tokenizer exact pour tous les historiques/documents, limites demandées plus restrictives, liaison des callbacks au modèle d'états, traduction exhaustive des erreurs et application des autorisations de session. UnavailableNativeP2Port reste fermé par défaut. Le chemin LanguageModel configurable existe sans prétendre qu'une intégration CORE est opérationnelle.

À vérifier physiquement : template/génération JNI ARM64 sur chaque appareil, mémoire réelle et thermique, arrêt pendant chargement et génération, retour au repos. La compilation ARM64 et les tests Linux ne remplacent pas ces essais. Aucune installation automatique ni benchmark intensif.
