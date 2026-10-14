# Premier diagnostic et campagne courte — Mission 003B

## Installation et sauvegarde

1. Télécharger `jarvis-llm-debug` depuis l'exécution GitHub Actions **réussie** liée dans `VALIDATION_003B.md` ; extraire le ZIP. Vérifier `JARVIS-LLM-debug.apk` contre `SHA256SUMS` : PowerShell `Get-FileHash .\JARVIS-LLM-debug.apk -Algorithm SHA256`, Linux/macOS `shasum -a 256 JARVIS-LLM-debug.apk`.
2. Conserver APK/empreinte/`SOURCE_COMMIT.txt`. Le SHA du ZIP affiché par GitHub est différent du SHA de l'APK.
3. **Avant remplacement : exporter le catalogue utilisateur depuis l'ancienne application et conserver les GGUF d'origine avec leur SHA-256 hors de son stockage privé.** Une signature debug différente peut imposer une désinstallation, laquelle détruit modèles importés et catalogue privé. Ne pas désinstaller sans ces sauvegardes. Aucun modèle n'est fourni dans l'APK.
4. Installer localement l'APK ARM64 (Android 10/API 29 minimum), identifiant `fr.jarvis.llm`. Une autorisation ponctuelle de la source d'installation peut être demandée. Fermer cette autorisation ensuite. Aucun autre projet JARVIS n'est remplacé.

## A — Passif, à faire en premier (aucun modèle nécessaire)

Sur Redmi Pad SE, et ultérieurement POCO, activer le mode avion ; désactiver aussi Wi-Fi/Bluetooth si Android les conserve. Ouvrir JARVIS, choisir **TABLETTE ÉCO**, sans importer de nouveau modèle ni envoyer de demande au LLM. Noter batterie, état thermique, mémoire disponible et firmware. Batterie normale, tablette au repos, pas de recharge ou applications lourdes pour la campagne comparative.

Ouvrir **Mesures → Démarrer une capture**. Attendre environ 10 secondes sans toucher à Envoyer ; **Mesures → Terminer la capture**, puis **Exporter les mesures** vers un fichier local `redmi-003b-passif.json`. La capture ne lance aucun calcul. Vérifier des échantillons PASSIVE, `policyChanged:false`, les valeurs absentes signalées et le commit. La configuration indiquée avant tout chargement est une **référence de diagnostic**, `configurationIsActual:false`.

À **3,46 Gio disponibles**, conserver le refus : **3,502 Gio au moins requis** pour un premier chargement avec 7,51 Gio accessibles. Un relevé passif ne peut pas prouver une consommation de modèle. On peut fermer normalement ses applications personnelles inutiles, mais ne pas tuer de services système, utiliser un « RAM booster », désactiver une protection ou forcer une allocation pour passer le seuil. Si l'admission naturelle reste impossible, **arrêter ici et fournir le relevé** ; aucun essai du modèle n'est demandé dans cet état.

## B — Campagne courte proposée, seulement si admission naturelle

Pas de benchmark, boucle automatique, modèle concurrent, stress mémoire ou test de chauffe. Utiliser uniquement le GGUF Qwen3 0.6B **Q4_K_M déjà vérifié**, profil TABLETTE ÉCO : contexte 512, sortie ≤64 tokens, batch 16, microbatch 4, un thread. Ne pas changer de modèle/profil ni importer pendant une capture. Chaque relevé doit garder l'APK, le SHA du GGUF, configuration et firmware identiques. Les autres profils/quantifications resteront non qualifiés.

1. **Froid court** : démarrer un nouveau processus (fermeture normale puis arrêt forcé depuis les réglages Android, réouverture ; pas de suppression des données). Attendre 10 s, vérifier absence de modèle résident et thermique normal. Démarrer la capture, attendre 5 s de baseline, envoyer une seule fois **« Écris exactement : test local terminé. »**. Il est possible que le catalogue réponde : seul l'événement `GENERATION_SUCCEEDED` avec `MODEL_LOADED` prouve ici le passage au moteur. Sans ces événements, le relevé ne qualifie pas le LLM. Si une clarification apparaît, ne pas multiplier les requêtes ; signaler le cas.
2. Laisser cette unique réponse courte se terminer. Observer la fin effective et le déchargement automatique (10 s d'inactivité en TABLETTE ÉCO), ou **Mesures → Décharger le modèle** quand l'interface est libre. Attendre encore 5 s. Terminer la capture (≤90 s), puis exporter `redmi-003b-froid-01.json`. En cas de blocage/erreur/alerte matérielle, utiliser Arrêter, attendre la fin réelle, ne pas relancer et exporter le diagnostic disponible.
3. Après retour à un état thermique normal et disponibilité stable, répéter **au maximum trois sessions froides au total**, espacées, sans chercher un débit maximal. Reporter durée approximative, activité système et toute anomalie. Ne pas enchaîner si température, batterie ou réactivité se dégradent. Ces répétitions qualifient seulement ce scénario bref, pas un contexte plein.
4. **Annulation courte séparée**, si les sessions précédentes sont saines : nouvelle capture à froid, une demande courte non couverte par le catalogue ; toucher Arrêter après le début du calcul, sans le prolonger pour obtenir un débit. Relever `STOP_REQUESTED` puis fin effective/déchargement et occupation UI. Si la réponse finit trop vite, noter « annulation non observée », sans prolonger le calcul. Pas de relance immédiate pendant le nettoyage. Export `redmi-003b-arret.json`.
5. **Arrière-plan séparé**, facultatif après examen des premiers relevés : une seule demande courte, retour à l'accueil puis réouverture. La capture s'arrête en arrière-plan ; attendre la fin effective avant export. Les événements de nettoyage peuvent apparaître sans nouveaux échantillons. Ce relevé ne mesure pas la consommation en arrière-plan. Ne pas provoquer de situation thermique/batterie dangereuse pour vérifier un seuil.

La limite de capture 90 s concerne l'observation, **pas une autorisation de générer pendant 90 s**. Une latence de sécurité mesurée n'est pas garantie pour toutes les conditions. Les messages peuvent être visibles dans la conversation locale mais ne sont pas inclus dans l'export JSON.

### Observation externe facultative (technique, locale)

Avec Android Platform Tools et débogage USB déjà autorisé, `adb shell dumpsys meminfo fr.jarvis.llm` peut compléter avant/pendant/après **une même session courte** les valeurs du processus, et non un benchmark supplémentaire. `adb shell pidof fr.jarvis.llm` identifie le processus. L'accès `/proc/PID/status` peut être refusé par Android ; ne pas contourner cette restriction. Noter l'heure/phase de chaque relevé ; les catégories `dumpsys` ne doivent pas être additionnées au PSS total. L'USB/recharge peut modifier le comportement : noter cette différence, ne pas mélanger ces sessions avec la campagne sur batterie. Ni les captures `dumpsys` ni VmHWM ne donnent un pic PSS continu. Éviter de partager un journal système contenant des données d'autres applications.

## C — Conditions avant de proposer un nouveau budget

Les exports réels doivent montrer baseline froide, vérification/chargement, génération, annulation, fin de déchargement, marge système et capteurs connus, sans échantillons évincés, avec configuration/hash identiques et répétitions cohérentes. Un refus seul, une réponse catalogue ou un PSS de fin ne suffit pas. Vérifier le retour au repos et l'absence de résidences doublées ; une baisse PSS ne prédit pas exactement la hausse de RAM disponible.

Toute enveloppe candidate devra couvrir le **processus entier**, pas seulement les poids : maximum observé des phases, incertitude des sondages/transitoires et pages partagées, variations répétées, runtime/JVM/SQLite, contexte d'usage prévu. La marge additionnelle doit être explicitée à partir des données et des limites ; aucune constante « magique » ni règle `593 Mio + petit pourcentage` ne justifie un seuil. Un essai très court ne couvre pas le contexte plein : s'il reste nécessaire, demander ensuite un protocole borné distinct avant toute qualification plus large. La réserve système et les seuils thermiques/batterie restent identiques.

**Décision actuelle : budget SMALL 2 Gio conservé.** Après analyse des relevés, une proposition chiffrée pourra être soumise à validation ; elle ne sera ni téléchargée ni appliquée automatiquement. Si les données ou marges restent insuffisantes, conserver le refus. Aucun benchmark intensif sans autorisation explicite.

## Fiche à joindre aux fichiers JSON

| Champ | À remplir |
|---|---|
| Appareil / firmware / APK et commit | |
| Profil / contexte / tokens / batch / threads | TABLETTE ÉCO 512 /64 /16-4 /1 |
| GGUF exact / SHA-256 / taille | |
| Batterie / thermique / applications ouvertes / USB | |
| Session froide réelle / durée / anomalie | |
| PSS pic échantillonné / RSS maximum de vie | Ne pas les confondre |
| RAM disponible minimale / réserve / marge minimale | |
| Chargement / génération / arrêt / déchargement confirmés | |
| Réactivité / retour au repos / capteurs absents | |

Partager manuellement les JSON et cette fiche après relecture ; aucun envoi réseau par JARVIS. Les tests du catalogue et ses sauvegardes restent documentés dans `ESSAIS-CATALOGUE.md` du paquet.
