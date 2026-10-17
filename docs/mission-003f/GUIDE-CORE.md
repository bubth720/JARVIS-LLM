# Remise de contrat à la future mission CORE — consommation bloquée

**Ne pas lancer CORE 004D avec cette révision.** Aucun dépôt CORE n'est modifié.
Ce guide permet la revue des fichiers et des incompatibilités, sans installation.

## Fichiers à examiner

- `fixtures/p2-contract/CONTRACT.txt` : sémantique candidate et F1/F2/F3.
- `fixtures/p2-contract/TABLES.json` : tous les champs/enums/tailles fermés.
- `fixtures/p2-contract/CONTRACT.sha256` : empreinte exacte du bundle candidat.
- `fixtures/p2-contract/src/main/aidl/fr/jarvis/fixture/p2/` : ABI append-only.
- `fixtures/p2-contract/v1/` : référence /1 figée indépendante.
- `docs/mission-003f/examples/discover.{json,sha256}` : vecteur synthétique fixe,
  **pas** un nonce à réutiliser en session réelle.

`python3 tools/check-recovery-contract.py` vérifie sans modifier : digest /1,
préfixes ABI, tables/génération Kotlin, digest candidat et vecteur Python/Kotlin.
Aucune valeur issue d'un fichier exemple ne constitue une autorisation.
Un changement d'un octet normatif exige un nouveau digest et une revue de paire.

Après clarification et validation seulement, CORE devra utiliser exactement la même
révision, authentifier UID/certificat/permission à chaque entrée/callback et refuser
UNKNOWN_TRANSACTION, mauvais digest et capacités absentes, sans tester submit et
sans retomber sur /1. Les epochs CORE ne peuvent être générés par LLM.

## Diff ABI vérifié

Les numéros ci-dessous sont ceux de Binder FIRST_CALL_TRANSACTION=1, AIDL Android :

| Interface | /1 conservé | Ajout /2 |
|---|---|---|
| IP2Endpoint | hello=1, submit=2, status=3, cancel=4 | recovery=5 |
| IP2Arbiter (oneway) | authorize=1 | recovery=2 |
| IP2AuthorizationCallback (oneway) | complete=1, inchangé | aucun |
| IRecoveryCallback (oneway) | absent | complete=1, nouveau descripteur |

Les packages/descripteurs et paramètres précédents restent identiques ; le contrôle
statique compare leurs préfixes aux AIDL /1 exacts. Les transactions /1 ne deviennent
jamais des preuves /2. Aucun service n'est ajouté à l'application historique.

## Identité et provisionnement (spécification de revue, non exécutée)

Packages/components fixes :
`fr.jarvis.fixture.core/fr.jarvis.fixture.core.P2ArbiterService` et
`fr.jarvis.fixture.llm/fr.jarvis.fixture.llm.P2EndpointService`.
Permission signature : `fr.jarvis.fixture.llm.permission.BIND_P2`, propriétaire LLM.
Certificat courant unique commun et dédié, aucune rotation/shared UID. Clé privée
hors Git et artefacts ; les signatures debug CI ne sont pas des clés de production.
Chaque nouvelle paire exige autorisation explicite de laboratoire et inventaire
indépendant : aucun remplacement d'APK personnelle ni effacement pour débloquer.
La sonde authorized porte le même applicationId que la future fixture CORE :
**ne pas les coinstaller/remplacer implicitement**.

Le lecteur de provisionnement attend dans le répertoire privé no-backup une
intention opérateur `lab-provisioning.json`, objet fermé de chaînes :
`version, contract, pilot_id, enrolment_id, core_cert_sha256, llm_cert_sha256,
core_apk_sha256, llm_apk_sha256, enrolment_authorized` ("true").
Les UUID sont nouveaux et autorisés, les SHA issus des APK/certificats observés.
Aucun fichier de provisionnement réel n'est livré, aucun endpoint ne peut l'écrire.
L'intention est renommée durablement en `lab-enrolment-accepted.json` avant création
du journal ; le marqueur consommé et les intentions/marqueurs du journal sont
vérifiés au redémarrage. Une intention déjà acceptée ne crée pas de journal neuf.

L'implémentation ne permet actuellement aucune négociation positive depuis ce
provisionnement (F1) : le constructeur de production ne reçoit aucune source
vérifiable d'epochs CORE. Ne pas activer le paramètre d'injection de tests dans
l'application pour contourner ce blocage.

## Règles pour la suite, après validation séparée

Le contrôle DISCOVER/INSPECT/CHALLENGE/ATTEST/COMMIT/ACK n'autorise jamais import,
load, prepareOperation ou génération. L'ancienne clé doit rester entière si elle
existe ; une nouvelle session de contrôle ne la remplace pas. Une opération
future exige un nouveau mandat réel lié à decision_id et génération suivante,
plus admission matérielle, foreground et contrôles natifs existants.
Un COMMIT ou une coroutine arrêtée ne libère aucun bail natif.

Le port post-natif de F3 doit observer le vrai backend, sans recréer llama.cpp ni
ajouter une seconde réservation P2Lifecycle. CLEANUP_REQUEST doit rester un refus
tant que fin native/identité du propriétaire/baux ne sont pas positivement connus.
Aucun timeout, nouveau PID, arrêt Binder ou remise à zéro de journal ne remplace
ces preuves. ProcessResources reste local au processus LLM, pas un arbitre Android.
