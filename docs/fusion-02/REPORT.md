# FUSION-02 — LLM : banc autonome et préqualification

**PARTIAL — GO logiciel L0/L1 ; JNI, tokenizer exact, CORE004D et matériel BLOCKED.**
Dépôt `bubth720/JARVIS-LLM`, branche `feat/fusion-02-llm-prequalification`.
Base explicite `e0b781442b21034a251f6af450396faabc5f667d` (FUSION-01).
Code publié : `34491eee148cdd54db09a09175b3ca1742d8e24c`. Le HEAD documentaire
est le commit contenant ce rapport, vérifiable par `git log`. Aucun autre dépôt modifié, merge, force-push, installation,
migration, modèle téléchargé ou activation sensible.

Missions lues intégralement : [LLM #4](https://github.com/bubth720/JARVIS-LLM/issues/4)
et [UNIFIED #5](https://github.com/bubth720/JARVIS-UNIFIED/issues/5).
[Provenance et références exactes](PROVENANCE.json) : sources de conception en lecture
seule, pas un BOM global ni une composition des autres modules.

## Fonctions et corrections livrées

1. **L0 préservation vérifiée.** Ancestry F3B→LLM-02→LLM-03→FUSION-01 vérifiée,
   baseline 285 cas rejouée avant tout ajout de test. Gel de 296 fichiers de la base,
   plus un changement CI précisément autorisé par le vérificateur. Tous les fichiers
   de production, SafetyPolicy, catalogue SQLite, modèles/imports, clés/configurations,
   contrats et285 anciens tests restent identiques octet pour octet.
2. **L1 banc TEST-ONLY étendu.**18 nouveaux tests JUnit : doubles ACK, identités
   divergentes, timeout non renouvelable, annulations concurrentes, chunks dupliqués,
   réponse après arrêt, refus après révocation, modèle/permission de fixture absents,
   état UNKNOWN conservé et interdiction de nouvelle admission. Un test parcourt4096
   traces bornées, couvrant les six états. [Machine d'états et limites](STATE-MACHINE.md).
3. **Fiabilité des preuves renforcée.** L'ancien collecteur contrôlait des minimums
   sans vérifier fraîcheur ni identité de chaque cas. Le nouvel outil exige un marqueur
   préalable, des sources inchangées, des XML postérieurs, l'accord résumés/cas réels,
   zéro doublon/échec/saut et la présence des 285 identités historiques exactes.11 tests
   Python négatifs couvrent ces refus. Les horodatages ne sont pas des signatures ;
   le runner reste de confiance. Aucune réécriture des anciennes preuves/outils.
4. **L2 revue native exploitable.** Sept préimages contrôlées et `git apply --check`
   sur le patch exact, sans application. [ABI, imports, propriété, UAF, baux et rollback](NATIVE-REVIEW.md).
5. **L3 qualification future préparée.** [Guide Android29+/Linux](QUALIFICATION-PROTOCOL.md),
   budgets conservés, premier/dernier token réellement observables, PSS/RSS distincts,
   mesures indisponibles null, arrêt/drain/unload exacts, campagne courte seulement
   après accord. [Formulaire vide sans données personnelles](MEASUREMENT-TEMPLATE.json).
6. **L4 consommateur CORE.** [Ports/erreurs, corrélation et transport à revoir](CORE-CONSUMER.md),
   distinction API24/API29, risques de concurrence VOICE. Aucun pont ni arbitre ajouté.

Aucune erreur applicative du client n'a été observée dans les nouveaux cas ; ses
sources sont conservées. La correction porte sur la **qualification des preuves**,
pas sur les seuils de sécurité. La fixture n'est jamais présentée comme un modèle.
Le client de production reste indisponible et les outils CORE/NEXUS/SERVICES ne
reçoivent aucune dépendance artificielle envers cette lane parallèle.

## Résultats réellement exécutés

Linux x86_64, outillage épinglé existant : JDK21.0.12.1+1, Gradle8.11.1,
Kotlin2.1.21, SDK36, NDK28.1.13356709, CMake3.22.1. Android min29/ARM64 et
permissions/signatures inchangés. Environnement existant vérifié avec les skills
cloud-environment-onboarding:setup et cloud-environment-runtime ; aucune modification
de configuration nécessaire, aucun service permanent. TLS/checksums conservés.

| Suite/contrôle | Résultat local |
|---|---|
| Baseline forcée avant ajouts | domaine127 + app40 + endpoint118 =285, zéro échec/erreur/saut |
| Passe finale forcée | domaine145 + app40 + endpoint118 =303, zéro échec/erreur/saut |
| Identités historiques |285/285 conservées dans les XML courants |
| Nouveaux JUnit |18, dont un test explorant4096 traces, pas4096 tests distincts |
| Validateur de preuves Python |11/11, fixtures XML synthétiques uniquement |
| CTest natif historique Linux sans GGUF |1/1 ; compilation bibliothèque JNI historique réussie |
| Compilation/Lint |272 tâches exécutées, BUILD SUCCESSFUL ; app/endpoint/client ARM64 |
| Lint |0 erreur ;45/10/6 avertissements app/endpoint/client conservés |
| Contrôles APK |Signature, permissions, ARM64, ELF/ZIP16Kio et séparation des fixtures PASS |
| Contrats /1, /2, vecteurs et vérificateur APK Node |PASS ;22 contrôles de référence, pas une preuve Android/JNI |
| Gel et revue F3B |PASS ;296 fichiers inchangés, sept préimages conformes, patch non appliqué |
| CI FUSION-02 distante |[Run40287437858](https://github.com/bubth720/JARVIS-LLM/actions/runs/40287437858) **SUCCESS** sur34491ee, toutes étapes réussies |

Les 303 cas incluent les 285 anciens : ne pas additionner baseline/passe finale.
Les 11 tests Python et CTest sont séparés, pas comptés comme nouveaux JUnit Android.
Tous les tests Gradle de la passe finale ont été forcés (`--rerun-tasks`) ; aucun
résultat UP-TO-DATE n'est relabellisé fraîchement exécuté. Les285 preuves du parent
ont aussi été réellement rejouées dans cette mission. Pas de test échoué sur le
code courant ; les échecs de validation XML sont des négatifs attendus.

Preuves détaillées : [VALIDATION.json](VALIDATION.json), JUnit bruts, logs baseline,
passe ciblée/finale, Python, CTest et contrôle des empreintes sous `evidence/`.
Le workflow publie `fusion-02-llm-evidence`. Les SHA locaux d'APK ne désignent pas
un APK GitHub Actions ; aucun APK personnel n'est installé ou distribué implicitement.

## Commandes reproductibles

```sh
python3 tools/check-fusion-02.py
python3 tools/review-fusion-02-native.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/tests/fusion02 -v
python3 tools/fusion02_evidence.py start
bash tools/cloud-env.sh ./gradlew -I docs/mission-003f-f3b/candidate.init.gradle :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :p2-endpoint:testDebugUnitTest :p2-endpoint:assembleDebug :p2-endpoint:lintDebug :p2-client:assembleDebug :p2-client:lint --rerun-tasks --max-workers=2 --console=plain
python3 tools/fusion02_evidence.py finish
bash tools/cloud-env.sh bash tools/test-native.sh
bash tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
bash tools/cloud-env.sh python3 tools/check-fixtures.py
python3 tools/check-recovery-contract.py
python3 tools/recovery-vectors.py
python3 docs/mission-003f-correction/reference/verify_vectors.py
node tools/test-apk-verifier.cjs
git diff --check
```

Le marqueur doit précéder les tests forcés et aucune source ne doit changer avant
`finish`. Un ancien XML, un changement de commit/source, un cas manquant ou un saut
fait échouer la collecte. Cela évite les erreurs de provenance, pas la falsification
par un hôte malveillant. Aucun état natif positif n'est émis par ces outils.

## CI et dépendances observées, pas réexécutées ici

- LLM FUSION-01 run40214822563 : SUCCESS sur le commit code378a1cf, recontrôlé.
- CORE run40215604539 : SUCCESS désormais observé ; ce n'est pas une preuve de
  FTS5/SAF Android ni de génération LLM. Son rapport36761ce reste une tranche JVM.
- UNIFIED run40214722427 : FAILURE ; cause non déduite de l'absence de logs accessibles.
- NEXUS API24 run40115049468, SERVICES API24 run40115305244 : FAILURE observée.
  La résolution de leurs CI/ports reste chez leurs propriétaires. Les branches
  API24 et finales ne sont pas supposées compatibles ni fusionnées.

Les SHA actuels sont dans PROVENANCE. Aucun code de ces modules n'est importé,
compilé ou modifié. L0/L1 n'attendent pas leurs portes ; aucun GO Android global
n'est déduit de ce lot LLM. La CI historique Android conserve toutes ses assertions,
mais cette branche est routée vers son workflow dédié comme FUSION-01 : gel plus
strict de la base, mêmes contrôles natifs/APK/contrats, nouvelles preuves forcées.
Le téléchargement historique de modèle reste workflow_dispatch opt-in seulement.

## Gels, limites et décisions

| Élément | État |
|---|---|
| Client jarvis.llm.client/1 |Inchangé, SHA256 f29f8a9f3e10645fe1f25ea01913ccc8cf2ee883cbc45f7adca18645ac322b0f |
| Bundle expérimental /2 |Inchangé, SHA256 3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef |
| F3B SENSITIVE.patch |Non appliqué, SHA256 be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374 |
| JNI avec GGUF / tokenizer exact |NOT_RUN / UNSUPPORTED ; pas de port natif qualifié |
| Fin native / unload / réadmission |UNATTESTED, observation inconnue, admission /2 fermée |
| Instrumentation Android/appareils |NOT_RUN ; aucune qualification physique POCO/Redmi |
| Pleine puissance Android |Toujours refusée ; mini-PC décrit mais non qualifié |
| IPC/CORE004D/VOICE |Aucune connexion/activation ; aucun arbitre global |
| PR brouillon |Texte préparé ; GraphQL Forbidden lors de la vérification réelle |

Risques ouverts : UAF des handles historiques, exhaustivité des admissions/imports,
retour JNI réellement corrélé, nettoyage défaillant et durabilité des reçus ; le
patch brut seul ne qualifie pas ces propriétés. Le moniteur ne prouve rien après
process death et son idempotence reste RAM ; l'autorisation CORE doit être réelle
et révocable. Pas de modèle sélectionné assimilé à un worker chargé, aucun gain
RAM/latence/p95/thermique annoncé. Pas de modification des budgets2/4Gio, réserve,
PSS, batterie, thermique, threads, contexte ou annulation native.

**Prochaine action :** revue humaine du dossier/diff L2 exact, puis autorisation
spécifique d'une mission native minimale et de ses tests. Les qualifications
Android29+/GGUF et physiques demandent leurs accords distincts. Le pilote Android
CORE/NEXUS/SERVICES peut progresser sans LLM ; ce dépôt ne lance pas CORE004D.

## Publication et lecture des preuves

[Branche publiée](https://github.com/bubth720/JARVIS-LLM/tree/feat/fusion-02-llm-prequalification),
code34491ee, sans force-push. [Workflow FUSION-02](https://github.com/bubth720/JARVIS-LLM/actions/runs/40287437858)
entièrement réussi sur ce SHA, statut recontrôlé après achèvement. Les 141 fichiers source recensés
pendant les tests locaux correspondent exactement aux blobs Git34491ee ;
[SOURCE-BINDING.json](evidence/SOURCE-BINDING.json). Le run local est antérieur au
commit et conserve son parent e0b7814 +les empreintes des sources de travail ; ce
binding ne prétend pas être une nouvelle exécution au SHA publié.

Le test `processRestartDoesNotCreateNativeUnloadEvidence` remplace un **moniteur en
mémoire** avec une autre epoch : il ne tue ni ne relance un processus Android.
Les barrières de concurrence sont JVM ; aucun effet natif ou OS n'en est inféré.
Le contrôle de fraîcheur et les archives brutes rendent explicites ces périmètres.

Les mises à jour du seul rapport/VALIDATION/preuves sont exclues du déclenchement
push CI ; tout changement de code, contrat, protocole, workflow ou outil déclenche
la validation. Le commit documentaire de clôture ne change aucun fichier testé.

PR non créée : vérification `gh pr list --repo bubth720/JARVIS-LLM --head
feat/fusion-02-llm-prequalification --json url,isDraft` refusée par GraphQL
`Post https://api.github.com/graphql: Forbidden`. Aucun contournement ni numéro
inventé. [Texte préparé](PR-DRAFT.md),
[comparaison pour créer le brouillon](https://github.com/bubth720/JARVIS-LLM/compare/feat%2Ffusion-01-llm-integration...feat%2Ffusion-02-llm-prequalification).
Base proposée FUSION-01 pour isoler cette tranche des missions non fusionnées.
Aucune PR non brouillon ou fusion n'est autorisée.

## Clôture — validation distante constatée

[CI40287437858](https://github.com/bubth720/JARVIS-LLM/actions/runs/40287437858),
**SUCCESS** au commit `34491eee148cdd54db09a09175b3ca1742d8e24c`,
26octobre2026,07:42:32→07:51:05UTC. Gel production/client, revue F3B sèche,
11tests Python, setup, CTest, tests forcés ARM64/Lint, fraîcheur285cas historiques,
contrôles APK et upload sont tous SUCCESS. [Reçu CI](evidence/CI.json).

L'artefact `fusion-02-llm-evidence` est publié sur cette exécution. Son upload et
le statut des étapes sont observés sur GitHub ; le ZIP/les logs individuels distants
ne sont pas téléchargés indépendamment, l'accès API étant refusé. Les303 cas
locaux sont vérifiés et archivés individuellement ; aucun compte issu d'un artefact
non relu n'est inventé. La collecte fraîche du runner et tous ses contrôles passent.

Le commit documentaire de clôture publie seulement REPORT, VALIDATION et evidence ;
aucun code ni protocole testé ne change après34491ee. Aucune fusion ou installation.
Main reste `917e3351f9b525857b6aa019e98d07872d8f6e76` sous réserve de vérification
remote finale. **L0/L1 logiciel livré, dossier L2/L3/L4 livré ; activation native,
matériel et intégration réelle restent BLOCKED.** Prochaine action : revue du diff
F3B exact et autorisation distincte d'une mission native limitée, jamais CORE004D
sur la seule base de ces preuves.
