# Mission 003B — mémoire et préparation multimodule

## Décision et périmètre

Branche `mission-003b-resources`, issue de `mission-003-catalogue` (0b5eba7). Application unique 0.3.1-mission003b : catalogue 0.3.0, import vérifié, Qwen3/llama.cpp et profils conservés. Aucun autre dépôt ni `main` modifié. Pas de nouvelle quantification, pas de modification des poids ou du code natif, pas de diminution du budget. PLEINE PUISSANCE reste non qualifiée.

Le pic PSS d'environ **593 Mio est un relevé fourni par l'utilisateur**, pas une mesure effectuée dans cette mission. Il rend une optimisation plausible, mais ne permet pas de qualifier un budget sûr : configuration exacte, phases couvertes, fréquence d'échantillonnage, activité système et répétabilité manquent. Aucun gain de RAM sur tablette n'est revendiqué.

## 1. Ce que mesurent les nombres

| Grandeur | Signification et limites |
|---|---|
| PSS Android | Pages privées et part proportionnelle des pages partagées du processus entier, JVM/UI/catalogue/JNI compris. Variable avec le partage ; sondages susceptibles de manquer une allocation transitoire. 593 Mio ≈ 0,579 Gio. |
| RSS `/proc/self/status` | Pages résidentes mappées au processus, pages partagées comptées intégralement. Différent du PSS, ni allocation maximale ni mémoire exclusivement consommée par JARVIS. |
| VmHWM | Maximum RSS depuis la création du processus, y compris avant la capture ; ne constitue pas un pic PSS. Peut être indisponible selon Android/OEM. |
| Buffers llama.cpp | Tailles modèle/contexte/calcul rapportées par les API natives existantes après chargement/génération. Hors autres allocations, tas JVM, piles, runtime ; ne décrivent pas un pic. Les poids mmap peuvent être partiellement résidents. |
| RAM disponible | `ActivityManager.MemoryInfo.availMem`, estimation Android de la disponibilité globale, influencée par les caches récupérables et les autres applications. Sa variation n'est pas exactement le PSS libéré. |
| Budget d'admission | Enveloppe conservatrice de planification et seuil PSS du processus : 2 Gio pour SMALL, 4 Gio pour MAIN. Ce n'est ni une allocation immédiate ni un pic mesuré. |
| Réserve système | `max(1 Gio, 20 % RAM accessible, seuil low-memory Android + 256 Mio)`. Indépendante du budget du modèle. Ne doit pas être réduite pour faire passer un test. |

Ces grandeurs se recouvrent : **ne jamais additionner PSS, RSS et buffers natifs**. Un GGUF sur disque n'est pas une mesure de RAM, et l'extension mémoire constructeur n'est pas de la RAM physique supplémentaire.

`SafetyPolicy.kt` est inchangé. Premier chargement : disponible ≥ réserve + budget. Pour un modèle déjà résident seulement, le crédit existant est la hausse PSS observée depuis sa baseline, bornée par PSS courant et budget, pour éviter de demander deux fois toute l'enveloppe. Ce crédit ne prouve pas des pages récupérables : Android peut changer le partage ou récupérer du cache. Le diagnostic l'expose séparément ; aucun crédit n'est créé pour un démarrage à froid.

Avec 7,51 Gio accessibles, réserve minimale 1,502 Gio et budget 2 Gio : **3,502 Gio requis** (davantage si le seuil Android l'impose). À 3,46 Gio disponibles, déficit approximatif **43 Mio**. Le refus reste correct selon la politique actuelle ; les valeurs d'interface sont arrondies. Le refus thermique, batterie, capteurs absents, arrière-plan et `lowMemory` reste prioritaire.

## 2. Modifications cloud livrées

- Capture volontaire **Mesures**, au premier plan, bornée à 90 secondes et 256 échantillons. Cible 500 ms plus frontières de phases ; ce n'est pas une mesure continue garantie. Aucun import, chargement ou calcul n'est déclenché par la capture. Arrêt en arrière-plan ; export JSON via le sélecteur Android local.
- Relevés horodatés : PSS, RSS, VmHWM, disponibilité, réserve, enveloppe, crédit, marge, seuil Android, pression mémoire, batterie, état/headroom thermique, configuration et profil, buffers natifs connus, état du coordinateur. Les valeurs manquantes restent nulles.
- Événements sans contenu des conversations : refus d'admission, modèle chargé, génération réussie, demande d'arrêt, erreur, fin effective de déchargement. Les événements de nettoyage peuvent être ajoutés pendant 120 s après le début même si la capture a été arrêtée ; pas de sondages supplémentaires après arrêt.
- Provenance APK/commit, matériel/firmware et références GGUF attendues **au début de capture et au moment de l'export**, avec avertissement en cas de changement. Le digest n'est pas recalculé par la capture : l'import et le chargement à froid conservent leur vérification. Ne pas remplacer les modèles/changer de profil au cours d'un relevé destiné à qualifier un budget.
- Rapport avertissant des données manquantes, d'une baseline chaude, de l'absence de génération/déchargement et de l'éviction d'échantillons. Aucun budget conseillé ou appliqué automatiquement. Pas de messages, réponses, chemins de documents ou identifiants matériels uniques exportés ; export manuel uniquement.
- Coordinateur de ressources **dans ce processus**, indépendant du moteur : un bail de travail lourd et un bail de résidence modèle. Modules déclarables LLM, import, catalogue, CORE, VOICE, INTERFACE, services. Les enveloppes sont déclaratives, ne réservent aucune RAM Android.
- Chargement/génération et vérification/import GGUF utilisent les baux ; opérations utilisateur du catalogue sont sérialisées par le même coordinateur. Une résidence modèle bloque les travaux lourds d'un autre propriétaire. Un second modèle est refusé, même de même nom. Les lectures légères et l'interface ne sont pas des travaux lourds.
- La demande d'annulation ne libère **aucun bail** : le moteur reste réservé jusqu'au retour natif, puis au déchargement/destruction effectif. Pas de libération par délai d'expiration. L'interface reste occupée pendant un déchargement en file, même après retour au premier plan, évitant une admission fondée sur une résidence périmée.
- Sans modèle ni travail en arrière-plan : watchdog suspendu sur événement, sans réveil périodique. Au premier plan passif : cadence 15 s ; modèle résident : 2 s ; calcul actif : 1 s ; capture volontaire : 500 ms cible. Les événements réveillent immédiatement la boucle. Délais existants de déchargement 10/30/120 s conservés. Ce changement réduit les réveils programmés, **sans prétendre à une économie batterie mesurée**.

## 3. Compatibilité multimodule et limites

Le contrat `ResourceRequest/ResourceLease` sépare propriétaire, module, opération, enveloppe déclarée et identité modèle. L'adaptateur de moteur reste interchangeable. Une future version pourra ajouter un arbitre IPC commun, une file avec priorités/annulation et des enveloppes qualifiées par matériel/configuration, sans coupler le catalogue à llama.cpp. Les baux de travail lourd et résidence doivent être distincts : une fin de génération ne libère pas les poids.

**Ce coordinateur ne contrôle pas les autres applications/processus Android**, ne réserve ni CPU ni mémoire au système et n'intègre pas encore CORE/VOICE/INTERFACE. Les états externes nécessiteront un protocole coopératif, identification des clients, gestion des morts de processus et confirmation de fin effective des calculs. Un heartbeat expiré ne suffira pas à prouver qu'un calcul est arrêté. Les services légers futurs devront aussi faire qualifier leur consommation au repos. Aucun module externe n'est lancé ou modifié ici.

Une erreur de nettoyage peut conserver un bail par prudence : ne pas libérer une ressource native dont la destruction n'est pas confirmée. L'UI désactivée et les tests simulés ne prouvent pas l'absence de tout incident OEM. Aucune nouvelle garantie sur accélération GPU/NPU ou PLEINE PUISSANCE.

## 4. Tests et preuves attendues

Cloud : tests domaine des baux concurrents, doubles modèles, annulation sans libération, jetons périmés, refus matériel inchangé ; parseurs RSS et capture bornée/nulles/événements ; simulations Android du diagnostic sans JNI, arrêt de capture en arrière-plan, absence de sondages au repos et attente du vrai déchargement ; tests catalogue 0.3.0 conservés. Compilation ARM64, Lint, tests natifs d'annulation/admission et contrôle APK/signature/alignement/permissions restent obligatoires dans GitHub Actions.

La comparaison GGUF courte déjà présente dans la CI mesure Linux/RSS et reste un contrôle de non-régression cloud : **elle ne qualifie aucun budget Android**. Robolectric n'est pas un émulateur de téléphone ni un test thermique réel. Les sources/tests natifs de sécurité sont conservés ; leurs résultats ne donnent pas une latence maximale d'arrêt sur tablette. Les compteurs du coordinateur ne constituent pas une preuve de PSS ou de batterie.

Voir [VALIDATION_003B.md](VALIDATION_003B.md) pour les résultats effectivement obtenus, références APK et limites ; [ESSAIS_MEMOIRE_003B.md](ESSAIS_MEMOIRE_003B.md) pour la campagne. Le budget reste inchangé jusqu'à examen des données réelles et validation explicite d'un éventuel nouveau budget.

## 5. Risques prioritaires et suite

1. **Pic manqué / mauvaise attribution** : sondages PSS, RSS partagé et buffers incomplets ; relever toutes les phases, VmHWM et répétitions à froid, pas seulement une réponse.
2. **Pression extérieure / LMK** : Android peut tuer le processus malgré une admission ; maintenir réserve, surveillance, faibles contextes, un travail lourd, pas de pression mémoire artificielle sur appareil.
3. **Annulation et libération différées** : le relevé utilisateur de 1–2 s n'est pas une borne ; conserver baux et UI occupée jusqu'à fin réelle. Toute erreur/absence de déchargement invalide la qualification.
4. **Capteurs et chauffe** : cloud incapable de qualifier leur fiabilité OEM. Ne pas provoquer d'échauffement ; arrêt aux seuils existants et tests brefs à froid.
5. **Multimodule encore local au processus** : pas de garantie interapplications avant arbitre IPC et validation dédiée ; aucun budget gagné ne doit être préalloué à des services encore non mesurés.
6. **Installation debug / perte de données** : signatures CI éphémères ; sauvegarder catalogue et GGUF d'origine avant toute désinstallation.

Ordre proposé : livrer diagnostic cloud validé → relevé passif tablette → seulement si admission naturelle, campagne courte ci-dessous → revue des exports et de leurs limites → proposition chiffrée de budget par configuration avec marge explicite → autorisation utilisateur et tests de non-régression → qualification multimodule séparée. Ni passage à PLEINE PUISSANCE ni benchmark intensif dans cette mission.
