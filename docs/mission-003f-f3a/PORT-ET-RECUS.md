# Port et reçus F3 — candidat interne, aucun contrat wire nouveau

## Composants effectivement ajoutés

Sous fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/f3/ :

- ObservationPort : interface suspendue de lecture, implémentation disponible
  seulement UnavailableObservationPort (retour null). Elle ne crée aucun backend.
  ObservationReview vérifie la cohérence d'un snapshot, sans authentifier sa source.
- AdmissionBarrier : ledger synchronisé, registre explicite et scellé de participants,
  tickets d'activité par identité objet, limite128, freeze idempotent même tour,
  rejet des nouvelles admissions et compteurs d’activités/file/imports. Le compteur
  active inclut les tickets actifs, pas seulement JNI : ne jamais le publier comme
  active_native_calls. Ce ne sont pas des
  baux et aucun appel à ProcessResources n'est ajouté. Aucun dégel automatique/API reset.
- ReviewJournal : journal local borné64, schéma `f3a-review-only/0`, domaine de hash
  `jarvis.f3a.review-only\0` + encodage C existant. Initialisation de labo explicite,
  chaîne/compteurs/tête vérifiés à chaque lecture, écriture exclusive pending/temp,
  fsync fichiers, publication et fsync répertoire avant retour. Incohérence conservée
  et bloquante ; aucun écrasement/réparation d'un journal existant. Le schéma stocke
  seulement process, digest de clé, état et code de revue, pas un prompt/chemin/exception.

**Aucun de ces composants n'est instancié ou appelé par le runtime.** Le journal
n'est ni RecoveryJournal ni une source de terminal_record_ref. Même une revue
UNLOADED sur un snapshot synthétique n'est pas un reçu natif. Aucun sérialiseur
vers ATTEST/SNAPSHOT /2 n'est fourni. Le bundle /2 et ses tables restent exacts.

## Observation proposée et règles conservatrices

L'attente comprend incarnation du processus, worker/owner, clé entière, génération
registry, révision d'admission, barrière et minima call_end_seq/unload_seq. La clé
comprend les neuf éléments prior de /2 (epoch, session, opération, corrélation,
annulation, authorization_ref, génération, lease_id, request_digest), plus pilot_id
et génération du bail interne. Pas de clé dérivée du PID ou d'un chemin modèle.
Ces identités devront être liées aux vrais objets avant le premier travail.

Les compteurs nullable sont UNKNOWN lorsqu'absents : queue, imports, appels,
baux MODEL/HEAVY. Handle/modèle/contexte ont EMPTY/OWNED/UNKNOWN séparés.
STOP_REQUESTED ne prouve aucune fin ; DRAINED requiert queue/imports/appels à zéro
et séquence de retour corrélée, mais peut encore avoir modèle/baux. UNLOADED requiert
également modèle/contexte vides, aucun bail et unload_seq corrélé ; un handle de
bibliothèque vide peut subsister. Toute provenance divergente, manque de source,
échec de nettoyage ou changement de révision garde UNKNOWN/CLEANUP_FAILED.

Le snapshot réel devra être sérialisé sur le vrai worker et cohérent avec le gate
avant/après lecture. La génération du registre change lors d'ajout/retrait/changement
de propriétaire, la révision d'admission à chaque transition des tickets ; il faut
reprendre l'attente après drain, pas réutiliser la révision du freeze initial.
Une observation réussie dans le worker après un appel bloqué attend son retour ;
aucune attente sous le verrou de l'autorité. La barrière n'est pas une exclusion
inter-APK et les compteurs ne sont pas des mesures de RAM.

## Port de commande suivant (proposition, non implémentée)

1. freezeAdmission(round,barrier), puis inspectFrozen avec registre exhaustif.
2. observe(expectedProcessEpoch,fullKey,owner,leaseGeneration), sans factory/load.
3. requestStopExact(fullKey) : valider le même détenteur, consigner intention uniquement.
4. awaitDrainExact : attendre retours réels de **tous** travaux et flux, sans timeout
   positif ni déduction à partir d'une coroutine. Erreur fatale/perte d'observation : UNKNOWN.
5. unloadExistingExact : nettoyage du même handle seulement après drain démontré,
   retry explicite, aucune création/réinitialisation pour obtenir un moteur vide.
6. persistTerminal puis readTerminal : seulement après état réellement instrumenté
   positif et vérifié sous barrière ; pas de reçu si une donnée manque.

## Reçu terminal futur (non produit)

Le futur format interne, à revoir avant implémentation, devra contenir : version et
domaine distincts, process/worker/owner, génération registre, barrière/révision,
clé complète et digest de requête, IDs et générations exacts des vrais baux,
séquences de fin de chaque appel et d'unload/destroy, états handle/model/context,
compteurs/absence confirmée des files/imports/calls/baux, référence au record
précédent du propriétaire et empreinte de l'observation cohérente. Aucun contenu
utilisateur. Les listes de baux devront être bornées et canoniques ; les tailles et
l'encodage normatifs de ce format ne sont **pas** gelés par cette mission.

Persister données puis tête/outbox par fsync fichier + répertoire avant publication
d'une référence ; tester chaque frontière, y compris après unload mais avant reçu,
capacité saturée et ACK perdu. Une référence terminale ne s'invente pas à partir d'un
hash, d'une revue, d'un reçu CORE, d'un timeout, d'une relance vide ou d'un COMMIT.
Un ancien journal/prior-key PRESENT sans reçu valide doit rester UNKNOWN, même si
le nouveau processus ne détient rien. Un reçu d'une autre clé/bail/incarnation n'est
pas réutilisable. Les hypothèses de confiance et le mapping dans /2 exigent revue.

## Limites de durabilité du banc isolé

Les tests de ReviewJournal font réellement les I/O et fsync de fichiers sur Linux.
DirectorySync y est factice : ils vérifient l'ordre et les refus, pas le stockage
Android après coupure matérielle. Les exceptions injectées représentent les
frontières d'interruption ; pas de crash natif/OS réel. Un retrait de record avec
tête conservée bloque ; un rollback cohérent de toute la sandbox ne peut pas être
prouvé impossible par ce journal logiciel. Aucun mécanisme de reset/migration n'est livré.
