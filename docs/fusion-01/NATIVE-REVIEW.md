# L2 — frontière sensible, L3 non autorisé

**BLOCKED. Patch F3B non appliqué, aucune activation JNI/admission /2.**
Le diff concret déjà préparé est [SENSITIVE.patch](../mission-003f-f3b/SENSITIVE.patch),
lié au [manifeste de fichiers avant/après](../mission-003f-f3b/PATCH.json) et à
[DIFF-A-APPROUVER.md](../mission-003f-f3b/DIFF-A-APPROUVER.md).
SHA-256 du patch inchangé :
`be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374`.
F3A/PORT-ET-RECUS, AUDIT et ADAPTATION-SENSIBLE restent les références de conception.
Les assertions de test de ces composants isolés ne sont pas des reçus du worker réel.

## Diffs et risques à examiner avant application

| Fichier/portion | Travail envisagé, non exécuté | Risque et preuve indispensable |
|---|---|---|
| bridge.cpp | Observation des vrais retours JNI, compteurs d'appels/handles/contextes/modèles, clôture après free | Pointeurs périmés, course cancel/destroy ; registry et preuve fin réelle sous même propriétaire |
| LlamaBackend.kt | Trace du worker et opérations/imports en vol ; STOP/DRAINED/UNLOADED/UNKNOWN liés à op+bail | Libération avant retour JNI, erreur nettoyage masquée ; conserver bail jusqu'au reçu exact |
| F3NativeApi/F3Trace/F3BackendObservation | Connexion au port F3A existant, pas de registre parallèle | Double autorité, faux reçu ; un unique propriétaire par objet |
| ResourceCoordinator.kt | Observation/liaison de fin sans double réservation P2Lifecycle | Ancien bail relâchant nouveau bail ; tests de génération/rejeu/interruption |
| fixture AndroidRuntime.kt | Consommation des reçus exacts, refus d'incertitude | Binder mort/timeout ne prouve rien ; /2 doit rester fermé avant validation |
| port NativeTokenCountPort | prepare/consume/discard lié au même worker/modèle/template/contexte/bail/epoch | TOCTOU, SYSTEM dupliqué, question tronquée ; pas couvert par le seul patch F3B |

Il n'existe pas de nouveau diff natif FUSION-01 : tous ces fichiers de production
restent identiques à LLM-03. L'application de F3B seule ne résoudrait pas la
qualification du tokenizer structuré ni les mappings CORE004D. Éviter une application
automatique du patch malgré sa conservation documentaire ; rebaser et relire avant
une éventuelle mission dédiée approuvée.

## Plan de preuve après validation humaine distincte

1. Vérifier diff minimal, propriétaires, ABI ARM64/min29 et permissions inchangés.
2. Tests C++/JNI Linux du vrai chemin avec modèle public **explicitement autorisé**,
   SHA/licence vérifiés : template SYSTEM composé une fois, question conservée,
   comptage exact == génération, contexte demandé plus petit que contexte chargé,
   SMALL/MAIN, allocations, stop, exceptions, cleanup failed et reçu op+bail.
3. Test Android instrumenté API29+ du vrai JNI, même modèle/template ; refuser
   concurrence et double handle. Les mocks ne peuvent satisfaire cette porte.
4. Panne/timeout/Binder mort/process death : UNKNOWN fermé, pas d'unload inféré,
   conservation des preuves/baux ; pas de reset par un processus nouvellement vide.
5. Seulement avec autorisation physique dédiée : essais courts, protections validées,
   PSS/RSS par phase, température/batterie, arrêt, déchargement et repos observés.
6. Revue séparée de l'identité/arbitrage inter-APK CORE ; un coordinateur process-local
   n'est pas global. Aucun lancement CORE004D à partir de ces tests unitaires.

Non exécutés ici : modèle GGUF réel, JNI de génération/cleanup avec poids, parité
Android native, tests physiques POCO/Redmi. CTest sans GGUF et compilation ARM64 sont
uniquement des non-régressions du code historique, pas une attestation de résidence.
