# PERF-03B-GATE-B — GGUF autorisé, admission fermée

Module LLM, dépôt `bubth720/JARVIS-LLM`. Branche `codex/llm-perf-03b-gateb-qualification`. Base de préqualification exacte `95bad9cac56dd30cc67eacc7239cf48ffb9dcac3`, runtime de référence `059cf9c3040d768e1074f62ec5d174c1a27a2590`. Tous les fichiers de cette base sont figés. Seuls `qualification/` et le nouveau workflow sont ajoutés ; aucune modification JNI, CORE/INTERFACE, protection ou fusion.

## Autorisation et campagne

[authorization.json](authorization.json) décrit l'accord utilisateur limité au GGUF officiel Qwen3-0.6B-Q4_K_M, révision8e42d41f70cb6c571f58c3f31bd9287b372d97cc,396704416octets, SHA256b0638f08417a2d3c8652760462eb5407c6e30173cf9608ad0820757a281eea0e. Il ne s'agit pas d'un mandat CORE, d'un bail ou d'un droit d'ouverture des ports productifs.

Le workflow réalise une seule acquisition depuis l'URL officielle immuable et ses redirectionsHTTPS du fournisseur. Aucune autre URL de poids ni modèle de repli. La licence Apache-2.0, la carte à la révision autorisée et les notices éventuelles du modèle source sont récupérées et conservées avant les poids. La révision exacte de la licence observée et son SHA256 sont enregistrés. Le pointeurLFS est comparé à l'accord. Toute différence taille/SHA, erreur réseau ou redirection non autorisée ferme la campagne sans chargement ni retry automatique. Le fichier est relu pour recalculer indépendamment son SHA après écriture.

Les poids sont placés uniquement dans un répertoire privé éphémère du runner, **hors checkout et hors rapports**, puis supprimés en fin de job, succès ou échec. Aucune mise en cache, APK, Git ou publication des poids. L'artefact est construit dans un second répertoire, par allowlist de petits fichiers texteUTF8 ; extension de poids, magieGGUF, binaire, symlink ou fichier trop grand sont refusés. Même en cas d'erreur de staging, l'upload ne pointe jamais sur le répertoire privé/raw. Le contrôle de source refuse toute nouvelle entrée de poids.

## Métadonnées avant tout chargement

`authorized_metadata` est compilé contre la révision llama.cpp historique épinglée par `tools/prepare-native.sh`, sans modifier ce script ni les validateurs. Il appelle `gguf_init_from_file(...,{true,nullptr})` et le **vrai** `validate_metadata` de `model_check.h`, sans allocation des poids, Engine, contexte de modèle ou sampler. Il vérifie Qwen3/SMALL/Q4_K_M, dimensions/nombre des tenseurs/éléments et présence/reconnaissance statique du template par la bibliothèque épinglée. `context_params(512,1,true)` vérifie les paramètres inchangés ; caps8/24 restent sous la limite native. Les tenseurs/poids ne sont jamais convertis.

Le wrapper revérifie taille et SHA avant le probe, borne son espace d'adressage à2Gio et son CPU à30s. Le picRSS de **ce probe de métadonnées** est distinct de la RAM d'un modèle résident, qui reste NOT_RUN. Reconnaître un template sans modèle n'est pas un comptage/tokenizer exact de la demande ni une preuve d'inférence. Tout refus natif est conservé et arrête la suite ; aucun garde n'est adapté pour accepter le fichier.

## Décision d'admission

`admission.py` observe les sources réellement gelées et ne peut retourner un PASS génératif. Le port historique annonce nativeReady=false, le tokenizer structuré demeure UNSUPPORTED, l'autorité productive n'a pas d'implémentation, et l'identité/révision réelle des baux n'est pas exposée. Le backend exige une autorité liée à sa préparation et à sa résidence avant le JNI. Le consentement au fichier ne remplit pas ces preuves.

La génération, le chargement des poids, TOKEN, annulation prefill/decode, backpressure réelle, résidence et nettoyage d'un modèle restent **BLOCKED/NOT_RUN** tant que l'admission ne peut être attestée légitimement. Aucun appel direct à generateStream/load ou au probe historique de mémoire/génération n'est utilisé pour contourner le port. Aucun UUID, mandat ou bail fictif n'est fabriqué. Une CI de contrôle réussie ne signifie pas GATE-B PASS.

[Proposition d'admission séparée, NON APPLIQUÉE](LAB-ADMISSION-PROPOSAL.md) ; [contrat illustratif non compilé](LabAdmission-proposal.kt.txt). Toute évolution de contrat ou des sources approuvées exige une nouvelle revue/validation. Le protocole réel préparé dans `prequalification/REAL-GENERATION-PROTOCOL.md` reste conditionné à ces prérequis.

## Preuves et résultats

Les verdicts de la campagne sont générés dans download.json, metadata.json, admission.json, preservation.json et les logs de compilation du head exact. Ils sont publiés en complément de la PR après lecture des archives, avec commit/tree et SHA256. Les résultats en configuration sont PENDING, jamais présumés PASS. Les tests unitaires vérifient checksum/taille/troncature, redirections, absence de réécriture et exclusion des poids de la publication ; leurs fixtures ne sont pas des modèles.

Mesures génératives :0essai comparable ; p50/p95/max/débit=null, NOT_RUN ; aucune valeur absente n'est remplacée par0. Aucun résultat hôte n'est attribué à Android ou CORE–INTERFACE. Le modèle n'est ni préservé résident ni dupliqué ; aucun moteur de génération n'est créé dans cette campagne fermée. GATE-B attend toujours la validation explicite de l'utilisateur et ne sera pas déclaré atteint sans les essais réels.

Les rapports texte sont conservés90jours avec SHA256SUMS ; les notices Apache restent avec eux. Seule cette archive texte peut être sauvegardée. Il ne faut jamais ajouter les poids à une archive pour préserver une preuve. La licence, l'URL/révision, taille et SHA suffisent à identifier le fichier vérifié. Le garde historique du manifeste reste inchangé et son FAIL connu n'est pas transformé en succès.

[Préparation PERF-03C](PERF-03C.md). Ni acquisition de service, appareil personnel, changement de protection, modèle différent ni fusion sont autorisés par cette mission.
