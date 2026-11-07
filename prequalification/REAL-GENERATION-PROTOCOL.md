# Protocole préparé — génération réelle NOT_RUN

Référence logicielle059cf9c3 ; patch autorisé747598dd… inchangé. Aucun téléchargement ni appel génératif n'est exécuté par ce document. L'accord sur le modèle précède son obtention. Le raccordement CORE/INTERFACE est une décision distincte.

## Admission du laboratoire

Après autorisation seulement : récupérer l'unique fichier/révision du manifeste, comparer taille puis SHA256 avant toute ouverture, conserver licence/notices, refuser tout écart sans repli automatique. Contrôler les vraies métadonnées GGUF via les gardes existants, Qwen3/SMALL/Q4_K_M, nombre de tenseurs/éléments, template et contexte. Aucune modification native pour faire accepter un modèle incompatible. Mesurer mémoire disponible ; budget SMALL existant2Gio, contexte512, thread1 et caps8/24, jamais relevés. Refus/erreurs/arrêts conservés. Un essai hôte n'atteste pas des capteurs Android ni l'admissibilité d'un mobile.

Un seul propriétaire et handle/worker de génération ; aucune double résidence. Les appels JNI de laboratoire sont distingués d'un mandat CORE : un harness autorisé n'est ni un broker global ni une intégration live. L'autorité Kotlin/CORE ne doit pas être remplacée par des UUID et des hashes fabriqués pour déclarer une intégration réussie. Si les conditions d'autorité de la composition choisie ne sont pas attestables, marquer cette cellule BLOCKED, sans changer le patch.

## Comparaison fonctionnelle avant statistiques

1. Sur le même binaire appliqué et le même handle/configuration, comparer l'ancienne entrée `generateFixtureP2` et `generateStream`, chacune armée explicitement par begin. Les deux utilisent un seul message user et le même système P2 **non préfixé** ; ne pas envoyer un SYSTEM déjà recomposé par P2Planner. Mesurer le rendu/template/tokenizer exact et les identités avant chaque essai.
2. Même fichier/SHA, contexte, greedy sampler, cap, threads, template et prompts publics figés. La mémoire KV est remise à zéro par le runtime ; poids chauds ne signifient pas KV chaud. Conserver les load idempotents. Alterner les variantes sans maintenir artificiellement la résidence au-delà de l'idle normal.
3. Collecter les vrais octets des chunks reçus pendant l'appel JNI, ordre strict et terminal corrélés ; concaténer sans trim et comparer aux octets du résultat complet historique. Aucun découpage du résultat final en faux tokens. Ne jamais remplacer du texte invalide par U+FFFD pour réussir l'identité.
4. Corpus public comprenant français accentué, caractères multioctets et écritures variées. Vérifier UTF-8 final strict, carries natifs incomplets, chunks4096, total64Kio, file16événements/16Kio, terminal réservé et compte de tokens réel distinct du cap. Si le modèle ne produit pas un cas Unicode particulier, ne pas prétendre l'avoir couvert ; garder les tests de composants séparés.
5. Annuler séparément avant entrée, pendant prefill réellement observé, décodage et callback. Si une phase n'a pas été observée, conserver NOT_RUN ; ne pas compter une simple temporisation comme preuve de cette phase. Tester double Stop et refus de cleanup/handle concurrent ; aucun token accepté après annulation/terminal/ancienne génération. Préserver le texte déjà affiché comme partiel, invalider le pending.
6. Backpressure : consommateur réellement ralenti ou momentanément suspendu, bornes observées, séquence inchangée lors de RETRY, reprise sans perte/duplication, puis annulation pendant attente. Respecter deadline existante ; ne pas ajouter un autre moteur ni worker. Ne pas assimiler un retour de callback à DRAINED/UNLOADED.
7. Nettoyage après vraie résidence : retour JNI, vidage des callbacks/files, unload, fermeture des baux et invalidation du handle séparément. Échec/absence de retour => UNKNOWN/CLEANUP_FAILED, arrêt de qualification, pas de remplacement automatique. Préserver bibliothèque, commit/tree, signal, hs_err et logs sans données personnelles ; aucun retry destiné à obtenir PASS.

## Mesures reproductibles

Deux variantes, trois états séparés PROCESS_COLD/MODEL_COLD/WARM_VERIFIED, caps8/24 ;20 tentatives comparables minimum par cellule ouverte, dans les protections. Un changement du cap peut provoquer une recharge via RuntimeConfig : ce cas ne devient pas warm par déclaration. Mêmes hashes de modèle/template/corpus/configuration et même environnement pour avant/après ; ordre alterné et repos normal, sans boucle matérielle sans surveillance. Conserver refus, Stop et erreurs dans le dénominateur.

Mesurer chargement, dispatch/préparation/prefill, premier sampling non-EOG, premier UTF-8 complet **non blanc**, premier callback reçu, réponse complète et débit observé, RSS/PSS/pic/CPU/thermique/batterie/énergie lorsque disponibles. Sur hôte, mesurer les durées Java avec la même nanoTime et les durées natives avec CLOCK_MONOTONIC séparément ; ne pas soustraire leurs origines sans calibration. Sur Android, vérifier boot et domaine CLOCK_BOOTTIME/elapsedRealtimeNanos avant corrélation. Un premier callback n'est pas une première frame Android.

p50/p95/max au rang le plus proche, n comparable exact ; aucun p95 si n<20. Rien n'est remplacé par zéro. Aucun TTFT CORE→INTERFACE tant que la chaîne réelle et le commit de frame de vrai texte ne sont pas qualifiés. Pas de prompt/réponse privés, chemin modèle, mandat brut ou credential de bail dans les traces. Les comparaisons de contenu publiques peuvent être conservées comme fixtures explicites dans un artefact de laboratoire séparé.

État courant : téléchargement, génération, identité ancien/nouveau, annulation en prefill/decode, backpressure de vrais tokens, mesures et objectifp95<3s **NOT_RUN/NOT_QUALIFIED**. Un succès d'émulateur model-free ne remplit aucune de ces cases et ne franchit pas GATE-B.
