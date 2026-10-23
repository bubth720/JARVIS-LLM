# LLM-03 — Tranche indépendante passive, worker natif BLOQUÉ

Branche `feat/llm-03-runtime`, base de mission LLM-02 `895a067ffc860d56a2dbb1ce22cb274096f8e3a2`.
Base native préservée `8f1c2f5a936d6c01874d7be6bb987f4459b83374`.
Dépendance UNI-01 livrée `d947dadf73f8dec91543a3f86a1aa2ffef0b0d2f`, utilisée comme preuve de tooling,
**sans importer automatiquement des types dans les applications**. LLM-02 demeure PARTIAL pour sa portion native.
Cette tranche logicielle indépendante ne constitue pas un déblocage du DAG ou du protocole.

## Fonction réellement codée

`RuntimePolicyProfiles` fournit les noms TABLET_ECO/MOBILE_ECO/EQUILIBRE/DEDICATED_FULL_POWER en déléguant
exactement à **SafetyPolicy existante**. Aucun budget, crédit PSS, seuil thermique/batterie, admission ou nombre
de workers changé. Pleine puissance reste refusée par la politique historique Android. CENTRAL reste décrit
UNQUALIFIED_NOT_ACTIVATABLE, sans backend PC sélectionnable.

`HardwareCapabilityDetector` expose les données déjà déclarées dans DeviceSnapshot et **ne qualifie aucun appareil**.
`RuntimeResourceProjection` classe passivement le snapshot du ResourceCoordinator existant ; aucun registre de
handles, arbitre, lease ou autorité concurrente n'est créé. `ProcessRuntimeObservationPort` est effectivement
relié au singleton ProcessResources, en lecture seule, sans initialiser LlamaBackend ou JNI.
L'observation n'expose ni propriétaire, opération textuelle, chemin du modèle ni contenu privé.

Un broker vide est **EMPTY_UNATTESTED**, jamais la preuve d'une mémoire vide. Les preuves nativeTermination
et nativeUnload restent UNATTESTED dans tous les cas. Le port n'offre ni acquire, stop, reset, release,
generation ni confirmation ; il ne peut débloquer une quarantaine ou autoriser une nouvelle résidence.

## Non livré / blocages

NativeHandleRegistry réel, reçus F3 natifs, récupération attestée, port PC exécutable et branchement d'un worker
modifié ne sont pas livrés. Tokenizer exact LLM-02 UNSUPPORTED, patch F3B non appliqué, revue native séparée
nécessaire. Aucun changement JNI, backend, admission, contrat /2, APK signé historiquement, protections ou voix.
CORE004D reste bloquée. Aucun essai GGUF Android ou appareil, aucune installation.

## Validation

Quatre tests domaine vérifient équivalence stricte à la politique historique (profils et refus), pleine puissance,
limites tablette et projection des leases du broker de test ; un test app lit réellement le broker partagé,
sans charger de moteur. Ce sont des preuves logicielles, jamais de libération JNI réelle.
Résultats, commandes et statut **PARTIAL** : `RECEIPT-LLM-03.json`.

Prochaine action sensible : revue du diff F3B et du prepare/consume/discard, instrumentation/native Android
après autorisation distincte. Aucune fonction réelle bloquée n'est remplacée par une confirmation simulée.
