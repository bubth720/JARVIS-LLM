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

Les contrôles locaux passent : **82 tests domaine (56 de base +26 nouveaux), 21 simulations Android**, zéro échec/erreur/ignoré ; CTest1/1, ARM64, Lint local 0 erreur/32 avertissements, contrôle APK et préservation. Résultats CI et lien à consigner après vérification de l’exécution. Le CTest historique vérifie configuration et annulation d'un graphe CPU, **pas P2**. Les tests Android sont Robolectric, pas du JNI ARM64 exécuté sur téléphone. Les preuves du package 003D restent des contrats/fixtures ; **parité native P2 non démontrée** et qualification physique en attente.

La CI construit et contrôle l'APK (signature/permissions/alignement) pour non-régression, enregistre son identité, mais **ne publie pas d'APK d'installation pour cette mission**. Les rapports de validation sont publiés. Le job GGUF Linux historique ne passe pas par P2 et ne prouve ni parité ni gain matériel.

## Blocages et prochaine action

1. Valider avec CORE la matrice désormais confrontée à `07cd282` : mapping texte limité testé, mais aucun adaptateur réel et pas de compatibilité complète. La référence initialement absente a été trouvée pendant la mission ; la question de localisation est résolue.
2. Valider le contrat local P2, ses limites plus strictes et son mapping avant toute modification partagée. Un contenu CORE aplati impossible à séparer fidèlement doit être refusé, pas heuristiquement reconstruit.
3. Autoriser une mission native opt-in et tests template/tokenizer/JNI effectifs selon ADAPTATIONS-NATIVES ; aucun branchement du lifecycle de fixture en double sur les baux du backend.
4. Plus tard seulement : décision O3/authentification/contrôle et arbitrage interprocessus, missions par dépôt, puis installations et essais physiques autorisés distinctement. Aucun fallback automatique sur un second moteur après timeout ou cleanup_failed.

Aucun seuil mémoire/thermique/batterie abaissé ; PLEINE PUISSANCE reste désactivée sur Android non qualifié. Aucun modèle privé, donnée personnelle ou profil VOICE publié. Aucun main modifié ou fusionné.
