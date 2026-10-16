# JARVIS LLM — rapport Mission 003C

## État de clôture cloud et portée

Module : **JARVIS LLM uniquement**. Mission : qualification matérielle Android et protections de ressources. Branche : `mission-003c-device-qualification`, départ exact `1756e29557287550b5cfdffdd911cada2a302db6` sur `mission-003b-resources`. Aucune fusion dans main, aucun autre dépôt JARVIS modifié, aucune connexion CORE–LLM/IPC ajoutée.

**QUALIFICATION PHYSIQUE EN ATTENTE UTILISATEUR**, pour les deux appareils. Aucun nouveau relevé matériel, gain RAM, autonomie ou performance Android mesuré par Codex. L'ADR-001 est signalée comme approuvée par l'utilisateur, sans consultation/modification de JARVIS-UNIFIED. Cette mission prépare des preuves, pas une intégration.

## Phase A — préservation et différences

[Rapport de préservation 003B](PRESERVATION-003B.md). Contrôle automatique de 38 fichiers de runtime/configuration : politique mémoire, application Android, domaine, catalogue livré, JNI, manifeste, scripts de vérification APK et source llama.cpp épinglée **inchangés**. Budgets SMALL/MAIN 2/4 Gio, réserve système, seuils et annulation conservés. PLEINE PUISSANCE reste désactivée. Aucun test antérieur supprimé ou rendu facultatif.

Seuls tests, guides et outils de preuve/publication évoluent. Version APK conservée **0.3.1-mission003b / code5** ; provenance de commit embarquée mise à jour par le mécanisme existant, certificat debug et SHA de l'APK susceptibles de changer selon runner. Ne pas confondre cette reconstruction avec l'APK historique. La CI publie l'identité réelle et la cherche dans les DEX pour détecter un ancien build avant empaquetage.

Les SHA des modèles réellement présents sur les appareils ne sont pas connus ici. Le hachage d'import et avant chargement reste actif ; un test avec octets synthétiques démontre détection d'altération/annulation et récupération **sans être un test de chargement de vrai modèle**. Le SHA public d'un modèle de la comparaison Linux ne constitue pas une vérification des fichiers utilisateur.

## Phases B–C — campagne utilisateur

[Protocole complet](PROTOCOLE.md), ordre obligatoire : vérification APK/signature/permissions → sauvegardes → processus sans modèle → passif → admission inchangée → chargement contrôlé → génération courte unique → arrêt et nettoyage → repos/arrière-plan/reprise → répétition limitée si stable. Refus maintenu si ressources insuffisantes, arrêt de campagne à toute anomalie ; aucun stress thermique/batterie/mémoire, aucun benchmark intensif.

Limites explicites : capture cible 500 ms, pas pic PSS continu ; RSS partagé et VmHWM historique ; buffers natifs incomplets ; pas de température CPU °C ni énergie/courant instrumentés ; premier token natif non instrumenté (réponse complète seulement) ; chargement/génération liés dans une demande, durées applicatives approximatives. Les données absentes restent nulles, PSS/RSS/buffers ne s'additionnent pas.

## Phase D — tests et limites de couverture

[Matrice détaillée](PRESERVATION-003B.md#matrice-de-couverture-de-la-phase-d). Couverture : second modèle, concurrence des travaux lourds, conservation des baux à l'arrêt/échec, reprise après nettoyage confirmé, attente d'un déchargement simulé, absence de sondages au repos en arrière-plan, capture stoppée, exports cohérents, transaction catalogue annulée inchangée même après réouverture, intégrité SHA sans changement catalogue.

Le CTest existant vérifie drapeau concurrent/callback/deadline et abort/recovery d'un graphe CPU réel. **La récupération d'un échec partiel de chargement JNI Android reste à qualifier**, les nouveaux tests de baux utilisent une erreur simulée. Ils ne prétendent pas charger ou libérer un vrai Qwen Android. Ne pas corrompre un modèle utilisateur pour tester cette récupération. Le coordinateur n'arbitre que les participants du processus LLM.

## Phase E — état de chaque appareil

| Appareil / cible | Mesure physique 003C | Conclusion actuelle |
|---|---|---|
| [Redmi Pad SE](REDMI-PAD-SE.md), TABLETTE ÉCO /0.6B Q4_K_M | Aucune | DONNÉES INSUFFISANTES — EN ATTENTE UTILISATEUR |
| [POCO X8 Pro](POCO-X8-PRO.md), AUTO ou ÉQUILIBRÉ /1.7B Q4_K_M si admis | Aucune | DONNÉES INSUFFISANTES — EN ATTENTE UTILISATEUR |

**Aucun tableau de configurations matériellement sûres n'est établi**, faute de preuves suffisantes. Les fiches décrivent des candidates, pas des configurations qualifiées. Les chiffres antérieurs 593 Mio PSS Redmi / arrêt 1–2 s sont des observations utilisateur historiques sans nouvelle qualification 003C.

Blocage matériel : aucun accès Android physique fourni à cette session cloud. Une tentative de détection ADB locale n'a pas pu démarrer son environnement utilisateur (répertoire par défaut en lecture seule) ; aucun scan réussi, aucune connexion ou mesure de téléphone revendiquée. Les appareils doivent être observés par l'utilisateur selon les guides. Il n'est pas nécessaire de configurer ADB pour le premier diagnostic, faisable depuis Android.

Formulaire simple : [RETOUR-UTILISATEUR.md](RETOUR-UTILISATEUR.md). Formulaire JSON optionnel : [FORMULAIRE-MESURES.json](FORMULAIRE-MESURES.json), entièrement vide et marqué template, distinct de l'export réel formatVersion1. Aucun profil privé ou résultat inventé publié.

## Phase F — preuves cloud

| Vérification | 003B historique | 003C locale, avant publication |
|---|---:|---|
| JVM domaine | 55 réussis | 56 réussis, zéro échec/ignoré |
| Android simulé Robolectric API35 | 16 réussis | 21 réussis, zéro échec/ignoré |
| Natif CTest | 1/1 réussi | 1/1 réussi |
| ARM64 debug | Réussi | Réussi |
| Lint | CI 0 erreur /36 avertissements | 0 erreur /32 avertissements (CI à comparer séparément) |
| Contrôle APK | Signature/permissions/alignement réussis | Réussi |
| Préservation 003B | Base de comparaison | 38 fichiers identiques |
| Vérificateur HTML local | Absent | Six scénarios Node/WebCrypto réussis, pas un navigateur Android |

Commandes exécutées dans ce cloud :

```sh
python3 tools/check-preservation-003c.py
node tools/test-apk-verifier.cjs
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 tools/publish-validation-003b.py --mission 003C
```

Les rapports JUnit/Lint/natifs seront fournis par l'exécution 003C, sans assimiler une simulation à un test physique. La comparaison GGUF courte héritée mesure Linux x86_64/RSS seulement, pas les appareils ; pas d'amélioration annoncée sur sa base.

### Publication et provenance

Références du commit source testé, exécution CI, artefacts, SHA-256 APK et certificat : **à consigner après succès effectif de cette exécution**. Le paquet de diagnostic contiendra les identités et rapports machine au moment du build, ainsi que les guides/fiches vierges, le vérificateur hors ligne et le formulaire. Un commit documentaire final pourra consigner les liens sans modifier le runtime ni le code testé.

## Prochaine action

D'abord vérifier l'APK et les sauvegardes, puis fournir **un relevé passif Redmi**, sans modèle, et la fiche correspondante. Si admission naturelle impossible (par exemple 3,46 Gio disponibles contre au moins 3,502 Gio requis), conserver le refus. Sur POCO, MAIN demanderait au moins 6,212 Gio à froid pour 11,06 Gio accessibles ; le repli SMALL n'est pas une preuve pour le 1.7B.

Après retour utilisateur : analyser les JSON et les incertitudes, décider si un essai bref est possible et si des preuves supplémentaires sont nécessaires. Aucune nouvelle enveloppe ni configuration ne sera qualifiée automatiquement ; aucune modification de seuil sans accord. PLEINE PUISSANCE, intégration CORE et benchmarks intensifs restent hors mission.
