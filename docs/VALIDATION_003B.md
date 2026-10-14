# Validation Mission 003B

## État avant essais physiques

Version 0.3.1-mission003b, branche `mission-003b-resources`, issue de `mission-003-catalogue` (0b5eba7). Aucun budget mémoire diminué : SMALL 2 Gio ; réserve système et protections existantes conservées. Aucun autre projet JARVIS modifié, aucune fusion dans main.

Le relevé utilisateur d'environ 593 Mio PSS sur Redmi ne constitue pas un résultat de cette campagne. **Aucune mesure matérielle nouvelle ni économie RAM/autonomie démontrée sur appareil** dans le cloud.

## Vérifications cloud locales

- 55 tests domaine JVM, dont capture/parseur mémoire et coordination exclusive/concurrente : réussis.
- 16 tests Android simulés Robolectric API 35, dont catalogue SQLite et 3 tests du cycle diagnostic/déchargement : réussis. Aucun appel JNI ni appareil Android réel dans ces simulations.
- Test natif CTest `native_safety` : réussi. Ce test couvre les mécanismes natifs existants, pas la latence d'arrêt sur tablette ni l'intégration JNI Android en conditions réelles.
- Compilation ARM64 debug réussie. Lint CI : **0 erreur, 36 avertissements** (local : 32 avertissements). Les avertissements ne sont pas assimilés à des tests matériels validés.
- Contrôle APK réussi : signature debug, ARM64, alignement ELF/ZIP 16 Kio, aucune permission réseau ni d’accès aux données (seule permission privée AndroidX).
- Les mêmes 55 tests domaine et 16 simulations Android sont réussis dans GitHub Actions, zéro échec/erreur/ignoré ; CTest natif 1/1 réussi. Les trois jobs de l’exécution sont réussis.
- `SafetyPolicy.kt` et les sources C++ inchangés depuis la base 003 ; empreinte du fichier de politique `7fefc8ce65f80375666eefb8ea05d44ab5edee940d49c2d53183e675818f3999`.

Le rapport machine `build/mission003b-validation.json`, les XML JUnit, Lint et le journal natif sont publiés dans `jarvis-llm-validation`. La comparaison GGUF cloud existante produit séparément `jarvis-llm-model-memory` : ses RSS Linux ne sont pas des PSS Android et ne justifient pas une baisse du seuil.

## APK et provenance

- **Exécution entièrement réussie :** https://github.com/bubth720/JARVIS-LLM/actions/runs/38636152472
- **Commit source testé et embarqué :** `e9d408e77319f4998821e723ef8bc0c872ee3274`.
- **Téléchargement APK / guides :** https://github.com/bubth720/JARVIS-LLM/actions/runs/38636152472/artifacts/11928576282 (`jarvis-llm-debug`, connexion GitHub généralement requise, conservation 30 jours).
- **Rapports tests / Lint / natif :** https://github.com/bubth720/JARVIS-LLM/actions/runs/38636152472/artifacts/11928257071
- **Comparaison GGUF cloud :** https://github.com/bubth720/JARVIS-LLM/actions/runs/38636152472/artifacts/11927929442
- **SHA-256 APK publié**, distinct de celui du ZIP : `6c6e98605012fa1ec50f36db4ecb93501d4ec9d5e2cf10e79b42f4f146981566`.

La notice CI publie l’empreinte de l’APK après les contrôles et la préparation du paquet ; GitHub publie séparément l’empreinte du ZIP. Résultats observés sur les pages publiques GitHub Actions, pas par téléchargement du ZIP dans ce cloud. Preuve structurée : [mission003b-e9d408e.json](evidence/mission003b-e9d408e.json). Un commit documentaire ultérieur consigne ces résultats sans modifier le code de l’APK ni relancer la CI.

 Le paquet `jarvis-llm-debug` contient APK, SHA256SUMS, SOURCE_COMMIT.txt, guide de premier diagnostic, guide catalogue, protocole mémoire et fixtures SYNTHÉTIQUES. Il ne contient aucun modèle.

Les clés debug de runners différents peuvent être différentes : exporter d'abord catalogue utilisateur et conserver GGUF/SHA d'origine avant toute désinstallation. Les contrôles APK portent sur signature debug, permissions, ABI et alignement ; ils ne certifient pas les protections OEM.


## Résultats mémoire effectivement disponibles

Le contrôle de non-régression cloud a exécuté de vrais GGUF dans des processus Linux x86_64 neufs : CPU un thread, une consigne française, sortie limitée à 24 tokens, deadline native 60 s. Pour Qwen3 0.6B Q4_K_M, SHA `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a` :

| Configuration existante | Pic RSS Linux | Buffer contexte déclaré |
|---|---:|---:|
| 768 / batch 32-8 | 515,94 Mio | 84 Mio |
| 512 / batch 16-4 | 487,85 Mio | 56 Mio |

L’écart d’environ 28,09 Mio RSS est mesuré dans cette comparaison courte **cloud**, entre des configurations déjà disponibles avant 003B. Ce n’est ni un nouveau gain apporté par 003B ni une économie Android démontrée. Les buffers et le RSS ne s’additionnent pas. La mission ajoute l’observation et l’exclusion de travaux concurrents ; elle ne modifie ni poids, ni contexte, ni enveloppe d’admission. Aucun pourcentage d’autonomie ou gain PSS tablette n’est inventé.

Le catalogue conservé passe l’essai SQLite 1 508 enregistrements dont 1 500 synthétiques sans charger llama.cpp (détails dans la preuve structurée). Ses temps/compteurs de tas JVM sont cloud, pas des PSS Android.

## Encore non vérifié sur appareils

PSS/RSS par phase et transitoires réels, coût de l'observation à 500 ms, latence du watchdog sous charge, capteurs/thermal headroom OEM, comportement low-memory/LMK, batterie/autonomie, retour au repos et annulation JNI complète sous ce nouveau coordinateur. L'arrêt antérieur observé en 1–2 s reste une observation utilisateur, pas une borne garantie.

Le coordinateur protège seulement les participants du processus JARVIS LLM ; aucun arbitrage externe CORE/VOICE/INTERFACE n'est revendiqué. Les paramètres de contexte et de modèle non couverts par les relevés resteront non qualifiés. PLEINE PUISSANCE reste refusée.

**Prochaine étape :** protocole [ESSAIS_MEMOIRE_003B.md](ESSAIS_MEMOIRE_003B.md), passif en premier ; si admission naturelle impossible, conserver le refus et recueillir seulement ce diagnostic. Toute nouvelle enveloppe nécessite des données suffisantes et validation humaine explicite. Aucun benchmark intensif autorisé par ce rapport.
