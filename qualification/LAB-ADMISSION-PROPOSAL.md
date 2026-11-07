# Proposition distincte — admission légitime de laboratoire, NON APPLIQUÉE

Le blocage n'est pas une absence de fichier GGUF. L'approbation limitée du modèle n'atteste pas un mandat CORE, un bail broker ni le lien entre une préparation locale et la demande autorisée. Le backend approuvé exige actuellement `coreMandateSha256` dans StreamGrant ; l'autorité doit vérifier le modèle réellement résident et le vrai bail. `ResourceLease` encapsule une fermeture privée, et les IDs Long sont privés au ResourceCoordinator. `StreamPrepared.requestSha256` est une empreinte locale, distincte de la référence logique CORE. Le port productif reste nativeReady=false.

## Deux périmètres possibles à décider

**A. Intégration CORE réelle** : modifier les contrats/adaptateurs CORE, LLM et INTERFACE sur des branches séparées, avec périmètre explicitement validé. Mandat authentifié et révocable, source des IDs métier, attestation broker et mapping exact logique→rendu/tokenizer/modèle. Ce périmètre est interdit par la présente mission et ne doit pas être simulé.

**B. Laboratoire local autonome versionné** : approuver un profil d'autorité explicitement LAB_CLOUD, incompatible avec les ports CORE/INTERFACE productifs. Il doit être un droit de laboratoire réel et vérifiable, pas un faux CORE : liaison à l'accord opérateur, commit/run, modèle/révision/SHA/taille vérifiés, limites admises, échéance et révocation. La séparation de type doit empêcher de fabriquer `coreMandateSha256` pour satisfaire le contrat existant. Le grant actuel ne peut être utilisé tel quel en remplaçant ce champ par un hash de fixture.

## Changements de sources à préparer pour une revue ultérieure

1. `domain/.../resources/ResourceCoordinator.kt` : observation atomique en lecture d'un bail réellement émis et possédé, avec identité/révision/incarnation, état de fermeture et owner. Le snapshot doit être minté par le broker, non constructible depuis un UUID arbitraire. Il ne confère aucun droit de fermer/transférer le bail et préserve release idempotent/antiABA. Aucun arbitre global nouveau.
2. Nouveau reçu de modèle vérifié : SHA/taille mesurés sur la copie admise, fichier ouvert/identité stable et configuration effectivement chargée. L'état déclaré du catalogue ou le nom de modèle ne remplace pas cette preuve. Lier ce reçu à l'incarnation native/résidence et à la préparation exacte.
3. `OwnedStreamContract.kt` : scope d'autorité discriminé (CORE validé ou LAB_CLOUD validé), credentials non forgés, aucune conversion implicite. Le champ CORE existant ne change pas silencieusement de sens. Ce fichier est une postimage approuvée : **nouveau diff exact et nouvelle validation requis avant application**.
4. `LlamaBackend.kt` : liaison atomique du snapshot de broker et du reçu vérifié à la même préparation/résidence, consommation unique et révocation. Conserver le worker/moteur, les budgets et les contrôles de load ; annulation ≠ retourJNI ≠ drain ≠ unload. Ce changement est également hors autorisation actuelle.
5. Nouveau module/harness de qualification : admission explicite du profilLAB_CLOUD et limite de ressource vérifiée ; tests de refus absence/altération/expiration/révocation/ancien bail, concurrence, mismatch SHA/template/demande et cleanupfailed. Aucune route productive et aucune émission vers INTERFACE/VOICE/NEXUS/SERVICES.

Le [contrat illustratif](LabAdmission-proposal.kt.txt) exprime les frontières, pas une implémentation de l'autorité. Les identifiants et attestations ne doivent pas être générés dans un adaptateur pour faire passer une fixture. Aucun code de cette proposition n'est compilé ou appliqué par cette mission.

## Preuves demandées avant campagne réelle

Revue du diff exact, mapping accord→autorité, tests de non-forgeabilité du broker et des scopes, séparation du modèle sélectionné/résident/vérifié, tokenisation/préparation exacte et annulation au point d'admission. Puis essais bornés du protocole comparatif existant, mêmes octets/greedy/configuration, événements natifs authentiques, refus/erreurs conservés. Refaire les régressions et publier le nouveau commit/tree. L'architecture ne justifie aucune hausse de budget ni modification C++ JNI implicite.

Décision attendue après ce rapport : choisir A ou autoriser la préparation d'un **diff de revue B**, sans l'appliquer avant validation. Ce document n'accorde ni cette application, ni GATE-B, ni une installation personnelle. Le JNI747598dd… et les contrats historiques sont inchangés aujourd'hui.
