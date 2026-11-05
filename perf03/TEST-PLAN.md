# PERF-03 — plan de tests et frontières de preuve

Module : LLM. Dépôt : `bubth720/JARVIS-LLM`. Phase exécutée : **03A**.
Référence : commit `09e278b54b8be58a3d665d2d91de10f8869e40bb`, tree
`975fc71395ac7758678cb5af1b76858ee614c9b2`. Les SHAs d'exécution sont
inscrits par la CI dans ses artefacts : ce document ne s'auto-atteste pas.

## Banc hôte non productif exécuté en 03A

`bash perf03/tests/run.sh` compile un exécutable autonome C++17, puis le même
jeu avec `-fsanitize=address,undefined`, sans charger JNI, Android, llama.cpp,
GGUF ou moteur. Le helper proposé `candidate/native/stream_support.h` est
inclus directement depuis son emplacement de revue. Le patch n'est pas appliqué.
Les sources et le compilateur sont enregistrés dans
`build/perf03/helper-tests/sources.sha256` et `compiler.txt`.

31 cas sont exécutés par variante :

| Ensemble | Cas et assertions |
|---|---|
| UTF-8 réel au niveau du helper | ASCII/TAB/LF/CR, toutes les coupures d'une chaîne multilingue, octet par octet, préfixe incomplet sans texte inventé, codepoint à la limite 4096, limite exacte 64 Kio, dépassement rejeté avant lecture, cumul, séquences invalides/overlong/surrogates, contrôles refusés, suffixe tronqué, terminal, pointeur nul, 1000 fragmentations déterministes. |
| Modèle de contrat isolé | 8 champs de corrélation, ACK et séquences, doublon exact, replay périmé contradictoire, réserve du terminal parmi 16 événements, 16 Kio en attente, retry sans perte, chunk invalide, 64 Kio cumulés. |
| Concurrence et annulation du modèle isolé | Producteur interrompu pendant backpressure, consommateur qui débloque, deadline sans consommation fictive, Stop après terminal producteur, double Stop, terminal CANCELLED, mort de processus et retour tardif. |
| Reçus du modèle isolé | Erreur callback représentée par UNKNOWN, CONTENT_DONE distinct de DRAINED et UNLOADED, CLEANUP_FAILED persistant, 100 identités de génération indépendantes. |

La file `tests/review_queue.h` est un **modèle de référence non productif**.
Elle ne vérifie aucun mandat CORE réel et ne libère aucun bail. Les tests qui
mentionnent callback/processus vérifient ses transitions déclaratives ; ils ne
déclenchent ni exception Java/JNI ni mort de processus Android. Les chaînes de
fixture servent uniquement à vérifier l'intégrité : ce ne sont pas des réponses
de modèle, des tokens réellement échantillonnés ou des mesures TTFT.

Les limites 16 événements / 16 Kio en attente / chunk 4096 / 64 Kio cumulés
sont candidates. Les copies JNI, objets Java, buffers C++, allocateurs, pile et
modèle s'ajoutent : ces valeurs ne constituent aucun budget PSS/RSS qualifié.
La politique candidate refuse explicitement les contrôles ASCII hors TAB/LF/CR
et les flux invalides ; elle ne les supprime pas silencieusement. Sa compatibilité
avec tous les modèles doit être évaluée sur de vrais tokens en 03B.

Localement LeakSanitizer peut être **BLOCKED** par le sandbox exécuté sous
ptrace. `PERF03_DETECT_LEAKS=0 bash perf03/tests/run.sh` est une invocation
explicite distincte : elle conserve ASan/UBSan et marque LSan `NOT_RUN` dans
`sanitizers.json`. Le workflow normal garde `detect_leaks=1`. Aucun retry
automatique ni repli silencieux n'est prévu ; conserver le premier journal
d'échec. Les résultats CI exacts priment sur l'état local.

## Revue Kotlin isolée, sans application du patch

`kotlin-review/src/test/kotlin/fr/jarvis/llm/engine/OwnedStreamBufferTest.kt`
contient 16 tests JUnit du **contrat candidat**, avec un `ResourceCoordinator`
local à chaque fixture et aucun singleton moteur : réserve terminal, bornes en
attente/cumul/chunk, copie immuable, ACK/ordre/doublons/replay, domaines
d'horloge et timestamp du premier token, Stop après terminal, grant étranger,
révocation, hashes préparés/binding, UTF-8, cap exact des tokens et absence de
preuve de drain/unload au retour. Les tests de backend candidat utilisent une
`NativeApi` factice ; aucun appel JNI n'est effectué. Le projet de revue compile
les fichiers candidats à leur emplacement de revue, sans les copier en runtime.
Les nombres et états finaux sont ceux des rapports JUnit/CI correspondants.

Le banc direct `tests/run_kotlin_review.py` peut compiler et exécuter ces tests
avec les dépendances JVM déjà vérifiées du cache, sans socket Gradle ni
téléchargement de modèle. Il ne remplace pas la compilation ARM64, JNI réelle,
Android ou le raccordement CORE. Les résultats historiques réutilisés ne doivent
pas être additionnés plusieurs fois lorsqu'ils sont relancés dans plusieurs
bancs.

## Qualification JNI à préparer, sans application avant GATE-A

| Contrôle | Méthode après accord sur le patch | État 03A |
|---|---|---|
| Préimages / frontières | SHA256, `git apply --check` sans application, garde complet des fichiers historiques | Exécuté par les outils de préservation 03A |
| JNI candidat compilé hôte | Compiler le patch explicitement approuvé, conserver warnings et symboles | BLOCKED GATE-A |
| Anciennes signatures | Comparer exports et appels JVM historiques ancien/candidat | Baseline seulement ; candidat BLOCKED GATE-A |
| JNI exceptions | Callback qui lève avant/après ACK, TOKEN, terminal ; préserver l'exception Java et libérer toutes les refs locales | BLOCKED GATE-A |
| UAF et nettoyage | ASan/UBSan sur appels JNI, stop/prefill/decode/callback, destruction concurrente contrôlée, échec cleanup et quarantaine | BLOCKED GATE-A |
| Unicode natif | Vrais `llama_token_to_piece`, fragmentation complète, absence de préfixe tronqué au terminal | BLOCKED GATE-A et modèle autorisé |
| Identité du texte | Ancien et nouveau chemin, mêmes GGUF/hash, template, prompt, greedy/config ; comparer octets finaux sans trim | NOT_RUN, modèle absent |
| Concurrence propriétaire | Un worker/moteur, bail attesté, rejet second appel, retour tardif, double Stop ; aucun réadmission avant reçu valide | BLOCKED raccordement propriétaire |
| ARM64 / 16 Kio | Build NDK épinglé, alignement ELF et ZIP, symboles historiques | Baseline seulement ; candidat BLOCKED GATE-A |
| Drain / unload | Mesurer réellement retour natif et callbacks ; déchargement séparé avec handle vide et reçu propriétaire | UNKNOWN avant implémentation qualifiée |

`generateStream` retourné ne suffit jamais à conclure UNLOADED. Un terminal
peut rester en attente d'affichage. Stop invalide le texte non rendu, y compris
après un terminal producteur, sans créer de preuve de libération native.

## Préparation 03C

La matrice non exécutée est fournie dans `BENCHMARK-PLAN.json`. Séparer
froid processus, froid modèle et modèle déjà chargé **avec résidence vérifiée**.
Comparer ancien/candidat sur le même appareil, modèle/hash, template/hash,
corpus/hash, paramètres, contexte, threads, profils et cap 8 ou 24. Alternance
des variantes et au moins 20 essais comparables par cellule, tant que les
protections autorisent chaque départ. Repos et arrêt restent décidés par la
politique existante ; ne pas maintenir artificiellement le modèle résident.

Conserver aussi refus, erreurs, Stop et essais incomparables dans les données
brutes. Ne pas compléter une cellule en effaçant des échecs. Aucun p95 sur moins
de 20 essais comparables ; aucune valeur absente remplacée par zéro.

Émulateurs API29/35 : génération réelle si modèle volontairement disponible,
sinon tests d'adaptateur explicitement étiquetés fixtures. Scénarios : normal,
caps 8/24, réponse longue, Stop prefill/decode/callback, rotation, arrière-plan,
force-stop/redémarrage, avion, refus thermique/RAM simulés, erreur native,
événement périmé. Inspecter applicationId distinct, testOnly, manifestes,
permissions, signatures, dépendances et ABI. Une simulation n'est aucune
preuve de performances du LLM.

Appareils POCO X8 Pro / Redmi Pad SE : **NOT_RUN, autorisation personnelle
requise séparément**. Aucun modèle téléchargé ni importé automatiquement.
Licence/hash/adéquation matériels doivent être vérifiés lors d'un import
volontaire. Aucun usage de Termux ou Android Studio demandé à l'utilisateur.

Horloges : monotones, identités corrélées, domaines déclarés. Android
`elapsedRealtimeNanos` et les valeurs natives ne doivent être soustraites que
si le domaine partagé ou une calibration est qualifié. ACK, premier token
échantillonné, premier UTF-8 utilisable et premier affichage réel sont distincts.
Un callback de commit de frame n'atteste pas le temps physique d'émission de
l'écran. Un TOKEN peut ne contenir que des espaces, TAB ou LF : le futur
adaptateur doit attendre un fragment textuel utilisable et non blanc de la
bonne opération avant d'armer le premier affichage. Un TOKEN, ACK ou frame
blanche ne suffit pas. **TTFT réel / p50 / p95 / maximum : NOT_RUN en 03A.**
