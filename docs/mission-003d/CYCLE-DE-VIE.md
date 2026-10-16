# Cycle de vie et contrat de ressources

Version : jarvis.llm.p2/1.0. P2Lifecycle est un modèle testable de protocole, distinct de l'état réel du backend tant que les callbacks ne sont pas raccordés.

États : IDLE → PREPARED → LOADING → READY → TOKENIZING → PROMPT_READY → RUNNING → NATIVE_DONE → UNLOADING → UNLOADED ; STOP_REQUESTED ne vaut pas NATIVE_DONE. CLEANUP_FAILED et UNKNOWN bloquent toute nouvelle opération. CLOSED requiert la libération confirmée.

prepare acquiert HEAVY ; beginLoad acquiert MODEL avant allocation. Une interruption ne ferme aucun bail. Un retour natif associé à la bonne opération prouve seulement la fin de cet appel. confirmUnloaded libère MODEL puis HEAVY après preuve de nettoyage. Résultats tardifs, annulés ou expirés ne deviennent pas des réponses valides. Un nettoyage échoué conserve les baux du modèle de protocole et exige une tentative explicite de récupération.

Dans LlamaBackend réel, un worker sérialise les appels. Le refus immédiat d'une deuxième génération évite leur accumulation. requestStop modifie seulement le drapeau atomique ; unload attend le worker. Le bail MODEL est conservé si le chargement puis son nettoyage échouent. Le réarmement et les nouveaux travaux restent interdits tant que le déchargement n'a pas réussi.

Le coordinateur reste **interne au processus LLM**. Il ne coordonne aucune autre APK. Un futur arbitre devra disposer d'une identité/session authentifiée, d'une génération de bail, d'une opération corrélée, de l'ACK d'arrêt, de la fin réelle du worker et d'une confirmation de déchargement. Ni un timeout, ni un changement d'écran, ni un résultat tardif, ni la seule disparition d'un heartbeat ne suffisent à redistribuer les ressources. Aucun IPC ajouté.
