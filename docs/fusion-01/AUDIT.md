# FUSION-01 LLM — inventaire et préservation L0

Base effective : `2efb3f7aa6205062ab71f570212019fd1d9885b6` (LLM-03).
Inspection du 25 octobre 2026, dépôt autorisé unique `bubth720/JARVIS-LLM`.
Les issues [directrice UNIFIED #4](https://github.com/bubth720/JARVIS-UNIFIED/issues/4)
et [LLM #3](https://github.com/bubth720/JARVIS-LLM/issues/3) définissent cette tranche.
Le manifeste [PROVENANCE.json](PROVENANCE.json) épingle les sources consultées.
Ce manifeste est une projection LLM, **pas le BOM F0 global**, qui appartient à UNIFIED.

## Filiation constatée

| Incrément | SHA | Décision |
|---|---|---|
| 003C | 43503f59f530f2bb48955bce9d7d71e2470fb668 | Ancêtre, qualification physique non transférable depuis CI |
| 003D P2 retenue | daa46432e847ed9613dc403045498e27281eff42 | Ancêtre ; autre 003D divergente non fusionnée |
| 003E | 030152a5f3d888017ad7a7fe8026f2e2c48e8fd5 | Endpoint expérimental séparé de l'APK utilisateur |
| 003F initiale | 111be993e970051686bd5c27b3115320513a4be7 | Ancêtre ; ne pas restaurer son contrat ancien |
| Correction F1/F2/COMMIT | 1ce24405b04a50f97cf30078b9716c7d00c593fc | Ancêtre conservé |
| F3A | aa85ad4b506bb655dbe5b50ec4b125d19b48e81e | Composants isolés, pas une attestation JNI |
| F3B | 8f1c2f5a936d6c01874d7be6bb987f4459b83374 | Ancêtre documentaire ; patch sensible **non appliqué** |
| LLM-01 | 062054edcbf47ba96408f751f25189d86b0b023d | Catalogue/import existants réutilisés |
| LLM-02 | 895a067ffc860d56a2dbb1ce22cb274096f8e3a2 | Planificateur conservé ; mesure exacte UNSUPPORTED |
| LLM-03 | 2efb3f7aa6205062ab71f570212019fd1d9885b6 | Base linéaire de cette nouvelle branche |
| main | 917e3351f9b525857b6aa019e98d07872d8f6e76 | Documentaire ; aucune modification/fusion |

Les liens sont vérifiés par `git merge-base --is-ancestor`, pas par les noms.
Les références externes sont consultées en lecture seule, sans vendoring ni nouvelle
bibliothèque. Rapports UNIFIED VERIFIED_STATUS/CAMPAIGN_REPORT et CORE-03 relus.
Les rapports finaux des autres modules décrivent des validations hétérogènes ; leurs
résultats ne sont ni rejoués ni comptés parmi les tests LLM. L'absence de résultat CI
visible n'est pas un succès. LLM-03 a deux workflows historiques : la garde globale
003F/F3A n'accepte déjà plus les ajouts Manifest LLM-01 autorisés. Ses assertions
restent intactes ; le workflow FUSION utilise le gel exact de la base LLM-03 et les
mêmes validations de compilation, natif, contrats de récupération et APK.

## Route effective et propriétaires

| Élément | Code existant | État réel |
|---|---|---|
| Domaine historique | Conversation.kt / LanguageModel | API suspend ; SYSTEM optionnel ; pas de streaming JNI nouveau |
| P2 structuré | generation/P2Contract.kt, P2Prompt.kt | UUID opaques, SYSTEM/historique/documents/question séparés, validation fermée |
| Planification exacte | prompt/ContextPlanner.kt | TemplateBoundTokenizer épingle modèle/template ; port natif exact indisponible |
| Moteur réel | engine/LlamaBackend.kt | Un worker sérialisé par instance, NativeBridge existant ; aucun nouvel appelant ajouté |
| JNI | app/src/main/cpp/bridge.cpp | Engine contient llama_model/context ; handles pointeurs ; unload libère contexte puis modèle |
| Tokenizer | llama_chat_apply_template / llama_tokenize | Fonctions natives existantes, notamment chemins de fixture ; **pas** de port prepare/consume/discard qualifié |
| Stop | NativeBridge.cancel / Cancellation | Drapeau atomique existant ; retour coroutine ou timeout ≠ preuve native |
| Baux | ProcessResources / ResourceCoordinator | Un singleton coopératif par processus LLM ; ni arbitrage global ni réservation inter-APK |
| Import catalogue | VerifiedModelCatalog / ModelImportCoordinator | Import SAF volontaire vers copie privée, SHA/taille vérifiés ; sélection persistée, sans charger le worker |
| Parcours autonome | ModelStore / ChatViewModel | Parcours historique distinct, conservé ; sélectionner dans le nouveau catalogue ne remplace pas sa résidence |
| Réponses rapides | catalogue SQLite historique | Aucun schéma, contenu ou comportement modifié |
| Diagnostics | DeviceMonitor / mesures PSS/RSS | Conservation intégrale ; buffers/PSS/RSS non additionnés |

La coexistence du store historique et du catalogue LLM-01 n'est pas résolue par une
migration cachée. L1 lit le catalogue **déjà existant** et annonce SELECTED_NOT_RUNNING.
Le modèle n'est pas re-hashé par cette lecture passive : intégrité à l'import ≠
intégrité actuelle. Le raccordement du chemin catalogue au worker reste un lot distinct.

## Gel matériel et technique

SafetyPolicy inchangée octet pour octet : budget SMALL 2 Gio, MAIN 4 Gio ; réserve
max(1 Gio, RAM totale/5, seuil low-memory Android +256 Mio), crédit PSS borné existant.
Refus thermique ≥3, headroom ≥1, batterie <10 %, capteurs inconnus, arrière-plan,
pression mémoire et ABI non supportée conservés. ÉCO renforcé 768/96/1 thread,
TABLETTE ÉCO 512/64/1, MAIN 2048/256/1..4 : inchangés. AUTO reste disponible.
PLEINE PUISSANCE reste refusée sur Android, indépendamment de la RAM déclarée.

JDK21, Gradle8.11.1, Kotlin2.1.21, compile/target SDK36, minSdk29, ARM64,
NDK28.1.13356709 et CMake3.22.1 conservés. llama.cpp reste épinglé à
`1b70350c92563449576bcbbf552994b1d1ed847d`. Aucun changement de dépendance,
permission, signature, licence, ressource artistique, poids, modèle ou moteur.
APK sans permission Internet. Pas de promesse API24 pour le module Android LLM.
