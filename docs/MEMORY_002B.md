# Mission 002B — mémoire et moteur local (14 octobre 2026)

## Situation POCO rapportée par l'utilisateur

RAM accessible : 11,06 Gio ; disponible : 3,68 Gio. Ce sont des relevés utilisateur, pas des mesures réalisées ici.
Ancienne admission 0.6B : enveloppe estimée 2 Gio + réserve max(1 Gio, total/5).
Avec 11,06 Gio : réserve = 2,212 Gio ; exigence = 4,212 Gio ; manque = 0,532 Gio (environ 545 Mio).
Les 2 Gio ne sont ni la taille du GGUF ni une consommation mesurée de Qwen3 : c'est une enveloppe prudente application + moteur + contexte + buffers.
L'admission la réserve comme capacité supplémentaire ; elle ne soustrait pas automatiquement le PSS de base de l'application.

## Changements réalisés

| Paramètre 0.6B | Ancien | ÉCO renforcé |
|---|---|---|
| Poids | Q4_K_M | Q4_K_M inchangé |
| Contexte total | 1024 | 768 tokens |
| Sortie maximale | 128 | 96 tokens |
| Batch / micro-batch | 64 /32 | 32 /8 |
| Threads | 1–2 | 1 |
| Sorties réservées dans le contexte natif | Défaut lié au batch | 1 (logits du dernier token seulement) |
| Buffers de poids repackés optionnels | Autorisés par défaut | Désactivés pour 0.6B |
| mmap | Activé | Conservé ; pas de mlock |
| Cache K/V | FP16 par défaut | FP16 explicite ; aucune quantification expérimentale du cache |
| CPU / offload GPU | CPU | CPU ; offloads KQV et opérations explicitement désactivés |
| Enveloppe d'admission | 2 Gio | **2 Gio conservés** |

Le 1.7B garde contexte 2048, sortie 256, batch 64/32 et plafond de quatre threads.
Le profil AUTO utilise aussi le 0.6B renforcé lorsqu'il sélectionne le repli. PLEINE PUISSANCE reste refusée.

## Compatibilité native examinée

llama.cpp est toujours épinglé à `1b70350c92563449576bcbbf552994b1d1ed847d`.
`src/llama-context.cpp` aligne n_ctx sur 256 : 768 est accepté sans arrondi supérieur.
Un helper partagé entre JNI et test natif construit les paramètres, vérifie alignement, threads, batch, FP16, CPU et maintien du callback d'abandon.
Le préfill utilise désormais la taille de batch effective renvoyée par llama.cpp ; plus de constante 64 dans la boucle.
Compilation ARM64 et test des paramètres natifs ne prouvent pas une inférence avec le vrai Qwen : celle-ci reste non exécutée.
La réduction de contexte peut rendre une question ou un historique trop longs ; le tokenizer réel retire les anciennes paires ou refuse la question, sans dépasser la fenêtre.

## Nouvelle comptabilité d'admission

Réserve = max(1 Gio, 20 % du total, seuil low-memory Android + 256 Mio). Cette règle n'abaisse jamais l'ancienne réserve.
Premier chargement : crédit = 0 ; disponible requis = budget estimé + réserve.
Pour poursuivre le **même** modèle : crédit limité à l'augmentation de PSS effectivement observée depuis la référence avant chargement, plafonnée par PSS courant et enveloppe.
Un PSS absent/zéro, une référence inconnue, un changement de modèle ou un déchargement supprime ce crédit.
L'ancien crédit égal au budget estimé n'est plus utilisé. Une estimation de résidence ne finance jamais un démarrage.
La marge affichée vaut disponible - disponible requis. Une marge négative explique le refus.
La pression Android, le plafond PSS, les capteurs, le thermique et la batterie restent des conditions indépendantes.

À 3,68 Gio, sans modèle résident prouvé, le refus est **conservé**, même en ÉCO renforcé.
Les nouveaux paramètres ne démontrent pas à eux seuls un budget inférieur.

## Diagnostics et mesures

Panneau défilant et texte sélectionnable : disponible/total, seuil Android, réserve, enveloppe estimée, crédit PSS observé,
disponible requis, marge d'admission, RAM au-dessus de la réserve et taille disque du GGUF 0.6B si présent.
PSS courant, référence au début d'opération, maximum échantillonné, minimum RAM disponible, nombre d'échantillons et maxima par phase.
Phases : passif, vérification SHA, chargement, génération, déchargement.
Échantillonnage environ 1 s actif /2 s résident et aux frontières de phase ; les pointes transitoires peuvent échapper aux mesures.
Le PSS est celui du processus complet, pas une mesure isolée du modèle. Son évolution peut inclure UI et buffers Java.
Le pic affiché n'est ni VmHWM/RSS, ni un maximum continu certifié. Aucun budget ne s'ajuste automatiquement sur ce relevé.
Les imports ne déclenchent pas un relevé PSS coûteux à chaque bloc ; les contrôles de batterie/thermique/mémoire restent exécutés.

## Gains établis et limites

Réductions de paramètres vérifiées : contexte et sortie -25 %, batch -50 %, micro-batch -75 %, un thread plutôt que jusqu'à deux.
Ce sont des réductions de configuration, **pas des gains de RAM ou de vitesse mesurés**.
Le mmap était déjà actif ; il ne constitue pas un nouveau gain.
Repacking désactivé et réserve de sorties limitée évitent des allocations optionnelles, mais leur gain dépend du modèle/backend et reste à mesurer.
**Aucun gain réel de PSS Qwen ou Android n'est démontré.** Hugging Face reste refusé (HTTP 403) après nouvelle vérification ; aucun appareil réel n'est connecté.
Tests synthétiques, source native et compilation ne justifient pas une baisse des 2 Gio.

## Révision future d'un budget

Exiger un artefact GGUF identifié, des mesures par phase sur appareil, une référence sans modèle et des marges au-dessus des seuils système.
Comparer le même firmware, backend et artefact ; examiner chargement froid, cache, reprise et concurrence système.
Un PSS passif ou une unique réponse ne démontre pas un plafond fiable. Inclure marge de variabilité et allocations non vues entre échantillons.
Une éventuelle baisse nécessitera revue explicite des preuves, tests de régression et validation ; aucune auto-calibration permissive.
Aucun essai intensif autorisé ou exécuté. Dans cette version, utiliser uniquement le diagnostic passif en attendant la qualification.
