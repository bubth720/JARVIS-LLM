# PERF-03B — matrice des preuves et risques résiduels

Module LLM, dépôt `bubth720/JARVIS-LLM`, branche `codex/llm-perf-03b-native-stream`. Base approuvée `ca9d8f060856e134d793d0c23764fae45ce2cc53`, tree `75798737dfc4a618f213ae299b0e467e7547032a`. Patch autorisé SHA256 `747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460`. Le commit/tree testé et les verdicts finaux sont portés par la CI et le complément de PR, sans autoproclamation dans ce plan.

## Campagnes séparées

| Contrôle | Ce que la campagne 03B peut prouver | Ce qui reste hors preuve sans modèle | Statut initial |
|---|---|---|---|
| SHA/preimages/postimages | Seul le diff approuvé a été appliqué aux quatre cibles | Absence de bug natif | PASS selon APPLICATION.json |
| Compilation C++/JNI et ARM64 | Compatibilité de compilation, exports et binaire réellement construit | Bon fonctionnement sous tous états, TTFT | PENDING |
| ERROR callback JNI | CallIntMethod réel, même thread, séquence d'erreur et retour cohérents | ACK/TOKEN/COMPLETED issus de llama.cpp | PENDING |
| Exception Java / OOM injectée | Préservation de l'exception et fin des callbacks dans le vrai JNI | Vrai OOM Android, budgets PSS, tous sites d'allocation JNI | PENDING |
| Mauvais descripteur et références locales | Refus contrôlé, validation `-Xcheck:jni`, refs bornées sur parcours exercés | Toute réf future/asynchrone ou chargement modèle | PENDING |
| Annulation durant callback d'erreur bloqué | Cancel accessible, aucun verrou moteur/broker retenu pendant le callback, refus des appels concurrents protégés | Arrêt pendant prefill ou décodage réels, délai matériel | PENDING |
| Handle stale / ABA / double usage | IDs opaques, ancien handle refusé, pin empêchant destroy/unload concurrents sur cas exercés | Toutes courses sous modèle résident ou imports actifs | PENDING |
| Unload/destroy sans poids | Moteur vide nettoyé et handle invalidé | Libération effective des poids/KV, pic/PSS nul | PENDING |
| Helpers UTF-8 et file Kotlin | Ordre, fragments, limites logiques, refus contradictoires et backpressure du composant testé | Identité des pièces réelles llama et consommation Android | PENDING pour rerun 03B |
| Régression historique | Cas originaux préservés et réussis au commit exact | Nouveau chemin réel non couvert par ces cas | PENDING |
| APK labo | ApplicationId/testOnly distincts, permissions, signature, ABI/16Kio et démarrage si exécuté | Performance physique, autorité CORE, génération modèle | PENDING |
| Contenu ancien/nouveau | Identité d'octets et mêmes vrais comptes tokens sur modèle/configuration identiques | Non accessible sans modèle autorisé | NOT_RUN |
| Mémoire/CPU/thermique/énergie/TTFT | Mesures seulement sur environnement réellement exercé et corrélé | Aucune inférence depuis les bornes de queue ou temps hôte | NOT_RUN / UNKNOWN |

Un `ERROR` sans modèle est une preuve utile d'interopérabilité et de refus. Il ne doit pas être converti en réussite de génération. Une fixture Kotlin/Java conserve une provenance de test, même lorsqu'elle appelle la bibliothèque réelle. Chaque résultat indique si le producteur est la vraie boucle llama, un appel natif de contrôle ou un NativeApi simulé.

## Invariants maintenus

- Un backend propriétaire, un worker de génération, pas de second moteur de compensation. Le test de concurrence vérifie le refus, il n'autorise pas deux générations.
- Les signatures historiques restent disponibles ; `LanguageModel.generate` conserve son résultat complet et le streaming n'est pas activé silencieusement dans l'ancienne application.
- Le nouveau chemin exige le grant exact, sa révocation, les identités d'opération/session/époque/génération/bail/modèle et la préparation native. Une identité déclarée ou un digest ne devient jamais une autorisation CORE/broker.
- Bornes candidates :16 événements dont un slot terminal,16Kio pending,4096octets/chunk,64Kio cumulés. Ces bornes n'incluent pas tous les objets JVM, piles et buffers natifs ; elles ne constituent aucun budget PSS attesté.
- Les contrôles ASCII/UTF-8 interdits produisent un refus explicite. Ils ne sont ni supprimés ni remplacés pour fabriquer une identité de texte ; la compatibilité de contenu doit être testée sur le modèle.
- Toutes les protections thermiques, batterie, mémoire, contexte et modèle restent inchangées ; FULL_POWER demeure non qualifié et désactivé.

## États et preuves de ressources

| État | Signification | Limite 03B |
|---|---|---|
| CONTENT_DONE | Terminal de contenu corrélé accepté | Pas un reçu de libération ; sans modèle, aucun contenu généré n'est attesté |
| DRAINED | Toutes tâches, appels JNI, callbacks, files et imports de l'incarnation sont terminés sous preuve propriétaire | Un retour `generateStream`, un poll vide ou une coroutine annulée ne suffisent pas ; preuve globale UNKNOWN |
| UNLOADED | Modèle/contexte réellement libérés et baux exacts fermés, en plus du drain requis | Un moteur jamais chargé ou un PID neuf ne prouve pas un déchargement de poids |
| UNKNOWN | Identité, retour ou preuve absente/contradictoire, mort de processus | Admission fermée ; aucune réadmission compensatrice |
| CLEANUP_FAILED | Échec de nettoyage observé | Quarantaine et ressources conservées selon politique ; pas de release déclarative |

## Blocages avant intégration réelle et GATE-B

1. Aucun GGUF trouvé dans le scan du workspace : génération réelle, Stop prefill/decode, identité ancien/nouveau et débit restent NOT_RUN. Ne pas télécharger un modèle pour lever ce blocage sans accord.
2. Mandat CORE et bail attesté, binding au modèle vérifié et correspondance fingerprint logique/préparation ne disposent pas d'un adaptateur productif qualifié. Les ports historiques restent fermés ; aucun autre dépôt n'est modifié.
3. L'adaptateur INTERFACE réel ne peut accepter des tokens natifs via une provenance fixture. Une branche complémentaire et son périmètre exigent une validation distincte.
4. Le nouveau binaire Android, son rendu et ses timings ne sont pas qualifiés par des tests hôte. API29/API35 et appareils physiques restent des campagnes distinctes.
5. Un défaut critique arrête la qualification. Les quatre fichiers appliqués ne peuvent pas être corrigés sous l'ancienne empreinte : nouveau diff et approbation requis.

GATE-B n'est pas déduit d'une CI verte. Il exige les preuves fonctionnelles réelles et la validation explicite de l'utilisateur. Installation personnelle, PERF-03C matériel et fusion conservent leurs points d'arrêt séparés.
