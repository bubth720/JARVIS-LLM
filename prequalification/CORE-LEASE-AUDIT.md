# Audit lecture seule — raccordement restant fermé

LLM : `059cf9c3040d768e1074f62ec5d174c1a27a2590`. CORE local exact : `5da98383ed12cb5a54e896fbe57560bacc432fb5`. INTERFACE locale exacte : `b9a6c1997710c182dd481b23a47ff7120e3e98a0`. Ces observations concernent ces références, sans présumer leur présence dans main. Aucun dépôt complémentaire modifié.

| Frontière vérifiée | Preuve dans les sources | Raccordement manquant et décision nécessaire |
|---|---|---|
| CORE→génération | `SessionCoordinator.kt`, `SessionActions.chat(text):ChatMessage` | Adapter une admission session/génération autorisée à un flux versionné ; le résultat complet actuel n'est pas un stream. Changement CORE hors périmètre de cette mission. |
| Mandat | `GenerationClientContract.kt` référence logique et authorizationRef ; port refuse hors autorité, nativeReady=false | Mandat authentifié lié à session/request/epoch/generation/cancellation/profil, transmis sous composition validée, pas un simple hash auto-déclaré. |
| Bail réel | `ResourceCoordinator.kt:16–47` ; IDs Long privés, `ResourceLease` encapsule release ; snapshot expose requêtes | Attestation atomique identité/révision/propriétaire/incarnation du vrai MODEL/HEAVY, vérification de révocation et de non-réutilisation. Un UUID leaseRef n'est pas un bail broker. |
| Préparation | `LlamaBackend.generateOwnedStream` puis `OwnedStreamContract.kt` | La préparation locale cadre système/question/rendu/template/modelKind/contexte/cap/threads ; l'autorité doit prouver son lien avec le fingerprint logique CORE et le fichier réellement résident/SHA. Pas seulement recopier requestSha256. |
| Revalidation native | `onNativePrepared` compare rendu/template/tokens avant ACK/prefill | Double préparation conservée ; pas de prepare/consume/discard structuré général. Ne pas supprimer le recomptage pour la performance ni ouvrir le tokenizer structuré UNSUPPORTED. |
| Grant | `StreamOwnerAuthority.authorize(prepared,resident)` sans implémentation productive ; identités d'instances et ownerSequence contrôlées | Fournir un adaptateur local attesté sous le propriétaire existant, sans fermer/transférer son bail ni créer de scheduler. Ni ce document ni l'approbation du modèle ne fournissent cette autorité. |
| INTERFACE | `CoreConversationClientAdapter.kt:24,42,60–61` exige origin LABORATORY et READY | Adapter un contrat natif versionné après validation du périmètre ; ne jamais étiqueter la génération réelle FIXTURE/LABORATORY pour franchir le garde actuel. |
| Fin/ressources | États CONTENT_DONE, UNKNOWN, CLEANUP_FAILED ; retours/baux gérés séparément | Barrière exhaustive de tâches/imports/JNI/callbacks/files et reçus durables d'unload ; un ACK, terminal, PID neuf ou retour JNI ne suffit pas à DRAINED/UNLOADED. |

Le modèle proposé satisfait seulement le choix de famille/quantification déclaré, pas l'autorité. L'autorisation de le télécharger ne sera pas une autorisation de changer CORE/INTERFACE, de simuler un mandat ou de modifier les quatre postimages JNI. Proposition de mission ultérieure : contrats/adaptateurs versionnés et attestations, sur branches dédiées après périmètre validé. Le laboratoire M50 synthétique reste une preuve de contrôle UI/CORE, jamais de génération LLM réelle.

Références complémentaires conservées : `perf03/CORE-INTERFACE-COMPAT.md`, `perf03b/QUALIFICATION-MATRIX.md` et `perf03b/BENCHMARK-PREP.md`. L'audit confirme les blocages, il ne les contourne pas. GATE-B reste non atteint pour la génération réelle.
