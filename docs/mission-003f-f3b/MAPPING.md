# Sources et transitions proposées

Ce mapping décrit le patch **non appliqué**. Les tests n'exécutent que les candidats
F3Trace/F3NativeApi, avec des événements synthétiques et un NativeApi factice.
Aucun tableau ci-dessous ne constitue un résultat JNI ou Android observé.

| Observation | Source proposée | Interprétation autorisée |
|---|---|---|
| Appel JNI actif | Token ouvert immédiatement avant l'appel delegate synchrone | Travail potentiellement actif, pas de fin sur annulation/deadline |
| Appel JNI terminé | Token fermé seulement après le retour ou l'exception rendue par delegate | Séquence callEndSequence progresse ; erreur retournée distincte d'un crash sans retour |
| Tâche en file | Ticket avant withContext(worker) | Inclut attente de load/generate/unload/close ; pas seulement generating |
| Tâche active | started dans le vrai corps worker, puis taskReturned dans son finally synchrone | Pas le finally de la coroutine appelante ; abandon avant entrée conserve UNKNOWN |
| Import/vérification actifs | Token avant le bloc synchrone .use, fermeture après retour et fermeture des flux | Distinct de JNI ; après exception I/O les use ont rendu la main, aucun unload supposé |
| Pointeurs modèle/contexte | Sonde C++ sur le même Engine, worker + handleLock | Flags indépendants ; sonde absente/fausse/incompatible = inconnus |
| Handle détruit | Retour réussi de NativeApi.destroy pour cet owner/trace | Jamais backend==null, nouveau PID/process, Binder mort ou compteurs seuls |
| Baux détenus | coordinator.observation sous son vrai verrou | Epoch + ID monotone + owner + classe/requête ; pas de nouvelle réservation |
| Baux relâchés | Disparition de ces mêmes IDs après chemin propriétaire existant | Révision empêche l'ABA acquisition/libération intermédiaire ; pas compteur inventé |
| STOP | entered(CANCEL) | STOP_REQUESTED : intention, y compris cancel rendu sans fin de generate |
| DRAINED | Aucune tâche/file/import/JNI actif, fin connue, sources pointeurs et baux connues | Peut garder modèle/contexte/baux : aucune libération native supposée |
| UNLOADED | Drain + séquence unload/destroy rendue + modèle/contexte réellement vides + zéro baux | Handle Engine vide peut rester OWNED ; handle EMPTY exige événement destroy réel |
| UNKNOWN | Source absente, sonde ratée, incohérence de révision, abandon avant worker, mauvais worker, naissance incomplète | Bloquant ; pas de régularisation par timeout, restart vide ou reset de journal |
| CLEANUP_FAILED | Exception rendue par unload/destroy | Ancien bail reste sous propriétaire existant ; retry explicite, même owner, après validation |

L'inspection ne compte pas sa propre tâche INSPECT comme travail lourd. Les appels
PROBE restent suivis comme appels JNI et rendent la main avant le snapshot. Les
compteurs d'activités F3A ne deviennent pas automatiquement active_native_calls.
Les révisions de trace et d'admission/barrière sont deux domaines distincts : le
mapping final doit garder leurs identités et ne pas les remplacer par un maximum.

## Liaison avec F3A et CORE

La proposition lit des données process-local brutes ; elle n'implémente pas encore
le port positif ObservationPort de F3A. Pour construire Observation : vérifier la
même incarnation, le registre propriétaire complet et scellé, le freeze de toutes
les entrées, le worker/owner exact, la clé complète attendue et les baux exacts.
Comparer le digest local au Request effectivement autorisé et conservé par l'hôte ;
un hash transmis par un client n'authentifie rien. Les neuf champs de clé, le
request_digest et pilot_id ne doivent jamais être reconstruits depuis des zéros.

Les IDs du coordinateur interne (epoch,id) ne sont pas les lease_id/generation CORE.
Une future liaison explicite doit associer les deux sans confondre leurs propriétaires.
ProcessResources n'est pas un arbitre entre APK. Aucun résultat COORD-001 n'est présumé.
Le snapshot doit être revalidé sous barrière jusqu'au reçu durable ; cette proposition
ne livre pas ce reçu et n'écrit pas dans RecoveryJournal. ReviewJournal F3A reste
review-only. Sans couverture exhaustive ou opération ancienne sans reçu : UNKNOWN.

Ni F3Trace.classify ni F3BackendObservation.coherent ne signifient que le processus
entier ou Android est qualifié. La cohérence brute est locale à l'échantillon ; elle
ne garantit pas seule sa stabilité jusqu'à un callback ni la vérité d'un pair CORE.
La révision native/worker doit être associée à la révision du gate au moment du futur
snapshot global ; ce travail reste fermé avec le port positif.
