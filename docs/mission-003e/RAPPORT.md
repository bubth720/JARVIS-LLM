# Clôture — JARVIS-LLM / Mission 003E

Branche : **fixture/003e-p2-secure-endpoint**. Base : **daa46432e847ed9613dc403045498e27281eff42**. Code : **5751646acd4a7bb13caaea4b96a4ab3a68ec6adb**. Le premier commit 2ca1663 introduit la fixture ; 5751646 ferme aussi les exceptions de capteurs et maintient la quarantaine de nettoyage pour tout échec. Le dernier commit documentaire est indiqué dans la livraison et consultable sur la branche.

## Livré

Endpoint Android isolé, Binder/AIDL explicite, UID/package/certificat, permission signature, bornes de transactions et corps fermés, identifiants/version/digest, anti-replay, délais, refus de concurrence, requêtes exclusivement synthétiques. Aucun service dans l'APK personnelle historique. Le client de sonde n'est pas CORE et ne possède pas d'arbitre.

Raccordement réel aux mêmes sources Kotlin/C++ llama.cpp : SYSTEM brut rendu une fois par le formateur canonique existant, mesure et génération sous worker/bail réels, digests du modèle/template/rendu et plafond demandé appliqué dans le JNI. L'historique P2Planner/P2Lifecycle est préservé mais jamais empilé sur les acquisitions du backend.

États et reçus liés à la clé complète et au bail exact ; arrêt distinct de retour natif et de libération ; texte retardé jusqu'au nettoyage ; journal privé avant import/chargement, UNKNOWN bloquant au redémarrage incertain. Les exceptions de capteur demandent l'arrêt, sans inventer une libération. Destruction du service : drainage puis fermeture du worker.

Aucun chemin modèle envoyé par le client : une référence GGUF publique locale peut être préparée via SAF dans la sandbox de fixture, sans copie immédiate. La copie sécurisée et le SHA demandent eux aussi un mandat CORE réel et l'admission matérielle ; le bail de copie est libéré avant le chargement. Aucun modèle importé ou appareil utilisé pendant la mission.

## Préservation

SafetyPolicy, budgets2/4Gio, réserves, batterie/température, profils/threads/KV/batch, SMALL/MAIN, contrôle GGUF, importation vérifiée, catalogue SQLite et réponses rapides : inchangés. Les tests de référence003D restent identiques. Le formateur natif est byte-identique à003D ; seules les nouvelles entrées de fixture imposent une borne demandée supplémentaire. Le contrôleur observe les baux réels ; aucun second broker interne. PLEINE PUISSANCE reste refusée sur Android non qualifié.

Les seuls fichiers runtime historiques examinés/modifiés sont LlamaBackend.kt, bridge.cpp et le harnais JNI Java ; configurations Gradle ajoutent les modules isolés sans dépendance app→fixture. Aucun autre dépôt modifié, aucune fusion de branche divergente, aucune installation, migration, désinstallation ou activation personnelle.

## Validation

[Résultats complets et preuves](VALIDATION.md) : CI38975009901 entièrement verte, **87 domaine +27 app +19 fixture =133 JUnit**, aucun échec/erreur/ignoré, CTest1/1, vrai JNI/GGUF Linux, ARM64, Lint et contrôle des APK/signatures/permissions/alignements. Pas de déduction de qualification Android physique à partir de Linux, Robolectric ou compilation.

Contrat : **jarvis.fixture.p2/1**, SHA-256 **fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9**. [Guide CORE004C](CORE-004C.md), [identité/signatures](SIGNATURES.md), [architecture et convergence D4-02C](CONVERGENCE.md), [références immuables](SOURCES.json), [vecteur synthétique](examples/p2-request.json).

## Limites et décision suivante

L'endpoint est prêt à être consommé **selon ce contrat de fixture**, avec refus sûr sans arbitre vérifiable. Aucun broker global fictif n'est fourni : **ARBITER_ABSENT bloque avant import et chargement**. Le vrai arbitre et client CORE004C restent à réaliser dans une mission autorisée propre à CORE. Le coordinateur du backend ne contrôle ni les APK personnelles ni tout Android.

Émulateur absent : aucun échange Binder entre APK exécuté, aucune permission signature exercée par le noyau avec les sondes ; seuls compilations/inspections et tests simulés sont prouvés. Aucun téléphone accessible ou utilisé. Aucun E2E CORE→LLM opérationnel revendiqué.

Une clé de laboratoire commune durable doit être provisionnée en privé pour la paire future ; les clés CI éphémères ne sont pas réutilisables et ne sont pas publiées. Réconciliation après mort/UNKNOWN et preuve de mort restent du ressort du broker CORE, sans reset serveur. Le corpus pilote est fermé ; RAG, historique et mémoire ne sont pas acceptés. La visibilité réelle de l'Activity LLM et les capteurs sont toujours exigés.

Prochaine action recommandée, **après validation explicite** : mission CORE004C pour le véritable broker et le client, revue du digest et des signatures, puis protocole séparé d'essai isolé à deux processus. Ne fusionner, installer ou qualifier aucun appareil sur la seule base de cette livraison.
