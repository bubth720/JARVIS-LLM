# Tests de revue et campagne après approbation

## Exécutables sans application

`run_review.py --baseline --historical` compile les sources actives figées et leurs tests ; `run_review.py --historical` compile **les postimages exactes sous labadmission/** et les mêmes tests plus les nouveaux cas. Domain et app sont des modules JVM séparés ; seul le friend-path des tests de domaine est autorisé. Aucun :app, JNI, GGUF, Android, patch appliqué ou vrai mandat n'est nécessaire.

Le banc couvre signature absente/fausse, clé/racine, validité/horloge monotone, révocation, nonce/rejeu inter-autorités, session/run/commit/worker, modèle/configuration, demande/UTF, broker étranger/clos/ABA, pins et watchers hors verrou, attestation de résidence perdue, template/compte, buffer borné/retry, terminal, défaut d'autorité, refus de fichier et préservation du moteur existant. Des fichiers locaux **synthétiques**, petits ou sparse de taille autorisée avec mauvais SHA, testent les refus sans télécharger de modèle. Ils sont supprimés et ne sont jamais des poids ou une preuve GGUF.

Les tests injectent explicitement une implémentation NativeApi de fixture et, pour certains refus de cycle de vie, une observation mémoire de fixture. Les clés Ed25519 éphémères restent en mémoire. Aucune fixture ne devient clé opérateur, bail fictif qualifié ou preuve d'inférence. Les scénarios staged de résidence simulent les transitions pour tester les contrats, pas le chargeur.

`contracts.py` compare les surfaces NativeApi/NativeBridge et callbacks par javap ; six programmes volontairement invalides doivent être rejetés pour les raisons attendues, pas une erreur d'infrastructure. `check.py` fige les 457 fichiers, compare modes/SHA et reconstruit les hunks en mémoire, puis exécute uniquement `git apply --check`.

## Après approbation du SHA exact — NOT_RUN aujourd'hui

Exiger aussi une racine opérateur approuvée/provisionnée, un permis signé lié au challenge réel/run/commit/demande/template et une copie du seul GGUF autorisé. Le patch n'acquiert rien ; la présente mission n'autorise pas une nouvelle campagne de téléchargement. Récupérer un hash exact de template avec le probe GGUF autorisé avant signature ; les rapports précédents établissent sa reconnaissance mais pas encore ce hash.

Dans un hôte Linux admis, refuser avant load si cgroup/mémoire/source/permissions/identité ne sont pas attestables. Instrumenter les transitions réellement observées et les IDs du broker en forme expurgée. Ne pas installer une clé générée par le candidat comme approbation de l'opérateur.

1. Load froid réel et validations après load ; comparaison des poids/source/configuration ; tentative de modèle différent, fichier changé, bail fermé, reçu périmé et consommation double.
2. HISTORICAL et STREAM alternés, même handle/configuration/prompt synthétique public, même modèle/SHA/template/sampler déterministe, caps 8/24. Comparer les octets finaux sans trim et l'ordre de fragments réellement produits. Aucun texte final découpé après coup.
3. Unicode réellement généré, carries et fragments UTF-8 ; si un caractère n'est pas produit, garder le cas réel NOT_RUN et distinguer les helpers.
4. Annuler pendant les phases **attestées** de load, prefill, decode, callback et backpressure ; double Stop, Job.cancel et expiration/révocation. Observer maintien des baux et retour natif effectif ; aucun nouveau propriétaire tant qu'un pin ou cleanup échoué subsiste.
5. Consommateur ralenti/suspendu, retry sans perte/duplication, ordre et limites 16 événements/16 Kio, 4096 octets par chunk et 64 Kio total. Aucun changement de ces limites candidates.
6. Injecter refus mémoire et erreurs de retour/callback. Un échec critique arrête la campagne, sans retry. Comparer drains/retours/unload et RSS après unload séparément ; timeout/mort de processus => UNKNOWN, pas reçu UNLOADED inventé.
7. Préserver les résultats négatifs, compte de refus/erreurs/arrêts et versions exactes. Sauvegarder les textes/rapports et hashes, **jamais les poids**.

## Mesures et Android

Processus froid, modèle froid et WARM_VERIFIED doivent être attestés séparément. Le coût du hash/signature/admission fait partie du chemin si exécuté après Submit ; ne pas le retirer pour améliorer le TTFT. Modèle inchangé, mêmes paramètres et repos, aucun maintien artificiel résident au-delà de l'idle historique. Mesurer charge, premier sampling natif, premier UTF-8 utilisable, premier callback, completion, RSS/PSS/pic/CPU/thermique/énergie si observables. Le JNI existant ne donne pas toutes les phases internes ; conserver UNKNOWN/NOT_RUN plutôt que soustraire des horloges non calibrées. p50/p95/max seulement avec assez d'essais comparables, p95 jamais sous 20. Aucune mesure hôte ne vaut Android ou CORE–INTERFACE.

API29/35 et matériels restent NOT_RUN : pas de répétition des runs macOS HV_UNSUPPORTED ; sonde Linux ARM64 précédente BLOCKED_NO_KVM. Chercher un hôte compatible, sans achat ni modification système. Le patch LAB est d'abord hôte Linux ; aucune activation Android productive, APK qualifiée ou campagne personnelle ne découle de sa revue.
