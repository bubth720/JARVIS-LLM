# Mission 002C — Redmi Pad SE : optimisation et limites de qualification

Travail limité à JARVIS-LLM, branche `mission-002c-tablet`, APK 0.2.1. Aucun changement de CORE, VOICE, INTERFACE ou main. Aucun essai physique ni benchmark intensif.

## Décision de sécurité

Les valeurs fournies (Android 15, Snapdragon 680, 7,51 Gio accessibles, 3,24 Gio disponibles, 76 %, thermique normal) ne constituent pas une mesure faite par ce cloud.
La réserve minimale vaut 1,502 Gio, éventuellement davantage selon le seuil low-memory Android. Le budget conservateur 0.6B reste 2 Gio. Premier chargement : au moins **3,502 Gio disponibles**, soit un déficit d'environ **0,262 Gio /268 Mio** dans le relevé fourni. Le refus est conservé à 3,24 Gio.
Ni la réduction du contexte, ni une mesure RSS Linux, ni la taille du GGUF ne donnent un crédit mémoire pour contourner cette admission. Fermer normalement des applications utilisateur peut libérer de la RAM ; aucune quantité libérée ni disponibilité durable n'est garantie. Ne pas utiliser de nettoyeur, swap ou arrêt de services système comme justification du budget.

## Mode autonome TABLETTE ÉCO

| Paramètre | ÉCO renforcé existant | TABLETTE ÉCO |
|---|---:|---:|
| Modèle Android admis | Qwen3 0.6B Q4_K_M | identique |
| Contexte total, prompt + historique + sortie | 768 | 512 |
| Sortie maximale | 96 | 64 |
| Batch / micro-batch | 32 /8 | 16 /4 |
| Threads CPU | 1 | 1 |
| Déchargement après inactivité | 30 s | 10 s |
| Budget d'admission estimé | 2 Gio | 2 Gio |

Cache K/V FP16, MMAP, repacking optionnel désactivé, un seul emplacement de logits et CPU uniquement conservés. Le contexte est réellement transmis à llama.cpp ; son alignement sur 256 est contrôlé. Les limites Kotlin et natives concordent, y compris la sortie 64. La génération garde une marge de 8 tokens et refuse une question trop longue après réduction de l'historique.
Le contexte 512 est court : réponses et suivi de conversation plus limités. Un lot plus petit peut ralentir le préremplissage. Le déchargement rapide économise de la mémoire en résidence, mais des rechargements répétés coûtent de l'énergie. Aucune autonomie ni vitesse n'est promise sans mesure sur appareil.
ÉCO, ÉQUILIBRÉ, AUTO restent disponibles. PLEINE PUISSANCE reste réservée et refusée sur ce prototype ; adaptateur `LanguageModel` préservé. Aucun lien au téléphone, service distant ou téléchargement intégré.

## Comparaison des modèles et quantifications

| Candidat | Intérêt | Limites et décision |
|---|---|---|
| Qwen3 0.6B Q4_K_M | Référence Android actuelle, multilingue, non-thinking imposé | Premier choix conservé ; qualité française et mémoire tablette restent à mesurer |
| Qwen3 0.6B Q3_K_M | Tenseurs quantifiés plus compacts, kernels K-quants présents dans llama.cpp épinglé | Baisse de précision susceptible d'affecter davantage un petit modèle ; aucune équivalence de qualité supposée ; non admis par l'application |
| Qwen3 0.6B Q2_K | Réduction supplémentaire de certains tenseurs | Dégradation potentiellement importante ; pas de recommandation pour un assistant français ; non admis par l'application |
| Qwen2.5 0.5B Instruct Q4_K_M | Alternative multilingue, architecture qwen2 supportée par llama.cpp ; cache potentiellement plus petit | Candidat prioritaire de comparaison cloud ; français à qualifier ; aucun bloc think Qwen3 ; non interchangeable sans adaptation du contrat d'import/template/budget |
| SmolLM2 135M /360M | Poids nettement plus petits | Orientation principalement anglaise : compréhension française non démontrée ici ; pas de choix par défaut |

Q4_K, Q3_K et Q2_K occupent respectivement 144, 110 et 84 octets par bloc de 256 éléments dans GGML, soit 4,5, 3,4375 et 2,625 bits/élément pour **ces types de tenseur**. Ce ne sont pas les tailles globales d'un GGUF Q4_K_M/Q3_K_M/Q2_K : mélanges de types, embeddings, métadonnées et vocabulaire changent le résultat. La quantification des poids ne réduit pas automatiquement le cache K/V FP16 ni le tas JVM.
Le script cloud interroge les dépôts officiels Qwen et, lorsque Qwen3 ne publie pas la quantification demandée, le dépôt tiers `unsloth/Qwen3-0.6B-GGUF`. Cette provenance tierce est explicite dans le rapport : elle ne constitue pas une publication officielle Qwen. Il épingle la révision retournée, exige un SHA-256 LFS et une taille bornée, puis vérifie le téléchargement immuable. Il note les quantifications absentes des dépôts consultés sans leur inventer un fichier. Aucun téléchargement de poids n'est intégré à l'APK ; aucun nouveau format n'est silencieusement autorisé dans l'import Android.

## Mesures et protocole de preuve

### Cloud

Le workflow compile l'APK ARM64, exécute les tests JVM, Android **simulés par Robolectric**, natifs, Lint, et vérifie permissions/signature/alignement ELF et ZIP 16 Kio.
Un outil séparé `model_memory_probe` exécute de vraies inférences CPU Linux x86_64 : un prompt français, au plus 24 tokens de sortie, un thread, deadline native 60 s, timeout du processus 90 s. Chaque modèle/contexte démarre dans un processus neuf. Ce n'est ni un test Android émulé d'inférence, ni un benchmark de qualité ou d'endurance.
Les fichiers `comparison.json` et `SUMMARY.md` sont publiés dans `jarvis-llm-model-memory` : révision et hash GGUF, taille disque, réponse, pic RSS Linux et buffers natifs modèle/contexte/calcul. Les modèles ne sont pas embarqués dans les artefacts. Un échec de téléchargement, de checksum, de chargement ou d'inférence d'un fichier disponible fait échouer le job ; aucune réussite fictive.
Les économies natives **mesurées dans ces essais** peuvent être comparées ; elles ne démontrent pas le pic PSS Android, les allocations transitoires, la latence d'arrêt JNI, le comportement du firmware ou l'autonomie de la tablette. Le budget mobile ne change pas en fonction du RSS Linux.
Dans ce cloud de développement, Hugging Face refuse l'accès réseau (403) : aucune mesure locale de vrais poids n'est affirmée. Le runner GitHub constitue une tentative indépendante et son résultat doit être vérifié.

### Application Android

Le panneau conserve PSS actuel/pic échantillonné et par phase, baseline, minimum RAM disponible, budget, réserve, crédit résident et marge/refus. Il ajoute le profil sélectionné et les buffers natifs effectivement alloués par llama.cpp après chargement. Ces buffers excluent une partie des allocations et le tas JVM : ils ne remplacent pas le PSS. API C++ diagnostique privée liée à la révision llama.cpp épinglée ; à revalider lors d'une mise à jour du moteur.
Le pic échantillonné (1 s en activité) peut manquer une pointe. Aucun PSS de modèle sur Redmi n'a été obtenu ici.

### Qualification nécessaire avant toute baisse de budget

1. Identifier exactement GGUF, SHA-256, architecture, quantification et paramètres ; même modèle et mêmes réglages tout au long des mesures.
2. Après validation des protections et admission normale, essai court sur appareil : baseline sans modèle, vérification, chargement, un échange français, arrêt manuel, déchargement, retour arrière-plan.
3. Relever PSS par phase, buffers natifs, minimum disponible, seuil Android, thermique et batterie ; compléter la capture des pointes par une mesure système externe adaptée. Ne jamais démarrer en contournant l'admission pour obtenir une preuve.
4. Répéter seulement les contrôles nécessaires, avec autorisation pour une campagne prolongée/intensive. Couvrir démarrage froid, pression de fond, erreurs de fichier, interruptions et questions proches de la limite.
5. Choisir une enveloppe supérieure aux pics reproductibles avec marge explicite pour les pointes non observées et les différences de firmware. Conserver intégralement la réserve système et le refus si cette enveloppe n'est pas justifiée.

## Résultats réels du cloud — commit 11984dd

Données observées dans l'annotation publique « Cloud GGUF evidence » de [l'exécution 38553690120](https://github.com/bubth720/JARVIS-LLM/actions/runs/38553690120), recopiées dans [le relevé JSON](evidence/mission002c-cloud-11984dd.json). Les hashes et révisions exacts permettent de retrouver les mêmes fichiers. Le rapport distant est dans l'artefact `jarvis-llm-model-memory`.

| Fichier testé | Taille GGUF Mio | Pic RSS Linux à 768 | Pic RSS Linux à 512 | Buffer contexte à 512 |
|---|---:|---:|---:|---:|
| Qwen3 0.6B Q4_K_M — Unsloth | 378,3 | 516,2 | 487,7 | 56,0 |
| Qwen3 0.6B Q3_K_M — Unsloth | 331,0 | 468,7 | 440,4 | 56,0 |
| Qwen3 0.6B Q2_K — Unsloth | 282,5 | 420,3 | 391,8 | 56,0 |
| Qwen2.5 0.5B Instruct Q4_K_M — Qwen | 468,6 | 531,3 | 527,9 | 6,0 |

Toutes les colonnes mémoire de ce tableau sont en **Mio**. Ce sont huit inférences courtes réussies sur Linux, pas des valeurs Android.
Pour Qwen3 Q4, le passage 768 ->512 réduit le buffer contexte de **28 Mio (84 ->56, -33,3 %)** et le pic RSS observé de **28,5 Mio**. Le buffer calcul ne baisse que d'environ **0,086 Mio** : dans cet essai, la baisse des lots économise peu de mémoire par rapport au contexte. Aucun gain PSS ni autonomie tablette n'est mesuré.
À 512, Q3 réduit le RSS observé de 47,3 Mio par rapport à Q4 et Q2 de 95,9 Mio. Ces gains ne démontrent pas une qualité suffisante ni un budget d'admission Android plus faible.
Qwen2.5 0.5B possède un cache nettement plus petit mais son fichier et ses buffers de modèle sont plus gros pour les artefacts comparés : son RSS dépasse Qwen3 Q4 d'environ **40,3 Mio**. Il n'est donc **pas retenu comme solution mémoire** sur ces preuves, malgré son nombre de paramètres nominal plus faible.

L'unique question était « Que signifie bonjour ? ». Q4 a répondu seulement « Bonjour ! », sans donner la définition. Q3 a produit notamment « C'est un salutation », Q2 une explication confuse et répétitive ; Qwen2.5 une réponse partielle et tautologique. Les sorties plafonnées à 24 tokens peuvent être tronquées. Ce test valide un chemin d'inférence non vide et permet de comparer les allocations ; **il ne valide pas la qualité française**, ne permet pas de classer globalement les modèles et ne mesure pas causalement une perte de qualité due à la quantification.

**Recommandation définitive pour cette livraison :** conserver Qwen3 0.6B Q4_K_M en TABLETTE ÉCO, avec le refus à 3,24 Gio libres. Ne pas activer Q3/Q2 ou Qwen2.5 sur Android sans qualification spécifique d'import, formatage, qualité et mémoire. Le gain de contexte est démontré sur Linux ; l'utilisabilité et la marge réelle sur Redmi restent à vérifier lors d'un essai court admis normalement, sans essai intensif.

## Premier essai et risques prioritaires

Installer seulement l'artefact CI réussi selon [FIRST_DIAGNOSTIC.md](FIRST_DIAGNOSTIC.md). Commencer sans importer de modèle : sélectionner TABLETTE ÉCO, copier le diagnostic passif et vérifier les capteurs. À 3,24 Gio libres, le refus attendu n'est pas une panne.
Les instructions d'une conversation courte ne constituent pas une autorisation de benchmark intensif. Aucun essai sur appareil n'est lancé par ce travail cloud.
Risques principaux : pic natif non capturé, mémoire de fond fluctuante, capteurs OEM incomplets, délai d'annulation dans parsing/allocations non immédiatement interruptibles, qualité française d'un très petit modèle, lenteur et chauffe du Snapdragon 680, rechargements fréquents, changement de signature debug lors d'une mise à jour.

## Statut de livraison

Voir la section Mission 002C de [VALIDATION.md](VALIDATION.md) pour les résultats réellement observés et les liens de publication. La disponibilité utilisable sur la tablette à 3,24 Gio n'est pas démontrée ; le refus sûr reste prioritaire.
