# POCO X8 Pro — fiche de qualification Mission 003C

**QUALIFICATION PHYSIQUE EN ATTENTE UTILISATEUR**  
**Conclusion actuelle : DONNÉES INSUFFISANTES.** Aucune session physique 003C exécutée par Codex, aucune configuration déclarée sûre ou qualifiée.

## Informations antérieures séparées des observations

11,06 Gio accessibles et 3,68 Gio disponibles annoncés antérieurement. Version Android, SoC exact, mémoire et conditions actuelles à observer ; aucune nouvelle valeur matérielle supposée.

Suivre [PROTOCOLE.md](PROTOCOLE.md) dans l'ordre 1 à 10. Ne pas commencer par une génération. Aucune baisse des seuils autorisée.

## Configuration candidate, non qualifiée

| Élément | Cible existante à confirmer dans l'export |
|---|---|
| Profil | AUTO ou ÉQUILIBRÉ, un seul profil pour la première campagne |
| GGUF | Qwen3 1.7B Q4_K_M ; poids non fournis |
| Contexte / sortie maximale | 2048 / 256 tokens |
| Batch / microbatch | 64 / 32 |
| Threads | min(4, cœurs exposés), à relever réellement |
| Enveloppe d'admission | 4 Gio, inchangée |
| Déchargement après inactivité | 120 s, inchangé ; manuel possible dès fin effective |
| Quantification KV / moteur | F16 / llama.cpp CPU épinglé 1b70350c92563449576bcbbf552994b1d1ed847d |
| PLEINE PUISSANCE | Désactivée / refusée sur matériel non qualifié |

## Identité et conditions réellement observées — à remplir

| Champ | Observation 003C |
|---|---|
| Modèle exact / Android / API / firmware | NON RELEVÉ |
| Version APK / versionCode / commit embarqué | NON RELEVÉ (attendus 0.3.1-mission003b /5, commit dans SOURCE_COMMIT.txt) |
| SHA APK / certificat SHA-256 comparés à la CI | NON VÉRIFIÉ SUR APPAREIL |
| GGUF exact / provenance fiable / SHA attendu et vérifié à froid | NON RELEVÉ |
| Profil et modèle effectifs / configuration exportée | NON RELEVÉ |
| Date relative de session / processus neuf / autres applications | NON RELEVÉ ; ne pas joindre d'identifiants personnels |
| RAM accessible / disponible / seuil Android / réserve | NON RELEVÉ |
| Batterie / recharge / état thermique / headroom | NON RELEVÉ |
| Température °C / source et type de capteur | NON RELEVÉ ; CPU non exposé par cette application |
| Stockage libre / mode avion | NON RELEVÉ |

## Mesures par phase — octets ou Mio explicitement, ne pas additionner

| Phase | PSS | RSS | VmHWM depuis création | Disponible | Réserve / marge | Buffers modèle/contexte/calcul | Batterie / thermique |
|---|---|---|---|---|---|---|---|
| Baseline passive froide | — | — | — | — | — | — | — |
| VERIFYING (SHA) | — | — | — | — | — | — | — |
| LOADING / modèle chargé | — | — | — | — | — | — | — |
| GENERATING / réponse | — | — | — | — | — | — | — |
| Arrêt / UNLOADING | — | — | — | — | — | — | — |
| Après déchargement / repos | — | — | — | — | — | — | — |
| Reprise au repos (capture séparée) | — | — | — | — | — | — | — |

`—` signifie non mesuré, jamais zéro. Les buffers sont des tailles déclarées, pas des PSS ; le pic RSS peut être indisponible et ne représente pas un pic PSS. Les estimations d'admission restent distinctes de toutes ces mesures.

## Résultats à renseigner

- Admission : **NON OBSERVÉE** ; autorisée/refusée et motif exact : …
- Modèle réellement chargé (pas une réponse catalogue) : **NON OBSERVÉ**.
- Vérification SHA / durée chargement approximative / méthode : …
- Latence envoi → réponse complète / durée génération courte : …
- Premier token natif : **INDISPONIBLE avec l'interface actuelle**, ne pas inventer de TTFT.
- STOP_REQUESTED pendant calcul actif : … ; si réponse déjà finie, indiquer non observé.
- Retour effectif du déchargement / MODEL_UNLOADED / délai observé : …
- Baux de résidence/travail / retour au repos / reprise : …
- Catalogue conservé après erreur/arrêt (comparaison de l'export privé local, sans le publier) : …
- Courant/puissance/énergie/autonomie : **NON MESURÉS**, pourcentage batterie ponctuel seulement sauf source OEM explicite.
- Export(s) JSON brut(s), référencés sans chemins personnels : …
- Anomalies / chauffe / pression mémoire / arrêt système : …
- Nombre de sessions stables, maximum trois après premier essai sain : …

## Décision

Choisir après revue humaine : **QUALIFIÉ / NON QUALIFIÉ / DONNÉES INSUFFISANTES**. Une admission refusée ne prouve pas une incompatibilité définitive : garder le refus et indiquer données insuffisantes pour l'inférence. Une anomalie matérielle justifie NON QUALIFIÉ pour le scénario concerné et arrêt de la campagne. Un essai court réussi ne qualifie pas contexte plein, autonomie prolongée, charge concurrente externe ou tous les profils.

Pour une qualification limitée d'un scénario bref : identité/configuration établies, mesures physiques couvrant les phases, marge système conservée, absence d'anomalies, répétitions cohérentes, arrêt réellement observé et déchargement confirmé. Toute mesure manquante limite explicitement la conclusion ; ne pas cocher QUALIFIÉ par défaut. L'absence de preuve d'annulation active laisse cette protection matériellement non qualifiée.

Prochaine action : diagnostic passif puis retour via [RETOUR-UTILISATEUR.md](RETOUR-UTILISATEUR.md). Aucun benchmark intensif.

Attention : à 11,06 Gio accessibles, MAIN exige au moins **6,212 Gio disponibles** à froid. À 3,68 Gio annoncés, le refus est attendu même pour SMALL (minimum 4,212 Gio). Une sélection AUTO/ÉQUILIBRÉ peut se replier sur SMALL si lui seul est admissible ; cet essai ne qualifie pas le 1.7B. Les essais ÉCO 0.6B/768 et TABLETTE ÉCO 0.6B/512 sur POCO seraient des configurations supplémentaires, jamais validées par transfert des résultats Redmi.
