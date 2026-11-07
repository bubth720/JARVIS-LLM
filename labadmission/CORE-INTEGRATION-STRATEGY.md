# Intégration CORE réelle — documentation uniquement

Aucune modification CORE/INTERFACE, aucun mandat simulé et aucune connexion VOICE/NEXUS/SERVICES. La réussite de LAB_CLOUD ne vaudra jamais réussite M50 ou TTFT CORE→INTERFACE.

1. Épingler les commits CORE/INTERFACE/LLM et leurs contrats avant de proposer trois branches séparées. Relire SessionCoordinator, SessionActions.chat, le client de génération et les politiques de provenance ; ne pas déduire une compatibilité d'un nom identique.
2. CORE doit émettre l'admission réelle depuis son SessionCoordinator : session/request/epoch/génération, mandat révocable, propriétaire et cible modèle, hash de demande logique et limites. LLM ne doit pas fabriquer ces champs pour satisfaire StreamGrant.
3. Le broker LLM doit attester les vrais MODEL/HEAVY de son processus ; établir l'autorité de CORE sur l'annulation sans lui transférer ou dupliquer la fermeture de ces baux. Un mandat CORE et un bail natif sont deux preuves différentes.
4. Spécifier le mapping exact de la demande logique vers le rendu canonique/template/vocabulaire/configuration et l'autorisation de chargement suivie du reçu de résidence. Lier cette préparation à une consommation unique. Aucun ticket LAB, scope ou clé opérateur ne devient un mandat CORE par conversion.
5. Versionner l'adaptateur d'événements natifs autorisés ACK/TOKEN/COMPLETED/CANCELLED/ERROR vers le client INTERFACE, avec provenance réelle distincte des fixtures M50-S1B. Faire valider côté CORE et INTERFACE les champs et l'ordre, les événements périmés/doublons, le changement de session, UNKNOWN/CLEANUP_FAILED et la conservation du texte partiel.
6. Qualifier une seule composition in-process sur APK testOnly distincte, un seul moteur/worker/propriétaire. Maintenir l'ancien client complet et le laboratoire synthétique comme régressions séparées. Pas de transport réseau ou inter-APK implicite.
7. Mesurer réellement Submit validé, admission, préparation/prefill, premier token natif, premier UTF-8 utile, callback et première frame Android avec horloges monotones corrélées. Vérifier API29/35 sur hôte ARM64 compatible, puis appareil physique uniquement après accord distinct.

Décisions futures : périmètre des branches CORE/INTERFACE, schéma de mandat et mapping exact, provenance des événements, autorité de révocation et preuves de cleanup, hôte Android disponible. Aucun changement de sécurité ou permission de ces dépôts ne découle de LAB-ADMISSION-01.
