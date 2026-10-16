# Mission 003C — guide d'essais Android courts

## État et portée

**QUALIFICATION PHYSIQUE EN ATTENTE UTILISATEUR.** Ce guide ne constitue pas un résultat. Base vérifiée : `mission-003b-resources`, `1756e29557287550b5cfdffdd911cada2a302db6`. Il prolonge [le protocole 003B](../ESSAIS_MEMOIRE_003B.md), avec un ordre obligatoire. Un seul projet/application, aucune connexion CORE–LLM, aucun IPC, aucun autre dépôt modifié. L'ADR-001 est une référence fournie par l'utilisateur : elle n'a pas été relue dans un autre dépôt et aucune conformité d'intégration n'est revendiquée.

L'application et les protections restent celles de 003B, version **0.3.1-mission003b / code 5**. La nouvelle APK peut avoir un autre commit embarqué et certificat debug. Voir le rapport 003C en ligne (lien dans `PREMIER-DIAGNOSTIC.md`), ainsi que `APK-IDENTITE.json` et `SOURCE_COMMIT.txt` fournis avec le paquet. Ne pas installer sur les deux appareils à la fois pour lancer des essais simultanés : procéder séparément et noter chaque configuration.

## 1 — Vérifier le fichier avant toute installation

Télécharger le ZIP `jarvis-llm-debug` depuis l'exécution Actions **réussie liée dans le rapport 003C**. Extraire dans un dossier local, pas dans un fournisseur de stockage cloud. Il contient l'APK, `SHA256SUMS`, `SOURCE_COMMIT.txt`, `APK-IDENTITE.json` et le dossier `mission-003c/`.

Depuis Android, ouvrir `mission-003c/VERIFIER-APK.html` dans un navigateur compatible ; activer le mode avion et sélectionner localement `JARVIS-LLM-debug.apk`. Copier le SHA-256 **APK** du rapport de confiance (pas celui du ZIP GitHub) dans le champ. Exiger **CONFORME**. Ce petit vérificateur n'envoie rien ; sa compatibilité navigateur Android reste à constater. Si Android refuse le HTML local ou WebCrypto est indisponible, utiliser une fonction SHA-256 déjà présente dans un gestionnaire de fichiers fiable, ou un ordinateur (`shasum -a 256` / PowerShell `Get-FileHash -Algorithm SHA256`). Ne pas installer avant vérification. Ne pas ouvrir le fichier HTML depuis un site tiers qui vous propose d'envoyer l'APK.

`APK-IDENTITE.json` donne le SHA-256 du certificat de signature debug et le SHA du fichier. La CI exécute `apksigner verify`, vérifie permissions, ABI ARM64 et alignement 16 Kio. Un hachage conforme n'est pas une certification matérielle. Android vérifie la signature à l'installation ; la vérification détaillée du certificat/manifest est une preuve CI, pas une capacité prétendue de l'interface Android. Une référence copiée depuis un ZIP non fiable ne fournit pas une vérification indépendante.

## 2 — Préserver les données puis installer

**Avant de remplacer une installation :** exporter le catalogue utilisateur depuis l'ancienne application et conserver cet export hors de son dossier privé. Conserver aussi les GGUF d'origine hors de l'application, leur provenance et le SHA-256 fiable indépendant. Vérifier que les sauvegardes sont accessibles localement. Ne transmettre aucun catalogue personnel à GitHub.

Si Android refuse la mise à jour pour signature incompatible, **ne pas désinstaller tant que ces sauvegardes ne sont pas confirmées**. Une désinstallation détruit catalogue et modèles privés. Installer ensuite l'APK localement (Android API 29+, ARM64), paquet `fr.jarvis.llm`. Autoriser ponctuellement l'installation de cette source puis retirer cette autorisation. Ne modifier aucune autre application JARVIS. Après installation incompatible, réimporter le catalogue sauvegardé seulement par la fonction existante, hors capture ; en cas d'erreur, conserver l'original et signaler le problème. Les fichiers SYNTHÉTIQUES ne sont pas à importer pour cette campagne matérielle.

## 3 — Démarrer sans modèle résident

Faire une fermeture normale. Pour une baseline réellement froide, utiliser **Paramètres Android → Applications → JARVIS LLM → Forcer l'arrêt**, puis rouvrir ; **ne pas effacer les données**. La sélection d'un profil ne lance pas de modèle. Choisir le profil prévu dans la fiche appareil. Les modèles peuvent être importés sur disque, mais aucun ne doit être résident au démarrage. En cas de doute, **Mesures → Décharger le modèle**, attendre la fin, puis recommencer depuis un processus neuf.

Ne pas faire une demande de génération maintenant. Noter version/commit, modèle Android exact, firmware, batterie, mémoire accessible/disponible, thermique, stockage. Les noms commerciaux et valeurs historiques ne remplacent pas ces observations. Un état thermique 0 est « normal » dans l'API Android, pas une température en °C. Privilégier une batterie confortable (par exemple ≥30 % pour l'essai utilisateur, sans changement du seuil logiciel de 10 %), tablette/téléphone au repos, sans recharge ni autre tâche lourde. Ne pas refroidir/chauffer artificiellement l'appareil.

## 4 — Diagnostic passif uniquement

Activer le mode avion et désactiver Wi-Fi/Bluetooth s'ils restent actifs. **Mesures → Démarrer une capture de 90 s** ; attendre environ 10 s sans Envoyer ni Importer. **Mesures → Terminer seulement la capture → Exporter les dernières mesures** vers un fichier local, par exemple `redmi-passif.json` / `poco-passif.json`. Aucun calcul n'est lancé par cette capture. Reporter les données dans la fiche correspondante. `configurationIsActual:false` désigne une référence de diagnostic, pas un modèle chargé.

## 5 — Examiner l'admission sans contournement

Premier chargement : disponible ≥ réserve + budget, crédit de résidence **0**. Réserve = `max(1 Gio, RAM accessible/5, seuil Android low-memory + 256 Mio)`. Budget SMALL = 2 Gio ; MAIN = 4 Gio. Le panneau passif affiche la référence SMALL, même si le but POCO est MAIN : **ne pas prendre son seuil SMALL pour l'admission du 1.7B**.

Exemples calculés, pas mesures 003C : Redmi 7,51 Gio → au moins 3,502 Gio requis en SMALL ; à 3,46 Gio, refus maintenu. POCO 11,06 Gio → au moins 6,212 Gio requis en MAIN, ou 4,212 en SMALL. Le seuil Android peut encore augmenter ces minima. D'autres conditions (capteurs, batterie, thermique, ABI, pression mémoire) peuvent aussi refuser. Les valeurs affichées sont arrondies ; le contrôle logiciel travaille en octets.

Si l'admission prévisible est impossible, **s'arrêter au diagnostic passif**, copier le motif et remplir « données insuffisantes / admission refusée ». On peut fermer normalement ses applications personnelles inutiles, mais ne pas arrêter de services système, activer un nettoyeur RAM, utiliser du swap comme RAM, ni provoquer une pression mémoire pour tester les seuils. Ne pas modifier les budgets. L'admission effective sera recontrôlée par l'application à la demande réelle, puis après déchargement avant chargement à froid.

## 6 — Chargement contrôlé, seulement après les étapes précédentes

Redmi : Qwen3 **0.6B Q4_K_M**, TABLETTE ÉCO. POCO : Qwen3 **1.7B Q4_K_M**, AUTO ou ÉQUILIBRÉ, seulement si naturellement admis. Pour isoler l'objectif 1.7B, vérifier le modèle effectif : AUTO/ÉQUILIBRÉ peuvent choisir SMALL. Un succès SMALL ne qualifie jamais MAIN.

Si le GGUF prévu n'est pas importé, utiliser l'importation locale sécurisée avec son SHA fiable, **hors capture**, seulement après le contrôle passif et dans de bonnes conditions. L'import ne lance pas l'inférence et ne vaut pas admission mémoire. L'export des mesures donne un **SHA attendu** provenant des métadonnées d'import ; la vérification complète du fichier est faite à l'import et avant chargement à froid. Ne pas supposer qu'un SHA de conversion différent est équivalent. Ne pas importer le fichier de référence 0.6B dans l'emplacement 1.7B.

Démarrer une nouvelle capture, attendre environ 5 s de baseline. L'application 003B ne fournit **pas de bouton de chargement seul** : une demande non couverte est admise, vérifiée, chargée puis générée dans cet ordre. Les étapes 6–7 sont donc deux phases d'une même demande contrôlée, pas deux boutons. Ne pas inventer une séparation de mesure que l'interface n'offre pas.

## 7 — Une seule génération courte, non personnelle

Envoyer une seule fois : **« Écris exactement : test local terminé. »** Utiliser un chronomètre Android si souhaité, sans capture vidéo personnelle. Noter l'instant d'envoi et le moment où la réponse complète apparaît. Ne pas modifier le catalogue pour forcer une route.

Si le catalogue répond, une clarification apparaît ou le moteur refuse : ne pas multiplier les questions ; exporter et signaler. Une réponse du catalogue ne valide pas le LLM. Exiger dans le JSON `MODEL_LOADED`, puis `GENERATION_SUCCEEDED`, avec la configuration effective attendue. L'application peut terminer avant un arrêt manuel ; le noter honnêtement.

Ne pas chercher à remplir le contexte. La limite de sortie réelle reste celle du profil : 64 tokens TABLETTE ÉCO, 96 SMALL ÉCO, jusqu'à 256 MAIN. La consigne courte n'est pas une garantie de longueur. Dès une réponse courte obtenue, ne pas relancer. Si la génération se prolonge, toucher Arrêter après quelques secondes (environ 5 s de génération observée), sans chercher un débit maximal. Si le chargement n'aboutit pas après une attente courte (environ 20 s), demander l'arrêt et consigner « chargement non terminé dans l'essai court ». Ce n'est pas une mesure de temps de chargement réussi. La deadline native de 90 s n'est pas une invitation à attendre 90 s.

## 8 — Arrêt demandé et libération effective

Toucher **Arrêter** pendant le calcul si possible. Noter le délai jusqu'à la fin effective et la disponibilité de l'interface ; **ne pas relancer pendant le nettoyage**. Arrêter n'est pas Terminer la capture : cette dernière coupe seulement l'observation. Si la réponse était déjà finie, noter « annulation active non observée », puis **Mesures → Décharger le modèle**. Attendre le déchargement effectif.

Le bail lourd est libéré au retour du travail ; le bail de résidence reste jusqu'au retour du déchargement natif. Un événement `STOP_REQUESTED` seul ne prouve pas la fin. `MODEL_UNLOADED` marque le retour du déchargement lorsque le modèle était connu résident ; en cas d'arrêt durant un chargement incomplet, cet événement peut manquer même après nettoyage. **Ne pas traduire son absence en preuve de fuite ou en preuve de libération** : garder ce cas non qualifié et signaler les événements disponibles.

## 9 — Retour au repos, arrière-plan et reprise

Attendre 5 à 10 s après le déchargement, relever PSS/RSS/mémoire disponible et état des ressources. Le maximum RSS depuis création du processus **ne redescend pas** ; c'est normal pour un maximum historique. Le PSS peut conserver runtime/caches SQLite : ne pas exiger zéro ni conclure que la hausse disponible égale la baisse PSS.

Terminer et exporter la capture avant toute nouvelle activité. Le JSON doit montrer la marge, les phases et un état des ressources sans résidence lorsque le déchargement est confirmé. Le coordinateur reste **interne au processus LLM**, pas un arbitre CORE/VOICE. Absence de sondages périodiques au repos en arrière-plan vérifiée en simulation seulement ; la batterie réelle reste à observer.

Ensuite, sur ce même processus déchargé, démarrer une capture passive, revenir à l'accueil puis rouvrir l'application. La capture doit s'être arrêtée avec motif `arrière-plan`, sans nouveau modèle chargé ni génération automatique. Exporter ce relevé séparément. Ce scénario vérifie la reprise **au repos** ; l'arrêt d'un calcul actif par mise en arrière-plan exige un essai distinct, seulement après examen du premier résultat stable. Quand la capture s'arrête en arrière-plan, les sondages s'arrêtent aussi ; des événements de nettoyage peuvent être ajoutés jusqu'à 120 s après le début. Au-delà, absence d'événement = observation insuffisante.

## 10 — Répétition limitée et arrêt de la campagne

Seulement si le premier essai est stable et sans anomalie, proposer au maximum **trois sessions froides au total par configuration**, espacées jusqu'au retour à l'état normal. Pas de boucle automatique, pas d'essais simultanés, pas de stress batterie/thermique/mémoire. Une annulation réellement active et un passage en arrière-plan actif peuvent manquer si la réponse termine trop vite : noter les limites, ne pas prolonger un calcul pour fabriquer le scénario. Demander d'abord l'examen des relevés avant ce scénario supplémentaire.

Stopper immédiatement les essais à la moindre chauffe gênante, hausse thermique inhabituelle, pression mémoire, batterie insuffisante, interface instable, fermeture inattendue ou comportement inhabituel. Utiliser Arrêter, attendre la fin réelle ; si l'application ne répond plus, revenir à l'accueil puis forcer son arrêt dans Android. Ne pas recommencer pour « confirmer » une anomalie. Conserver le relevé déjà exporté ; une capture non exportée peut être perdue après arrêt du processus. Ne pas induire volontairement les seuils de sécurité.

## Lecture des mesures et limites

| Donnée | Source / interprétation |
|---|---|
| PSS par phase / pic échantillonné | Processus entier, partage proportionnel ; sondage cible 500 ms + frontières de phase, pics transitoires manquables. Valeur indisponible = null. |
| RSS / maximum RSS | `/proc/self/status`, pages partagées intégrales ; VmHWM maximum **depuis création du processus**, pas maximum PSS ni limité à la capture. Peut être interdit par l'OEM. |
| RAM accessible / disponible | Android MemoryInfo, globale ; hors extension constructeur. La disponibilité n'est pas une mesure de l'allocation exclusive de JARVIS. |
| Réserve / budget / marge / crédit | Politique et estimations d'admission, pas allocations réellement consommées. Crédit existant limité à une résidence déjà observée. |
| Buffers natifs | Modèle/contexte/calcul rapportés après opérations natives ; incomplets et non continus, parfois absents, non assimilables au pic total. |
| Thermique | État Android 0..6, headroom prévisionnel si disponible (valeur mise en cache jusqu’à 15 s par l’application), pas température CPU. Pas de °C exportés par 003B. Une température batterie affichée par les réglages OEM doit être notée comme **batterie**, avec sa source, jamais comme CPU. Sinon null / indisponible. |
| Batterie / consommation | Pourcentage ponctuel Android ; variation sur essai court trop grossière pour prouver des mAh/Wh ou autonomie. Courant, énergie et puissance non instrumentés : null, sauf observation OEM explicitement sourcée et comparable. Une variation de 0 % ne signifie pas consommation nulle. |
| Durée chargement | Écart entre frontière LOADING et événement MODEL_LOADED : approximation horodatée côté application, inclut file worker et coût d'observation ; vérification SHA séparée en phase VERIFYING. Aucun temps JNI précis instrumenté. |
| Réponse | Écart GENERATING → GENERATION_SUCCEEDED et chronomètre envoi → réponse complète ; inclut orchestration/rendu selon méthode. **Premier token natif indisponible**, car aucun streaming ; ne pas annoncer un TTFT. |
| Arrêt / libération | STOP_REQUESTED → MODEL_UNLOADED, quand événements présents et reliés à la même opération ; comprend sortie du calcul et nettoyage. Pas de valeur si calcul déjà fini/chargement incomplet/événement manquant. |

**Ne jamais additionner PSS + RSS + buffers.** Les mesures de ressources sont du processus entier, pas uniquement du modèle. Une capture change elle-même légèrement la charge ; son coût matériel reste à évaluer. Un test court ne qualifie ni contexte plein, ni autonomie prolongée, ni PLEINE PUISSANCE.

Utiliser les fiches [Redmi](REDMI-PAD-SE.md) / [POCO](POCO-X8-PRO.md) et le [formulaire simple](RETOUR-UTILISATEUR.md). Conserver les exports JSON bruts localement ; ne pas les committer. Le modèle `FORMULAIRE-MESURES.json` est **vide, non mesuré**, distinct du JSON brut de l'application. Il ne déclenche aucun ajustement ni qualification automatique.
