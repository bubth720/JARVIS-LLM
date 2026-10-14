# Validation Mission 002 — 13 octobre 2026

## Périmètre réalisé

Branche dédiée `mission-002-prototype`, application indépendante `fr.jarvis.llm`.
Diagnostic passif, import offline, conversation textuelle, adaptateur JNI CPU et protections de départ.
Aucune modification de CORE/VOICE, aucun modèle inclus, aucun test intensif sur appareil.
La phase 1 matérielle réelle et la phase 3 sur POCO/Redmi restent **non terminées** faute d'appareils accessibles.

## Résultats Mission 002 (historique)

| Vérification | Résultat |
|---|---|
| Tests JVM métier/sécurité/import | 22 exécutés, réussis |
| Test natif CTest | 1 exécuté, réussi : arrêt inter-thread, deadline, callback de chargement, abandon/reprise d'un graphe CPU réel, métadonnées modèles |
| Tests Android simulés Robolectric API 35 | 3 exécutés, réussis : démarrage sans activation JNI, refus sans modèle et accueil/réouverture passive |
| Compilation ARM64 / APK debug | Réussie sur les sources finales |
| Lint debug | Dernière exécution réussie : 0 erreur, 11 avertissements |
| Permissions, signature, ELF/ZIP 16 Kio | Inspection réussie sur l'APK final |
| Qualification POCO / Redmi | Non exécutée |
| Génération avec un vrai Qwen GGUF | Non exécutée : destination Hugging Face refusée par le proxy cloud |
| GPU / NPU, consommation, chauffe, p50/p95 | Non mesurés |
| CI GitHub actuelle | [Run 38465105554](https://github.com/bubth720/JARVIS-LLM/actions/runs/38465105554), commit `01afcdc98a7ea8f76259d6ef9bf3874038d9b275` : exécution complète réussie, job verify réussi, deux artefacts présents |
| CI GitHub historique | Runs 38462140007 et 38462348185 : échec setup-android v3, avant les tests |

Le test natif n'est pas un benchmark ni une preuve de délai d'arrêt d'un vrai Qwen sur Android.
SHA-256 de l'APK inspecté : `6e0c86a672a22b282dce825d35c088dcd15b4fbdc192801a16f4ae3a60e165f1`.
Robolectric ne valide ni le firmware, ni les pilotes, ni Android 16 sur appareil.
L'APK peut être construit sans poids ; une compilation seule ne prouve pas la qualité ou le fonctionnement de l'inférence.
Les avertissements Lint portent sur le périmètre ARM64, l'icône absente, une suggestion KTX et des textes UI non localisés.
Ils ne sont ni supprimés par configuration ni présentés comme des erreurs corrigées.

## Reproduire dans ce cloud

```sh
./tools/setup-cloud.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
```

SDK/NDK et JDK vérifiés, sources llama.cpp épinglées. Le setup a été réexécuté avec succès sur les outils installés.
Les caches sont conservés ; aucun processus applicatif serveur n'est nécessaire.

## Suite autorisée des phases 1–3

1. Renseigner les fiches matérielles et le diagnostic passif.
2. Valider la plausibilité des capteurs et les budgets sur appareil avant essais prolongés.
3. Premier essai court ÉCO/0.6B : chargement, réponse, arrêt manuel et passage en arrière-plan.
4. Mesurer PSS, arrêt natif, récupération et température ; abandonner le profil si inadmissible.
5. Comparer ensuite 1.7B sur le téléphone, avec entrée/sortie standardisées.
6. Mesurer démarrage froid/résident, premier token, tokens/s, p50/p95, pic PSS, état thermique et batterie.
7. Déclarer les configurations réellement qualifiées et conserver les replis.

Critères provisoires : arrêt <=2 s, premier token p95 <=10 s téléphone /20 s tablette, réponse courte p95 <=60 s.
Ces objectifs ne sont pas atteints par les tests cloud présentés. Ne pas forcer une configuration qui les manque ou dépasse les protections.
RAG, catalogue massif, voix, actions, IPC et PLEINE PUISSANCE sur station restent hors de cette mission limitée.

## Finalisation CI et préparation du diagnostic

Les deux échecs distants ont lieu dans `android-actions/setup-android@v3` ; les pages publiques confirment cette étape.
L'API GitHub et les logs détaillés sont inaccessibles depuis ce cloud : aucune cause interne plus précise n'est affirmée.
Cette action est remplacée par `tools/setup-cloud.sh`, épinglé, déjà testé et vérifiant les archives.
Runner Ubuntu 24.04 explicite, JDK/SDK/NDK isolés et variables ANDROID_HOME/ANDROID_SDK_ROOT cohérentes.
Chaque vérification est obligatoire ; publication du paquet seulement après tests, Lint, build et inspection APK réussis.
Le paquet contient APK, SHA256SUMS, commit et guide passif ; rapports de validation séparés.
Un test simulé supplémentaire vérifie accueil/réouverture sans fichier modèle ni travail natif.
Le test CPU livre désormais le signal d'arrêt depuis un autre thread après démarrage du graphe.

Pour ce premier diagnostic, suivre [FIRST_DIAGNOSTIC.md](FIRST_DIAGNOSTIC.md) : **ni import ni génération**.
Les capteurs réels, la compatibilité du firmware, l'inférence Qwen et le délai d'arrêt Android restent non vérifiés.
L'empreinte locale ci-dessus ne désigne pas l'APK CI : sa clé debug et son empreinte peuvent différer.
### Référence distante vérifiée

- [Exécution réussie](https://github.com/bubth720/JARVIS-LLM/actions/runs/38465105554) ; [job verify réussi](https://github.com/bubth720/JARVIS-LLM/actions/runs/38465105554/job/115529739945).
- [Paquet APK jarvis-llm-debug](https://github.com/bubth720/JARVIS-LLM/actions/runs/38465105554/artifacts/11847564585), ID `11847564585`, affiché à 4,25 MB compressés ; rétention configurée à 30 jours.
- [Rapports jarvis-llm-validation](https://github.com/bubth720/JARVIS-LLM/actions/runs/38465105554/artifacts/11847489631).
- Empreinte du **ZIP d'artefact GitHub**, pas de l'APK : `64a8d82960fcb7c30522ce883f80b6daf0996f476a6c37bab547c13574d0f8e3`.
- L'empreinte de **JARVIS-LLM-debug.apk** est publiée dans `SHA256SUMS`, au sein du paquet, et dans le résumé du job par le script de packaging.

Le workflow à cette révision impose bootstrap, test natif, tests JVM/Android simulés, Lint, compilation et inspection APK, sans continue-on-error.
La réussite globale et celle du job, ainsi que présence/noms/IDs des artefacts, ont été observées dans les pages publiques GitHub.
Les logs détaillés et le contenu du ZIP distant n'ont pas été téléchargés dans ce cloud faute d'accès web/API correspondant : les comptes de tests ci-dessus proviennent des rapports **locaux**, pas d'une lecture des XML distants.
La copie locale et son packaging SHA-256 ont aussi été contrôlés. Ne pas utiliser son empreinte pour vérifier l'APK GitHub, signé par une autre clé debug.

### Protections restant à qualifier

La politique thermique/batterie/mémoire et les seuils sont testés sur snapshots simulés ; les valeurs des capteurs OEM, le PSS réel et leurs réactions sur POCO/Redmi ne sont pas validés.
Le signal atomique et l'abandon/reprise de calcul CPU sont testés ; le cycle de vie JNI complet avec un Qwen chargé, les phases non immédiatement interruptibles et le délai d'arrêt sur Android restent à tester.
L'alignement ARM64/16 Kio et la signature sont inspectés ; installation, affichage Android 15/16 et compatibilité du firmware restent non vérifiés sur appareil.
Aucun benchmark intensif, import de modèle ni inférence sur appareil n'a été lancé.

## Mission 002B — 14 octobre 2026

Branche dédiée `mission-002b-memory`, version APK 0.2.0. Voir [MEMORY_002B.md](MEMORY_002B.md) pour la décomposition mémoire et les limites des gains.
Les 11,06 Gio accessibles /3,68 Gio disponibles sont des relevés utilisateur ; aucune mesure physique reproduite ici.
À ce niveau de RAM, le test de régression confirme le refus : au moins 4,212 Gio requis, manque 0,532 Gio.
Budget 0.6B conservé à 2 Gio ; réserve jamais abaissée. Crédit résident limité au PSS observé, plus au budget estimé.
Configuration 768/96, batch 32/8, un thread, repacking optionnel désactivé, Q4_K_M intacte.
Compatibilité vérifiée par lecture de l'alignement natif sur 256, test partagé des paramètres et build ARM64 ; inférence Qwen 768 non exécutée.
30 tests JVM et 3 tests Android simulés ont passé ; test natif des paramètres/annulation réussi. Vérification finale et CI de la nouvelle révision en cours.
Aucun gain PSS de Qwen ou sur appareil réel mesuré ; aucune baisse d'admission fondée sur des données synthétiques.
Diagnostics ajoutés : PSS actuel/pic échantillonné par phase, baseline, minimum RAM disponible, réserve/budget/crédit/marge et taille disque GGUF distincte.
Thermique, batterie, arrêt natif et absence de réseau maintenus. Aucun essai intensif autorisé ou lancé.
