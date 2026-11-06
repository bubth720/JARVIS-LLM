# PERF-03B → PERF-03C — préparation des mesures

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03b-native-stream`, base `ca9d8f060856e134d793d0c23764fae45ce2cc53`. Le [plan PERF-03A](../perf03/BENCHMARK-PLAN.json) et le [protocole PERF-02](../perf02/PROTOCOL.md) sont conservés ; ce document précise leur statut après l'accord GATE-A.

**Aucune campagne de génération ni de matériel n'est lancée par ce plan. TTFT réel, débit et comparaison avant/après : NOT_RUN. Cible p95<3000ms : NOT_QUALIFIED.** Aucun fichier GGUF n'a été trouvé dans le workspace ; aucun modèle ne sera téléchargé ou embarqué automatiquement. GATE-B et l'autorisation personnelle restent distincts.

## Conditions d'ouverture d'une cellule

Vérifier avant chaque essai : commit/tree/APK et bibliothèque exacts ; modèle déjà disponible et explicitement autorisé, licence/SHA256/adéquation matérielle ; import volontaire et copie vérifiée ; owner/grant/bail valides ; préparation/template/tokenizer identiques ; profil réellement admis ; capteurs et protections valides. L'absence d'une de ces preuves ferme la cellule avec BLOCKED/NOT_RUN, sans repli vers une fixture présentée comme modèle.

L'autorisation d'appliquer le JNI ne fournit pas automatiquement un mandat CORE ou un adaptateur INTERFACE. Un benchmark LLM local pourra être qualifié comme tel uniquement si son propriétaire de laboratoire est explicitement autorisé et vérifiable. Il ne sera pas appelé TTFT de l'intégration CORE–INTERFACE tant que ces frontières ne sont pas raccordées et qualifiées.

## Variantes et matrice

| Axe | Valeurs séparées |
|---|---|
| Variante | Chemin historique complet à la base approuvée ; nouveau chemin streaming appliqué |
| Résidence | PROCESS_COLD ; MODEL_COLD ; MODEL_RESIDENT_VERIFIED |
| Cap demandé |8 et24 tokens ; conserver le nombre réellement généré séparément |
| POCO X8 Pro | ECO ; BALANCED uniquement si la politique l'admet |
| Redmi Pad SE | TABLET_ECO ; repli propre si ressources insuffisantes |
| Android émulé | API29 etAPI35 compatibles avec ABI de laboratoire ; résultats fonctionnels séparés du matériel |

Chaque cellule conserve le même appareil/build Android, modèle/hash, template/hash, corpus synthétique/hash, paramètres déterministes, contexte, threads, budgets, profil et conditions de sûreté. Ne pas comparer un modèle différent, un cap différent ou une recharge effective à un état warm. La résidence vérifiée ne se déduit pas d'une simple sélection modèle ou d'un égalité de configuration.

Prévoir au moins20 essais comparables par cellule réellement ouverte. Alterner les variantes pour limiter les biais, conserver repos et idle unload normaux, et arrêter lorsque les protections demandent de s'arrêter. Aucune boucle sans contrôle thermique, aucun modèle artificiellement maintenu résident, aucun retry automatique pour effacer un mauvais résultat.

Les refus, annulations, erreurs et essais incomparables restent dans les données brutes et les comptes de tentatives. Ils ne deviennent pas des latences nulles. Les percentiles des succès comparables indiquent leur `n` exact à côté des taux d'erreurs/refus ; n<20 interdit un p95 publié. Aucun mélange de cellules pour atteindre artificiellement ce minimum.

## Marqueurs et calculs

| Marqueur | Événement réel requis |
|---|---|
| Envoi validé UI | Validation effective de l'opération, avant la chaîne admise |
| Acceptation CORE | Mandat réellement accepté pour la même requête/session/époque/génération/bail ; UNSUPPORTED si non raccordé |
| Entrée moteur/JNI | Appel effectivement entré ; distinguer dispatch, load et préparation |
| Premier token natif | Premier token non-EOG réellement échantillonné par llama.cpp, même incarnation |
| Premier texte utilisable | Premier UTF-8 complet non blanc issu de ce flux ; exclure ACK, statut, catalogue et fragment incomplet |
| Premier affichage Android | Commit de frame correspondant au vrai texte et à la bonne opération ; affecter TextView seul ne suffit pas |
| Contenu complet | Terminal accepté et texte intégral connu ; pas DRAINED/UNLOADED |
| Retour JNI / drain / unload | Observations séparées et corrélées, pas déduites d'un timeout ou d'une annulation |

Android utilise `elapsedRealtimeNanos` et le natif proposé CLOCK_BOOTTIME ; le domaine/boot/corrélation doivent être vérifiés avant soustraction. L'hôte CLOCK_MONOTONIC/JVM nanoTime est mesuré séparément. Ne jamais soustraire arbitrairement deux horloges ou deux processus/boots. Les traces ne contiennent ni prompt, réponse, chemin privé, mandat brut ou identifiant de bail réutilisable.

Calculer p50/p95/max par rang le plus proche, durée de chargement séparée, réponse complète p50/p95, vrai débit tokens/s avec périmètre de durée explicite. Les caps8/24 ne sont pas des comptes observés. Conserver aussi PSS/RSS/pic, CPU, thermique, batterie et énergie lorsque mesurables ; sinon `null` avec UNKNOWN/NOT_RUN. Ne pas additionner buffers llama aux PSS comme s'ils étaient indépendants, ni déduire l'énergie d'un simple pourcentage batterie.

## Scénarios Android à préparer

API29/35 : démarrage/fermeture, demande normale, caps8/24 et longue réponse, Stop avant entrée/prefill/decode/callback, consommateur ralenti, rotation, arrière-plan, force-stop/redémarrage, avion, refus thermique/RAM simulés, erreur native, retour périmé. Sans modèle, seuls les parcours de contrôle et de cycle de vie explicitement marqués model-free sont exécutables ; ne pas faire produire de faux TOKEN par un String final.

APK indépendante `testOnly`, applicationId distinct, permissions/signatures/ABI/16Kio et dépendances inspectées. Une campagne physique ne commence qu'après le second accord explicite avec APK/SHA256, licence/hash du modèle et procédure utilisateur simple ; aucun Termux ou Android Studio requis. FULL_POWER reste désactivé sur matériel non qualifié.

## Décisions futures

Une optimisation additionnelle est un changement isolé : référence/commit, même environnement, durée avant/après, mémoire, CPU, erreurs et décision conserver/annuler. Le patch approuvé ne peut être modifié sans nouvelle validation. Le streaming peut rendre le premier texte visible plus tôt sans accélérer le calcul ; aucune amélioration n'est revendiquée tant que les mesures comparables ne l'établissent pas.

Le succès sur POCO ne qualifie pas la tablette. Un résultat matériel sûr mais p95>3000ms sur une configuration demeure PARTIAL pour l'objectif de performance ; aucun seuil de sécurité n'est déplacé pour obtenir PASS.
