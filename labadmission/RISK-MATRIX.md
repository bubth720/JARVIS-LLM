# Risques de revue

| Risque | Gravité | Traitement proposé / preuve actuelle | Limite ou arrêt |
|---|---|---|---|
| Faux mandat CORE | Critique | Types/domaine LAB distincts ; aucune propriété coreMandate ; rejets de compilation | Aucun raccordement productif |
| Permis forgé ou clé autochoisie | Critique | Ed25519, empreinte de clé attendue distincte du permis | Racine réelle non provisionnée ; composition à approuver, runner compromis hors preuve |
| Rejeu/double admission | Haute | Registre process partagé borné, nonce consommé avant I/O, incarnation réelle | 4096 entrées → refus ; aucun redémarrage avec restauration de credentials |
| Boucle chargement/résidence | Haute | Autorisation de load d'abord, baux épinglés, reçu après retour et revalidation | Aucun reçu natif cryptographique |
| Bail fermé/ABA/étranger | Critique | Référence d'objet, owner, classe, IDs/révision/incarnation et pins atomiques | Chemin chargé réel NOT_RUN |
| Révocation pendant calcul | Critique | Watchers hors verrou, native.cancel même handle, callbacks fermés au retour | Délai 100 ms candidat ; prefill/decode réels NOT_RUN |
| Fin confondue avec unload | Haute | États distincts, résultat ne prétend pas DRAINED/UNLOADED | Mesures après vrai déchargement NOT_RUN |
| Cleanup échoué masqué | Critique | Premier échec conservé, pas de retry automatique, baux en quarantaine | Branche avec poids résidents NOT_RUN |
| TOCTOU/mutation des poids | Critique hors hypothèse | Snapshot privé 0400/0500, inode/hardlink/SHA avant et après load | Same-UID/root hostile non résolu sans nouveau JNI/FD ; décision explicite |
| Budgets RAM / cgroup | Haute | Budget SMALL inchangé, headroom/RSS observés, allocations bornées, watcher | Pas limiteur atomique ; parser et mapping Linux à qualifier sur hôte cible |
| Thermique/batterie | Haute | Aucun garde Android modifié, aucun capteur hôte inventé | Aucune qualification mobile/thermique ; appareils personnels interdits |
| Scopes internes communs | Haute | StreamBinding commun seulement au buffer ; autorité CORE reste typée CORE | Getter interne OwnedStreamEvent.grant change de type ; revue de contrat requise |
| Refactor chargeur historique | Haute | Même API native et worker, régressions JVM comparées | Pas campagne Android/chargement réel ici |
| Journaux privés | Haute | toString redacted, empreintes et codes fixes, pas clé/permis runtime publiés | Collecteur futur doit expurger paths natifs/exceptions ; pas promesse contre logs d'un runner compromis |
| Dépendances de contrôle | Moyenne | JDK21/Kotlin2.1.21 épinglés, jars SHA256 ; Ed25519 hôte | Support LAB Android non activé ; erreur de provider ferme l'admission |
| Accélération apparente | Haute | Aucun chiffre TTFT publié ; coûts de hash séparés à mesurer | Double hash ~397 Mo et polling peuvent augmenter la latence ; aucun gain présumé |
