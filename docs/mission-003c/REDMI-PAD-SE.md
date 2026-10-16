# Redmi Pad SE — fiche de qualification Mission 003C

**QUALIFICATION PHYSIQUE EN ATTENTE UTILISATEUR**  
**Conclusion actuelle : DONNÉES INSUFFISANTES.** Aucune session physique 003C exécutée par Codex, aucune configuration déclarée sûre ou qualifiée.

## Informations antérieures séparées des observations

Android 15, Snapdragon 680, 7,51 Gio accessibles ; environ 593 Mio de pic PSS échantillonné et arrêt observé en 1–2 s dans des essais antérieurs. Ces informations utilisateur ne sont pas des mesures 003C et ne qualifient aucune durée maximale.

Suivre [PROTOCOLE.md](PROTOCOLE.md) dans l'ordre 1 à 10. Ne pas commencer par une génération. Aucune baisse des seuils autorisée.

## Configuration candidate, non qualifiée

| Élément | Cible existante à confirmer dans l'export |
|---|---|
| Profil | TABLETTE ÉCO / TABLET_ECO |
| GGUF | Qwen3 0.6B Q4_K_M ; poids non fournis |
| Contexte / sortie maximale | 512 / 64 tokens |
| Batch / microbatch | 16 / 4 |
| Threads | 1 |
| Enveloppe d'admission | 2 Gio, inchangée |
| Déchargement après inactivité | 10 s, inchangé ; manuel possible dès fin effective |
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

Attention : avec 7,51 Gio accessibles et 3,46 Gio disponibles annoncés, **3,502 Gio au moins** sont requis pour SMALL ; conserver le refus et fournir seulement le diagnostic passif si cette situation persiste. Le pic utilisateur antérieur de 593 Mio ne constitue pas un budget sûr.
