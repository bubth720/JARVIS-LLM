# Retour utilisateur simple — un formulaire par appareil/configuration

Copier ce formulaire dans la conversation JARVIS LLM. Remplir seulement ce qui a été réellement observé ; écrire « indisponible » ou « non testé » plutôt qu'estimer. Ne pas envoyer le catalogue privé, le GGUF, des conversations, numéros de série, comptes ou chemins personnels. Les exports bruts peuvent être joints **après relecture**, jamais committés dans Git.

```text
MISSION 003C — appareil :
Android / API / firmware observés :
APK : version / commit / SHA vérifié / certificat de référence :
GGUF : nom / provenance / SHA / vérification à froid :
Profil et configuration réellement chargés (modèle, contexte, tokens, batch, threads) :
Conditions : processus neuf, batterie, recharge, mode avion, thermique :
Passif : RAM accessible / disponible / réserve / marge / PSS / RSS / maximum RSS :
Admission : autorisée ou refusée / motif exact :
Calcul bref : résultat / durée chargement / envoi→réponse complète / méthode :
Mesures par phase : fichier JSON joint ou tableau de la fiche appareil :
Arrêter pendant calcul actif : oui/non/non observé ; déchargement confirmé / délai :
Retour au repos / baux / arrière-plan passif / reprise sans chargement automatique :
Catalogue conservé / anomalies / informations indisponibles :
Nombre de sessions stables et références des JSON (sans chemins privés) :
Conclusion proposée : DONNÉES INSUFFISANTES / NON QUALIFIÉ / qualification limitée à examiner :
```

À défaut de modèle admissible, remplir uniquement identité + passif + motif de refus ; c'est un retour utile, sans autoriser un chargement. Une valeur nulle de consommation ou un « arrêt réussi » ne doit pas remplacer une mesure absente. Pour le premier token natif : écrire **indisponible**, l'application actuelle ne le mesure pas.

Le formulaire structuré `FORMULAIRE-MESURES.json` est optionnel : c'est un document manuel vide, **pas le format d'import du catalogue ni un export physique réel**. Garder les JSON bruts de l'application sans les réécrire ; ils contiennent les horodatages et avertissements utiles à la revue. Leur format reste `formatVersion:1`.
