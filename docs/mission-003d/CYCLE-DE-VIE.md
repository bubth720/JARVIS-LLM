# Modèle de cycle de vie P2 — pure contract, pas télémétrie JNI

`P2Lifecycle` est un modèle opt-in utilisé par les tests. Il reçoit le `ResourceCoordinator` existant, un propriétaire, un epoch destinataire et une horloge monotone locale non négative. **Il n'est pas branché au backend** : les méthodes de confirmation sont des entrées d'évidence à alimenter plus tard par le vrai worker, pas des preuves physiques par leur seul nom. Aucun snapshot n'est publié comme observation native réelle.

## Séquence et états observables

1. `prepare(request, admitted, authorization)` : autorisation exacte, admission locale et configuration compatibles ; epoch/génération/opération vérifiés ; bail lourd acquis. PREPARED, aucun appel natif commencé.
2. `beginLoad` : bail modèle acquis **avant** un chargement susceptible d'allouer ; LOADING, nativeCallActive et nativeEverStarted. Dans un futur adaptateur, vérifier SHA et gardes dans le pipeline unique.
3. `modelReady` : preuve attendue du retour du chargement, READY ; si arrêt demandé, NATIVE_DONE/CANCELLED à la place. Les buffers peuvent rester résidents, les baux restent détenus.
4. Après chargement, le planner peut utiliser le template/vocabulaire **réels**. `beginGeneration(key, prepared)` exige le même objet requête immuable, sa configuration et son budget ; RUNNING. Cette séparation évite d'exiger une tokenisation du modèle avant sa réservation/son chargement.
5. `requestStop` : idempotent pour la clé complète. Premier appel pendant nativeCallActive retourne true pour indiquer qu'un futur adaptateur doit appeler le vrai requestStop ; STOP_REQUESTED, **aucun bail libéré**. L'appel ne signale pas lui-même le JNI.
6. `nativeReturned` : callback de retour natif attendu, NATIVE_DONE. Un résultat reçu après stop/expiration est écarté. Le retour ne signifie pas déchargement.
7. `beginCleanup` → UNLOADING, seulement après fin effective/non-démarrage établi. `cleanupFailed` → CLEANUP_FAILED, texte effacé, baux conservés, nouvelles opérations/close bloqués. Un retry de nettoyage doit être **explicite**, jamais une libération par délai.
8. `confirmUnloaded` → UNLOADED, uniquement après retour réel unload/destroy dans l'adaptateur futur ; fermeture des baux modèle puis lourd. `takeResult` livre une fois le texte ou l'erreur, avec la même clé, seulement après cette confirmation. Un résultat devenu tardif à la livraison est EXPIRED.
9. `close` seulement hors travail/résidence, état CLOSED ; pas de close « réussi » pendant un nettoyage incertain.

Le modèle conserve volontairement le bail lourd jusqu'au déchargement, plus strictement que le backend autonome actuel qui finit son bail lourd au retour de load/generate tout en gardant la résidence. **Ne pas entourer LlamaBackend d'une seconde acquisition par P2Lifecycle** : cela ferait une double acquisition/refus. Le raccordement futur devra alimenter ce contrat depuis les **baux et le worker déjà existants**, ou refactoriser leur propriété dans une mission autorisée, sans nouveau coordinateur concurrent.

## Invariants de corrélation, temps et pannes

- Clé exacte : epoch destinataire, session, corrélation, operationID, cancellationID, authorizationRef, leaseID et génération. Une ancienne clé ne peut ni annuler ni libérer la nouvelle opération.
- Génération strictement croissante dans l'epoch ; operationID déjà vu refusé, même avec une nouvelle génération. Registre borné à 256 opérations ; ensuite BUSY, pas d'éviction ouvrant un rejeu. Nouveau cycle d'epoch uniquement après réconciliation sûre, jamais comme échappatoire au nettoyage. Pas de cache de résultat idempotent persistant implémenté.
- Le délai restant est construit dans le domaine monotone du destinataire, puis devient deadline locale ; aucune comparaison avec une horloge monotone CORE distante. `pollDeadline` indique le stop à signaler, sans terminer le travail ni libérer. Il n'y a **pas de scheduler/watchdog P2 actif** ici ; futur adaptateur devra le relier aux protections et à l'annulation réelles.
- UNKNOWN après perte de contrôle reste bloquant ; si un callback local de retour authentique arrive pour l'appel actif exact, on peut établir sa fin et nettoyer. Une simple déconnexion, heartbeat ou isLoaded=false ne permet pas ce callback. Perte de processus/epoch et réconciliation interprocessus restent hors implémentation.
- Le snapshot décrit état, stop demandé, appel actif/commencé, effectiveEnd, unloadConfirmed et baux détenus ; pas de prompt, réponse ou document. `modelHeld` veut dire bail de résidence possible, pas mesure de RAM réellement résidente.
- CLEANUP_FAILED ne se transforme jamais en IDLE sur timeout. Après retry confirmé, l'erreur de nettoyage reste le résultat, pas un texte de succès recyclé.

## Ce qu'un futur arbitre interprocessus devra vérifier

Identité/permissions CORE et transport validés (O3), autorité de session, epoch/génération/digest de requête, admission matérielle locale fraîche, absence de warm-up historique concurrent, état de **tous** les participants UI/import/CORE/Whisper/VOICE concernés et confirmation authentique du retour natif puis du déchargement. Il devra gérer interruptions, décès/redémarrages et demandes de status sans fallback automatique sur un second moteur. Résultats privés référencés, aucun texte dans le journal.

ProcessResources reste un coordinateur process-local ; ses baux ne réservent pas la RAM Android ni n'empêchent une autre application de charger un modèle. O4 n'autorise pas à déclarer l'arbitre global implémenté. La localisation du broker, son protocole et ses preuves exigent une validation séparée. UNIFIED v1 ne définit pas les endpoints prepare/cancel/status/unload : **aucune capability cancel inventée** dans une enveloppe fermée.
