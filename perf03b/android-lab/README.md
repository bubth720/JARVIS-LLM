# PERF-03B — APK de laboratoire distincte

Module opt-in `:perf03b-lab`, ajouté exclusivement par `perf03b/lab.init.gradle`.
ApplicationId : `fr.jarvis.llm.perf03b.lab`. Android min29/target36, ARM64,
NDK28.1.13356709 et CMake3.22.1 historiques. Le manifeste impose `testOnly=true`,
sans permission, réseau, import de modèle, stockage, sauvegarde ou migration.
La clé debug `.tools/perf03b-lab.keystore` est distincte des APK historiques ;
elle utilise des identifiants debug publics, jamais une clé de production.

```bash
bash tools/cloud-env.sh ./gradlew -I perf03b/lab.init.gradle \
  :perf03b-lab:testDebugUnitTest :perf03b-lab:lintDebug :perf03b-lab:assembleDebug \
  --rerun-tasks --no-daemon --max-workers=2 --console=plain
```

Le module compile le **bridge appliqué** de `app/src/main/cpp` et les trois
sources Kotlin appliquées (`LlamaBackend`, `OwnedStreamContract`,
`ProcessResources`) copiées sans transformation dans ses sources générées. Il
ne compile pas les copies `perf03/candidate`. Les contrats de domaine restent
ceux du projet `:domain`. Aucun sourceSet, settings, build ou ressource de
l'application historique n'est modifié pour inclure ce module.

## Ce que fait l'écran

L'écran annonce explicitement `Génération réelle, tokens et TTFT : NOT_RUN`.
Il ne présente ni conversation synthétique ni grant d'autorité fabriqué.
Sans modèle autorisé et autorité réelle validée, il ne propose aucune génération.

Le seul test actif crée un `LlamaBackend` réel sur un thread de contrôle hors UI,
appelle `prepareOperation`, `requestStop` puis `close`. Le backend conserve son
propre worker unique. Aucun `load`, `generate`, `generateOwnedStream` ni modèle
n'est utilisé par l'Activity. Le résultat PASS signifie seulement que ces appels
de cycle de vie ont retourné ; il ne prouve ni token généré, ni résidence de
modèle, ni unload d'un modèle, ni barrière globale DRAINED.

Le propriétaire est retenu lors de la rotation : une nouvelle Activity ne crée
pas de second backend pour un test en cours. L'arrière-plan demande Stop. La
destruction du propriétaire annule sa coroutine, mais son `finally` attend
l'appel `close` en contexte non annulable. Un échec de fermeture conserve
l'instance en quarantaine ; aucune autre factory ni tentative automatique de
nettoyage n'est autorisée. Les tests unitaires injectés exercent ces décisions,
sans les assimiler à une exécution JNI ou Android instrumentée.

## Qualifications séparées

- Compilation ARM64, manifeste final, absence de permission, signature et
  alignement16Kio : à lire dans les preuves de la CI du commit exact.
- Huit tests JVM du contrôleur : doubles refusés, cleanup réel attendu,
  annulation, erreurs et quarantaine. Leur backend est injecté.
- Callbacks JNI hôte : banc distinct `perf03b/native-tests`, pas cet écran.
- Streaming de véritables tokens, identité ancien/nouveau sur modèle,
  Android API29/35 et appareils physiques : NOT_RUN tant que les prérequis
  et accords nécessaires ne sont pas satisfaits.

La présence d'un export JNI streaming dans cette APK n'active pas sa génération.
Aucune installation sur appareil personnel n'est autorisée par sa compilation.
La procédure de qualification physique et son modèle volontaire feront l'objet
de l'accord GATE-B séparé ; aucun Android Studio ni Termux n'est demandé à
l'utilisateur.

## Préparation API29 / API35 pour PERF-03C

1. Partir uniquement de l'APK vérifiée du run exact, de son SHA256 et de son
   certificat de laboratoire. Conserver le rapport `lab-apk.json` et le manifeste.
2. Choisir un émulateur cloud appartenant à la campagne, avec prise en charge
   attestée d'`arm64-v8a`. Une image x86 sans support ARM64 est `BLOCKED_ABI` ;
   ne pas présenter son échec d'installation comme une régression JNI et ne pas
   élargir silencieusement les ABI de l'APK.
3. Identifier explicitement le serial de cet émulateur avant toute installation.
   Le marqueur `testOnly` doit rester présent. Aucun appareil personnel n'est une
   cible implicite de cette procédure.
4. Sans modèle : contrôler lancement, fermeture, cycle natif model-free, Stop,
   rotation sans second propriétaire, arrière-plan, force-stop/redémarrage et
   mode avion. Enregistrer API, ABI, Android build, SHA APK et résultats exacts.
   Un callback d'erreur ou un cycle de vie réussi n'est pas un token généré.
5. La génération et ses benchmarks ne pourront être activés qu'après validation
   des prérequis d'autorité et d'un modèle déjà autorisé, sans téléchargement
   automatique. Réutiliser la matrice [PERF-03](../../perf03/BENCHMARK-PLAN.json)
   : même modèle/template/configuration, états froid/chaud séparés, caps8/24,
   au moins20 essais comparables et protections inchangées.

Ces scénarios sont un plan non exécuté. L'émulateur ne qualifie jamais le TTFT
physique du POCO X8 Pro ou de la Redmi Pad SE. Aucun chiffre absent n'est zéro.
