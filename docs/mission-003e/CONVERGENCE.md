# Mission 003E — endpoint expérimental P2

Base imposée : `mission-003d-p2-core-contract`, `daa46432e847ed9613dc403045498e27281eff42`. Branche : `fixture/003e-p2-secure-endpoint`. Lecture intégrale de D4-02C au commit `0dbb978994994fa3210124f1e150ca23426d410d`, empreintes dans SOURCES.json. Les mentions « proposé/non approuvé » des documents source sont remplacées **uniquement pour le périmètre de ce prototype** par l'autorisation T1–T5 du demandeur. Aucun autre dépôt modifié ; autre branche 003D divergente non fusionnée.

## Architecture

- `fixtures/p2-contract` : AIDL et protocole fermé versionné. `CONTRACT.txt` et les trois AIDL forment le bundle canonique, avec son SHA-256. Wire fournit une implémentation de référence de validation/encodage, dépendante des types du domaine LLM ; CORE peut générer ses bindings à partir des AIDL et implémenter son adaptateur sans importer l'APK LLM.
- `fixtures/p2-endpoint` : APK **fr.jarvis.fixture.llm**, sandbox propre et service Binder explicite `fr.jarvis.fixture.llm.P2EndpointService`, permission de signature. Aucun service ajouté au manifeste historique **fr.jarvis.llm**.
- `fixtures/p2-client` : sondes d'identité uniquement : `authorized`, `wrongPackage`, `wrongSignature`. La sonde autorisée n'est pas CORE 004C, ne contient pas d'arbitre et ne charge aucun modèle. Elle occupe l'identifiant futur fr.jarvis.fixture.core exclusivement dans une éventuelle installation de laboratoire autorisée séparément.
- Sources partagées : tâche Sync génère le source-set de fixture à partir de LlamaBackend, ProcessResources, DeviceMonitor et ModelStore originaux. Aucune copie source maintenue à part ; le contrôle APK vérifie leur identité octet pour octet. Même CMake, bridge.cpp et pin llama.cpp. Aucun deuxième moteur implémenté.

## Canonicalité P2

La fixture passe le SYSTEM brut, jamais le SYSTEM déjà composé de P2Planner. `format()` C++ reste identique à 003D et ajoute exactement une fois la politique fixe, le SYSTEM configurable et le préremplissage Qwen3. Les nouvelles méthodes JNI measureFixtureP2/generateFixtureP2 réutilisent cette fonction et le vocabulaire du modèle réellement chargé. La mesure retourne en interne les octets rendus/template ; le backend calcule leurs SHA-256 sans les exporter ni les journaliser. Le port Binder n'en reçoit que les digests, nombre de tokens et texte final synthétique.

Le comptage et la génération sont sous **le même worker et le même bail HEAVY du backend**. `tokens + sortie_effective + 8 <= min(contexte_admis, contexte_demandé)`, contrôlé avant génération et de nouveau dans le JNI. Le contexte chargé peut rester plus grand ; son coût mémoire n'est pas réduit dans l'admission. MAIN demandé sous 768 est refusé. SMALL et TABLETTE ÉCO gardent leurs profils. Aucun P2Lifecycle créé/acquis par l'endpoint ; il observe les retours du vrai backend.

La voie historique `generate` et le P2 003D existant restent disponibles et conservent leurs comportements. Les politiques/threads/batch/KV/réserves ne sont pas modifiés.

## Identité et autorité

UID capturé dans la transaction Binder avant dispatch, puis résolution package/certificat. Un UID doit correspondre à l'unique package fr.jarvis.fixture.core ; multi-packages/shared UID, certificat différent, signataires multiples et rotation sont refusés. Vérification avant décodage AIDL via onTransact (transaction <=32 Kio), puis avant traitement de la méthode. Le corps JSON est limité à 24 Kio. Une permission signature ajoute la barrière Android, sans remplacer les contrôles applicatifs.

L'autorité est uniquement le Bound Service **réel** `fr.jarvis.fixture.core/fr.jarvis.fixture.core.P2ArbiterService`. La connexion est explicite, identité de package/certificat vérifiée ; les callbacks d'autorisation sont eux-mêmes contrôlés par UID. Le mandat doit reproduire exactement clé, digest de demande, UID client/serveur, modèle et limites, approuver le bail et expirer dans le délai demandé. Le callback est asynchrone, borné à 3 s au maximum et jamais interprété après expiration. Une liaison absente/rompue ou un bail invalide interdit toute allocation modèle.

**Aucun arbitre simulé n'est empaqueté.** Les faux Authority/RuntimePort existent uniquement dans les tests. L'arbitre CORE réel n'est pas livré ici ; aucune admission interprocessus ni intégration à deux APK n'est revendiquée.

## Ressources et journal

États : AUTHORIZING (aucune admission), ADMITTED → LOADING → READY → RUNNING → NATIVE_DONE → UNLOADING → UNLOADED. STOP_REQUESTED n'est jamais une fin ; UNKNOWN/CLEANUP_FAILED interdisent les nouvelles opérations. Le booléen native_ever_started est alimenté après le retour réel de create(), non par un défaut true. effective_end suit les retours attendus du worker, sous drain NonCancellable. model_unloaded suit le retour d'unload. release_confirmed requiert aussi l'écriture du journal terminal.

Reçus liés à la clé complète, génération, bail, epoch et digest ; une ancienne clé ne peut consulter/arrêter/libérer la nouvelle. Aucun texte n'est fourni avant UNLOADED ni après annulation/expiration/perte du client. L'endpoint rejette les replays, générations non croissantes, opérations concurrentes et sessions au-delà de 256 opérations ; pas d'éviction permettant un replay. Un redémarrage n'est pas une réconciliation.

Le journal privé est engagé avant tout import/chargement et conserve seulement les identifiants techniques et le reçu sans texte. Un échec d'écriture ou nettoyage maintient le blocage. Un redémarrage avec réservation non résolue démarre UNKNOWN ; aucune API de reset ou preuve de mort déduite d'un PID n'est ajoutée. Les preuves de l'ancien processus et la quarantaine CORE restent à réconcilier par la mission CORE.

Mort du token client, déconnexion/arbitre perdu, expiration, passage en arrière-plan ou protection matérielle : stop natif puis attente et déchargement, jamais abandon de coroutine comme preuve. À la destruction du service, le worker est fermé après drainage. Aucun sondage périodique au repos ; le watchdog actif cesse lors de l'arrêt et de la fin. Le coordinateur ne couvre que le processus de fixture LLM, pas les APK personnelles ou tout Android.

## Préparation du GGUF et admission

La petite Activity de fixture peut enregistrer une URI SAF locale et un SHA indépendant, sans copie, génération ni téléchargement. **L'importation sécurisée et la vérification SHA ne commencent qu'après mandat réel CORE et admission matérielle**, sous un bail MODEL_IMPORT de fichier puis libéré avant les baux du backend. ModelStore/VerifiedCopy et leurs limites d'origine sont réutilisés ; aucun chemin fourni par Binder n'est accepté. Seuls les GGUF publics Qwen3 Q4_K_M du pilote sont à utiliser. La clé privée de signature n'a rien à voir avec le SHA du GGUF.

La visibilité de la vraie Activity est requise : le Bound Service seul ne transforme pas foreground=false en autorisation. Un futur essai CORE/LLM devra garder la fixture LLM réellement reprise (par exemple multi-fenêtre API29+) ou constater un refus ; aucun faux capteur/foreground imposé en production. Pas d'installation ni de sélection de fichier effectuée pendant cette mission.
