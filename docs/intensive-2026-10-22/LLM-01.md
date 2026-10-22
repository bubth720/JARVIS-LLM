# LLM-01 — Catalogue GGUF interchangeable et gestionnaire de provenance local

## 1. Identité officielle et base
**Module :** LLM. **Dépôt :** `bubth720/JARVIS-LLM`. **Mission :** LLM-01 (P0). **Dépendances :** Aucune.
**Branche code de référence :** `design/003f-f3b-worker-connection` · SHA `8f1c2f5a936d6c01874d7be6bb987f4459b83374`.
**Branche de planification :** `plan/intensive-2026-10-22`. **Branche de code proposée :** `feat/llm-01-runtime`. **État :** préparée, exécution non autorisée.

## 2. Ce qui est déjà développé / bloqué
LLM Android autonome Kotlin/C++ llama.cpp, catalogue SQLite, imports vérifiés, protections RAM/batterie/thermique, budgets. F3B a un diff candidat non appliqué (code backend inchangé, JNI non prouvé Android), contrat /2 fixture bloqué sur F1/F2/F3. CI et tests Java/native Linux historiques ne constituent pas une qualification Android.

## 3. Objectif concret
Faire de l'application LLM un moteur réellement interchangeable : gestion de modèles locaux vérifiés, profils et sélections, sans télécharger ni charger automatiquement des poids.

## 4. Fonctionnalités à implémenter
1. Créer ModelDescriptor (URI opaque, SHA256, taille, architecture, quantification, licence, capacités et date vérification).
2. Coder VerifiedModelCatalog persistant en stockage privé SQLite, index d'identité exact et détection de duplication.
3. Étendre le ModelStore existant pour importer en streaming SAF une seule copie vérifiée à la fois avec vérifications d'espace et reprise atomique.
4. Créer ModelCompatibilityChecker distinguant compatible statiquement, importable, exécutable non qualifié, incompatible, et motif précis.
5. UI locale de sélection et suppression volontaire ; aucun modèle sélectionné ne se charge automatiquement.
6. Conserver historiques profils tablette/mobile ; préparer manifestes de modèles mini-PC sans accroître ressources Android.

## 5. Architecture, classes et responsabilité
1. ModelDescriptor/ModelCapabilities/ModelQualificationState
2. VerifiedModelCatalog + ModelImportCoordinator
3. CompatibilityPolicy + LicenseProvenance
4. ModelSelectionViewModel + ModelStoreAdapter
Séparation stricte du domaine, des adaptateurs Android et du code JNI. Un modèle résident maximum, support de modèles interchangeables mais pas de moteur doublé.

## 6. Fichiers existants et cibles
1. Existants : app/src/main/java/fr/jarvis/llm/{ModelStore,ChatViewModel,MainActivity}.kt
2. À créer : app/src/main/java/fr/jarvis/llm/model/{VerifiedModelCatalog,ModelDescriptor,ModelCompatibilityChecker}.kt
3. À créer : domain/src/main/kotlin/fr/jarvis/domain/model/ModelSelection.kt et tests
Respecter les sourceSets et chemins réels. Les fichiers présentés « à créer » sont les cibles proposées, non la preuve qu'ils existent.

## 7. Ordre de programmation
1. Réemployer SHA SAF, budgets SafetyPolicy et catalogues antérieurs ; établir schéma additionnel distinct.
2. Implémenter vérification streaming de modèles référencés et conservation licence métadonnée.
3. Coder registre, états et refus ; exposer écran de sélection contrôlé.
4. Démontrer import d'un petit modèle synthétique uniquement pour format/refus, et inspection fichier public déjà autorisé seulement si accessible.
5. Compiler et tester doublons, hash invalide, import interrompu, absence de poids.

## 8. Technologies, dépendances, performance
Kotlin/Android, SQLite et SAF existants, SHA256 JVM ; aucune librairie native additionnelle.
Ne pas remplacer versions de dépendances ou bibliothèque native à l'aveugle. Kotlin/JVM et compilation ARM64 vérifiables. Contexte2048/sortie128 et admission mémoire existants non relevés ; priorité thermique/batterie.

## 9. Connexions entre modules
CORE consommera sélection et capacité après future intégration ; aucune clé/URI de modèle dans UNIFIED.

## 10. Limites techniques et sécurité
Aucun téléchargement autorisé hors Qwen épinglé déjà approuvé côté CORE ; aucun transfert poids ni installation.
Préserver ABI JNI historique, signature, packages, ancienne application, modèle privé, IDENTITY, catalogue et stockage. UNKNOWN et cleanup non confirmé restent bloquants. Pas de second moteur, ni plein régime Android.

## 11. Livrables réels
Classes/domain et adaptateurs fonctionnels, build/tests ciblés sur vrais composants, exemple d'utilisation local, rapport concis. Si une partie native nécessite nouvelle autorisation, fournir un patch isolé explicitement non appliqué, sans qualifier la fonction de livrée en production.

## 12. Critères de réception
1. Deux descripteurs de modèles se consultent, se sélectionnent et se désélectionnent avec persistance réelle.
2. Un import invalide est rejeté sans effacer le modèle précédent.
3. Le catalogue ne déclenche jamais un second chargement JNI.
Distinguer compilé, simulé, JNI Linux et JNI Android réel ; jamais d'équivalence automatique.

## 13. Séquençage
Aucune. Une seule mission LLM active dans les fichiers `bridge.cpp`/`LlamaBackend.kt` à la fois ; les autres dépôts peuvent créer ports indépendants.

## 14. Git et autorisations
Le dépôt documentaire n'autorise ni exécution de Codex, ni patch sensible, ni import de poids privé, ni appareil personnel, ni fusion main. Demander validation du diff sensible exact avant toute application ; créer branches spécifiques.

## 15. Retour CENTRAL obligatoire
Module, mission, branche, commit de base et de livraison, tests effectués, résultats, blocages, risques, prochaine action, et tout code laissé inactif.
