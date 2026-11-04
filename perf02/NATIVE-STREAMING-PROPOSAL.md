# Proposition JNI PERF-02 — décision distincte requise, NON APPLIQUÉE

Base de revue : `1b62a5c50eb979d82ef8eb0b488ab38c6fe462ce`. `bridge.cpp` et `LlamaBackend.kt` restent byte pour byte inchangés. Le patch F3B historique `be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374` reste non appliqué et son contrôle sec ne qualifie pas le natif.

## Décision proposée

Privilégier **un callback synchrone depuis le worker natif déjà appelé par Kotlin**, avec admission CORE et propriété du handle/bail existantes. Le callback est un transport de pièces de texte, pas un second moteur, arbitre, scheduler ou droit de répondre. Aucun nouvel export JNI ni callback productif n’existe dans ce lot.

| Option | Avantages | Risques / conditions |
|---|---|---|
| Callback depuis le worker existant | Pas de polling au repos, premier token proche de sa production ; thread JNI déjà attaché lors de l’appel Java→native | Référence globale callback, exceptions Java/OOM, réentrance, backpressure et cycle cancel/destroy à qualifier |
| Ring buffer natif borné + poll | JNI de lecture non bloquant, découplage de cadence UI | Durée de vie du buffer/handle, copies et preuve de drain ; poll sur le worker occupé serait inefficace ; poll UI/IO doit être borné, sans boucle au repos |

Pas de GO avant revue d’un patch exact, compilation/JNI hôte, sanitizers et laboratoire Android autorisé. L’architecture F3B de preuve d’ownership et le tokenizer lié à prepare/consume/discard restent des prérequis, pas des capacités déjà fournies.

## Contrat candidat, pas amendement de client/1 ou Binder /1-/2

Le DTO test-only `fr.jarvis.perf.NativeTokenEvent` explore : clé complète `{request, session, generation, epoch, lease, modelSha256}`, séquence stricte, type `ACK/TOKEN/COMPLETED/CANCELLED/ERROR`, chunk UTF-8 borné. Dans la version productive proposée il faudra ajouter/binder l’empreinte de la demande approuvée, l’autorisation CORE et le template/préparation exacts. Un UUID ou SHA seul n’autorise rien.

Schéma JNI indicatif à soumettre comme patch distinct :

```text
generateStream(existingHandle, existingRoles, existingTexts, existingLimits,
               exactOperationBinding, callback) -> nativeTerminalReceipt
callback.onChunk(exactOperationBinding, sequence, firstTokenOrdinal,
                 lastTokenOrdinal, rawUtf8Chunk, nativeBoottimeNanos) -> ACCEPTED | BACKPRESSURE | CANCEL
```

Les anciennes signatures `generate/generateP2 -> ByteArray` restent disponibles et n’annoncent aucun streaming. Pas de conversion du String final en faux tokens. `ACK` est du contrôle, jamais premier texte. Le client live actuel rejette les origines FIXTURE : sa version/capacité future NATIVE doit être revue avec CORE/INTERFACE avant raccordement ; le contrat gelé n’est pas modifié implicitement.

## Temps et vrai premier texte

- Mesurer au worker le premier token non-EOG réellement échantillonné ; distinguer cet instant du premier chunk UTF-8 non vide et de sa frame UI. Les byte-fallback tokens peuvent couper un caractère : carry <=3 octets, aucune pièce vide affichée, pas de caractères de remplacement inventés.
- Android natif devra utiliser une horloge monotone qualifiée cohérente avec `elapsedRealtimeNanos` (par exemple CLOCK_BOOTTIME vérifié), pas soustraire aveuglément `steady_clock`, nanoTime Linux ou timestamps d’APK différents. Corrélation et domaine de clock/boot doivent être validés ; sinon UNSUPPORTED/UNKNOWN.
- UI : seul le texte venant du worker de la bonne génération peut armer le commit de frame. Statuts, catalogue déterministe, ACK, spinner et écran prêt sont exclus. Un frame commit est une métrique de rendu définie, pas une sonde physique de photons.
- `CONTENT_DONE` ne prouve ni retour JNI, ni drain, ni unload. Une coroutine annulée ou un timeout ne fabriquent aucune de ces preuves.

## Threading, mémoire et durée de vie

1. Un seul Engine et son worker existant restent propriétaires. Ne pas déplacer create/begin/cancel/destroy ni appliquer F3B par effet de bord. Fixer l’ordre des locks ; ne jamais appeler UI/CORE sous `handleLock` ou sous un verrou du coordinateur.
2. Créer au plus une `jobject` global ref de callback par appel admis ; toutes les exceptions NewGlobalRef/NewByteArray/CallMethod sont explicites. La détruire après le vrai retour et fin des callbacks de cette opération, jamais sur simple annulation UI. Aucun thread attaché nouveau pour un callback synchrone.
3. Handle, callback, ring éventuel et préparation doivent vivre jusqu’au retour effectif de l’appel. Pas de delete Engine/unload pendant callback ou decode. Après process death ou perte de preuve : UNKNOWN, aucun retry/réadmission automatique. Tester UAF, ABA, ancien epoch et génération invalidée.
4. Prototype : <=16 événements en file, <=16 Kio de texte en attente, <=64 Kio cumulés, <=4096 octets/chunk. Dernière empreinte/payload et buffers UTF-8 temporaires sont des coûts additionnels ; ces limites logiques **ne sont pas une mesure PSS ni un nouveau budget de résidence autorisé**. Préallouer/binder les buffers et mesurer le vrai coût avant toute activation.
5. `BACKPRESSURE` ne consomme pas la séquence ni ne perd un token accepté. Le producteur doit attendre de façon interruptible, avec deadline et vérification du cancel existant ; pas de spin, file illimitée ou production anticipée. Si le consommateur disparaît, arrêter puis attendre les preuves de fin. Un canal fermé n’est pas un drain natif.
6. Rendu au plus une fois par frame/batch borné de **tokens déjà réels** ; premier chunk disponible présenté dès la prochaine frame. Préserver l’ordre et le contenu, sans reconstruire les 40 messages à chaque token. Cette coalescence de rendu n’invente aucun texte ni token.
7. Le prototype ne mémorise que la dernière émission pour son idempotence. Une implémentation IPC productive devra qualifier sa fenêtre/replay bornée, ses accusés et ses doublons plus anciens ; ne pas extrapoler ce prototype à un transport fiable.

## Optimisations natives à étudier, pas à appliquer ici

- Réutiliser le scratch de `llama_token_to_piece` au lieu d’allouer un vector à chaque itération, avec contrôles de taille/UTF-8 et contenu strictement identique.
- Éviter le double format/tokenize de measureFixtureP2 puis generateFixtureP2 par une préparation consommable une seule fois, liée à modèle/template/demande/contexte/génération/bail. Ne pas introduire de cache global approximatif ni réutiliser un KV entre sessions non liées.
- Ne pas supprimer le second `load` Kotlin : son no-op protège contre un unload déjà en file, et le backend revalide son état sur le même worker.
- Pas de changement de quantification/modèle, greedy sampler, prompt fixe, limites de contexte/sortie, threads, batch/micro-batch, GPU layers, chauffe ou budgets RAM. Le gain éventuel doit être mesuré avant/après, sur le même modèle et matériel, après approbation.

## Matrice de validation avant accord d’activation

| Domaine | Preuves requises |
|---|---|
| ABI / JNI | Patch exact et préimages, exports anciens inchangés, ARM64/16 Kio, host JNI réel avec modèle volontaire, tests de callback/OOM/exceptions, ASan/UBSan |
| Autorité | Mandat CORE valide, hash de demande, epoch/session/génération/bail exacts ; invalidation et doublons, aucune nouvelle autorité locale |
| Concurrence / arrêt | Stop avant entrée, pendant prefill/decode/callback, tampon saturé, double stop, fermeture client, unload/destroy en erreur, aucune réadmission sans drain/unload réels |
| Correctitude | Identité du texte, Unicode fragmenté, EOG/fin, aucun token après terminal, refus de dépassement et absence de troncature silencieuse |
| Performances | >=20 essais comparables par cellule froid/chaud et caps 8/24, p50/p95/max et échecs, PSS/RSS, thermiques/énergie ; appareil physique NOT_RUN tant que non essayé |

**État : proposition à valider, pas un patch natif approuvé.** Les tests de DTO/queue/horloge n’établissent ni TTFT Android ni sûreté JNI. Aucune installation personnelle, téléchargement automatique de modèle ou activation FULL_POWER n’est incluse.
