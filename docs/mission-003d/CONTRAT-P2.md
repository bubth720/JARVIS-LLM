# Contrat local P2 — Mission LLM 003D

**Version locale `jarvis.llm.p2/1.0`, testable, inactive dans le parcours Android.** Ce n'est ni le contrat UNIFIED 1.0.0, ni un protocole IPC, ni un contrat partagé modifié. Sources : [D4-02 épinglé](SOURCES.json). O1, O2/P2 et O4 sont autorisées par la présente demande utilisateur, même si les documents D4-02 antérieurs les décrivent encore comme propositions. Aucun accord O3/transport, activation native ou installation n'est déduit.

## Séparation des contenus et autorité

`P2Request` est immuable, à constructeur fermé, créé par `create` ou le décodeur local `decode(Fields)`. Les listes sont copiées et non modifiables. Système CORE, tours d'historique, extraits documentaires et question sont des champs distincts. Aucun outil, rôle tool/system dans l'historique, permission, URI, chemin ou mémoire à écrire ne peut être ajouté au DTO. Un texte généré n'accorde aucune permission et n'exécute aucune action.

| Champ | Type et règle |
|---|---|
| version | Exactement `jarvis.llm.p2/1.0` |
| key | Epoch destinataire, opération, session, corrélation, annulation, référence d'autorisation, bail : UUID canoniques minuscules ; génération entière Long 1..2^53−1 |
| system | Instructions CORE, UTF-8 strict, ≤8192 octets, vide autorisé ; jamais déclassées en user |
| history | ≤6 tours, nombre pair ; alternance user/assistant, ≤4096 octets UTF-8 par tour ; aucun rôle system/tool/action |
| documents | ≤4 extraits, ref opaque UUID unique + révision Int >0 + texte ≤4096 octets UTF-8 ; toujours données non fiables |
| question | Non vide, UTF-8 strict ≤8192 octets, intégralement conservée |
| model / modelSha256 | SMALL ou MAIN, digest exact minuscule de 64 hex ; pas un nom libre ni un nouveau modèle |
| limits | contextTokens Int 512..2048 aligné256 ; outputTokens 1..256 ; inputBytes 1..65536 ; remainingMillis 1..90000 |

Le décodeur `Fields` reçoit des listes explicites de champs pour refuser les doublons, champs inconnus/manquants et mauvais types (ni booléen, ni flottant, ni coercition de chaîne en nombre). C'est **un décodeur de DTO en mémoire, pas un parseur JSON**. Un futur transport doit limiter les octets/profondeur, refuser clés dupliquées/UTF-8 malformé et normaliser strictement Int/Long **avant** construction des objets. Aucune compatibilité wire n'est affirmée.

`inputBytes` borne la somme des contenus textuels décodés, pas les tokens ni la taille d'une enveloppe sérialisée. Le futur contrôle UNIFIED `max_input_bytes` devra aussi porter sur le payload réellement transporté, métadonnées comprises ; aucune équivalence automatique de ces budgets.

Les marqueurs ChatML (`<|…`), marqueurs de raisonnement (`<think>`, `</think>`, `/think`, `/no_think`), `[INST]`, `[/INST]`, `<<SYS>>`, NUL et contrôles incompatibles sont refusés dans tous les textes entrants, y compris système CORE. Les règles fixes internes ne viennent pas du demandeur. Ce choix ferme refuse certaines citations littérales ; aucun échappement ou suppression silencieuse ne les transforme.

Ce filtrage **ne prouve pas l'absence d'injection sémantique** dans un document en langue naturelle. L'autorité d'outils, de confirmation et de mémoire doit rester dans CORE ; un système en langage naturel n'est pas un mécanisme d'autorisation.

## Autorisation et admission : refus par défaut

`CoreAuthorizationGate` doit être fourni par un hôte de confiance qui vérifie identité, autorisation courante et **requête exacte**. Par défaut il refuse. Les fixtures acceptent explicitement des requêtes synthétiques ; ce callback de fixture n'est pas une authentification CORE. Les UUID, authorizationRef, rôles déclarés ou un texte « autorisé » ne prouvent aucun droit. La réception IPC/authentifiée, reçu/digest canonique et révocation restent à concevoir/valider dans leur mission propre.

Le lifecycle exige cette autorisation avant son bail. Le planner la revérifie avant tokenisation. Les données d'admission (`SafetyDecision`), empreinte GGUF vérifiée et tokenizer sont des **entrées de confiance locales**, jamais des déclarations provenant du payload. Le futur pipeline doit les produire depuis DeviceMonitor/SafetyPolicy/ModelStore réels ; appeler le backend directement ou fabriquer un SafetyDecision côté client n'est pas permis.

Aucun seuil n'évolue : SMALL 2 Gio, MAIN 4 Gio, réserve et contraintes batterie/thermique/arrière-plan inchangées. PLEINE PUISSANCE reste refusée. Le modèle admis doit être exactement celui demandé : un repli SMALL ne valide pas une demande MAIN. Configuration validée par les contraintes `GenerationRequest` existantes ; seul le plafond de sortie peut être réduit au minimum demande/profil. La limite de contexte utile est aussi le minimum, sans agrandir/reconfigurer le KV ou les budgets matériels.

## Mapping sémantique vers Qwen3

`P2Planner` prépare une seule instruction SYSTEM : **politique native fixe exacte**, instructions CORE autorisées subordonnées, puis règle explicite sur les documents non fiables. Les tours user/assistant gardent leur rôle et ordre. Le dernier USER contient les extraits identifiés comme données, puis la question complète en fin. Aucun texte documentaire n'est promu en SYSTEM. Le préremplissage assistant de raisonnement court est conservé : `<think>\n\n</think>\n\n`.

Chaque message **après composition** est aussi borné à 16000 caractères et 128 Kio UTF-8, conservant les bornes textuelles legacy/native ; un agrégat de documents trop long est refusé avant le tokenizer, jamais raccourci.

Les structures `PromptSpec/PromptMessage` ne contiennent pas de ChatML construit à la main. `TemplateTokenizer` devra appliquer le **template officiel du GGUF réellement chargé**, puis son vocabulaire, politique BOS/special tokens et préfill, et retourner `TokenMeasurement` avec digest modèle/template et origine de vérification. Un tokenizer absent/incompatible doit refuser. Les tests actuels injectent un compteur **FIXTURE** ; un test compare la politique fixe à la source C++ existante, pas un rendu Qwen réel.

Admission : `tokens(prompt complet formaté + préfill) + sortie effective + garde 8 ≤ contexte utile`. L'addition est en Long pour éviter un débordement. Système, documents, historique et question sont tous comptés. **Aucune troncature**, même d'historique : un dépassement refuse toute la requête. L'appelant peut proposer une nouvelle requête avec une sélection explicite, sans mutation silencieuse ni changement de permissions.

`fixtureOnly=false` refuse un compteur FIXTURE. L'étiquette NATIVE_VERIFIED est réservée au futur adaptateur de confiance ; elle n'est pas un champ de requête, et aucune implémentation native de ce compteur n'existe ici. Une étiquette ou fixture ne démontre pas la parité.

## Compatibilité actuelle et refus explicite

`Conversation.kt`/`LanguageModel`/`GenerationRequest`, `LlamaBackend.kt`, `ProcessResources.kt`, `bridge.cpp`, SafetyPolicy et catalogue restent inchangés. L'ancien parcours autonome garde exactement sa politique native et son retrait d'historique actuel. La nouvelle règle de refus intégral s'applique au contrat P2 seulement.

Le bridge actuel ajoute lui-même son SYSTEM fixe, accepte uniquement user/assistant et peut retirer des tours. Il ne représente donc pas P2. **`UnavailableNativeP2Port.generate` retourne NATIVE_P2_UNAVAILABLE**, sans appeler, préchauffer ou charger l'ancien backend. Aucun repli système→user, aucune activation dans l'APK, aucun nouveau moteur ou worker. Les appels de contrat ne passent pas par le catalogue ; le catalogue et son routage rapide restent ceux de l'application autonome.

Voir [cycle de vie](CYCLE-DE-VIE.md), [matrice CORE](COMPATIBILITE-CORE-004B.md) et [mission native restante](ADAPTATIONS-NATIVES.md).
