# LLM-02 — Moteur de préparation de prompt et compteur de contexte réel

## 1. Identité officielle et base
**Module :** LLM. **Dépôt :** `bubth720/JARVIS-LLM`. **Mission :** LLM-02 (P1). **Dépendances :** LLM-01.
**Branche code de référence :** `design/003f-f3b-worker-connection` · SHA `8f1c2f5a936d6c01874d7be6bb987f4459b83374`.
**Branche de planification :** `plan/intensive-2026-10-22`. **Branche de code proposée :** `feat/llm-02-runtime`. **État :** préparée, exécution non autorisée.

## 2. Ce qui est déjà développé / bloqué
LLM Android autonome Kotlin/C++ llama.cpp, catalogue SQLite, imports vérifiés, protections RAM/batterie/thermique, budgets. F3B a un diff candidat non appliqué (code backend inchangé, JNI non prouvé Android), contrat /2 fixture bloqué sur F1/F2/F3. CI et tests Java/native Linux historiques ne constituent pas une qualification Android.

## 3. Objectif concret
Remplacer les budgets estimés par une préparation du prompt compatible avec le tokenizer natif, en préservant le système et la sécurité ; livrer le port réel et ses essais isolés avant activation sensible.

## 4. Fonctionnalités à implémenter
1. Créer PromptFrame avec messages system/user/assistant strictement séparés et texte non fiable encadré.
2. Coder TemplatePolicy Qwen3 à partir du template GGUF réel, avec option /no_think explicitement versionnée et fallback refusé si le format sûr n'est pas garanti.
3. Créer NativeTokenCountPort qui calcule le nombre réel via API de tokenize sans lancer une inférence ou un second modèle ; si API historique ne le permet pas sans risque, isoler derrière feature gate et signaler UNSUPPORTED.
4. Implémenter ContextPlanner qui tronque à frontière de tours et préserve SYSTEM, instructions de sécurité et derniers échanges dans budget inchangé.
5. Traiter encodage UTF-8/Modified UTF-8 aux interfaces natives avec tests Unicode BMP et non-BMP, après revue JNI séparée.
6. Générer diagnostic de comptage source/roles/citations sans imprimer le contenu privé dans les logs.

## 5. Architecture, classes et responsabilité
1. PromptFrame/ChatTemplateRenderer/TemplateSafety
2. NativeTokenCountPort et TokenCountResult
3. ContextPlanner et BoundedHistorySelector
4. Utf8BoundaryAdapter et TemplateDiagnostics
Séparation stricte du domaine, des adaptateurs Android et du code JNI. Un modèle résident maximum, support de modèles interchangeables mais pas de moteur doublé.

## 6. Fichiers existants et cibles
1. Existants : domain/src/main/kotlin/fr/jarvis/domain/generation/{P2Prompt,P2Lifecycle}.kt
2. Existants sensibles à revoir, ne pas modifier sans accord : app/src/main/cpp/bridge.cpp, app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt
3. À créer : domain/src/main/kotlin/fr/jarvis/domain/prompt/{ContextPlanner,ChatTemplateRenderer}.kt ; tests
Respecter les sourceSets et chemins réels. Les fichiers présentés « à créer » sont les cibles proposées, non la preuve qu'ils existent.

## 7. Ordre de programmation
1. Lire les résultats GGUF Qwen réels et compatibilité avec source C++ épinglée, sans revendiquer exécution Jinja complète.
2. Implémenter types/domain rendering déterministe séparé du JNI.
3. Construire port TokenCount dont version JVM utilise un backend injectable et version native reste gated jusqu'à revue.
4. Coder sélection de tours par budget sans jamais retirer instructions système.
5. Tester scénarios de 2048 contexte et 128 sortie, puis livrer patch sensible séparé si tokenizer natif nécessaire.

## 8. Technologies, dépendances, performance
Kotlin, tokenizer llama.cpp existant ; zéro moteur additionnel, native patch sur branche distincte après validation.
Ne pas remplacer versions de dépendances ou bibliothèque native à l'aveugle. Kotlin/JVM et compilation ARM64 vérifiables. Contexte2048/sortie128 et admission mémoire existants non relevés ; priorité thermique/batterie.

## 9. Connexions entre modules
CORE/IDENTITY fourniront messages structurés via contrats existants ; ne pas fusionner /1 et /2 ni ouvrir CORE004D implicitement.

## 10. Limites techniques et sécurité
Toute modification JNI du chargement/tokenize/apply_template est sensible et requiert revue/accord explicite avant application.
Préserver ABI JNI historique, signature, packages, ancienne application, modèle privé, IDENTITY, catalogue et stockage. UNKNOWN et cleanup non confirmé restent bloquants. Pas de second moteur, ni plein régime Android.

## 11. Livrables réels
Classes/domain et adaptateurs fonctionnels, build/tests ciblés sur vrais composants, exemple d'utilisation local, rapport concis. Si une partie native nécessite nouvelle autorisation, fournir un patch isolé explicitement non appliqué, sans qualifier la fonction de livrée en production.

## 12. Critères de réception
1. Le plan de prompt conserve les rôles, respecte le budget et refuse un template ambigu.
2. Un comptage réel n'est marqué valide que lorsqu'une implémentation native lui correspond.
3. Les tests Unicode et injection par documents sont ajoutés, sans contourner le gate F3B.
Distinguer compilé, simulé, JNI Linux et JNI Android réel ; jamais d'équivalence automatique.

## 13. Séquençage
LLM-01. Une seule mission LLM active dans les fichiers `bridge.cpp`/`LlamaBackend.kt` à la fois ; les autres dépôts peuvent créer ports indépendants.

## 14. Git et autorisations
Le dépôt documentaire n'autorise ni exécution de Codex, ni patch sensible, ni import de poids privé, ni appareil personnel, ni fusion main. Demander validation du diff sensible exact avant toute application ; créer branches spécifiques.

## 15. Retour CENTRAL obligatoire
Module, mission, branche, commit de base et de livraison, tests effectués, résultats, blocages, risques, prochaine action, et tout code laissé inactif.
