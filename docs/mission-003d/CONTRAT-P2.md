# Contrat P2 local versionné

Version domaine : `jarvis.llm.p2/1.0`, sans modification de contrat UNIFIED. Les types P2Request, OperationKey, P2Limits, P2Result et P2Error sont fermés et testés. Rôles d'historique USER/ASSISTANT ; système distinct ; extraits documentaires marqués non fiables ; identifiants, version, SHA et bornes obligatoires. Le decodeur Fields est une vue locale typée, pas un parseur JSON ni un endpoint.

Autorisation refusée par défaut. Le futur hôte devra authentifier le demandeur, vérifier l'autorisation exacte, le GGUF, l'admission matérielle et la session avant appel. Aucun texte ne peut autoriser une action CORE.

## Représentation native actuelle

`GenerationRequest(messages, config, systemPrompt = null)` reste compatible avec les appels existants. Null conserve l'ancien JNI et le système fixe. Une chaîne vide explicite active P2 et ses refus stricts. Une chaîne personnalisée est limitée à 8192 octets UTF-8 valides ; le système fixe natif est toujours préservé, puis les instructions supplémentaires sont ajoutées dans le même rôle SYSTEM.

Le JNI P2 valide à nouveau tailles, UTF-8, délimiteurs et rôles, indépendamment de Kotlin. Les messages sont rendus par `llama_chat_apply_template` à partir du template du GGUF chargé. Le préremplissage Qwen3 `<think>\n\n</think>\n\n` est conservé. Le tokenizer du même vocabulaire compte exactement l'entrée complète avant le premier décodage ; le dépassement déclenche `P2_CONTEXT_EXCEEDED`. La question n'est jamais tronquée.

Ce point d'entrée interne ne remplace pas le protocole P2Request complet. Les plafonds de contexte/sortie plus petits demandés par CORE, documents, preuve d'autorisation et corrélation des callbacks doivent passer par un futur adaptateur vérifié. Le planificateur teste déjà ces règles avec tokenizer injecté ; ses fixtures ne prouvent pas ce raccordement. Aucun repli automatique du port CORE vers un appel historique.

## Capacités et erreurs

`LanguageModel.capabilities` expose `jarvis.llm.engine/1.0`. Le backend réel annonce `separateSystemPrompt=true`, `nativeCancellation=true`, `processLocalCoordinator=true`, mais `structuredCoreSession=false` et `interprocessArbiter=false`. Les adaptateurs historiques non qualifiés gardent les valeurs prudentes par défaut.

Capacités à annoncer au futur CORE : un moteur CPU llama.cpp, modèles SMALL/MAIN vérifiés, SYSTEM distinct disponible dans l'ABI locale, profils admis fournis par SafetyPolicy, contexte et sortie effectifs bornés par le modèle résident, arrêt natif disponible, un seul modèle résident, coordinateur local au processus, absence de transport et de preuve de qualification physique.

Erreurs typées domaine : version/champs/rôles/UTF-8/tailles invalides, contexte dépassé, modèle incompatible, refus d'autorisation ou de ressources, BUSY, CANCELLED, EXPIRED, résultat tardif, NATIVE_FAILURE, CLEANUP_FAILED, UNKNOWN_STATE. Les exceptions JNI restent des IllegalStateException locales ; leur traduction exhaustive en erreurs CORE est à valider, pas un contrat partagé déjà opérationnel.
