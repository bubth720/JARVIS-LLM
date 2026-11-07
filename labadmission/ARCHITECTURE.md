# LAB-ADMISSION-01 — architecture proposée, non appliquée

Module LLM ; dépôt bubth720/JARVIS-LLM ; base exacte de PR #14 : `2500a123a69b059bf21ac6bec24be5a52b54a9c5`, tree `34e16b628a577e9cfae0633785502da742da9287`. Branche de revue `codex/llm-lab-admission-review`. L'autorisation actuelle couvre la préparation et les tests isolés, pas l'application, un téléchargement, une inférence ou GATE-B.

## Scopes et racine d'autorité

`StreamGrant` et `StreamOwnerAuthority` restent le chemin CORE : leur mandat garde sa signification. `LabCloudAuthority`, `LabSignedPermit` et `LabStreamBinding` sont des types distincts. Un permis LAB ne peut pas satisfaire l'interface d'autorité CORE, n'a aucun `coreMandateSha256` et utilise le domaine de liaison `jarvis.lab-cloud.stream/1`. Le contrat commun `StreamBinding` est réservé au buffer/callback interne, sans conversion de credential ni connexion CORE/INTERFACE. Les six contre-exemples du compilateur vérifient ces frontières.

Le permis est signé Ed25519 par un opérateur de confiance indépendant du candidat. Le code ne possède aucune clé privée, ne s'auto-signe pas et ne choisit pas la clé depuis le permis. La clé publique et son empreinte attendue doivent provenir d'une racine **préalablement approuvée**. Le constructeur de la racine est privé et l'empreinte ancrée dans le candidat est vide : `fromApprovedKey` refuse donc toute clé. Fournir une vraie empreinte exigera un nouveau diff exact ; aucune clé choisie par l'appelant ne peut servir d'approbation. Les tests franchissent ce constructeur uniquement par réflexion isolée, sans JNI, comme fixture de cryptographie. Aucun câblage, clé ou permis utilisable n'est installé dans les applications historiques ou la CI de revue.

Le message signé est canonique : chaque champ UTF-8 est précédé de sa longueur int32 big-endian. Il inclut le scope/version, dépôt logiciel, run/attempt/commit, nonce, session, challenge réel owner/worker de ce backend, émission/expiration, dépôt/révision/nom/taille/SHA du modèle unique, template exact, empreinte de la demande, configuration et variante HISTORICAL/STREAM. Les données de run sont observées dans un job Linux GitHub Actions ; ces variables ne constituent pas à elles seules une signature ou une attestation contre un runner compromis. Le manifeste signé doit être comparé au run/commit inspecté par l'opérateur, dans un workflow figé.

La validité initiale est contrôlée en UTC, puis sa durée restante (maximum 300 s) est ancrée une fois à `nanoTime`. Aucun calcul ne soustrait les origines UTC et monotone. Une modification ultérieure de l'heure UTC ne prolonge pas le permis. Le nonce est consommé atomiquement avant toute opération fichier/native, même si elle échoue. Le registre partagé refuse les replays entre instances d'autorité, conserve les révocations et refuse de nouveaux permis après 4096 entrées ; aucune éviction ne rend un nonce réutilisable. Une nouvelle incarnation backend/worker est requise après mort du processus ; aucun permis ne se restaure depuis des IDs fournis par le client.

## Ordre d'admission, sans cercle chargement/résidence

1. Créer le backend habituel **vide**, ou réutiliser cette même instance, et lire son challenge réel ; ce challenge n'accorde aucun droit. Un opérateur approuve ensuite un permis de tentative lié à ce challenge, au run/commit/modèle et à une demande publique exacte.
2. Vérifier signature, racine, contexte d'exécution, challenge, demande, modèle/configuration et temps. Consommer le nonce. Fermer le backend à tout retour implicite au scope historique dès qu'une tentative LAB est admise. Une variante/buffer incompatible est refusée, sans réutiliser son nonce.
3. Observer les ressources Linux réelles : MemTotal/MemAvailable, toutes les limites visibles des ancêtres cgroup v2 et VmRSS. Appliquer le budget SMALL **inchangé de 2 Gio**, la réserve au moins 1 Gio ou 20 % et refuser une observation manquante/contradictoire. Aucune modification de cgroup, seuil, permission ou politique Android. Les capteurs batterie/thermiques Android ne sont jamais simulés sur hôte.
4. Obtenir le vrai bail HEAVY du broker pour ce propriétaire. Vérifier le snapshot privé déjà acquis : fichier exact, taille/SHA autorisés, fichier régulier sans lien symbolique/hardlink, identité d'inode stable, droits fichier 0400 et dossier 0500. Rien n'est téléchargé ou converti par le patch.
5. Sous le worker existant, armer le même handle. Pour une nouvelle résidence, réutiliser le chargeur natif et son contrôle GGUF historique, acquérir MODEL puis **épingler atomiquement les deux vrais baux avant `native.load`**. Le permis de chargement est donc antérieur au reçu de résidence. Un modèle historique non attesté est déchargé et ne devient jamais LAB par déclaration.
6. Après retour réel du chargeur, vérifier à nouveau les octets/identité/configuration et les allocations natives, puis émettre le reçu local lié au bail, au même worker et à la nouvelle incarnation de résidence. Ce reçu ne prétend pas être une preuve cryptographique émise par llama.cpp.
7. Sur une résidence chaude, recontrôler le fichier et l'identité réelle du bail/incarnation, réépingler HEAVY/MODEL ; ne pas recharger si la configuration exacte et le reçu correspondent. Un changement du cap 8→24 modifie encore RuntimeConfig et peut entraîner la recharge historique ; ne pas l'étiqueter WARM par hypothèse.
8. Appeler `measureFixtureP2` sur le modèle chargé : rendu et template réels, compte token natif. Lier les hashes, la demande d'origine, la configuration et les preuves du broker à une préparation locale à usage unique. Le template doit correspondre au hash **déjà signé**, sans valeur de secours. La tokenisation exacte est déterminée par les mêmes octets/modèle/vocabulaire et le code natif épinglé ; aucun nouveau vecteur de tokens ni ticket de prefill n'est exposé par ce patch.
9. Autoriser une unique invocation : `generateFixtureP2` historique complet ou `generateStream` existant. Pour le streaming, `onNativePrepared` recompare rendu/template/compte avant ACK/prefill, puis les événements sont liés au scope LAB. Les deux variantes utilisent le même moteur, worker, modèle, paramètres et prompt P2 non préfixé. Aucun découpage du texte final en pseudo-tokens.
10. Fermer le callback seulement après retour JNI effectif, conserver le terminal exact et les preuves séparées de drainage/déchargement. Une erreur de fichier/template/ressource ferme la tentative. Un échec critique de stop/nettoyage met en quarantaine les baux disponibles et interdit tout retry automatique ou nouvelle admission ; récupération explicite ou arrêt de la campagne.

## Révocation, concurrence et ressources

L'observation broker vérifie les objets de bail réels, owner/module/classe, IDs monotones, révision et incarnation, sous un même moniteur. Ses constructeurs ne confèrent pas la propriété. Fermer un bail épinglé le révoque immédiatement ; le slot reste occupé jusqu'à fermeture de la dernière observation. Les watchers sont appelés **hors verrou broker** ; ils ne génèrent aucun token et demandent uniquement l'annulation du même moteur. Le stop tardif vérifie l'identité de la tentative active pour ne pas annuler une nouvelle génération.

Un seul watchdog de contrôle par processus traite expiration, annulation du Job appelant et pression mémoire ; aucun moteur ou worker de génération supplémentaire. La surveillance est candidate à 100 ms, non une garantie mesurée de latence Stop. Les callbacks vérifient aussi révocation, baux et incarnation. Le watchdog est détaché à la fermeture de la tentative ; les nonces restent conservés, sans credentials bruts ni callbacks de backend dans le registre fermé.

Le budget hôte est un contrôle conservateur observable, **pas un limiteur matériel de pic atomique**. Un dépassement transitoire ou un arrêt natif tardif reste à tester après approbation. Un cgroup/mapping non qualifiable est un refus ; aucune valeur Android fictive ni seuil assoupli ne remplace une observation manquante. Le choix d'un profil hôte n'active aucune route mobile et n'altère pas SafetyPolicy.

## États et limites de preuve

- AUTHORIZED : permis signé accepté/consommé ; aucun modèle attesté.
- FILE_VERIFIED : octets vérifiés ; aucun chargement encore attesté.
- RESIDENT : chargeur retourné, validations locales après chargement et baux réels présents.
- PREPARED puis INVOKING : préparation exacte liée, puis invocation unique.
- CONTENT_DONE : terminal de contenu confirmé, pas DRAINED/UNLOADED.
- CANCELLED : terminal natif d'annulation confirmé ; un simple Stop ou Job.cancel n'est pas cette preuve.
- ERROR : terminal d'erreur confirmé ; UNKNOWN : retour/état non confirmé ou exception ; CLEANUP_FAILED : arrêt/nettoyage non confirmé, admission verrouillée.

DRAINED et UNLOADED demeurent des preuves distinctes selon les contrats historiques. L'API de revue ne les invente pas à partir du résultat. La mort du processus termine ses ressources OS mais n'émet aucun reçu de déchargement rétrospectif.

La protection du fichier est coopérative : le code JNI historique rouvre un pathname et ne retourne ni hash de poids chargés ni reçu de FD. Un acteur hostile du même UID, root ou un runner compromis peut contourner des permissions 0400/0500 ; ce scénario est **hors frontière du laboratoire proposé** et n'est pas déclaré résolu. Si cette hypothèse ne peut pas être acceptée, bloquer l'application et préparer un autre diff exact de chargement par FD/reçu natif. Aucun changement JNI C++ n'est inclus ici.
