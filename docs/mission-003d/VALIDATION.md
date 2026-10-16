# Validation 003D — P2 natif

Module : JARVIS-LLM. Branche : `mission-003d-p2-core-contract`. Base : `43503f59f530f2bb48955bce9d7d71e2470fb668`. Code vérifié : **`0f13573eafaf461714ba9dc00bbb208b1e723fb8`**. Le commit de clôture ne change que la documentation et les preuves ; aucun rebuild ou changement de source applicative après ce résultat.

[GitHub Actions entièrement réussie — 38963365435](https://github.com/bubth720/JARVIS-LLM/actions/runs/38963365435).

| Contrôle | 003C de référence | Cette branche |
|---|---|---|
| Domaine JVM | 56 tests | 87 réussis, 0 échec/erreur/ignoré |
| Android simulé | 21 tests | 21 réussis |
| Backend réel avec API native simulée | Non ajouté en 003C | 4 réussis ; total app JUnit : 25, aucun ignoré |
| CTest natif | 1 test | 1 réussi, callbacks d'arrêt et protections existants |
| Vrai JNI et GGUF | Pas de preuve P2 | Nouveau harnais hôte réussi, même bridge.cpp |
| ARM64 | Référence compilable | Compilation APK réussie |
| Lint | Référence existante | 0 erreur ; 36 avertissements CI, 32 localement |
| APK | Référence vérifiable | Signature v2, permissions, ABI ARM64 et alignement ELF/ZIP 16 Kio vérifiés |
| Politiques/catalogue/tests historiques | À préserver | Contrôle de préservation réussi, aucune modification de seuil |
| Appareils physiques | Non qualifiés | Aucune nouvelle mesure physique |

Le harnais JNI Java charge le même bridge.cpp et vérifie le rôle SYSTEM réellement rendu par le template, sa position avant USER, les règles fixes, le préremplissage Qwen3 et l'augmentation du nombre de tokens avec le système. Il exerce les générations historique/P2, les refus de rôle/délimiteur/taille/contexte/UTF-8, l'arrêt répété et depuis un autre fil, le déchargement répété, puis un échec de chargement avec absence de contexte résident. Le point exact d'interruption dans le graphe JNI n'est pas instrumenté ; le CTest existant vérifie indépendamment l'interruption d'un vrai graphe CPU. Aucun arrêt Android chronométré n'est revendiqué.

Les 4 nouveaux tests backend utilisent le vrai worker et ProcessResources, avec injection déterministe d'une API native simulée : transmission SYSTEM et concurrence, conservation des baux pendant l'arrêt, nettoyage de chargement échoué, échecs de déchargement/fermeture et récupération. Ce sont des tests JVM, pas des appels JNI.

Catalogue rejoué dans la CI : 1 508 entrées dont 1 500 synthétiques ; SQLite sous Robolectric API35 ; `llama_loaded=false`. Pas de comparaison de vitesse ou de gain RAM avec 003C. Aucun GGUF, profil privé ou donnée personnelle publié.

## Preuves téléchargeables

- [Validation et rapports JUnit/Lint/identité APK](https://github.com/bubth720/JARVIS-LLM/actions/runs/38963365435/artifacts/12073444020), empreinte ZIP annoncée par GitHub : `b42a2233ae3d12dd45da1a2e4573cfc06ffdd0a1b821801caf1512f13119375d`.
- [Preuves modèles Linux, dont p2-jni.json](https://github.com/bubth720/JARVIS-LLM/actions/runs/38963365435/artifacts/12073891727), empreinte ZIP annoncée par GitHub : `54290e34524438712503651d55cbed82b649ea2fdd93f12a8c87d84c0239fff6`.
- Observations publiques et résultats locaux : [JSON de preuve](../evidence/mission003d-p2-0f13573.json). Les ZIP n'ont pas été téléchargés par Codex ; les conclusions d'étapes et annotations JSON publiques ont été examinées.

GGUF Qwen3 0.6B Q4_K_M du test : `unsloth/Qwen3-0.6B-GGUF`, révision `50968a4468ef4233ed78cd7c3de230dd1d61a56b`, fichier `Qwen3-0.6B-Q4_K_M.gguf`, SHA-256 `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a`. Repli de provenance public déjà prévu par le script lorsque le fichier recherché n'est pas publié dans le dépôt Qwen interrogé. Pas de changement de modèle admis dans l'application. Harnais : contexte 512, CPU 1 thread, sorties courtes 8 tokens, une génération limitée à 64 tokens interrompue ; pas de test de stress.

APK CI compilée et contrôlée, **non distribuée et non installée** : version historique conservée `0.3.1-mission003b`, code 5, commit DEX `0f13573…`. SHA-256 : `c0930f55b186eb5fe809d0d673e12c5ede84fd77ee19a5e68b5a07a17c957751`. Certificat debug éphémère : `abd929f0fff9b88a453d0bc5369b4dfe46f2f258daac0c65cfb40b1c45468c28`. Le binaire local utilise une autre clé debug et possède une autre empreinte, consignée séparément. La version affichée ne suffit pas à identifier cette compilation : utiliser le commit et le SHA. Les symboles ARM64 locaux confirment generateP2 présent et renderForTest absent.

SHA-256 SafetyPolicy inchangé : `7fefc8ce65f80375666eefb8ea05d44ab5edee940d49c2d53183e675818f3999`.

## Reproduction cloud

```sh
python3 tools/check-preservation-003d.py
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
./tools/cloud-env.sh python3 tools/apk-identity-003c.py app/build/outputs/apk/debug/app-debug.apk
```

L'identité APK exige une compilation depuis un checkout propre du commit annoncé. Le job model-probe compile aussi model_memory_probe puis exécute compare-model-memory.py avec le JDK vérifié : le SHA de chaque GGUF est vérifié avant le harnais JNI et les fichiers modèles sont supprimés après usage. Pas de dépendance Internet pour l'application Android en fonctionnement.

## Clôture et limites

Les exécutions intermédiaires 38962939901 et 38963197342 ont été annulées automatiquement lors des mises à jour de la même branche ; elles ne sont pas utilisées comme validations finales. Le run 38963365435 est intégralement réussi sur le dernier commit applicatif.

Qualification physique : **EN ATTENTE UTILISATEUR** pour Redmi Pad SE et POCO X8 Pro. Aucun gain RAM/autonomie/thermique/latence mobile revendiqué. Budgets, réserves, modèle unique et modes préservés ; PLEINE PUISSANCE non activée.

Le port de session CORE structuré reste fermé par défaut ; le système configurable est disponible via LanguageModel/JNI. Raccorder les callbacks réels au protocole d'états et valider l'adaptateur de session constitue la prochaine étape recommandée, après validation explicite. Toute modification de contrat partagé, qualification physique, IPC ou installation exige son autorisation propre. Aucun autre dépôt modifié, aucune fusion dans main.
