# Diff de contrats proposé

Les scopes métiers CORE et LAB_CLOUD ne sont pas convertibles. Aucun fichier d'un autre dépôt ni contrat réseau/INTERFACE n'est modifié.

| Source proposée | Changement | Compatibilité/validation nécessaire |
|---|---|---|
| ResourceCoordinator.kt | Entry avec bail réel/pins/révocation, observation atomique de paire et watchers hors verrou | acquire/snapshot/close restent disponibles ; un bail épinglé fermé conserve désormais son slot jusqu'au dernier unpin |
| OwnedStreamContract.kt | StreamBinding scellé ; StreamGrant reste CORE ; buffer/événement/callback acceptent ce type interne ; origine de demande facultative sur StreamPrepared | Ancien constructeur StreamPrepared à 6 arguments conservé ; **type du getter grant et signatures internes de bind/constructeurs changent**, ne pas prétendre une ABI Kotlin entièrement inchangée |
| LabCloudAdmission.kt | Nouveaux permis, racine de confiance, registre anti-rejeu, étapes et binding LAB | Ancrage fermé (empreinte vide), constructeur privé ; autre diff requis pour une clé réelle ; aucune clé/permis productif, aucun coreMandateSha256 LAB |
| LabHostBudget.kt | Observations mémoire Linux/cgroup v2 et refus si budget non attestable | Profil hôte distinct ; SafetyPolicy Android intacte |
| LlamaBackend.kt | API interne qualifyLab et challenge, extraction loadOnWorker, contrôle scope exclusif et nettoyage | Même native.create/handle/worker ; anciens chemins restent par défaut, constructeur NativeApi conservé ; nouveau chemin opt-in sans call site |

NativeApi, NativeBridge et les deux méthodes de callback JNI sont comparés par javap : la CI publie les descripteurs et rejets du compilateur. Aucun C++, CMake, ABI native ou seuil Android n'est changé par ce patch. L'empreinte du patch approuvé PERF-03 natif `747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460` reste historique ; **ce nouveau diff Kotlin/broker n'est pas couvert par cet accord**.

La préparation LAB inclut SHA de demande d'origine, rendu, template, compte natif, contexte/configuration, modèle signé et identités réelles du broker/résidence. Le streaming recompare ces sorties via le callback existant avant prefill. L'ancien chemin complet n'expose pas de timestamp de premier token ni de ticket tokenisé réutilisable ; aucune mesure absente n'est inventée. Les opérations natives sont encore formatées/tokenisées plusieurs fois : optimisation future distincte, pas suppression des vérifications.

L'observation/pin est une nouvelle sémantique coopérative du broker LLM, pas un broker global multi-APK. Les fixtures peuvent injecter NativeApi et une observation mémoire de test via le constructeur interne : **uniquement JVM sans JNI**, jamais preuve d'autorité ou d'admission réelle. Le constructeur historique normal emploie les observations Linux réelles pour LAB ; il ne lance aucun watchdog tant que qualifyLab n'est pas appelé.
