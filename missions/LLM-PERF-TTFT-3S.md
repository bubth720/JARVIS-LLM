# JARVIS LLM — PERF-02 : objectif premier texte réel < 3 secondes

**Statut : mission de conception et mesure, pas activation native.**
Dépôt : `bubth720/JARVIS-LLM`.
Branche mission : `mission/llm-perf-ttft-3s-20261104`.
Base immuable : `b670181987d39d27e2d0d9574fa448397a38fe42` (M50-S1 A2, PR#8).
Créer branche d'implémentation `codex/llm-perf-ttft-3s` issue de la mission et PR brouillon.

## Problème prouvé dans les sources

`app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt` retourne un `String` **complet** depuis `suspend fun generate(request: GenerationRequest): String`. Le JNI `NativeApi.generate/generateP2` retourne `ByteArray` complet. Le client ne peut donc pas afficher le véritable premier token d'une longue réponse avant la fin. Aucun objectif de temps au premier token ne peut être revendiqué par les seuls tests synthétiques CORE/INTERFACE. Ne pas confondre temps complet, temps avant premier token, ACK et simple écran prêt.

## Objectifs à atteindre (hypothèses mesurables, non garanties)

1. **TTFT réel** : objectif `p95 < 3000 ms` entre envoi validé et premier token de texte IA affiché, sur un profil Android ECO et un modèle supporté **déjà chargé**, matériel autorisé, avec corpus d'essai fixé. Cold start et import de modèle mesurés séparément.
2. **Réponse courte complète** : viser `p95 < 3000 ms` seulement pour un jeu de réponses courtes explicitement défini (par exemple 8 à 24 tokens), si le matériel le permet; jamais pour des réponses de longueur arbitraire.
3. **Correctitude sans compromis** : jamais inventer un ACK, un token, un unload, une citation ou une identité. Si budget, chauffe ou mémoire insuffisants : réponse de capacité indisponible, pas de contournement.
4. **Énergie/batterie** : pas de modèle maintenu artificiellement actif ni d'augmentation des threads, contexte ou budgets RAM sans preuve et autorisation. Pleine puissance réservée à du matériel adapté.

## Phase A — Baseline et instrumentation non intrusive (autorisée)

Lire `AGENTS.md` si disponible, LLM #6/#8, `LlamaBackend.kt`, `NativeBridge`, `RuntimePolicyProfiles`, `docs/fusion-02/NATIVE-REVIEW.md`, tests JNI et provenance des modèles.

Sur branche isolée et fixture sans modèle privé : produire une matrice temporelle **tracée par identifiant de requête**, sans texte ni chemin :
- t_ui_send, t_CORE_accept, t_engine_enter, t_native_first_token si réellement exposé (sinon `UNSUPPORTED`), t_ui_first_render et t_completed ;
- temps warm versus cold; tokens d'entrée exacts seulement si tokenizer qualifié, sinon `UNKNOWN` ;
- p50/p95/max sur au moins 20 tours comparables et 2 longueurs de réponse, 0 sorties de texte dans les logs ;
- spécifier durée de chargement, RSS/PSS, annulation, retour de drain, chauffe et consommation si disponibles.
- Toute mesure de p95 avec une source de temps manquante ou un modèle simulé est `NOT_RUN`, jamais 0 ms.

Créer des tests JVM sur les instruments (clock monotone, erreur clock, absence de token, sortie hors ordre, double emit, Stop et UNKNOWN). Les tests ne peuvent pas attester un Android physique.

## Phase B — Architecture du streaming (PROPOSITION avant native)

Définir contrat candidat `NativeTokenEvent` : request/session/generation/epoch, seq monotone, UTF-8 chunk borné, ack, terminal, cancel, provenance de modèle et bail; UI reçoit uniquement des tokens réels du worker. Ne pas dupliquer le LLM ni le scheduler; CORE reste autorité et propriétaire du droit de répondre.

Évaluer chemin callback JNI/llama.cpp versus polling de buffer borné; séparer sécurité de threads, jobject global refs, backpressure, process death, UAF, cancel/unload, double réponse, token après terminal, generation invalidée. Aucun callback JNI productif, changement ABI ni nouvelle bibliothèque native sans accord explicite sur un patch exact.

Prévoir un mode **réponse déterministe locale** pour quelques commandes très simples, avec provenance et type explicites ; cela ne s'affiche jamais comme sortie du modèle. Ne pas dégrader qualité du modèle, budget de contexte exact ou sources pour gagner artificiellement quelques secondes.

## Phase C — Qualification et revue

Comparer baseline et variante candidate sur même matériel, même modèle importé volontairement, mêmes prompts synthétiques, mêmes politiques ressources et mêmes longueurs. Rendre rapport :
- module/mission/branche/SHA/tree ;
- diff, garde historique et CI, tests host / JVM / native / Android et statut physique ;
- p50/p95 TTFT et réponse complète **avec données**, sinon `NOT_RUN` ;
- thermiques/énergie/RAM et erreurs ;
- blocages JNI/unload, contrat CORE/INTERFACE et sécurité ;
- matrice de décision pour ECO et ÉQUILIBRÉ avec seuils matériel ;
- rollback réversible.
Interdire tout merge main, modèle téléchargé sans permission, import privé, installation appareil personnel, activation du JNI streaming ou changement de signature/permission/ABI sans validation de l'utilisateur.

## Critère de succès de cette mission

La mission de mesure/proposition est terminée quand le code de benchmark non productif et les contrats candidats sont testés aux SHAs exacts et publiés en PR brouillon. **Le SLA utilisateur de <3 s reste NON QUALIFIÉ tant qu'un vrai modèle ne l'a pas démontré sur le matériel cible.** Le pilote M50-S1B synthétique ne valide ni cette mission ni M50-G3.
