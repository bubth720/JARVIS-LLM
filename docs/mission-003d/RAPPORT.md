# JARVIS LLM — Mission 003D

## Livraison et niveau de preuve

Module JARVIS LLM, branche `mission-003d-core-generation-contract`, base exacte **43503f59f530f2bb48955bce9d7d71e2470fb668** de 003C. Contrats et tests P2 locaux implémentés, **aucune intégration CORE–LLM opérationnelle**, aucun transport/service IPC, aucune activation ou installation sur appareil, aucun autre dépôt modifié.

O1/O2-P2/O4 sont appliquées dans ce périmètre autorisé. O3/identités/transport et modification de contrats partagés restent à valider séparément. Les contrats UNIFIED sont seulement lus ; leur namespace/version n'est pas réutilisé automatiquement.

## Livrables

- [Audit moteur et préservation](AUDIT.md) : 38 fichiers existants inchangés, dont Conversation.kt, backend, JNI, ProcessResources, politique et catalogue.
- [Contrat P2 typé/versionné](CONTRAT-P2.md) : validation fermée, UTF-8/octets/rôles, système distinct, autorisation par défaut refusée, documents non fiables, mapping Qwen sémantique, budget tokens complet sans troncature.
- [États, erreurs et cycle de vie](CYCLE-DE-VIE.md) : arrêt demandé/fin réelle/nettoyage/déchargement distincts, retard et replays refusés, baux retenus sur erreur, preuve future requise.
- [Matrice CORE004B ↔ LLM003D](COMPATIBILITE-CORE-004B.md) : CORE004B `07cd282` lu ; projection texte sans contexte/sources implémentée et testée. Versions/IDs, budget, contexte aplati, erreurs et fin/libération ont des divergences explicites, aucune compatibilité complète déclarée.
- [Adaptations natives à autoriser séparément](ADAPTATIONS-NATIVES.md) et [sources UNIFIED lues](SOURCES.json).

## Ce qui est implémenté / ce qui ne l'est pas

Les DTO/validateurs, planner avec compteur injecté, machine d'états opt-in et mapping d'erreurs sont du code exécutable testé. Le port natif P2 actuel est explicitement **indisponible**, sans repli silencieux vers l'ancienne API. L'application autonome garde ses comportements et son catalogue.

Les fixtures de comptage/lifecycle ne chargent pas un vrai GGUF. Aucun tokenizer officiel Qwen P2, JNI P2, authentification CORE, serializer UNIFIED, callback natif de contrôle ou arbitre global n'est implémenté. `CoreAuthorizationGate` refuse par défaut ; son autorisation explicite de fixture ne prouve aucune identité ou permission sur appareil. Ne pas présenter ce contrat comme un endpoint sûr déjà disponible.

## Validations et comparaison à 003C

Les 56 tests domaine et 21 simulations Android de référence sont conservés intégralement (fichiers de tests de base comparés octet pour octet, aucun retrait/édition). Nouveaux tests : système/role/document séparés, correspondance de la règle native fixe **dans la source**, refus des champs/versions/rôles/délimiteurs/UTF-8, limite UTF-8 différente des tokens, minimum de budgets, frontière exacte prompt+sortie+8, question et historique intacts, gate refusé par défaut, registre de rejeu borné, mapping des vrais champs CORE004B et refus du contexte/sources non représentables, port natif indisponible sans dégradation, stop idempotent y compris pendant le comptage, bornes du message après composition, retour tardif, epoch/génération/rejeu, deadline locale, baux maintenus, cleanup_failed bloquant, retry explicite et sortie malformée.

Commandes exécutées :

```sh
python3 tools/check-preservation-003d.py
./tools/cloud-env.sh ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
./tools/cloud-env.sh ./tools/test-native.sh
./tools/cloud-env.sh python3 tools/check-apk.py app/build/outputs/apk/debug/app-debug.apk
python3 tools/publish-validation-003b.py --mission 003D
```

Les contrôles locaux passent : **83 tests domaine (56 de base +27 nouveaux), 21 simulations Android**, zéro échec/erreur/ignoré ; CTest1/1, ARM64, Lint local 0 erreur/32 avertissements, contrôle APK et préservation. GitHub Actions confirme **83 tests domaine /21 Android simulés**, zéro échec/erreur/ignoré ; CTest1/1, ARM64, contrôles APK et **Lint CI 0 erreur/36 avertissements** (local :32). Les trois jobs sont réussis. Le contrôle de préservation confirme les 38 fichiers existants inchangés et les tests de base identiques octet pour octet. Le CTest historique vérifie configuration et annulation d'un graphe CPU, **pas P2**. Les tests Android sont Robolectric, pas du JNI ARM64 exécuté sur téléphone. Les preuves du package 003D restent des contrats/fixtures ; **parité native P2 non démontrée** et qualification physique en attente.

La CI construit et contrôle l'APK (signature/permissions/alignement) pour non-régression, enregistre son identité, mais **ne publie pas d'APK d'installation pour cette mission**. Les rapports de validation sont publiés. Le job GGUF Linux historique ne passe pas par P2 et ne prouve ni parité ni gain matériel.

## Publication vérifiée

- **Branche :** `mission-003d-core-generation-contract`, sans fusion dans main.
- **Base 003C :** `43503f59f530f2bb48955bce9d7d71e2470fb668`.
- **Contrats initiaux :** `89a9defb725259b6be9af8e26b1b0b2e2ace6366`.
- **Cycle de comptage / bornes des messages :** `b25af4a`.
- **Code final testé (dont compatibilité du défaut CORE /no_think) :** `74093e37c3cf2e9951af7f0fe4c98defe4972159`.
- **CI entièrement réussie :** https://github.com/bubth720/JARVIS-LLM/actions/runs/38928638903
- **Rapports JUnit/Lint/CTest/préservation/identité du build :** https://github.com/bubth720/JARVIS-LLM/actions/runs/38928638903/artifacts/12058282293
- **Contrôle GGUF Linux hérité, hors P2 :** https://github.com/bubth720/JARVIS-LLM/actions/runs/38928638903/artifacts/12057728080
- **Preuve structurée :** [mission003d-74093e3.json](../evidence/mission003d-74093e3.json).

Les résultats sont observés sur les pages publiques/annotations Actions, sans prétendre avoir téléchargé le ZIP distant dans cette session. Connexion GitHub généralement nécessaire pour les rapports, rétention 30 jours. Aucun artefact d'APK d'installation publié dans cette exécution ; seulement rapports et comparaison Linux héritée. L'outil d'identité APK historique 003C est réutilisé pour le fichier réellement construit au commit74093e3, sans qualification physique.

Les exécutions des commits intermédiaires sont remplacées comme preuve de livraison par celle du code74093e3. Un dernier commit **documentaire seulement** consigne ces résultats et le diagramme de cycle de vie avec `[skip ci]`, sans modifier le code testé ou les protections. Ne pas attribuer les nouveaux tests P2 au chemin natif : la disponibilité P2 effective reste false.

## Blocages et prochaine action

1. Valider avec CORE la matrice désormais confrontée à `07cd282` : mapping texte limité testé, mais aucun adaptateur réel et pas de compatibilité complète. La référence initialement absente a été trouvée pendant la mission ; la question de localisation est résolue.
2. Valider le contrat local P2, ses limites plus strictes et son mapping avant toute modification partagée. Un contenu CORE aplati impossible à séparer fidèlement doit être refusé, pas heuristiquement reconstruit.
3. Autoriser une mission native opt-in et tests template/tokenizer/JNI effectifs selon ADAPTATIONS-NATIVES ; aucun branchement du lifecycle de fixture en double sur les baux du backend.
4. Plus tard seulement : décision O3/authentification/contrôle et arbitrage interprocessus, missions par dépôt, puis installations et essais physiques autorisés distinctement. Aucun fallback automatique sur un second moteur après timeout ou cleanup_failed.

Aucun seuil mémoire/thermique/batterie abaissé ; PLEINE PUISSANCE reste désactivée sur Android non qualifié. Aucun modèle privé, donnée personnelle ou profil VOICE publié. Aucun main modifié ou fusionné.
