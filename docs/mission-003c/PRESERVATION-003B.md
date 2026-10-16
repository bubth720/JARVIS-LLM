# Préservation 003B — Mission 003C

## Référence contrôlée

Dépôt autorisé : `https://github.com/bubth720/JARVIS-LLM.git`. Branche de départ locale et distante : `mission-003b-resources`, commit **1756e29557287550b5cfdffdd911cada2a302db6**, exactement celui demandé. Nouvelle branche `mission-003c-device-qualification`. `main` observée à `917e3351f9b525857b6aa019e98d07872d8f6e76`, sans fusion. Les historiques 003B restent dans [VALIDATION_003B.md](../VALIDATION_003B.md) et [sa preuve CI](../evidence/mission003b-e9d408e.json).

## Invariants vérifiables automatiquement

`python3 tools/check-preservation-003c.py` vérifie l'ascendance depuis le commit demandé et l'absence de différences dans 38 fichiers suivis : tous les `app/src/main`, `domain/src/main`, configuration Gradle/version, wrapper, source native épinglée et scripts de contrôle APK/tests natifs. Les fichiers Android/Kotlin/JNI, manifeste et catalogue livrés sont inchangés. Les empreintes par fichier sont enregistrées dans `mission003c-preservation.json` (artefact CI). L'outil refuse les fichiers de runtime non suivis et les modifications locales de ces chemins.

- SMALL 2 Gio / MAIN 4 Gio ; réserve `max(1 Gio, total/5, seuil Android +256 Mio)`, crédit résident borné existant.
- Batterie <10 % refusée ; capteurs obligatoires manquants refusés ; état thermique ≥3 refusé ; headroom exploitable ≥1 refusé ; pression mémoire/arrière-plan refusés.
- Réduction vers ÉCO lors d'économie énergie, batterie <20 %, état thermique ≥1 ou headroom ≥0,8 ; pas de contournement pour MAIN.
- Contextes/batch/sorties/threads existants, TABLETTE ÉCO conservé ; délai natif 90 s, drapeau atomique et callbacks d'arrêt natif conservés.
- Baux lourds/modèle internes au processus ; aucune libération par timeout ou demande d'arrêt seule ; libération modèle après déchargement/destruction effectif. Pas d'IPC ni de contrôle de CORE/VOICE.
- Import GGUF SHA-256, contrôles de taille/architecture/quantification Q4_K_M, mmap CPU et profils existants inchangés.
- Catalogue privé SQLite, schéma, migration, variantes, révisions, tombstones, import/export et routage avant LLM conservés.
- PLEINE PUISSANCE reste refusée ; pas de permissions réseau/données ajoutées ; aucun modèle ni donnée utilisateur publié.

Empreinte SHA-256 de `SafetyPolicy.kt` : `7fefc8ce65f80375666eefb8ea05d44ab5edee940d49c2d53183e675818f3999`. llama.cpp demeure `1b70350c92563449576bcbbf552994b1d1ed847d` ; le script existant refuse un autre checkout ou des modifications suivies.

## Différences intentionnelles

Ajout de guides, fiches **non remplies**, formulaire vide, vérificateur SHA-256 HTML hors ligne pour l'APK, tests complémentaires et preuves CI. Le test du HTML utilise Node/WebCrypto, pas un navigateur Android physique. L'environnement existant JDK21/SDK36/NDK28.1/CMake3.22.1 est réutilisé via `tools/cloud-env.sh`, sans changement nécessaire de configuration d'environnement.

Aucune nouvelle logique de génération, aucun nouveau capteur ni streaming. L'APK reconstruite reste 0.3.1-mission003b/code5 ; sa provenance `BuildConfig.SOURCE_COMMIT` évolue avec le commit source et sa signature debug peut changer selon le runner. Son SHA de fichier n'est donc pas celui de l'APK historique ; comparer à l'identité de **la bonne exécution**.

## Intégrité des modèles et des APK : niveau de preuve

La CI contrôle le fichier APK produit, sa signature, l'ABI/alignement/permissions et publie SHA du fichier + certificat SHA-256. L'utilisateur doit vérifier le fichier réellement reçu. La référence historique 003B était l'APK SHA `6c6e98605012fa1ec50f36db4ecb93501d4ec9d5e2cf10e79b42f4f146981566`, commit embarqué `e9d408e77319f4998821e723ef8bc0c872ee3274` ; elle n'est pas automatiquement la référence de l'APK 003C.

Le modèle 0.6B de la comparaison **cloud historique** utilisait SHA `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a` pour la conversion/revision indiquée dans sa preuve. **Le SHA des GGUF effectivement présents sur les appareils est inconnu ici**, notamment celui du 1.7B. Ne pas recopier un hash d'une autre conversion comme preuve. Le test synthétique `ModelIntegrityTest` vérifie refus d'altération, annulation du hachage et récupération sans changement du catalogue ; il ne charge aucun vrai GGUF. Le chargement à froid physique devra confirmer la vérification réelle.

## Matrice de couverture de la phase D

| Exigence | Preuve automatisée | Limite physique/intégration |
|---|---|---|
| Second modèle / gros travaux concurrents | ResourceCoordinatorTest : refus + concurrence huit threads | Participants du même processus seulement |
| Annulation sans libération anticipée | Baux conservés au contrat ; ResourceLifecycleTest attend la fin du faux unload | Latence et chemin JNI Android non mesurés |
| Récupération erreur de chargement | Test contrat d'échec simulé : résidence gardée jusqu'au nettoyage confirmé, puis retry ; vérification SHA réelle sur octets synthétiques | Pas une injection de panne dans le chargement JNI Android ; à vérifier sur appareil sans corrompre de modèle utilisateur |
| Annulation native | CTest : drapeau concurrent, callback chargement, deadline, graphe CPU abort/recovery | Pas un vrai Qwen Android ni une borne de latence |
| Repos / capture arrière-plan | Simulations existantes : aucun sondage périodique sans résident en arrière-plan, capture stoppée | Réveils et autonomie OEM non mesurés |
| Cohérence export | DiagnosticExportTest : unités séparées, nulls, horodatages, calcul marge, changement modèle/profil et éviction signalés | Données entièrement synthétiques, pas relevés physiques |
| Catalogue après échec | Échec transaction avec export et révisions inchangés, réouverture SQLite ; échec hachage sans écriture catalogue | Sauvegarde et vérification utilisateur locale avant/après essai |

Ces preuves complètent les tests des missions précédentes ; aucun comportement existant ni assertion n'est supprimé. Les simulations ne qualifient pas les téléphones.
