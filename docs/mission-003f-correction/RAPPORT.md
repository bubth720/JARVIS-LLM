# JARVIS LLM 003F — correction F1/F2/COMMIT D4-02E.1

Branche : `fix/003f-d4-02e1-commit`.
Base vérifiée locale/distante : `fixture/003f-bootstrap-attestation`,
`111be993e970051686bd5c27b3115320513a4be7`. Aucun merge de branche divergente.
Code validé : `9745599b3894619ae00d57b3aefab2b5ef2b5f35` ;
[CI39058623037](https://github.com/bubth720/JARVIS-LLM/actions/runs/39058623037) entièrement réussie :
185 JUnit, CTest1/1, JNI Linux historique avec GGUF public vérifié, ARM64, Lint et APK.
Les commits et limites de chaque plateforme sont détaillés dans VALIDATION.md.

Cible approuvée : [UNIFIED 49dd4027](https://github.com/bubth720/JARVIS-UNIFIED/tree/49dd4027d2ccb54bf330f591d3ee17f5dc577132/docs/D4-02E).
Approbation [275eca892c20b6dae3bfa20b241099542fd4ced6](https://github.com/bubth720/JARVIS-UNIFIED/blob/275eca892c20b6dae3bfa20b241099542fd4ced6/docs/DECISIONS/VALIDATION-IDENTITY-001B-D4-02E-2026-10-17.md).
L'issue LLM1, tous les documents D4-02E, tables, diff, vecteurs et approbation sont lus.
Les empreintes des sources consultées figurent dans `SOURCES.json`.

## Résultat et périmètre

F1/F2 et l'algèbre/les outboxes COMMIT sont implémentés sur la voie de contrôle de
fixture. Les tests hôtes exercent le chemin positif sans modèle avec un inventaire
synthétique explicitement identifié et un canal authentifié injecté dans le harnais.
Le Service Android relie ce canal aux faits UID/certificat et au token vivant réels ;
sa compilation et ses contrôles statiques ne prouvent pas un échange entre APK Android.
Aucun fournisseur d'epochs CORE injectés en production ne subsiste.

**F3 reste non implémentée ; admission native /2 fermée ; CORE004D BLOQUÉE.**
Ni cette correction, ni une CI verte, ni le nouveau SHA n'autorisent sa consommation
par CORE avant revue du bundle exact et livraison/validation distincte de F3.
Pas de génération UI, d'installation, migration, réparation de journal ou test physique.

## F1 : négociation et incarnations

DISCOVER possède exactement les onze champs approuvés. CORE fournit ses UUID de
processus/broker et la fenêtre monotone. LLM renvoie ses propres epochs et l'écho
exact, sans dériver les UUID CORE de PID/UID ou les générer à sa place.

Le Service capture l'UID dans le thread Binder, valide package/certificat/permission
et pins avant décodage, puis revalide avant traitement et callback positif.
Les deux services épinglés doivent être présents, activés, exportés, protégés par
la permission exacte et associés aux bons UID. Cette vérification porte sur leur
configuration ; Binder authentifie l’UID de l’APK appelante, pas son Activity ou
Service interne. Tout rejet de confiance ferme la liaison de récupération.
L'autorité lie offre entière, UID/certificat, session/tour/nonce et binding/token
vivant au service courant. Rejeu exact vivant/frais : réponse identique sans écrit.
Mutation, anciens tours/brokers retirés, autre liaison, service mort, certificat ou
package modifié : refus. Les ensembles sont bornés, sans éviction silencieuse.
Une récréation de Service conserve le vrai runtime Application et change service_epoch.
Une perte de callback ne devient jamais une fin native.

## F2 : propriété des journaux

SNAPSHOT ne contient aucun core_journal_*. Il décrit uniquement le journal, la
génération, le propriétaire, la clé antérieure et l'inventaire LLM locaux.
CORE_PREPARE est un type local fermé (appel RPC autonome interdit), transmis dans
CHALLENGE par le canal CORE actuel. Sa déclaration CORE est liée par hash, rôles,
champs communs et tête LLM attendue. Les champs CORE de l'ATTEST sont des échos de
cette déclaration, aucune lecture ou attestation de la sandbox CORE par LLM.

La tête locale ou l'inventaire ayant changé depuis INSPECT interdit PREPARED.
Une génération/clé/identité de preuve discordante est rejetée avant l'écriture.
Les neuf champs prior sont tous exigés si PRESENT, tous absents si NONE.
Les anciennes opérations sans preuve native demeurent UNKNOWN : le registre de
naissance du nouveau processus ne les déclare jamais libérées.

## Engagements, journal et interruptions

Les quatre engagements utilisent C canonique et les labels exacts D4E1 :
`core_prepare`, `llm_attest`, `prepare_set`, `decision`.
Les deux preuves complètes CORE_PREPARE/ATTEST sont transmises en base64url de JSON
canonique strict : limites 6144 octets décodés/8192 caractères, vérifiées avant
allocation/décodage ; réencodage identique obligatoire. Types, doublons, contrôles,
Unicode malformé et représentations JSON/base64 non canoniques sont rejetés.

L'appartenance est vérifiée contre la preuve CORE conservée dans le PREPARED local
et la propre preuve LLM dans ATTEST_OUTBOX. Recalculer tous les hashes d'une preuve
remplacée ne suffit pas. Les références PREPARED des deux rôles restent séparées.

Le format exact du record et de sa référence est figé dans CONTRACT.txt : cinq
champs canoniques, H_record étiqueté, pas de hash propre dans le record. Pilot et
génération appartiennent au payload technique. Les anciens formats sont bloqués et
préservés, pas migrés. Le lecteur vérifie chaîne, tête, séquences et générations ;
la durabilité est relue avant chaque traitement, y compris avant un ACK idempotent.

Le graphe local est :

1. PREPARED : CHALLENGE exact, sans ATTEST ni hash de soi.
2. ATTEST_OUTBOX : preuve LLM exacte citant PREPARED, durable avant callback.
3. COMMITTED : décision et références exactes, sans ACK.
4. ACK_OUTBOX : ACK exact et référence au COMMITTED, durable avant callback.

L'ACK cite COMMITTED, pas l'outbox qui le contient. Quatre places sont exigées avant
PREPARED ; caps512 records/128 tours conservées. Avec un record d'enrôlement et
quatre records par cycle complet, 127 cycles complets au plus tiennent sans autre
écriture ; ce n'est pas une promesse de capacité à128 cycles. Aucune purge automatique.

Fsync fichier, publication atomique et fsync répertoire sont exercés aux44 points
instrumentés des quatre appends. Échec I/O/temporaires incohérents : conservation,
FAULT/blocage, pas d'ACK positif. Un arrêt après une transition complètement durable
est distingué d'une écriture ambiguë. Après COMMITTED durable sans ACK_OUTBOX, même
processus/service/canal/fenêtre et mêmes preuves : création du même ACK puis outbox
durable avant réponse. Outbox corrompue existante : refus, aucune reconstruction.
ACK perdu : réponse exacte sans append supplémentaire ni deuxième résolution.

La fenêtre est revérifiée après l'I/O et avant callback : un COMMIT arrivé juste
avant expiration ne rend pas son ACK automatiquement frais après des fsync longs.
Après nouvelle incarnation/expiration, seules négociation, inspection et deux preuves
neuves peuvent réconcilier l'historique sans native ; aucun ancien ACK ne rouvre le gate.

## Préservation

`check-preservation-d4e.py` compare à111be993 l'ensemble app/domain, les AIDL, /1,
Wire, AndroidRuntime, FixtureApplication, BootstrapRegistry, manifeste et tests
historiques non concernés. Aucun changement du backend/JNI, politiques, budgets,
réserves, profils, threads, batterie, température, catalogue SQLite ou modèles.
Pas de second moteur/bail ; ProcessResources reste local au processus LLM.
Aucun autre dépôt modifié. Main, les clés de signature et les données personnelles
ne sont pas modifiés ; clés et GGUF exclus de Git et des artefacts.

Les43 tests de fixture précédents sont conservés en nombre. Les23 tests de
RecoveryTest sont adaptés aux nouvelles sémantiques approuvées :

- F1 ancien refus d'epochs manquants → refus de canal absent et négociation exacte.
- F2 ancien refus SNAPSHOT incomplet → SNAPSHOT complet sans journal CORE.
- Préparation désormais durable dans ATTEST_OUTBOX, séquence ACK_OUTBOX5 et référence
  COMMITTED4 ; les tests ne confondent plus dernière tête et PREPARED cité.
- Reprise exige une nouvelle négociation ; les anciens epochs restent refusés.
- Budgets de replays testés avec CHALLENGE/nonces canoniques et complets.
- Le CORE synthétique atteste son propre inventaire, indépendamment de l'inventaire
  LLM inconnu/occupé. Les assertions de refus matériel restent présentes.

Les suites EndpointTest/AndroidIdentityTest/BootstrapApplicationTest et tous les
anciens tests app/domain sont inchangés. Le catalogue et le modèle unique restent couverts.

## Contrat et preuves

Version transport : `jarvis.fixture.p2/2`, révision D4-02E.1.
Les octets TABLES.json sont exactement ceux de TABLES_PROPOSEES.json approuvé ;
le champ historique `revision=D4-02E.1-PROPOSED` est conservé sans réécriture,
l'approbation étant portée par la décision immuable séparée.
AIDL byte-identiques à111be993, aucun changement de descripteur/ordinal.

SHA du nouveau bundle CONTRACT+TABLES+AIDL triés :

`3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef`

/1 demeure `fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9`.
Les deux anciens /2 et /1 ne peuvent négocier le nouveau digest : INCOMPATIBLE,
sans downgrade. Le sentinel64zéros des vecteurs de référence est interdit en production.

Voir `VALIDATION.md` pour commandes, résultats exécutés, commits et CI ;
`examples/vectors.json` pour les vecteurs avec le vrai digest et `reference/` pour
la copie des vecteurs synthétiques approuvés, jamais une autorisation.

## Limites et prochaine action

Les inventaires des tests nouveaux sont synthétiques, pas des preuves de libération
JNI. Le registre réel de naissance et le backend historique restent inchangés.
`CLEANUP_REQUEST` retourne CLEANUP_UNAVAILABLE ; chaque admission native retourne
RECOVERY_REQUIRED, y compris après COMMIT. Aucune preuve native F3 n'est simulée.
Les tests de fichiers utilisent fsync Linux et des interruptions injectées, pas
une coupure physique de stockage Android. Ni TEE, scan de RAM absolu, mort de
propriétaire prouvée par Binder/PID, ni anti-rollback matériel ne sont revendiqués.

Faire la revue de ce bundle exact et du rapport. F3 nécessite une mission native
séparée approuvée et ses preuves ; CORE004D reste bloquée. Les tests physiques et
l'échange de deux APK Android restent non exécutés et ne sont pas autorisés par cette livraison.
