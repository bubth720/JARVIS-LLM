# Validation Mission 003B

## État avant essais physiques

Version 0.3.1-mission003b, branche `mission-003b-resources`, issue de `mission-003-catalogue` (0b5eba7). Aucun budget mémoire diminué : SMALL 2 Gio ; réserve système et protections existantes conservées. Aucun autre projet JARVIS modifié, aucune fusion dans main.

Le relevé utilisateur d'environ 593 Mio PSS sur Redmi ne constitue pas un résultat de cette campagne. **Aucune mesure matérielle nouvelle ni économie RAM/autonomie démontrée sur appareil** dans le cloud.

## Vérifications cloud locales

- 55 tests domaine JVM, dont capture/parseur mémoire et coordination exclusive/concurrente : réussis.
- 16 tests Android simulés Robolectric API 35, dont catalogue SQLite et 3 tests du cycle diagnostic/déchargement : réussis. Aucun appel JNI ni appareil Android réel dans ces simulations.
- Test natif CTest `native_safety` : réussi. Ce test couvre les mécanismes natifs existants, pas la latence d'arrêt sur tablette ni l'intégration JNI Android en conditions réelles.
- Compilation ARM64 debug et Lint : contrôle final avant publication ; résultats CI et empreinte ajoutés après l'exécution, sans annoncer de succès à l'avance.
- `SafetyPolicy.kt` et les sources C++ inchangés depuis la base 003 ; empreinte du fichier de politique `7fefc8ce65f80375666eefb8ea05d44ab5edee940d49c2d53183e675818f3999`.

Le rapport machine `build/mission003b-validation.json`, les XML JUnit, Lint et le journal natif sont publiés dans `jarvis-llm-validation`. La comparaison GGUF cloud existante produit séparément `jarvis-llm-model-memory` : ses RSS Linux ne sont pas des PSS Android et ne justifient pas une baisse du seuil.

## APK et provenance

Références de l'exécution GitHub Actions, commit source, artefacts et SHA-256 de l'APK à consigner ici après vérification effective. Le paquet `jarvis-llm-debug` contient APK, SHA256SUMS, SOURCE_COMMIT.txt, guide de premier diagnostic, guide catalogue, protocole mémoire et fixtures SYNTHÉTIQUES. Il ne contient aucun modèle.

Les clés debug de runners différents peuvent être différentes : exporter d'abord catalogue utilisateur et conserver GGUF/SHA d'origine avant toute désinstallation. Les contrôles APK portent sur signature debug, permissions, ABI et alignement ; ils ne certifient pas les protections OEM.

## Encore non vérifié sur appareils

PSS/RSS par phase et transitoires réels, coût de l'observation à 500 ms, latence du watchdog sous charge, capteurs/thermal headroom OEM, comportement low-memory/LMK, batterie/autonomie, retour au repos et annulation JNI complète sous ce nouveau coordinateur. L'arrêt antérieur observé en 1–2 s reste une observation utilisateur, pas une borne garantie.

Le coordinateur protège seulement les participants du processus JARVIS LLM ; aucun arbitrage externe CORE/VOICE/INTERFACE n'est revendiqué. Les paramètres de contexte et de modèle non couverts par les relevés resteront non qualifiés. PLEINE PUISSANCE reste refusée.

**Prochaine étape :** protocole [ESSAIS_MEMOIRE_003B.md](ESSAIS_MEMOIRE_003B.md), passif en premier ; si admission naturelle impossible, conserver le refus et recueillir seulement ce diagnostic. Toute nouvelle enveloppe nécessite des données suffisantes et validation humaine explicite. Aucun benchmark intensif autorisé par ce rapport.
