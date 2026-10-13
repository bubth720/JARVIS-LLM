# Protections et limites

| Profil | Modèle | Contexte/sortie | Threads CPU | Résidence après réponse |
|---|---|---|---|---|
| ÉCO | 0.6B | 1024/128 tokens | 1–2 | 30 s |
| ÉQUILIBRÉ | 1.7B admissible, sinon 0.6B | 2048/256 ou 1024/128 | 1–4 ou 1–2 | 120 ou 30 s |
| AUTO | Même politique adaptative initiale | Selon modèle | Plafonnés | Selon modèle |
| PLEINE PUISSANCE | Refusée sur mobile | Non qualifiée | — | — |

AUTO et ÉQUILIBRÉ partagent volontairement la même politique prudente dans cette version.
Aucun changement de modèle au milieu d'une génération ; arrêt puis repli possible à la demande suivante.

## Admission et surveillance

- Premier plan, ARM64, statuts batterie/thermique valides.
- Batterie <10 % : refus ; <20 % ou économie d'énergie : ÉCO.
- Dès thermique LIGHT : ÉCO ; MODERATE : ÉCO ; SEVERE ou supérieur : arrêt.
- Headroom >=0,8 : ÉCO ; >=1 : arrêt si valeur finie disponible. Cet indicateur n'est pas une température.
- Headroom indisponible : statut thermique ; statut absent/incohérent : refus.
- Budgets estimés conservateurs : 2 Gio (0.6B), 4 Gio (1.7B), plus réserve système max(1 Gio, 20 % du total accessible).
- Aucun budget basé sur l'extension mémoire. Ces budgets peuvent refuser un appareil autrement capable.
- Crédit résident pour poursuite ; après changement de modèle, réadmission sans crédit.
- PSS au-delà du budget ou pression mémoire Android : arrêt.
- Vérification toutes les deux secondes et callback thermique ; prévision thermique toutes les quinze secondes au maximum.
- Une opération lourde ; deadline monotone native de 90 s pour chargement/génération.
- Arrêt manuel, arrière-plan, profil ou sécurité : signal atomique, déchargement après retour natif.
- Import borné en taille/disque et contrôles par bloc ; aucune inférence automatique au démarrage/import.

Aucun root, mlock, wakelock, service permanent ni modification des protections système.

## Limites

Parsing, appels système et certaines allocations/libérations ne sont pas immédiatement interruptibles.
Abandon d'un graphe CPU GGML testé sur hôte ; arrêt d'un vrai Qwen sur Android encore à qualifier.
Le contrôle périodique n'est pas un plafond RAM atomique ; Android peut tuer le processus avant un contrôle.
Capteurs OEM et températures SoC non garantis. Protections natives Android toujours indispensables.
Zéro chauffe, zéro usure et zéro risque ne sont pas promis ; charger la batterie ne relève pas les limites.
