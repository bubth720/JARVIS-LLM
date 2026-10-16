# Mission native séparée nécessaire — aucune adaptation native active 003D

Le périmètre présent livre les contrats purs, pas un JNI P2. Avant toute implémentation ou activation, faire valider :

1. Une entrée **opt-in/versionnée** du backend/JNI existant, avec absence de branchement dans le parcours autonome tant que non qualifiée. Pas de second moteur, service ou lib native concurrente.
2. Un SYSTEM correctement transmis au template officiel chargé du GGUF, conservant politique fixe et système CORE autorisé. Ne pas faire passer le nouveau SYSTEM à l'ancienne API user/assistant, qui le refuse ; ne pas laisser cette API préfixer une deuxième politique ni tronquer l'histoire P2.
3. Un compteur fiable rendu complet + préfill + tokens spéciaux/BOS du même modèle/vocabulaire, provenance/digest template, refus si prompt+sortie+8 dépasse le contexte utile. Aucun estimateur caractères/octets présenté comme tokenizer. Vérifications avant allocations additionnelles/génération et maintien des contraintes SMALL/MAIN.
4. Un pipeline commun vérification GGUF, admission matérielle actualisée, annulation native et ressources pour toutes les entrées. Les gardes sont aujourd'hui principalement dans ChatViewModel ; **ne pas appeler LlamaBackend seul depuis un futur endpoint**. Revoir l'ordre admission/bail → SHA → load → format/tokenize → generate → retour → unload et les limites exactes de chaque buffer UTF-8.
5. L'observation du worker réel et de ses baux : begin/load/generate/requestStop/unload/destroy, erreurs et retours partiels. Raccorder un état de contrat sans empiler les acquisitions du modèle de test sur celles de LlamaBackend. Conserver les baux si cleanup_failed/inconnu et bloquer une nouvelle opération tant que la fin/libération n'est pas prouvée.
6. Tests natifs effectifs : systèmes distincts, template officiel Qwen3 exact, délimiteurs, contextes512/768/2048 et cap SMALL/MAIN, annulation répétée pendant load/decode, résultat tardif, erreur partielle et nettoyage. Les tests de DTO et le CTest historique ne couvrent pas ces chemins.
7. Qualification de performances/ressources **sur appareil**, après autorisation d'installation/essais distincte, sans stress ni baisse des seuils. Aucun succès cloud ou mock n'autorise une activation automatique.

Même après cette mission native, CORE004B/contrats partagés/transport/identités et arbitre global exigeraient leurs validations propres. Aucun changement de ces éléments n'est implicitement autorisé par 003D.
