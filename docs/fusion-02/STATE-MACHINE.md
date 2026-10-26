# L1 — machine d'états du client, TEST-ONLY

Contrat préservé : `jarvis.llm.client/1`. Aucun état ci-dessous n'est une preuve JNI,
ni une modification du contrat partagé UNIFIED. Le moniteur est une projection
RAM par requête ; il ne gère pas de bail ni de journal crash-safe.

| État courant | Entrée exacte avant deadline | Résultat |
|---|---|---|
| WAITING | Ack0 / FIXTURE en banc explicite | STREAMING, aucun contenu ni admission native |
| WAITING | Error0 avec origine admise | REFUSED |
| STREAMING | Chunk suivant, origine correcte, bornes respectées | STREAMING, contenu ajouté une fois |
| STREAMING | Completed suivant | CONTENT_DONE, jamais NATIVE_DONE/UNLOADED |
| WAITING/STREAMING | Cancel exact | STOP_REQUESTED, contenu effacé |
| STOP_REQUESTED | Cancel exact répété | DUPLICATE, état conservé |
| WAITING/STREAMING | Événement identique déjà accepté | DUPLICATE, pas d'ajout de contenu ni extension du délai |
| WAITING/STREAMING | Mauvaise origine/digest, séquence sautée ou conflit | UNKNOWN, contenu effacé |
| WAITING/STREAMING/STOP_REQUESTED | Deadline atteinte ou horloge régressive | UNKNOWN |
| Tout état | OperationKey ancienne/divergente | STALE, aucune modification de la requête courante |
| Tout état | Cancel avec référence différente | STALE, aucune interruption de la requête courante |
| CONTENT_DONE/REFUSED/UNKNOWN | Nouvel événement/cancel exact | TERMINAL, état conservé |
| STOP_REQUESTED | Événement tardif avant expiration | TERMINAL, aucun contenu réintroduit |

Le mode live accepte uniquement un Error d'origine UNAVAILABLE. Il refuse les ACK
et textes de fixture, même valides. La factory fixture est explicitement signalée
« FIXTURE — sans moteur LLM » ; le producteur simulé est dans `src/test` seulement.

NativeResourceState(observation=null) signifie **observation inconnue**, pas absence
native. termination/unload restent UNATTESTED et mayAdmitAnotherEngine=false dans
tous les états. Un broker EMPTY_UNATTESTED n'est pas UNLOADED. Un nouveau moniteur
ne peut établir aucune preuve concernant l'ancien processus ou son bail.

## Banc étendu

18 nouveaux tests JUnit couvrent les doubles ACK, délais non renouvelables, huit
composantes d'identité divergentes, annulation exacte, changement d'incarnation,
refus après révocation, modèle/permission de fixture absents, UTF-8 multioctet,
origine incorrecte, refus terminal et trois courses avec barrières explicites.
Les timeouts de5s des Futures sont des limites de test : leur expiration fait
échouer le test, elle ne confirme jamais une libération native.

Un test explore les4096séquences de quatre actions parmi huit : ACK, chunk,
completion, cancel, expiration, ancien événement, erreur et digest contradictoire.
Les invariants vérifient l'absence de preuve native positive, les états terminaux
absorbants et l'absence de texte après arrêt/refus/incertitude. Les six états sont
atteints. Ces4096traces sont **un seul cas JUnit**, pas4096tests matériels ni des
mesures de performance. Le reçu généré ne contient aucun texte, modèle ou identifiant.
