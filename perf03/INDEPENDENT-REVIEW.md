# PERF-03A — revue indépendante du candidat

Module LLM, mission PERF-03A, dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03a-native-review`. Référence : `09e278b54b8be58a3d665d2d91de10f8869e40bb`, tree `975fc71395ac7758678cb5af1b76858ee614c9b2`.

Cette revue porte sur les copies hors runtime de `bridge.cpp`, `stream_support.h`, `LlamaBackend.kt`, `OwnedStreamContract.kt` et leurs tests. Le diff relu après corrections a pour SHA256 **`747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460`**. Aucun patch n'a été appliqué et aucun JNI modifié n'a été exécuté par cette revue. Les résultats exécutés de compilation et de tests sont ceux des rapports de CI liés au commit exact ; cette lecture ne constitue pas une campagne native supplémentaire.

## Constats corrigés avant publication

| Constat de revue | Correction dans le candidat | Test ciblé |
|---|---|---|
| Un entier natif `COMPLETED` pouvait produire un succès sans événement terminal accepté et sans préparation validée. | `generateOwnedStream` exige le terminal exact du tampon ; un succès exige aussi la préparation canonique du callback. | `returnedCompletedWithoutAcceptedCallbackCannotProduceSuccess` |
| Un retour natif contradictoire avec le terminal accepté pouvait laisser `CONTENT_DONE` et un `COMPLETED` encore consommable dans la file. | L'exception invalide la projection : état `UNKNOWN`, suppression du texte non rendu et du reçu terminal, puis propagation de l'erreur. | `nativeStatusMustMatchTheExactAcceptedTerminal` |
| Une exception de callback ou une utilisation tardive pouvait laisser une file ouverte après la fin de l'invocation. | Erreur de callback/invocation persistante dans le tampon ; fermeture monotone du callback dans `finally` ; refus des offres après fin. Un callback tardif rejeté ne réécrit pas un résultat déjà établi. | `callbackExceptionClearsUnrenderedTextAndPermanentlyMarksUnknown`, `callbackCannotEmitLateTextAfterActualInvocationFinishes` |

Les corrections ont été relues dans les postimages et sont couvertes par les cas ajoutés. Le texte déjà affiché reste la responsabilité de l'adaptateur UI futur, qui devra le marquer partiel selon sa politique d'annulation.

## Lecture des protections proposées

Le registre natif utilise des IDs opaques monotones et des pins exclusifs pour empêcher un destroy/unload pendant un appel sensible ; le cancel garde une courte section protégée sur son drapeau atomique. Aucun callback ni calcul llama.cpp n'est exécuté sous le verrou du registre. Le callback JNI est une référence locale enracinée pendant l'appel synchrone uniquement, sans nouveau thread ni conservation asynchrone. Les références temporaires du nouveau callback sont encadrées par des frames locales et une exception Java en attente empêche tout callback supplémentaire.

Le premier marqueur natif est placé après le sampling non-EOG réel, avant conversion en pièce ; il ne provient pas d'un résultat final découpé. L'assembleur UTF-8 refuse les préfixes non canoniques, conserve au plus trois octets incomplets et n'invente aucun caractère de remplacement. La reprise de backpressure conserve payload et séquence, attend sans verrou d'ownership et recontrôle l'annulation existante.

Ces propriétés sont des observations de code à vérifier sur le binaire candidat après GATE-A, pas une preuve d'absence d'UAF, de deadlock, d'OOM ou d'erreur JNI.

## Limites qui restent ouvertes

- L'autorité CORE/broker concrète et le lien attesté avec le modèle réel restent **BLOCKED**. L'identité du `ResourceLease` local, les UUID et les empreintes déclarées n'établissent pas cette autorité.
- Le candidat est limité au P2 texte seul. Les historiques multitours, documents et contrats productifs CORE/INTERFACE ne sont pas raccordés ; les voies historiques restent disponibles.
- Les refus des contrôles ASCII du chemin streaming peuvent refuser certaines sorties historiquement retournables. L'identité du texte ancien/nouveau sur le même modèle reste **NOT_RUN**.
- Les bornes de 16 événements, 16 Kio en attente, 4096 octets par chunk et 64 Kio cumulés sont logiques. Elles ne qualifient pas le coût du tas JVM, des copies JNI, du scratch natif ni la PSS/RSS Android.
- Un TOKEN peut ne contenir que des espaces ou être précédé d'un token vide/incomplet. Le futur marqueur de premier affichage doit identifier du texte modèle réellement utilisable de la bonne opération, pas ACK, statut, frame blanche ou simple affectation de vue.
- Le retour de l'invocation ne prouve jamais `DRAINED` ou `UNLOADED`. Les tests JNI hôte réels, les sanitizers du bridge modifié, ARM64 appliqué, Android API29/35 et matériel physique restent distincts des tests isolés ; aucun p95 de génération n'est qualifié ici.

Décision proposée : dossier disponible pour l'examen **GATE-A** du diff exact, avec application exclusivement en laboratoire sur la branche 03B après accord explicite. Aucun feu vert à une intégration live, une fusion ou une campagne personnelle ne découle de cette revue.
