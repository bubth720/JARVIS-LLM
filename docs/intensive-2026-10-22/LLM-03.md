# LLM-03 — Worker natif unique, récupération sûre et profils Android/mini-PC

## 1. Identité officielle et base
**Module :** LLM. **Dépôt :** `bubth720/JARVIS-LLM`. **Mission :** LLM-03 (P2). **Dépendances :** LLM-01,LLM-02,UNI-01.
**Branche code de référence :** `design/003f-f3b-worker-connection` · SHA `8f1c2f5a936d6c01874d7be6bb987f4459b83374`.
**Branche de planification :** `plan/intensive-2026-10-22`. **Branche de code proposée :** `feat/llm-03-runtime`. **État :** préparée, exécution non autorisée.

## 2. Ce qui est déjà développé / bloqué
LLM Android autonome Kotlin/C++ llama.cpp, catalogue SQLite, imports vérifiés, protections RAM/batterie/thermique, budgets. F3B a un diff candidat non appliqué (code backend inchangé, JNI non prouvé Android), contrat /2 fixture bloqué sur F1/F2/F3. CI et tests Java/native Linux historiques ne constituent pas une qualification Android.

## 3. Objectif concret
Finaliser le véritable cycle de vie de l'unique worker llama.cpp et préparer l'extension mini-PC, en évitant les faux retours 'stopped' et les deux modèles en mémoire.

## 4. Fonctionnalités à implémenter
1. Créer NativeHandleRegistry complet et corrélé aux epochs/réservations/retours natifs, en s'appuyant sur F3B patch non appliqué.
2. Ajouter état DRAINING, RELEASE_CONFIRMED, UNKNOWN et trace native structurée sans texte de prompt ni données personnelles.
3. Séparer demande d'arrêt logique de confirmation native de fin; refuser toute nouvelle admission tant que le worker précédent existe.
4. Créer RuntimePolicyProfiles TABLET_ECO/MOBILE_ECO/EQUILIBRE et DEDICATED_FULL_POWER, dernier non sélectionnable sous Android.
5. Introduire RuntimeBackendPort interchangeable pour Android ARM64 et futur mini-PC CPU, avec une seule autorité active.
6. Écrire démo instrumentée du cycle de vie isolée avec backend de test ; toute preuve native doit être exécutée sur vrai runtime approuvé.

## 5. Architecture, classes et responsabilité
1. NativeHandleRegistry + NativeGenerationReceipt
2. LifecycleAuthority + RuntimeResourceLease
3. RuntimePolicyProfiles + HardwareCapabilityDetector
4. RuntimeBackendPort + SingleOwnerGate
Séparation stricte du domaine, des adaptateurs Android et du code JNI. Un modèle résident maximum, support de modèles interchangeables mais pas de moteur doublé.

## 6. Fichiers existants et cibles
1. Existants : app/src/main/cpp/bridge.cpp, app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt, app/src/main/java/fr/jarvis/llm/ProcessResources.kt
2. Existants : domain/src/main/kotlin/fr/jarvis/domain/resources/ResourceCoordinator.kt ; docs/mission-003f-f3b/SENSITIVE.patch
3. À créer sous gate : app/src/main/java/fr/jarvis/llm/engine/{NativeHandleRegistry,RuntimePolicyProfiles}.kt et tests
Respecter les sourceSets et chemins réels. Les fichiers présentés « à créer » sont les cibles proposées, non la preuve qu'ils existent.

## 7. Ordre de programmation
1. Revue diff F3B SHA et décisions D4-02D ; ne pas appliquer sans accord sensible.
2. Coder domain state machine et profils indépendamment du backend natif.
3. Créer couche d'observation et stockage de reçus uniquement dans la future branche d'application approuvée.
4. Brancher un seul worker préservant budgets et refus UNKNOWN, puis compiler ARM64.
5. Qualifier sur runtime approprié ultérieurement ; documenter précisément les différences observées Android/Linux.

## 8. Technologies, dépendances, performance
Kotlin, C++ NDK, llama.cpp déjà épinglée, Android thermal APIs sans élargir budgets ; mini-PC via adaptateur futur.
Ne pas remplacer versions de dépendances ou bibliothèque native à l'aveugle. Kotlin/JVM et compilation ARM64 vérifiables. Contexte2048/sortie128 et admission mémoire existants non relevés ; priorité thermique/batterie.

## 9. Connexions entre modules
CORE demeure orchestrateur, LLM moteur unique ; fixtures P2 pas raccordées à production tant que F1/F2/F3 non résolus.

## 10. Limites techniques et sécurité
Mission partiellement sensible : ne pas appliquer le patch F3B ni changer JNI, admissions, permissions ou signatures sans validation exacte.
Préserver ABI JNI historique, signature, packages, ancienne application, modèle privé, IDENTITY, catalogue et stockage. UNKNOWN et cleanup non confirmé restent bloquants. Pas de second moteur, ni plein régime Android.

## 11. Livrables réels
Classes/domain et adaptateurs fonctionnels, build/tests ciblés sur vrais composants, exemple d'utilisation local, rapport concis. Si une partie native nécessite nouvelle autorisation, fournir un patch isolé explicitement non appliqué, sans qualifier la fonction de livrée en production.

## 12. Critères de réception
1. Tests d'état interdisent deux résidences et toute réadmission après UNKNOWN.
2. Code natif produit véritables reçus seulement après autorisation et instrumentations réelles.
3. Pleine puissance ne peut jamais s'activer sur tablette/téléphone.
Distinguer compilé, simulé, JNI Linux et JNI Android réel ; jamais d'équivalence automatique.

## 13. Séquençage
LLM-01,LLM-02,UNI-01. Une seule mission LLM active dans les fichiers `bridge.cpp`/`LlamaBackend.kt` à la fois ; les autres dépôts peuvent créer ports indépendants.

## 14. Git et autorisations
Le dépôt documentaire n'autorise ni exécution de Codex, ni patch sensible, ni import de poids privé, ni appareil personnel, ni fusion main. Demander validation du diff sensible exact avant toute application ; créer branches spécifiques.

## 15. Retour CENTRAL obligatoire
Module, mission, branche, commit de base et de livraison, tests effectués, résultats, blocages, risques, prochaine action, et tout code laissé inactif.
