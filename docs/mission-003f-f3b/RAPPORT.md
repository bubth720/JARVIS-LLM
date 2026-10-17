# JARVIS LLM — Mission 003F-F3B

## Résultat et décision attendue

**Diff technique concret livré, non appliqué.** Les composants candidats isolés
sont testés ; le backend et JNI historiques ne sont pas modifiés. La partie sensible
s'arrête à la revue, conformément à la demande utilisateur F3B.

Dépôt unique : bubth720/JARVIS-LLM. Branche : `design/003f-f3b-worker-connection`.
Base : `feat/003f-f3-native-observation`, `aa85ad4b506bb655dbe5b50ec4b125d19b48e81e`,
vérifiée localement et sur origin avant création de la branche. Aucun worktree,
installation, fusion ou changement de budgets Android.

- [Diff exact à approuver et risques](DIFF-A-APPROUVER.md).
- [Patch unifié complet](SENSITIVE.patch), [empreintes avant/après](PATCH.json).
- [Mapping des observations et transitions](MAPPING.md).
- [Tests, reproduction et limites](VALIDATION.md).
- [Sources consultées et COORD-001](SOURCES.json).

SHA-256 du patch : `be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374`,
distinct du SHA du commit et du bundle /2.
La prochaine décision demandée autorise seulement son application pour compilation
et tests sur branche dédiée, pas une installation, admission /2 ou activation CORE004D.

## Livré dans cette branche

Les trois fichiers candidat Kotlin sont des documents de code sous candidate/.
Deux seulement (F3Trace et F3NativeApi) entrent dans le sourceSet **test** via un
init script Gradle explicite. Aucun fichier app/domain/fixtures n'est modifié.
F3BackendObservation et les changements backend/CPP/coordinateur/runtime restent
uniquement dans le patch. Ils n'ont pas été compilés ni exécutés sous forme modifiée.

F3NativeApi décore et délègue l'API existante ; aucun second moteur ni propriétaire.
Le test avec le vrai LlamaBackend utilise son constructeur d'injection existant et
un NativeApi factice : appels synchrones réellement ordonnés sur le worker historique,
aucun JNI. Les événements synthétiques du traceur restent séparés de ce scénario.
Aucun résultat de simulation n'est publié comme un reçu natif.

Le générateur lit les blobs Git immuables et écrit seulement le patch/manifeste
sous docs/. Le vérificateur exige une génération déterministe, vérifie le SHA,
exécute git apply --check **sans appliquer**, gèle les108 fichiers historiques,
contrôle les XML JUnit et vérifie que F3Trace n'apparaît pas dans les dex des APK.
La CI reprend ces contrôles, les suites historiques, compilation et Lint ; elle
ne compile jamais le backend avec le patch en attente.

## Résultats locaux

**232 JUnit réussis**, zéro échec/erreur/saut :87 domaine,27 app,118 fixture
(101 hérités +17 F3B). CTest1/1 sur le code natif historique inchangé.
Compilation ARM64, Lint et contrôle des APK historiques/fixtures : voir VALIDATION.md.
Les protections RAM/batterie/thermique, catalogue, modèles, modes, permissions,
annulation et baux restent byte-identiques à la base. Aucun gain matériel annoncé.

## COORD-001

Pas de résultat trouvé dans le checkout, les branches UNIFIED dont le nom contient
coord, ou les fichiers de main UNIFIED au commit consulté (SOURCES.json).
Une référence a été demandée dans la conversation, sans bloquer le travail indépendant.
Cette recherche bornée ne prétend pas prouver l'absence du résultat dans tous les
dépôts ou branches. Aucune décision de gouvernance ou d'arbitrage n'en est déduite.
Une référence communiquée ultérieurement doit être comparée avant application.

## Blocages conservés

- Modification sensible en attente de validation explicite du patch exact.
- Backend modifié, sonde JNI, changements de coordinateur et runtime : non compilés/non exécutés.
- Census/freeze exhaustif, adapter positif F3A et reçus terminaux durables : non raccordés.
- Pas de preuve F3 native, Binder entre APK, crash natif/OS ou qualification Android physique.
- Aucun mandat natif /2, aucun CORE004D ; /1 et /2 inchangés.

Le bundle /2 conserve `3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef`.
Le coordinateur reste local au processus. Les données privées, clés et GGUF ne sont
pas ajoutés à Git ni aux artefacts. Le rapport de clôture donne les commits et CI
exacts sans transformer une CI verte en validation du patch sensible non exécuté.

## Publication et décision suivante

Préparation testée/publiée : `99b946e56fdc5b53ccea2c6ef86efcdd301aa5ce`.
[CI39077934892](https://github.com/bubth720/JARVIS-LLM/actions/runs/39077934892)
entièrement réussie ; [preuves et limites](VALIDATION.md), [détail des étapes](CI.json).
Le commit suivant clôt uniquement la documentation, sans changement au patch.

**Arrêt avant application du diff sensible.** Validation explicite attendue pour
appliquer le patch `be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374`
et compiler/tester les sept fichiers concernés, en conservant /2 fermé et CORE004D
bloquée. La recherche COORD-001 a été revérifiée avant clôture : références UNIFIED
inchangées, aucun résultat nouveau retrouvé dans les emplacements consultés.
