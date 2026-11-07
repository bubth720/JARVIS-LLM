# Retour arrière et arrêt

État livré : données de revue uniquement, aucune source active remplacée. Fermer la PR/abandonner la branche suffit ; PR #14 et les sources actives demeurent identiques. Ne jamais exécuter le patch au titre de cette instruction.

Après éventuelle approbation distincte : appliquer une seule fois le SHA validé sur une nouvelle branche isolée depuis la préimage exacte, conserver avant/après et preuves, sans fusion. Pour rollback code, revenir à la référence de revue non appliquée ou abandonner la branche ; ne pas réécrire les branches historiques.

Pour un moteur réellement actif : révoquer d'abord la tentative et demander Stop sur le même moteur ; attendre retour JNI/callback fermé ; drainer/annuler la file selon le contrat ; appeler unload explicitement et fermer les vrais baux seulement après réussite native confirmée. Ne pas supprimer un GGUF encore mappé pour prétendre l'avoir déchargé. CONTENT_DONE, DRAINED et UNLOADED ne sont pas interchangeables.

Stop/nettoyage échoué ou retour non confirmé : conserver UNKNOWN/CLEANUP_FAILED et la propriété/quarantaine, arrêter les essais dépendants. Aucun retry automatique, second moteur ou bail de remplacement. Une récupération explicite documentée ou la fin du processus de laboratoire sont des actions distinctes ; la mort du processus n'émet pas de reçu de déchargement historique. Aucun risque de migration ou données personnelles : cette revue n'en crée aucune.
