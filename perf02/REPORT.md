# PERF-02 — diagnostic vérifiable et instruments non productifs

Mission lue intégralement : `missions/LLM-PERF-TTFT-3S.md`. Dépôt `bubth720/JARVIS-LLM`, module LLM, branche `codex/llm-perf-ttft-3s`, cible PR brouillon `mission/llm-perf-ttft-3s-20261104`. Référence exacte `1b62a5c50eb979d82ef8eb0b488ab38c6fe462ce`, tree `9ca2891928df37963d0f83c5550b94b0d8ae1aae`. Ascendant runtime M50-S1 A2 : `b670181987d39d27e2d0d9574fa448397a38fe42`. Aucune fusion main.

**Objectif premier texte réel affiché < 3 s : NON QUALIFIÉ.** Aucun modèle importé ou téléchargé, aucune génération GGUF, aucune installation personnelle, aucun appareil Android physique testé. Cette livraison achève le banc logiciel et la proposition de la phase A/B ; elle ne constitue pas une qualification matérielle ni une optimisation prouvée du moteur.

## Périmètre et diagnostic

Les sources, contrats et historiques #6/#8 ont été examinés : `LlamaBackend`, `ChatViewModel`, `MainActivity`, `bridge.cpp`, P2 endpoint et client, provenance native/tokenizer, profils et budgets, broker et lifecycle, protocole de qualification et revue native F3B. Aucun AGENTS.md trouvé. Tous les fichiers de la référence, leurs modes et leurs chemins restent gelés par `check.py`, sans exception pour les workflows historiques. Les ajouts PERF Kotlin sont compilés uniquement dans les tests. Le DEX de l’APK main inspecté ne contient aucune classe PERF ni décorateur de mesure.

| Observation dans les sources | Implication et action |
|---|---|
| `LanguageModel.generate` retourne un String complet ; NativeApi retourne un ByteArray complet | Premier token natif UNSUPPORTED. Aucun découpage du résultat final en faux streaming. Proposition native séparée requise. |
| `ChatViewModel` publie la réponse complète ; MainActivity reconstruit le texte de conversation | Le premier affichage attend la fin. Reconstructions potentiellement coûteuses, coût réel non mesuré. Étudier ensuite une mise à jour bornée par frame avec texte authentique. |
| Construction du backend appelle `native.create()` sur le thread appelant | Point potentiellement bloquant ; le décorateur actuel ne chronomètre pas séparément cette construction. Mesure dédiée nécessaire avant changement de lifecycle. |
| Routage catalogue, vérification modèle, décision sécurité et appels load répétés | Séparer leurs durées avant toute suppression. Le second load protège notamment contre un unload déjà en file : conservé. Aucun cache de confiance ajouté. |
| `measureFixtureP2` puis génération refont formatage/tokenization ; boucle native alloue des pièces | Candidats à profilage et réutilisation bornée, sans preuve de gain ici. Aucun patch natif appliqué, aucune réutilisation KV/prompt non validée. |
| Autorité CORE et transport live non raccordés ; fixtures synthétiques | CORE_ACCEPT UNSUPPORTED ; ACK/statut/écran prêt ne sont jamais du texte modèle. Aucun changement CORE/INTERFACE. |

La file autorise aussi Stop après un terminal producteur déjà en attente : elle supprime le texte non rendu pour succès, annulation et erreur, sans fabriquer un drain/unload. Test ajouté et exécuté. Les corrections isolées concernent les nouveaux instruments : première émission conservée, gestion des erreurs d’horloge sans casser stop/le délégué, refus des identités périmées et du rendu tardif, bornes mémoire, doublons et UTF-8 fragmenté. Aucune accélération en production n’est revendiquée. Modèle, qualité, contexte, threads, idle unload et protections sont inchangés.

## Mesures et tests locaux du 4 novembre 2026

[Données brutes locales](LOCAL-RESULTS.json) : SHA256 de chaque source testée, identités des nouveaux tests, APK et limites de provenance. Baseline et variante exécutées dans le même workspace/hôte, même JDK/SDK/NDK vérifiés et source llama épinglée ; aucune mesure génération avant/après disponible. La baseline de code runtime est intacte. Les ajouts étaient présents mais non compilés pendant le build baseline. Le dernier rerun domain inclut la correction Stop après terminal, app/endpoint sont conservés de leurs runs précédents. Une première tentative Gradle a été bloquée avant les tests par les sockets du sandbox ; le rerun autorisant les sockets a réussi. Ce relevé local est un **worktree**, sans marqueur frais de commit : il ne prétend pas être une attestation de build propre.

| Vérification | Référence | Variante | Résultat |
|---|---:|---:|---|
| JVM domain | 145 | 190 | PASS, +45 |
| JVM app | 40 | 49 | PASS, +9 |
| JVM endpoint | 118 | 118 | PASS |
| Total JVM | 303 | 357 | 0 échec, erreur ou skip ; tous les cas historiques conservés |
| Python PERF | — | 34 | PASS |
| Python historique m50/prefusion/fusion02 | 38 | 38 | PASS |
| CTest native_safety hôte | — | 1 | PASS ; aucun GGUF/JNI réel |
| Recovery contract/vectors, correction vectors, vérificateur APK | — | exécutés | PASS |
| Revue patch natif F3B | — | 7 préimages | PASS en dry-run ; patch non appliqué |
| ARM64 app/endpoint/clients, lint app/endpoint/client | build baseline | runtime inchangé | PASS ; APK et frontières contrôlés séparément |

Tests ajoutés : concurrence/double émission, Stop en cours de travail et maintien des ressources jusqu’au retour réel, erreur backend/horloge, absence de token, timestamps désordonnés, stale epoch/génération, terminal/process death, capacité et dépassement du budget de file, backpressure sans perte, UTF-8 incomplet, JSONL hostile/borné et comparaison non comparable. Les dépassements sont des tests de limites logicielles ; aucun OOM Android réel, aucune saturation PSS physique ou course JNI réelle n’a été provoqué.

Microbenchmark **JVM hôte du collecteur seulement**, horloge System.nanoTime, JIT préparé, 40 échantillons/lane, rang le plus proche, 100 cycles/échantillon. Valeurs en ns/cycle, dernier run local :

| Marqueurs doublons/cycle | Contrôle p50 / p95 / max | Collecteur p50 / p95 / max |
|---|---|---|
| 16 | 82 / 287 / 310 | 1 363 / 3 099 / 3 155 |
| 128 | 200 / 495 / 2 525 | 1 725 / 3 056 / 3 351 |

Le contrôle est une boucle sans observateur, pas un moteur LLM baseline. JIT, bruit et outliers interdisent toute extrapolation Android ou promesse de gain TTFT. Ce test mesure un coût d’instrumentation, pas une accélération.

| Mesure de génération | Froid processus | Froid modèle | Déjà chargé |
|---|---|---|---|
| TTFT réel UI→premier texte affiché, p50/p95/max | NOT_RUN | NOT_RUN | NOT_RUN |
| Réponse complète caps 8/24, p50/p95/max | NOT_RUN | NOT_RUN | NOT_RUN |
| Chargement natif réel, RSS/PSS, chauffe, énergie | NOT_RUN | NOT_RUN | NOT_RUN |
| Premier token JNI / acceptation CORE live | UNSUPPORTED | UNSUPPORTED | UNSUPPORTED |
| Comptes tokenizer exacts / drain / unload attestés | UNKNOWN | UNKNOWN | UNKNOWN |

Android physique et rendu réel : **NOT_RUN**. Les ports de rendu JVM et FakeNative sont SYNTHETIC, explicitement exclus de toute qualification. Aucun temps manquant n’est remplacé par 0.

## Reproductibilité et garde historique

[Protocole détaillé](PROTOCOL.md), [matrice planifiée](MEASUREMENT-TEMPLATE.json), [commandes](README.md). La CI PERF-02 reconstruit d’abord la référence propre puis le commit candidat exact sur le même runner, avec `--rerun-tasks`. Elle vérifie les 303 identités historiques, lie marqueur/commit/SHA256/fraîcheur des XML, inspecte le DEX et publie `build/perf02/results.json`, XML et rapports. Le SHA exact et tree de publication sont ceux de la PR ; les artefacts sont nommés avec ce SHA. Le résultat de cette CI doit être lu séparément des données locales ci-dessus, sans transformer une configuration de workflow en test exécuté.

Les gardes anciens restent inchangés et sont exécutés à leurs SHAs d’origine via `check.py --historical`. À la référence PERF comme au candidat, exécuter directement `tools/check-preservation-003e.py` retourne **1**, même message `Protected baseline drift: app/src/main/AndroidManifest.xml`. Cela reproduit un échec hérité de périmètre, pas un succès du garde ni une nouvelle régression démontrée. Le workflow Android historique reste intact et peut donc rester rouge pour cette raison. Le garde PERF impose en complément l’identité exacte de **tous** les fichiers historiques. Aucun contournement ni modification de reçu historique.

## Streaming et prochaines décisions

[Proposition JNI distincte](NATIVE-STREAMING-PROPOSAL.md), NON APPLIQUÉE. Préférence à évaluer : callback synchrone sur l’unique worker, reçu terminal distinct du drain/unload, liaison request/session/generation/epoch/bail/SHA, backpressure interruptible. Prototype de revue uniquement : 16 slots, 16 KiB pending, 4 KiB/chunk, 64 KiB cumulés, séquences et UTF-8 stricts. Ces bornes ne remplacent pas un budget heap/PSS mesuré. Pas de nouvelle ABI/export, bibliothèque native, scheduler, moteur ou worker.

Mode déterministe local proposé pour « aide », « état du modèle », « annuler » : routage explicite vers commandes déjà autorisées, provenance `DETERMINISTIC_LOCAL`, validation du mandat et des protections existantes, aucune affirmation générative. « Annuler » délègue au stop existant sans inventer de reçu de libération. Ce mode n’est pas ajouté au runtime et reste exclu du SLA modèle. Ne pas détourner une question libre vers une réponse prédéfinie.

| Profil | Seuils existants conservés | Décision actuelle |
|---|---|---|
| MOBILE_ECO / TABLET_ECO | ARM64, foreground, capteurs valides, batterie ≥10 %, thermalStatus <3, headroom <1 si disponible ; marge mémoire SafetyPolicy ≥0. SMALL budget estimé 2 Gio, contexte/threads inchangés | NO-GO qualification <3 s ; campagne réelle requise |
| ÉQUILIBRÉ | Mêmes refus ; MAIN budget estimé 4 Gio si admissible, sinon repli SMALL existant ; réserve max(1 Gio, 20 % RAM totale, seuil LM Android +256 Mio), crédit PSS existant borné | NO-GO qualification <3 s ; aucune hausse de budget |
| FULL_POWER | Refus existant, matériel mobile non qualifié | NO-GO, inchangé |

Ordre proposé : revue des instruments et du contrat candidat ; validation explicite d’un **patch natif exact séparé** et des contrats CORE/INTERFACE ; qualification tokenizer/clock/lifecycle JNI ; tests natifs et injection de fautes ; puis campagne autorisée avec vrai modèle et matériel cible, ≥20 tentatives/cellule, froid/chaud séparés et caps8/24. Sans ces étapes, pas de GO utilisateur. Pas de téléchargement automatique ni d’installation personnelle.

Rollback : abandon/revert des seuls ajouts PERF et de leur nouveau workflow. Aucun manuscrit, store, poids, contrat, permission ou code de production n’a été modifié. Pour toute future modification native, un revert ne prouvera jamais un drain/unload.
