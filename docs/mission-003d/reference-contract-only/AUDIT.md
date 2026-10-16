# Audit 003D — sources et comportement préservé

Base locale et distante vérifiée : `mission-003c-device-qualification`, **43503f59f530f2bb48955bce9d7d71e2470fb668**. Nouvelle branche `mission-003d-core-generation-contract`. `main` observée à 917e3351f9b525857b6aa019e98d07872d8f6e76. Aucun autre dépôt modifié, aucune installation, aucun endpoint ajouté.

## Sources lues

- Conversation.kt, LlamaBackend.kt, ProcessResources.kt, bridge.cpp, SafetyPolicy, ResourceCoordinator, tests domaine et Android, outils de vérification et rapports 003B/003C.
- Les **six documents D4-02**, SEMANTICS et les **cinq schémas UNIFIED v1**, sur `design/d4-02-ownership-mapping` à `52a44fb6cf01623a8c5d96edc5d2bbac76abd9a1`. Lecture d'une archive épinglée, sans checkout modifié ni script tiers exécuté ; hashes et URL dans SOURCES.json. main UNIFIED ne contenait que README, c'est la branche D4-02 qui fait référence.
- La branche CORE004B est apparue pendant la mission : `mission-004b-inference-contract`, `07cd282603205d57f3d2ded1b14d5d95e68989e2`. InferenceContract/Controller, LocalLanguageModel, PromptBuilder et les rapports ont alors été lus sans modifier CORE. La matrice identifie les vraies divergences ; les 174 tests rapportés par CORE ne sont pas rejoués ici.

## Constat sur les méthodes existantes

| Méthode / composant | Comportement source actuel, conservé |
|---|---|
| Message / GenerationRequest | user/assistant uniquement ; dernier user ; ≤8 messages /16000 caractères ; bornes SMALL/MAIN, pas de SYSTEM configurable |
| prepareOperation | NativeBridge.begin sous verrou de handle, drapeau réarmé /deadline native90s ; doit précéder l'opération, pas être appelé concurremment pour annuler un stop actif |
| load | Worker unique ; déchargement précédent ; bail lourd puis résidence ; JNI inspecte GGUF, Qwen3/Q4_K_M/shape/slot/taille puis mmap et contexte ; cleanup dans les chemins d'erreur |
| generate | Même worker et bail lourd ; configuration chargée exacte ; seuls rôles user/assistant transmis ; allocationDiagnostic après retour |
| bridge::format | SYSTEM fixe, template officiel chargé du GGUF, préfill de raisonnement vide ; pas d'entrée P2 |
| bridge::generate | Neutralise `<\|` dans texte legacy, refuse rôles autres ; tokenize exact, réserve sortie+8 ; peut retirer des couples d'historique puis refuse si question seule trop longue |
| requestStop | Annulation native sous verrou, **pas** une preuve de fin ni une libération |
| unload | Attente sur le worker puis NativeBridge.unload ; libération du bail modèle seulement au retour ; pas une réservation/désallocation garantie de toute la RSS Android |
| close | Demande stop puis destruction sur worker, verrou du handle et baux libérés après retour ; l'erreur de destruction ne doit pas être convertie en succès par un futur adaptateur |
| ProcessResources | Singleton process-local, heavy/model, aucun contrôle des autres applications/CORE/VOICE |
| ViewModel / ModelStore | Admission et surveillance matérielle, hachage GGUF, import, gestion foreground/arrêt sont au-dessus du backend ; ne pas les contourner dans une future API |
| Catalogue | Même SQLite, schéma/révisions/tombstones/variantes, routage rapide sans modèle ; aucun nouveau catalogue ni routage CORE ici |

## Préservation démontrée et portée

`tools/check-preservation-003d.py` compare tous les **38 fichiers existants** de runtime/configuration/native aux sources 003C. Il n'autorise que **de nouveaux fichiers Kotlin dans `domain/.../generation/`**, refuse tout changement existant et vérifie l'absence de référence à ce package dans les parcours de production. Les anciens tests restent inchangés et sont rejoués.

Budget SMALL2Gio / MAIN4Gio, réserve max(1Gio,total/5,seuilAndroid+256Mio), seuils batterie/thermiques, PSS/annulation native, profils et refus PLEINE PUISSANCE sont inchangés. SafetyPolicy SHA-256 : `7fefc8ce65f80375666eefb8ea05d44ab5edee940d49c2d53183e675818f3999`. llama.cpp épinglé `1b70350c92563449576bcbbf552994b1d1ed847d` et vérifié par son script existant.

Les classes de contrat ajoutées peuvent être compilées dans le domaine, mais **aucune route ne les active**. L'APK de compilation reste 0.3.1-mission003b/code5, avec métadonnée de provenance du build ; aucune APK d'installation n'est distribuée pour 003D. Le modèle de lifecycle pur doit encore être relié aux événements du worker/baux existants dans une mission autorisée, pas empilé autour du backend.

Aucun gain de performance ou qualification physique n'est annoncé. La campagne 003C reste en attente utilisateur. La compilation/validation de l'APK et la comparaison GGUF Linux héritée ne testent pas le prompt P2 natif.
