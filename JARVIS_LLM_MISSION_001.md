# JARVIS LLM — Mission 001 : Étude de faisabilité et architecture

**Statut : analyse exclusivement — aucun développement autorisé.**

## 1. Objectif

Concevoir un module autonome **JARVIS LLM** (application Android indépendante dans un premier temps) offrant une intelligence artificielle **100 % locale**, sans serveur, compte cloud, API distante ni connexion Internet pour les fonctions essentielles. Il devra comprendre le français naturel, exploiter une base documentaire importante, accéder à plus de 1 000 réponses préenregistrées, conserver une mémoire locale contrôlable et, ultérieurement, proposer/exécuter des actions Android autorisées et vérifiables.

Après stabilisation, le module pourra dialoguer avec **JARVIS CORE** (orchestrateur central) et **JARVIS VOICE** (entrées/sorties vocales), sans modifier ces projets pendant cette mission.

## 2. Matériel visé et contraintes

- **POCO X8 Pro**, Android 16, stockage affiché 528 Go au total sur la capture utilisateur. Le téléphone sera d'abord la plateforme d'inférence principale. **Vérifier** chipset, RAM **physique**, accélération CPU/GPU/NPU compatible, ABI, limites thermiques, vitesse de stockage et version du système ; ne pas confondre extension mémoire et vraie RAM.
- **Redmi Pad SE**, Android 15, 256 Go de stockage, dont environ 238,6 Go indiqués comme utilisés sur la capture. Viser une interface et une inférence locale allégée ; ne pas supposer qu'un modèle exigeant y sera fluide. Vérifier la variante exacte, la RAM physique et l'espace libre effectif.
- **Futur matériel dédié** : PC local ou station d'inférence, sans limitation artificielle de taille de modèle. Architecture portable et paramétrable ; ne pas promettre de compatibilité universelle.

La mission doit séparer explicitement : faits vérifiés (documentation et dépôt), hypothèses, estimations, et mesures qui restent à réaliser sur les vrais appareils. **Aucune mesure de performance sur matériel réel ne peut être prétendue sans exécution sur cet appareil.**

## 3. Profils de puissance à prévoir

1. **ÉCO** : petit modèle, contexte réduit, ressources plafonnées, longues périodes d'inactivité possibles.
2. **ÉQUILIBRÉ** : compromis qualité / vitesse / énergie, mode par défaut du téléphone.
3. **PLEINE PUISSANCE** : destiné principalement au futur matériel adapté, modèles plus volumineux et contexte plus long, jamais un moyen de désactiver les protections système.
4. **AUTO** : choix de profil selon le matériel, la disponibilité de la RAM, l'état thermique et les contraintes de batterie.

Les profils sont des **politiques de configuration**, pas quatre moteurs à coder séparément. Prévoir changement de modèle/backend et plan de compatibilité sans reconstruire l'architecture métier.

## 4. Exigences fonctionnelles

### Compréhension
- Intentions formulées en langage naturel, paraphrases, ambiguïtés, contexte et demandes composées.
- Génération libre seulement lorsque récupération documentaire et réponses structurées sont insuffisantes.
- Refus des affirmations non fondées ; citation des passages documentaires utilisés ; possibilité d'admettre l'incertitude.

### Réponses préenregistrées
- Plus de 1 000 entrées catégorisées, variantes facultatives (y compris exactement une seule).
- Métadonnées de contexte, priorité, version, critères d'activation et expressions d'exemple.
- Routage sémantique, pas de dépendance exclusive aux mots-clés ; gestion des conflits et des faux positifs.

### Base documentaire
- Plusieurs milliers de documents potentiels, ingestion et indexation incrémentales, provenance, suppressions/mises à jour, tailles, PDF textuels et cas OCR séparés.
- Étudier SQLite/FTS, embeddings locaux, recherche hybride et éventuel reranking ; coûts RAM / stockage / temps d'indexation.
- Les contenus importés sont **non fiables** : protection contre injection de prompt et exécution d'actions provoquée par des documents.

### Mémoire
- Historique conversationnel, contexte court terme, souvenirs persistants explicites, modification / effacement / export des données.
- Stockage privé Android, permissions minimales, documentation sur la confidentialité et les sauvegardes.

### Actions réelles
- Modèle propose une intention structurée ; **moteur indépendant** valide types, paramètres, autorisations, confirmation et preuve de résultat.
- Catalogue strict d'actions Android, niveaux de risque, annulation, journalisation locale et limites API Android 15/16.
- Aucune exécution de shell arbitraire générée par LLM, pas de root, pas de contournement des protections Android.

### Hors ligne
- Inférence, embeddings, indexation/recherche, mémoire et actions de base disponibles sans Internet après installation/import local des modèles.
- Distinguer **100 % hors Internet** de **fonctionnement indépendant des deux appareils** : la communication entre téléphone et tablette par Wi-Fi local est facultative ; une perte de réseau ne doit pas rendre le module inutilisable en autonomie.
- Préciser comment importer modèles et données sans cloud ni télémétrie ; évaluer permissions réseau et vérification d'intégrité des ressources.

### Protection du matériel
- Mesurer et limiter RAM, CPU, concurrence, batterie et échauffement ; politique de mise en veille/déchargement du modèle ; surveillance via API Android disponibles, throttling, annulation et arrêt.
- Définir des limites adaptatives, des seuils prudents à mesurer, et un comportement sûr en cas de mémoire insuffisante, appareil chaud, batterie faible ou mode économie d'énergie.
- Ne pas promettre **zéro usure** ni suppression de tous les risques matériels ; aucune désactivation des protections thermiques natives.

## 5. Références et cohabitation avec JARVIS CORE

Le dépôt existant est **`bubth720/JAVIS-Local-v2`** (bien respecter l'orthographe `JAVIS`). Le consulter **en lecture seule**, en particulier `AGENTS.md` et `docs/PROJECT_KNOWLEDGE.md`, et confirmer l'état présent du code avant toute comparaison.

Sa documentation récente indique déjà un moteur GGUF (`llama-android`), Qwen3 1.7B prévu, SQLite, historique, recherche lexicale PDF, Whisper et TTS. **Éviter une duplication injustifiée** : fournir une matrice de réutilisation, de séparation des responsabilités et de migration future, sans toucher au dépôt CORE.

Pour la future interface : proposer un contrat local versionné (`interpret`, `answer`, `plan_action`, `execute_action`, `get_status`), avec séparation stricte entre requête, autorisation et exécution. Évaluer Binder/AIDL ou autre IPC Android ainsi que, séparément, une option réseau local chiffrée entre appareils. N'implémenter aucune connexion dans cette mission.

## 6. Comparaisons techniques obligatoires

- **Modèles** : au minimum Qwen3 0.6B, Qwen3 1.7B, et un modèle intermédiaire/plus puissant pertinent, avec performances en français, licence, format, tailles quantifiées, RAM totale attendue et limites des tâches complexes.
- **Moteurs** : llama.cpp / Android JNI, LiteRT-LM ou autre moteur embarqué crédible. Vérifier support réel CPU/GPU/NPU sur les appareils ciblés (ne pas supposer une accélération NPU), contraintes de maintenance et licences.
- **Retrieval** : lexical seul, embeddings seuls, hybride et éventuel reranker.
- **Actions** : Android intents, API système autorisées, permissions et restrictions d'arrière-plan.
- **Architecture** : application Android autonome aujourd'hui, service Android/IPCs demain, futur backend local dédié. Comparer coûts, risques, modes de panne et degré de portabilité.

## 7. Livrables requis (documentation uniquement)

Produire dans la réponse Codex les contenus suivants ; ne créer les fichiers dans le nouveau dépôt **JARVIS-LLM** que si l'utilisateur l'autorise explicitement :

1. `docs/FEASIBILITY.md` : faisabilité, contraintes matérielles, distinction fait/hypothèse, risques et recommandations.
2. `docs/ARCHITECTURE.md` : composants, flux, interfaces et frontières de sécurité ; diagramme simple.
3. `docs/MODELS_AND_ENGINES.md` : matrice de comparaison sourcée, licences et dimensionnements.
4. `docs/DEVICE_SAFETY.md` : profils ÉCO, ÉQUILIBRÉ, PLEINE PUISSANCE, AUTO ; règles thermiques / batterie et gestion des ressources.
5. `docs/ROADMAP.md` : phases progressives, dépendances, critères d'acceptation, scénarios de tests, risques et points de décision.
6. `docs/CORE_COMPATIBILITY.md` : lecture factuelle de CORE existant, capacités déjà présentes, séparation des responsabilités, interfaces futures et plan anti-duplication.

Fournir enfin un **résumé en français de moins de 15 lignes** avec la configuration de départ recommandée, les incertitudes et le feu vert / non-feu vert pour le prototype.

## 8. Protocole de validation à PROPOSER (pas à exécuter durant cette mission)

- Vérification du véritable hors-ligne, de l'intégrité des modèles et des données.
- Latence au premier token, tokens/s, p50/p95, RAM maximale / PSS, température, énergie et batterie sur vraies machines.
- Tests linguistiques français avec intentions paraphrasées, ambiguës, multi-étapes ; jeux de tests distincts des exemples enregistrés.
- RAG : Recall@5, preuves utilisées, exactitude, refus si documents insuffisants.
- Actions : aucune action non autorisée, confirmations obligatoires sur opérations sensibles, journalisation et test d'annulation.
- Stress, batterie faible, mémoire sous pression, reprise après interruption.

## 9. Limites strictes de la mission

- **NE PAS CODER** ; aucun projet Android, APK, prototype, téléchargement de modèles ou benchmark prétendument réalisé.
- **NE PAS MODIFIER JARVIS CORE ni JARVIS VOICE**, ni y créer de branche, fichier, commit ou PR.
- Ne pas créer ni modifier d'autres fichiers GitHub sans nouvelle autorisation explicite. Si un autre dépôt est requis, le signaler à l'utilisateur sans substituer CORE.
- Ne pas choisir une technologie seulement sur promesse marketing ; signaler les problèmes de compatibilité qui nécessitent des mesures sur appareil.
- S'arrêter après la remise du dossier d'analyse et demander la validation de l'utilisateur avant la mission 002.

**Réponse attendue : rapport argumenté, adapté au POCO X8 Pro et à la Redmi Pad SE, et recommandation d'architecture évolutive jusqu'au mode PLEINE PUISSANCE.**
