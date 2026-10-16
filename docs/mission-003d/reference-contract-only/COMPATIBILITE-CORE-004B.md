# Matrice CORE 004B ↔ LLM 003D — sources lues, mapping partiel testé

CORE `mission-004b-inference-contract` est apparue pendant cette mission. Révision lue **07cd282603205d57f3d2ded1b14d5d95e68989e2**, dépôt `bubth720/JAVIS-Local-v2`, sans checkout/modification du dépôt. InferenceContract, InferenceController, LocalLanguageModel, PromptBuilder, début des tests du contrôleur et les trois documents 004B ont été relus. URL/hashes dans [SOURCES.json](SOURCES.json). La question initiale de localisation 004B est ainsi résolue.

**Mapping des champs texte synthétiques testé ; compatibilité complète et parité native non démontrées.** Les 174 tests mentionnés dans VALIDATION CORE sont un rapport versionné lu, pas des tests réexécutés par cette mission LLM. Aucun code CORE importé ou moteur copié.

## Requêtes : mapping effectif limité

`Core004BRequestView` est une **projection locale de fixture**, pas la classe CORE ni un schéma wire. `hasSources` doit être dérivé de la vraie liste sources par un futur adaptateur de confiance, jamais d'un champ client déclaratif. `Core004BMapping.textOnly` est implémenté et testé, sans appel de moteur.

| CORE004B réel | LLM003D | Acceptation / divergence |
|---|---|---|
| contractVersion:Int=1 | version locale `jarvis.llm.p2/1.0` | Conversion explicite pour version CORE1 seulement ; aucune équivalence avec UNIFIED1.0.0 |
| sessionId:String, requestId:String | key.sessionId / key.operationId | Égalité exacte avec binding fourni par l'hôte ; UUID canoniques requis par P2, CORE n'impose que non-vide (les chaînes legacy/prepare et fixtures s1/r1 ne sont pas des IDs intermodules acceptés) |
| systemPrompt | system → rôle SYSTEM | Conservé distinct, jamais injecté dans USER ; `/no_think` du défaut CORE est accepté dans le système autorisé et conservé, `/think` refusé ; règles fixes LLM restent prioritaires |
| question | question intégrale en dernier USER | Jamais transformée en `renderPrompt()` ni tronquée |
| context:String | history/documents typés | **Refus de tout contexte non vide, même espaces**, dans le mapping texte initial : CORE concatène mémoire/documents/historique, séparation fidèle impossible sans nouvelles données structurées |
| sources:List<InferenceSource> | documents avec référence opaque/révision | **Refus de sources non vides** dans ce pilote ; IDs numériques documentId/chunkId, nom/index CORE ne valent pas UUID/révision P2. Aucun RAG/citation/mémoire déplacé |
| GenerationLimits(contextSize,maxTokens) | P2Limits(contextTokens,outputTokens) | Valeurs conservées puis bornes P2 contrôlées ; contextSize CORE1..2048 n'est pas forcément aligné256/≥512 : refus plutôt qu'arrondi. Sortie effective min(demande, profil) |
| pas d'epoch destinataire, bail/auth/cancel/délai/SHA dans InferenceRequest | OperationKey + bindings modèle/délai de l'hôte | Nécessitent handshake/adaptateur de confiance et validation des contrats de contrôle ; pas des champs ajoutés silencieusement à CORE ou UNIFIED |

Le mapping texte suppose context vide/sources vides, cas possible du PromptBuilder sans mémoire/historique/documents. Ce n'est **pas** une affirmation que les parcours ordinaires de CORE ont toujours ce contexte vide. Il ne reconstruit pas les sections `MÉMOIRE…`, `DOCUMENTS…` et `HISTORIQUE…` par heuristique ; `CONTEXT_NOT_REPRESENTABLE` refuse ces cas. La requête P2 générale sait porter des documents/historique typés, mais CORE004B ne les fournit pas encore séparément.

CORE conserve son garde conservateur UTF-8 : `bytes(renderPrompt) + bytes(systemPrompt) + maxTokens +128 ≤ contextSize`. Ce n'est pas le tokenizer Qwen. Le mapper ne remplace **ni ce garde CORE, ni le tokenizer et garde8 LLM** ; les deux validations devront rester satisfaites dans un futur parcours. Tout remplacement/élargissement des limites CORE exige une validation CORE séparée.

## Résultats, arrêts, erreurs : correspondances à relier, pas adaptateur opérationnel

| CORE004B réel | LLM003D | Condition / lacune |
|---|---|---|
| InferenceResponse(sessionId,requestId,text,nativeDone=true par défaut) | P2Result.Text avec clé exacte, publié après déchargement confirmé dans le modèle P2 | Ne jamais s'appuyer sur le défaut true. Le futur adaptateur devra renseigner la preuve réelle ; aucune Text fixture ne doit être émise comme réponse runtime |
| Aucun modelUnloaded sur InferenceResponse | UNLOADED + unloadConfirmed | CORE continue prudemment à considérer le modèle détenu après infer ; un unload explicite corrélé/status confirmé sera nécessaire, même si P2 a déjà libéré. Contrat actuel non idempotent wire défini : à valider |
| StopResult.REQUESTED/UNSUPPORTED/NOT_RUNNING | requestStop retourne l'intention de signaler le natif une seule fois | true n'est **pas** un ACK natif ; false après stop répété n'est **pas** NOT_RUNNING. Le futur adaptateur doit distinguer déjà demandé, appel absent et capacité unsupported |
| EngineCompletion(nativeDone,modelUnloaded) | Fin effective et libération séparées | Seuls retours worker/unload réels peuvent alimenter ces booléens ; ni snapshot fixture ni isLoaded=false ne suffisent |
| UnloadResult.CONFIRMED/UNKNOWN/FAILED | UNLOADED/UNKNOWN/CLEANUP_FAILED | UNKNOWN/FAILED ne libèrent pas une nouvelle opération ; confirmations doivent être liées à epoch/bail/opération |
| InferenceFailure(nativeDone,releaseUnconfirmed) | NATIVE_FAILURE/CLEANUP_FAILED + états | CLEANUP_FAILED exige releaseUnconfirmed=true et maintien de quarantaine ; **ne pas le convertir en simple InferenceRejected** avec nativeDone supposé true |
| Contrôleur quarantined, pas de reset forcé | Nettoyage explicite réessayable dans le modèle LLM | Une récupération locale LLM ne peut pas effacer automatiquement la quarantaine CORE ; procédure explicite encore à décider |
| BUSY, CONTRACT, SYSTEM_PROMPT, BUDGET, STALE_RESPONSE, STOP_REQUESTED, RELEASE_UNCONFIRMED, DELEGATE_DISABLED | Erreurs P2 plus détaillées | Pas d'isomorphisme : UNAUTHORIZED, EXPIRED et INSUFFICIENT_RESOURCES n'ont pas de refus CORE dédié. Leur mapping ne doit pas masquer ces motifs ; évolution/validation nécessaire |
| Epoch locale Long CORE, invalide sur stop/session/sélection | Epoch destinataire UUID + génération de bail | Domaines distincts ; ne pas convertir l'un dans l'autre. Rejeter réponses anciennes dans les deux contrôleurs |
| HISTORICAL / DELEGATED_SIMULATOR, factory paresseuse | Aucun délégué P2 opérationnel | Historique conservé ; simulateur non activé. CORE historique stop=UNSUPPORTED, drainage obligatoire. Aucun fallback automatique vers LLM |

Le contrôleur CORE reste process-local, tout comme ProcessResources LLM. Aucun des deux ne garantit seul exclusivité inter-APK ou absence de warm-up historique/Whisper. P2Lifecycle ne doit pas acquérir une deuxième fois les baux autour du LlamaBackend actuel ; le futur adaptateur doit raccorder des preuves réelles au pipeline unique.

## Référence UNIFIED v1 inchangée

Les cinq schémas, SEMANTICS et six documents D4-02 ont été lus à `52a44fb…` ; leur rédaction demeure proposée. La demande utilisateur valide O1/O2-P2/O4 pour cette mission, pas une modification des schémas.

- Request-envelope `text.generate` peut référencer un DTO P2 derrière content_ref après négociation du type/version et résolution privée autorisée. Ne pas ajouter directement system/history/documents dans l'enveloppe v1 fermée ; pas de chemins arbitraires.
- P2 n'est ni protocol_version1.0 ni contract_version1.0.0. Canonicalisation/digest, parsing strict octets/profondeur/entiers avant DTO et négociation manquent. Le budget inputBytes local borne les contenus textuels, pas l'enveloppe entière : contrôles wire supplémentaires obligatoires.
- Response-envelope : résultat privé via artifact_ref+SHA **ou** erreur, provenance véritable. Verification=fixture pour fixtures, pas runtime. `P2Error.unifiedCode` propose des codes déjà permis, sans serializer.
- INVALID_ROLE/UTF8/INPUT_TOO_LARGE/CONTEXT_EXCEEDED/CONTEXT_NOT_REPRESENTABLE → INVALID_INPUT ; UNSUPPORTED_VERSION/MODEL_MISMATCH/NATIVE_P2_UNAVAILABLE → INCOMPATIBLE ; UNAUTHORIZED/BUSY/EXPIRED/CANCELLED/INSUFFICIENT_RESOURCES/CLEANUP_FAILED gardent leur sens. STALE_OPERATION → INTERRUPTED, UNKNOWN_STATE → UNKNOWN ; statut/retryable à valider, aucun retry automatique.
- Resource-lease : génération, stop, fin et release distincts ; pas de réservation RAM ni bail inter-APK livré. Capability/health doivent annoncer P2 natif indisponible ; disponibilité du module autonome ≠ P2 actif.
- Les cinq enveloppes ne définissent pas prepare/cancel/status/unload : aucune capability cancel inventée. Nouveau protocole de contrôle et O3 doivent être validés séparément.

## Validations encore requises

1. Accord croisé sur versions/IDs/bindings, limites et périmètre texte strict ; CORE004B n'est pas un drop-in P2.
2. Évolution distincte pour contexte/histoire/mémoire/documents et provenance opaque/révisions, sans parsing heuristique ni modification de données canoniques.
3. Mapping exhaustif réponses/erreurs/stop/unload/quarantaine ; callbacks et compteurs du worker réel, sans valeurs par défaut trompeuses.
4. Rendu/tokenizer/JNI P2 opt-in réel, puis tests natifs/physiques, aucun seuil modifié.
5. Serializer/handshake/identités/arbitre, O3 et autorisations par dépôt. Aucun endpoint ni intégration opérationnelle ici.
