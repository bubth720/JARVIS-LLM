# Suite PERF-03B — préqualification GATE-B

Base exacte : `059cf9c3040d768e1074f62ec5d174c1a27a2590`, tree `82283b5a03e92af5487fbce08cdaaae8f7d031d5`. Dépôt LLM uniquement ; branche `codex/llm-perf-03b-gateb-prequalification`. Tous les fichiers de cette base, JNI, APK, contrats, anciens workflows et protections compris, sont gelés. Seuls ce dossier et le nouveau workflow sont ajoutés. Le commit/tree testé est inscrit dans les artefacts du run exact ; le complément de livraison de la PR contient les verdicts finaux.

## Android sans modèle

La machine locale est x86_64 sans `/dev/kvm`. L'APK ARM64 n'est pas remplacée par un build x86. La [documentation officielle des runners](https://github.com/actions/runner-images/blob/main/README.md) associe actuellement `macos-15` au runner ARM64 standard. Le workflow tente séparément API29 et API35 sur ce label, sans runner payant large/xlarge. Il enregistre l'architecture et la version d'image réelles, puis vérifie SDK, accélération et disponibilité de chaque image officielle ARM64. Une indisponibilité donne BLOCKED avec la commande et le journal, jamais PASS Android ; un job vert ne signifie donc pas nécessairement que le scénario a été exécuté.

APK réutilisée, sans reconstruction ni modification : source059cf9c3, run42246910154, artefact13520085146, SHA256 `b1dfa7a93850db92019121d4c1a0000bd4a465709cc8e581d4f2a8daa7fa8411`. Le runner vérifie cette empreinte, le serial de son propre émulateur, API/ABI et `ro.kernel.qemu=1` **avant** installation. Aucun ADB par défaut ni appareil personnel n'est utilisé. L'AVD reste hors des rapports ; seuls logs, XML, capture éventuelle et résultats sont archivés avec SHA256,90jours.

Scénarios exécutables : installation testOnly, ouverture, indication NOT_RUN, vrai cycle create/begin/Stop/close sans poids, rotation (survie de l'écran), arrière-plan/retour, force-stop/redémarrage en mode avion. La course Stop UI, les refus thermiques/RAM simulés et la génération restent NOT_RUN : aucune capacité de fixture n'est ajoutée à l'APK pour les inventer. Les blocages d'hyperviseur ou d'image ne sont pas des régressions du JNI.

## Modèle proposé — accord requis

**Qwen/Qwen3-0.6B-GGUF**, fichier **Qwen3-0.6B-Q4_K_M.gguf**, révision immuable `8e42d41f70cb6c571f58c3f31bd9287b372d97cc`.

- [Source officielle Qwen](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/blob/8e42d41f70cb6c571f58c3f31bd9287b372d97cc/Qwen3-0.6B-Q4_K_M.gguf).
- Taille déclarée exacte : **396704416 octets** (396,7Mo /378,3Mio), pas la RAM nécessaire au modèle.
- SHA256 déclaré : `b0638f08417a2d3c8652760462eb5407c6e30173cf9608ad0820757a281eea0e` ; [pointeur LFS textuel vérifiable](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/raw/8e42d41f70cb6c571f58c3f31bd9287b372d97cc/Qwen3-0.6B-Q4_K_M.gguf).
- Apache-2.0 dans la carte du dépôt à cette révision ; [licence du modèle officiel](https://huggingface.co/Qwen/Qwen3-0.6B/blob/main/LICENSE). Usage local de laboratoire permis sous ses conditions : conserver licence et notices ; toute redistribution devra respecter ces conditions. Aucun modèle n'est embarqué ou redistribué ici.

Le modèle correspond **sur déclaration** aux exigences actuelles Qwen3/SMALL/Q4_K_M ; l'admission native doit encore vérifier les métadonnées et400–850millions d'éléments, le template et les limites existantes. Le SHA binaire n'est pas recalculé : seuls page officielle, licence et petit pointeur texte ont été consultés. Aucun poids téléchargé, aucun chargement ni essai. Un modèle plus petit d'une autre famille serait refusé par les protections et n'est pas proposé comme contournement.

[Manifeste](model.json), [protocole réel préparé](REAL-GENERATION-PROTOCOL.md), [audit du raccordement manquant](CORE-LEASE-AUDIT.md).

**Décision attendue avant toute campagne réelle :** autoriser explicitement le téléchargement de cet unique fichier, à cette révision/SHA/taille, pour laboratoire cloud seulement, puis sa vérification et une campagne locale bornée. Cet accord n'autoriserait aucun autre modèle, modification JNI, appareil personnel, raccordement CORE/INTERFACE ou passage automatique de GATE-B. Aucun outil de ce dossier ne télécharge un modèle.
