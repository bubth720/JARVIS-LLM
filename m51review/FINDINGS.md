# Constats de l'audit secondaire

Source examinée : PR #15 au commit 69400fe26d61688262b94cc8a2d5efe20d1da4be, patch original 8df826451e501a107305f8fcaee5ca3d5334e22de23246d32cc7fd5312da21e8. Les tests lisent et compilent ses postimages sans les appliquer. Un test suffixé REPRODUCED réussit lorsqu'il démontre **la limitation**, jamais lorsqu'il qualifie une admission.

| ID | Gravité / verdict | Source et observation | Décision / correction proposée, non appliquée |
|---|---|---|---|
| A01 | P0 — G-ROOT fermé | LabOperatorTrust.fromApprovedKey refuse toute clé : empreinte vide et constructeur privé | Conserver ce refus ; approbation de fingerprint public et diff de provision distinct indispensables |
| A02 | P0 — menace à décider | LabFileProof contrôle inode/taille/mtime/mode/liens, puis le JNI rouvre un pathname ; aucun reçu de poids chargés | GO possible seulement si l'utilisateur accepte un hôte éphémère isolé sans écrivain hostile same-UID/root ; NO-GO Android/transposition privée |
| A03 | P1 — correction de contrat à revoir | ResourceCoordinator.release appelle observations.values.forEach sans isoler les exceptions : la première exception empêche les watchers suivants | Agréger les échecs après notification de tous les watchers ou primitive stop non levante, avec état UNKNOWN conservé ; nouveau diff exact broker requis |
| A04 | P1 — limite de deadline | Un unique ScheduledThreadPoolExecutor exécute le monitor mémoire et le cancel d'expiration ; un monitor bloqué retarde la notification d'expiration | Borne explicite du monitor et voie de deadline indépendante, sans second moteur/worker de génération ; mesurer avant toute campagne native |
| A05 | P1 — frontière de confidentialité | Une exception Files.toRealPath/readAttributes contient le pathname brut ; toString redacted ne couvre pas ce canal | Collecteur/boundary d'erreur expurgé obligatoire avant publication ; pas de journal brut d'exceptions ; aucune fuite personnelle actuelle n'est alléguée |
| A06 | P1 — composition d'autorité non qualifiée | LabExecution peut être construit avec des champs arbitraires ; observe existe mais n'est pas imposé par qualifyLab | Le bootstrap approuvé doit vérifier run/attempt/commit/job/artefact réellement exécuté, pas accepter un contexte déclaré par le consommateur ; aucune inférence n'est autorisée par cette fixture |
| A07 | P2 — machine d'états interne faible | LabAttempt.terminal accepte CLEANUP_FAILED puis CONTENT_DONE ; la méthode ne scelle pas les terminaux | Verrouiller les transitions terminales dans un futur diff ou restreindre l'API ; le backend actuel possède des gardes supplémentaires, aucune réadmission réelle n'est démontrée |
| A08 | P2 — risque d'ordre de verrous | Les watchers s'exécutent hors moniteur broker, mais sous le moniteur ResourceLease.close | Documenter les callbacks autorisés, bornés, sans réentrée dans lease.close ; pas de deadlock du handler natif actuel prétendu sans preuve |
| A09 | P1 — sécurité mémoire non mesurée | Headroom/RSS/cgroup sont échantillonnés ; aucun limiteur atomique de pic ni mesure de vraie résidence | Faire qualifier les mappings cgroup visibles, les pics et le Stop sur le hôte autorisé ; aucune hausse de budget ou capteur mobile fictif |

## Démonstrations sans GGUF

Le test FD ouvre un descripteur d'écriture sur un fichier synthétique, passe son mode à 0400, puis écrit encore via le descripteur déjà ouvert. Ce comportement normal POSIX prouve que le mode courant ne révoque pas un écrivain existant.

Le test de stabilité utilise un fichier sparse synthétique de taille autorisée et construit explicitement, par réflexion **de test seulement**, le checker local de métadonnées. Il change des octets, restaure mtime/mode et observe que requireStable ne détecte pas le changement. Il ne prétend jamais avoir vérifié le SHA d'un vrai GGUF, chargé un modèle ou satisfait une autorité. Le fichier est supprimé en fin de test.

La défaillance de watcher est rendue déterministe en inspectant l'ordre de l'IdentityHashMap de ce seul broker de fixture : un watcher levant est placé avant le watcher témoin. La révocation des baux est bien effective, mais le témoin n'est jamais notifié. Les slots épinglés restent occupés : il ne s'agit pas d'une réattribution illégitime démontrée.

Le test de deadline bloque délibérément le monitor de fixture sur un latch, sans bloquer le worker natif puisqu'aucun n'est chargé. Il vérifie l'absence de callback d'annulation pendant le blocage, puis sa réception après libération. Cela démontre une dépendance de scheduling, pas la latence d'un vrai Stop JNI.

Les limites A03–A08 sont dans un patch encore non appliqué et dont la racine reste fermée. Elles ne deviennent pas des vulnérabilités actives du cockpit M51. Le score d'intégration ne gagne aucun point d'inférence à partir de cette revue.
