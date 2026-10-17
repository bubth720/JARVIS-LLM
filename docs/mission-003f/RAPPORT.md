# JARVIS LLM 003F — livraison partielle bloquée pour revue

État historique au111be993. Pour le lot approuvé D4-02E.1 et le nouveau bundle, voir
[la correction F1/F2/COMMIT](../mission-003f-correction/RAPPORT.md).

## Statut

**CONTRAT CANDIDAT, NON CONSOMMABLE PAR CORE 004D.** Les composants indépendants
sont implémentés et testables. Le bootstrap positif de la paire réelle ne peut pas
être validé suivant les tables proposées sans clarification F1/F2. Conformément à
l'issue, la portée concernée est arrêtée, sans epochs ni journaux inventés.
CORE 004D n'a pas été lancé. Aucun test physique, émulateur, installation, migration,
effacement d'ancienne paire ou fusion n'est exécuté.

Branche : `fixture/003f-bootstrap-attestation`.
Base vérifiée : `fixture/003e-p2-secure-endpoint`,
`030152a5f3d888017ad7a7fe8026f2e2c48e8fd5` (code `5751646acd4a7bb13caaea4b96a4ab3a68ec6adb`).
Les commits et résultats effectivement exécutés figurent dans `PREUVES.md`.
CI [39031742868](https://github.com/bubth720/JARVIS-LLM/actions/runs/39031742868)
**réussie** sur `b2f6bc052871a2e4f604d9170f385b71c241837b` : 157 JUnit, CTest,
JNI P2 Linux/GGUF public vérifié, ARM64, Lint et contrôles APK. Cela ne lève aucun
des blocages fonctionnels F1/F2/F3 ci-dessous.

## Références intégralement lues

- [Issue LLM #1](https://github.com/bubth720/JARVIS-LLM/issues/1).
- UNIFIED, commit [27e6740](https://github.com/bubth720/JARVIS-UNIFIED/tree/27e674057a348f8074a86851f4e2c11a933162e9/docs/D4-02D) :
  `BOOTSTRAP_RECOVERY.md`, `PROTOCOLE_PROPOSE.md`, `MISSIONS_003F_004D.md`, `TESTS_ET_PREUVES.md`.
- [Validation V1–V5](https://github.com/bubth720/JARVIS-UNIFIED/blob/8ddac8fe8a1861a27d3abeca81eb4ab8f110c3a9/docs/DECISIONS/VALIDATION-UTILISATEUR-D4-02D-2026-10-17.md).
  Les quatre textes D4-02D sont identiques entre ces deux références.

## Préservation

`tools/check-preservation-003f.py` compare les octets à la base 003E : tout `app/`,
`domain/`, les tests historiques des fixtures, le client, les scripts JNI/natifs,
les politiques/profils et les outils de vérification du modèle restent inchangés.
Aucun seuil thermique, batterie, mémoire, thread ou contexte n'est abaissé.
Le catalogue SQLite, les réponses rapides, Qwen3, llama.cpp, SMALL/MAIN et les
modes existants sont conservés. PLEINE PUISSANCE demeure interdite sur Android.
Le JNI et le worker natif de production ne sont pas modifiés.

Les modifications fonctionnelles sont limitées au contrat et à l'APK de fixture.
Leur refus /1↔/2 est volontaire ; l'application historique n'exporte aucun nouveau
service. Les clés de test restent ignorées, séparées de la clé historique.
Aucun autre dépôt n'est modifié ; UNIFIED est consulté en lecture seule.

## Composants livrés

- `/1` : copie exacte `fixtures/p2-contract/v1/`, digest historique vérifié,
  signatures/indices AIDL antérieurs conservés. Méthodes `recovery` ajoutées en fin
  d'ABI ; callback `complete` oneway. Les méthodes /1 de l'endpoint /2 refusent.
- `/2` candidat : tables fermées `TABLES.json`, codec strict séparé, tailles
  24 Kio/32 Kio, UUID/digests/nombres/nonce canoniques, fenêtre au plus 5 secondes,
  vecteur Python/Kotlin. Les marqueurs UNKNOWN ne valent jamais zéro.
- Autorité unique créée par `Application.onCreate`, registre de naissance scellé,
  propriétaire unique du vrai `AndroidRuntime`. Un Service ne recrée aucun runtime.
  `process_epoch` et `service_epoch` sont distincts ; destruction, déliaison et
  mort du client invalident le tour. Aucun second bail ProcessResources.
- Registre initial : prouve uniquement l'absence d'appel/import depuis la naissance
  instrumentée. Tout passage avant import/natif invalide définitivement cette
  preuve dans cette révision ; il n'existe pas encore de port post-natif complet.
  L'état vide ne résulte donc pas du seul `backend == null`.
- Provisionnement privé explicite d'une nouvelle paire de laboratoire : packages,
  UID résolus, certificat courant unique commun, propriétaire de permission de
  signature, empreintes des deux APK, contrat/cohorte/intention. Aucun enrôlement
  depuis une requête Binder. Mise à jour de package ou conflit : refus.
- Journal technique dans `noBackupFilesDir` : intention consommée, marqueurs,
  enregistrements chaînés append-only, génération monotone, checksum, tête exacte,
  fsync fichiers/répertoires. Fichiers intermédiaires, corruption et erreurs I/O
  sont conservés et bloquent. 512 enregistrements/128 tours préparés au maximum,
  pas d'éviction ou de remise à zéro. Une intention acceptée ne peut pas ré-enrôler
  automatiquement après la perte des marqueurs. Aucun texte, URI ou modèle stocké.
- Les transitions CHALLENGE→ATTEST→COMMIT→ACK sont testées avec epochs et inventaire
  **injectés explicitement dans les tests hôtes**. PREPARED est durable avant ATTEST ;
  COMMITTED et l'ACK exact sont durables avant callback. Un COMMIT identique frais
  retourne le même ACK ; un autre digest, tour, fenêtre ou service est refusé.
- Exécuteur de contrôle : un worker et une place de file. Pas d'appel distant ou
  d'attente du worker natif sous le verrou de l'autorité. Les callbacks sortants
  sont effectués après sa libération. `CLEANUP_REQUEST` est refusé, pas simulé réussi.

La composition SYSTEM, le template officiel et le chemin natif 003D/003E restent
identiques. **Aucune admission native /2 n'est activée**, même après un COMMIT :
l'attestation n'est jamais interprétée comme un mandat de génération.

## Blocages et décisions nécessaires

### F1 — origine des incarnations CORE dans NEGOTIATED

`PROTOCOLE_PROPOSE.md` §1 ferme DISCOVER sur exactement sept champs. Il ne fournit
ni `core_process_epoch` ni `broker_epoch`, pourtant obligatoires dans NEGOTIATED.
Ces identifiants logiciels ne sont pas déductibles du PID, UID ou token Binder.
L'implémentation ne fabrique pas ces valeurs : `PEER_INCARNATION_REQUIRED`.
Une question de clarification est présentée à l'utilisateur. Aucun ajout silencieux
aux tables DISCOVER n'est appliqué. Proposition à valider : fournir les deux epochs
par le DISCOVER authentifié, puis les relier au défi et au token vivant.

### F2 — origine du journal CORE dans SNAPSHOT

Le §3 ferme INSPECT sur R+I+K, mais exige J complet dans SNAPSHOT, y compris le
journal CORE. LLM n'a ni accès à cette sandbox ni port authentifié de lecture de
ce journal. Le codec ne peut pas accepter un SNAPSHOT incomplet.
Réponse conservée : `PEER_JOURNAL_REQUIRED`. Il faut définir comment CORE transmet
son J authentifié, ou préciser une table SNAPSHOT ne revendiquant que le journal
LLM. Aucun changement UNIFIED ou extension implicite n'est appliqué.

### F3 — preuves réelles post-natif et cleanup

Le backend réel n'expose pas encore un inventaire complet et corrélé des handles,
files, appels réellement retournés et reçus terminaux durables à l'autorité /2.
Une empreinte de reçu seule ne suffit pas. Les anciennes opérations ambiguës restent
bloquées, même dans un nouveau processus vide. `CLEANUP_UNAVAILABLE` est retourné
pour le retry explicite ; aucun reset, unload fictif ou nouvelle réservation.
La conservation des baux et l'annulation réelles restent celles de 003E, inchangées.
Leur observation par /2 et les nouveaux mandats post-récupération restent à câbler
après résolution du contrat, avec tests du vrai worker.

### Mapping de commit candidat à revoir

Le digest canonique de décision et les champs ACK sont précisés dans CONTRACT.txt.
`prepare_set_sha256` reste un engagement opaque du CORE, lié à la preuve LLM exacte
par `decision_sha256`. LLM ne démontre pas l'appartenance de la preuve CORE à cet
ensemble. Cette limite et la formule doivent être revues avant gel compatible.
L'ACK cite le premier COMMITTED durable ; un second enregistrement conserve son
contenu exact avant callback pour éviter un hash autoréférentiel. Ce second
append ne permet pas d'annoncer un ACK après une erreur I/O.

## Limites de preuve

Les tests hôtes utilisent Robolectric et des fichiers Linux avec fsync réel,
avec injections explicites pour les pannes et incarnations du pair. Ce ne sont pas
des transactions Binder entre deux processus Android. La construction ARM64 et
l'inspection des APK ne démontrent ni le runtime sur appareil ni les coûts RAM/PSS.
Le harnais JNI Linux historique est une preuve native séparée si réellement exécuté,
jamais une preuve du bootstrap /2 ou de libération Android.

L'attestation reste logicielle, bornée au processus fixture et à une paire signée
fiable : aucun scan absolu de RAM, TEE, anti-rollback matériel, preuve par PID ou
preuve de disparition de résidence après mort du propriétaire. Un effacement ou
rollback coordonné de toutes les sandboxes nécessite une preuve externe et une
nouvelle autorisation de laboratoire, jamais une réinitialisation automatique.

## Prochaine action

Clarifier F1/F2, revoir le mapping de commit, puis terminer les preuves F3 et les
tests de panne restants dans LLM avant de figer une révision /2 consommable.
Le digest courant fige seulement les **octets candidats livrés pour revue**.
Ne pas démarrer CORE 004D, ne pas installer les APK, ne pas fusionner.
