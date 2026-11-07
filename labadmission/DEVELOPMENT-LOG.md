# Anomalies de préparation conservées

Aucun échec natif critique : ni modèle ni JNI n'ont été chargés durant cette mission.

- Première série ajoutée : 239 cas JVM, 1 échec de **fixture**. GenerationRequest rejetait déjà l'UTF-8 invalide avant l'entrée du bloc de refus attendu par le nouveau test. Le test a été corrigé pour inclure la construction de la requête ; aucun garde de production n'a changé. Log conservé dans evidence/request-fixture-setup-failure.log.
- Vérification des programmes volontairement non compilables : le compilateur refusait correctement une extension de scope scellé, mais le matcher attendait une autre formulation. Le séparateur des friend-paths a aussi été corrigé pour tester les types sans s'appuyer sur des erreurs de visibilité accessoires. Diagnostic initial conservé dans evidence/compiler-message-fixture-failure.log. Aucun garde métier n'est modifié pour obtenir ce PASS.
- Revue du diff avant empreinte finale : correction du traitement d'un terminal CANCELLED confirmé et de la conservation du premier cleanup échoué sans retry ; ancrage de confiance fermé à toute clé autochoisie et révocation signée ; snapshot de l'historique mutable avant vérification de signature pour empêcher une demande modifiée après admission. Ces corrections ne sont présentes que dans les postimages de revue.

Les contrôles sont relancés après ces changements explicites. Il n'existe aucune boucle de retry automatique ni effacement d'un échec d'inférence. Le SHA à valider est uniquement celui du manifeste final, pas une empreinte intermédiaire.
