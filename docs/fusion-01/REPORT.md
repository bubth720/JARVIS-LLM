# FUSION-01 — JARVIS LLM

**Statut : PARTIAL. L0/L1 logiciel livrable ; L3 natif et CORE004D BLOCKED.**
Module `bubth720/JARVIS-LLM`, branche `feat/fusion-01-llm-integration`.
Base `2efb3f7aa6205062ab71f570212019fd1d9885b6` ; code publié et testé au commit
`378a1cf4c2ceaaf531ded60670dc9161dc269575`. HEAD documentaire : commit contenant
ce rapport, identifiable par `git log -1`. Aucun autre dépôt modifié, aucun merge/force-push,
installation, modèle téléchargé, donnée personnelle ou activation sensible.

## Ce qui est livré

- [Audit L0 et filiation](AUDIT.md), références immuables dans [PROVENANCE.json](PROVENANCE.json).
- API locale `jarvis.llm.client/1` : GenerateRequest, StreamChunk, Cancel,
  NativeResourceState, TokenCountStatus, erreurs explicites ; requêtes corrélées et
  liées par digest, priorités, cibles explicites. [Contrat et mapping CORE](CONTRACT.md).
- Moniteur borné de flux, refus des replays contradictoires, événements périmés,
  sources incorrectes et sorties après stop. Fixture TEST-ONLY, jamais native.
- Lecture ponctuelle du catalogue GGUF existant et du broker partagé ; aucun nouveau
  stockage, moteur, worker ou bail. Sélection ≠ modèle chargé ; intégrité à l'import
  ≠ revalidation actuelle. Parcours autonome et catalogue de réponses préservés.
- [Revue sensible L2](NATIVE-REVIEW.md) référençant le diff F3B exact non appliqué ;
  [portabilité mini-PC L4](MINI-PC.md) sans backend Linux ni FULL_POWER activable.
- Workflow dédié, garde de 168 fichiers historiques et outil de collecte de résultats.
  Le workflow historique garde ses assertions et ses sondes modèle opt-in ; il ne
  s'applique pas à cette branche, dont le gel exact est celui de LLM-03, incluant les
  ajouts autorisés LLM-01/02/03. Les contrôles natifs/APK/récupération restent exécutés.

Le nouveau client est **indisponible par défaut et même après autorisation locale** :
il ne lance jamais de génération native. L'APK historique garde son moteur existant,
mais ce fait ne qualifie pas la nouvelle API ni une communication CORE–LLM.

## Vérifications réellement exécutées dans le cloud

Environnement Linux x86_64, JDK21.0.12.1+1, Gradle8.11.1, SDK36,
NDK28.1.13356709/CMake3.22.1 ; outils épinglés existants, TLS/checksums conservés.
Les skills cloud-environment-onboarding:setup et runtime ont guidé la vérification
de l'environnement existant ; aucune modification de configuration nécessaire.

| Vérification | Résultat |
|---|---|
| Baseline avant changement, `--rerun-tasks` | 254 tests : domaine100, Robolectric36, fixture118 ;0échec/erreur/saut |
| Résultat courant distinct | 285 tests : domaine127, Robolectric40, fixture118 ;0échec/erreur/saut |
| Nouveaux cas | 27domaine +4Robolectric (2scénarios × API29/35), soit31 |
| CTest Linux historique sans GGUF | 1/1 PASS ; bibliothèque JNI Linux compilée, pas de génération modèle |
| APK ARM64 app et fixtures | Compilation réussie, signature/permissions/ZIP+ELF16Kio contrôlés |
| Lint app/endpoint/client | 0erreur ;45/10/6warnings historiques, aucune suppression |
| Récupération /1,/2 et vecteurs | PASS ;22checks référence, aucune preuve JNI inférée |
| Vérificateur APK hors ligne Node | PASS ; pas un essai navigateur Android |
| Garde intensive + FUSION | 75+168fichiers contrôlés (périmètres recouvrants), aucun code historique changé |
| GitHub Actions | [Run40214822563](https://github.com/bubth720/JARVIS-LLM/actions/runs/40214822563) **SUCCESS**, code378a1cf ; toutes les étapes réussies, artefact publié |

Les totaux ne s'additionnent pas : les285incluent les254de référence. Les118tests
endpoint inchangés proviennent de leur exécution forcée de baseline de cette mission,
réutilisée ensuite UP-TO-DATE. Les suites nouvelles domaine/app sont réexécutées.
Après isolation finale du mock test-only, la suite domaine est rejouée. Les builds
utilisent les sorties incrémentales valides ; les tâches réellement exécutées sont
conservées dans les logs. Ces preuves restent logiciel/JVM/Robolectric.

Une première passe échoue sur deux variantes du nouveau test de bail : le test
omettait HEAVY avant MODEL, que le coordinateur refuse correctement. Correction du
fixture pour suivre HEAVY→MODEL→releaseHEAVY, sans modifier ni assouplir la politique.
Les deux variantes et toutes les régressions passent ensuite.

Commandes reproductibles :

```sh
python3 tools/check-fusion-01.py
bash tools/cloud-env.sh ./gradlew -I docs/mission-003f-f3b/candidate.init.gradle :domain:test :app:testDebugUnitTest :p2-endpoint:testDebugUnitTest --rerun-tasks --max-workers=2 --console=plain
bash tools/cloud-env.sh ./gradlew -I docs/mission-003f-f3b/candidate.init.gradle :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint --max-workers=2 --console=plain
bash tools/cloud-env.sh bash tools/test-native.sh
bash tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
bash tools/cloud-env.sh python3 tools/check-fixtures.py
python3 tools/check-recovery-contract.py
python3 tools/recovery-vectors.py
python3 docs/mission-003f-correction/reference/verify_vectors.py
node tools/test-apk-verifier.cjs
python3 tools/collect-fusion-01.py
```

L'artefact CI `fusion-01-llm-evidence` contient JUnit, Lint, log CTest et rapports APK.
Les résultats locaux bruts et empreintes se trouvent sous [evidence/](evidence/).
Les empreintes locales ne désignent pas des APK GitHub Actions ; aucune clé publiée.

## Compatibilité, limites et décisions

API cliente proposée locale : SHA-256
`f29f8a9f3e10645fe1f25ea01913ccc8cf2ee883cbc45f7adca18645ac322b0f`.
Contrat expérimental /2 inchangé :
`3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef`.
Pas d'amendement des contrats partagés UNIFIED, ni de dépendance d'un autre dépôt.

| Porte | État / blocage |
|---|---|
| L0 audit, L1 API passive | PASS logiciel ; non composé dans l'app utilisateur |
| F0 global UNIFIED | Non produit par ce dépôt ; aucune intégration globale autorisée depuis ce manifeste local |
| Comptage exact | UNSUPPORTED, pas d'estimation de caractères/tokens substituée |
| L2 revue | Diff et risques documentés ; validation humaine préalable à l'application |
| L3 worker/JNI, /2, CORE004D | BLOCKED ; pas de reçu de fin/résidence, pas de port exact qualifié |
| Android instrumenté / JNI avec modèle | NOT_RUN ; pas d'émulateur exécuté ni GGUF réel dans ces essais |
| POCO X8 Pro / Redmi Pad SE | NOT_RUN ; aucune qualification physique ni mesure inventée |
| Linux mini-PC | Port décrit, backend absent, matériel non qualifié |
| CORE API24 contre LLM min29/ARM64 | Incompatible in-process sur API24 ; aucun minSdk modifié |
| PR brouillon automatique | GraphQL GitHub Forbidden, CONNECT API403 ; texte et lien de comparaison préparés |

Risques conservés : handles pointeurs historiques sans registry F3B ; observation
ponctuelle non atomique pour l'admission ; fichier sélectionné non revalidé en lecture
passive ; idempotence du moniteur uniquement en RAM par requête, pas un journal
crash-safe ; aucun transport/authentification inter-APK ni arbitre global. L'état
UNKNOWN reste fermé ; EMPTY_UNATTESTED n'est jamais libéré. Pas de métrique de
latence/moyenne/p95, consommation ou gain RAM sans campagne reproductible.

Prochaine action : revoir les contrats et le diff L2, puis autoriser **séparément**
une mission native minimale avec tests réels et qualification Android/appareils.
Conserver le consommateur CORE en indisponibilité jusque-là. FUSION-01 global reste
PARTIAL, aucune mission CORE004D lancée et aucune fusion proposée pour exécution.

## Publication et PR

Branche distante : [feat/fusion-01-llm-integration](https://github.com/bubth720/JARVIS-LLM/tree/feat/fusion-01-llm-integration).
Code378a1cf publié par `git push -u origin feat/fusion-01-llm-integration`, sans force.
[Comparaison pour préparer la PR](https://github.com/bubth720/JARVIS-LLM/compare/feat%2Fllm-03-runtime...feat%2Ffusion-01-llm-integration),
[texte du brouillon](PR-DRAFT.md). Base de revue proposée : `feat/llm-03-runtime`,
pour isoler ce lot des missions précédentes non fusionnées dans main. L'API REST refuse CONNECT403 et `gh pr list` retourne
`Post https://api.github.com/graphql: Forbidden`. Aucun contournement ni PR créée
revendiquée ; création manuelle en **brouillon** possible, sans fusion.

Les mises à jour du seul rapport/des preuves sont exclues du déclenchement push CI,
pour conserver le reçu du vrai commit de code sans reconstruire pour un résultat
textuel. Toute modification de code, contrat, workflow ou vérificateur déclenche la
validation. Les PR de cette branche conservent le workflow FUSION.

## Clôture de validation distante

[Workflow FUSION-01 entièrement réussi](https://github.com/bubth720/JARVIS-LLM/actions/runs/40214822563),
code `378a1cf4c2ceaaf531ded60670dc9161dc269575`. Exécution du25octobre2026,
17:06:04→17:13:53UTC : gel, setup, CTest, JVM/Robolectric, compilation ARM64,
Lint, contrôles APK et collecte/upload **SUCCESS**. [Reçu observé](evidence/CI.json).
Le collecteur exige au minimum127/40/118tests sans échec/erreur/saut ; il réussit
sur le runner neuf. Les compteurs locaux détaillés sont dans LOCAL-RESULTS.json.
Les logs individuels/ZIP distant ne sont pas téléchargés indépendamment (API refusée) ;
le statut GitHub des étapes et la publication de l'artefact sont effectivement lus.
Cela n'atteste aucun JNI avec GGUF, transport CORE, émulateur ou appareil réel.

L'ancienne CI LLM-03 avait deux échecs : setup dans le workflow intensive et gel
historique dans Android, voir [HISTORICAL-CI.json](evidence/HISTORICAL-CI.json).
Ces anciennes exécutions ne sont pas réécrites ou présentées comme vertes. Le nouveau
setup réussit sans changement de script ; aucune cause précise de l'ancien échec
setup n'est inventée. La CI verte annoncée est celle de FUSION, à son SHA exact.

Le commit documentaire suivant publie les preuves/ce rapport seulement ; aucun
code testé n'est changé après378a1cf. La branche reste PARTIAL et non fusionnée.
