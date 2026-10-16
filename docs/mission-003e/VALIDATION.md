# Validation et limites 003E

Les résultats définitifs, le commit de code, l'empreinte de contrat et le lien CI sont ajoutés à la clôture. Les preuves 003D ne remplacent pas une exécution 003E.

## Vérifications reproductibles

```sh
python3 tools/check-preservation-003e.py
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint --no-daemon
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
./tools/cloud-env.sh python3 tools/check-fixtures.py
python3 tools/publish-validation-003e.py
```

Le job model-probe reconstruit le JNI hôte et exécute le harnais Java sur le GGUF Qwen3 0.6B Q4_K_M public dont le SHA est vérifié. Les nouveaux cas comparent le rendu de mesure au rendu réellement partagé, l'unicité SYSTEM et le budget 512 demandé sur contexte768. Aucune simulation ne remplace ce harnais ; son statut est rapporté séparément. Un test de graphe CPU natif existant vérifie aussi l'abort callback.

Niveaux de preuve : domaine JVM ; backend/worker avec API native simulée ; endpoint et identité Android sous Robolectric API35 ; vrai JNI/GGUF Linux ; compilation ARM64 et inspection statique des APK ; Binder inter-APK non exécuté ; Android physique non exécuté.

## Restrictions maintenues

Aucune modification de SafetyPolicy, ResourceCoordinator, ProcessResources, DeviceMonitor, ModelStore, native_configuration, cancellation, model_check ou catalogue ; aucun service ajouté au manifeste historique. Les sources partagées générées sont comparées aux originales. Les tests 003D restent byte-identiques ; les nouveaux tests sont ajoutés séparément. Aucun seuil RAM/thermique/batterie abaissé, aucune activation PLEINE PUISSANCE, aucun benchmark sur appareil.

L'absence d'arbitre réel entraîne ARBITER_ABSENT avant import/chargement. Les faux brokers et moteurs ne sont présents que dans les tests. Une reprise après UNKNOWN/CLEANUP_FAILED n'est pas autorisée par timeout, disparition du client ou relance de l'Activity. Journal privé technique sans prompt, réponse, URI ou profil personnel ; les UUID ne sont pas des permissions.

## Blocages explicites

- Pas d'émulateur installé dans cet environnement : pas d'installation, de signature permission effectivement exercée par le noyau Android ou de Binder entre APK testé.
- CORE004C et son broker réel sont hors périmètre de ce dépôt : pas d'admission interprocessus réelle, pas d'E2E CORE→LLM.
- Pas d'accès physique POCO/Redmi : aucune mesure ni qualification Android physique.
- Clé de fixture commune durable pour une paire future à provisionner par canal privé autorisé ; les clés CI sont éphémères.
- Corpus synthétique volontairement fermé ; pas d'historique, RAG, mémoire ou documents. Le service ne transforme pas sa liaison en foreground ; absence d'Activity réellement reprise entraîne un refus.
- Un redémarrage avec journal non résolu reste UNKNOWN : la réconciliation multi-processus et la preuve de mort sont à réaliser côté CORE selon le contrat, pas un reset serveur.

Aucun APK installé, aucune migration/désinstallation, aucun autre dépôt modifié, aucune fusion. Prochaine action : revue de cette livraison puis mission CORE004C sur autorisation distincte, avant tout essai à deux processus ou sur appareil.
