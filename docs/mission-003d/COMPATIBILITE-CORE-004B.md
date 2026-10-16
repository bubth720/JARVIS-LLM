# CORE 004B ↔ LLM 003D

Références immuables et empreintes : SOURCES.json. Aucune modification de CORE/UNIFIED.

| Besoin CORE | État LLM | Limite avant raccordement |
|---|---|---|
| systemPrompt distinct | GenerationRequest optionnel → JNI P2 → SYSTEM Qwen3 | Qualification Android réelle en attente |
| question conservée | Refus P2 si contexte insuffisant | Pas de troncature silencieuse |
| contrat versionné | P2Request/Key/Limits/Result/Error locaux | Pas un schéma partagé ou un transport |
| contexte/documents | Types séparés dans P2 | Le contexte aplati CORE et ses sources sont refusés par le mapping texte seul |
| plafonds context/output | Planificateur conservateur testé | Adaptateur avec tokenizer réel de tous les messages à raccorder |
| autorisation | Refus par défaut ; règles fixes natives | Authentification/permissions CORE à l'hôte de confiance |
| nativeDone/modelUnloaded | Modèle d'états testable ; unload réel sérialisé | Callbacks corrélés au protocole à raccorder |
| annulation | Drapeau natif, pas seulement coroutine | ACK d'arrêt distinct de fin réelle et déchargement |
| ressources | Un seul modèle, un travail lourd local | Aucun arbitrage inter-APK |
| erreurs CORE | Mapping sémantique proposé | Codes manquants et quarantaines nécessitent validation |
| capacités | Profils et bornes existants conservés | Aucun service de découverte exporté |

L'absence de source documentaire dans le mapping initial est une restriction explicite, pas une suppression silencieuse. Aucun endpoint ni connexion CORE–LLM opérationnelle. Les différences d'identité d'époque, de preuve de déchargement et de codes d'erreur doivent être validées avant un contrat partagé ou IPC.
