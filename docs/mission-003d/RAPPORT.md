# Mission 003D — P2 natif et contrat local pour CORE

Branche : `mission-003d-p2-core-contract`. Base : `43503f59f530f2bb48955bce9d7d71e2470fb668` (003C). Aucun changement de CORE, UNIFIED ou main. Aucun transport, installation ou déploiement.

## Périmètre livré

Les contrats typés versionnés, le planificateur, le mapping conservateur CORE 004B et le modèle de cycle de vie de la première livraison 003D sont repris. Leurs documents historiques, désormais dépassés sur la disponibilité de P2 natif, sont conservés dans `reference-contract-only/`.

`GenerationRequest.systemPrompt` est facultatif. Sa présence sélectionne `NativeBridge.generateP2` dans le moteur llama.cpp existant. Le système personnalisé reste un message SYSTEM, ajouté sous les règles fixes natives. Son absence sélectionne exactement l'ancien JNI `generate` et conserve sa politique historique de réduction d'historique.

P2 refuse les délimiteurs, UTF-8 malformé, contrôles, tailles incompatibles, rôles incorrects et alternances incorrectes. Il compte le système fixe, le système configurable, l'historique, la question, le template officiel GGUF et le préremplissage de raisonnement vide. Admission : tokens d'entrée + sortie demandée + 8 <= contexte réellement chargé. Aucun retrait silencieux de tour P2. Le budget n'est pas une estimation en caractères.

Le backend refuse une deuxième génération concurrente avant sa mise en file. Les opérations natives restent sur le worker unique. Une demande d'arrêt n'est pas un déchargement. Un échec de nettoyage après chargement conserve le bail MODEL et interdit réarmement/chargement/génération jusqu'à confirmation du déchargement.

## Préservation

`tools/check-preservation-003d.py` compare au commit 003C tous les fichiers applicatifs, domaine, scripts de validation et configurations. Les seules exceptions explicites sont Conversation.kt, LlamaBackend.kt, bridge.cpp, CMakeLists.txt et test-native.sh, ainsi que les nouveaux tests JNI et contrats. Tous les tests de référence restent inchangés.

SafetyPolicy, ResourceCoordinator, ProcessResources, native_configuration.h, cancellation.h, model_check.h, vérification GGUF/SHA, pipeline d'importation, catalogue SQLite, modes et surveillance Android restent identiques. Aucun budget RAM, contexte de profil, nombre de threads, seuil batterie/thermique ou réserve système modifié. PLEINE PUISSANCE reste refusée sur Android non qualifié.

## Niveaux de preuve

- Tests domaine : contrats, mapping, défauts historiques, validation, états, refus de concurrence, conservation des baux, annulation répétée, résultat tardif et nettoyage en échec.
- Tests Android simulés : catalogue, importation, hors ligne et fonctionnement existant ; ce ne sont pas des tests physiques.
- CTest : protections et interruption du graphe CPU réel existantes.
- Nouveau harnais Java : charge la bibliothèque JNI issue du **même bridge.cpp**, avec un GGUF Qwen3 0.6B Q4_K_M public vérifié par SHA-256. Vérifie format SYSTEM/USER effectif, règles fixes, tokenisation, génération historique/P2, refus de dépassement, annulation, déchargement et échec de chargement. Inspection du prompt disponible uniquement dans la compilation hôte, absente d'Android.
- ARM64, Lint et contrôle APK : compilation et inspection statique de l'application, sans installation.

Les résultats exacts de cette branche seront consignés dans `VALIDATION.md` après exécution. Les anciennes exécutions ne sont pas des preuves de ce nouveau chemin JNI.

## Limites et prochaine étape

Le modèle `P2Lifecycle` décrit un protocole de preuves ; ses callbacks ne sont pas automatiquement alimentés par le backend Android. `UnavailableNativeP2Port` reste le refus par défaut pour le futur port CORE structuré. Le nouveau chemin bas niveau accepte le système via LanguageModel, mais il n'existe pas encore de session CORE authentifiée, de gestionnaire de baux interprocessus ou d'adaptateur qui raccorde les preuves JNI au protocole complet. Le mapping texte seul refuse le contexte aplati et les sources CORE non représentables fidèlement.

Le système transmis ne confère aucune permission ; les protections contre les délimiteurs ne prouvent pas une résistance sémantique universelle aux injections. Aucun appel d'outil n'est introduit.

Le test JNI Linux ne qualifie ni la thermique ni la mémoire ni les délais d'arrêt des POCO/Redmi. Leur qualification physique reste en attente utilisateur. Aucune parité Android P2 physique annoncée. Prochaine mission recommandée : validation du raccordement local des preuves d'état et de l'adaptateur CORE, puis qualification physique courte autorisée ; toute évolution de contrat partagé ou IPC exige une validation distincte.
