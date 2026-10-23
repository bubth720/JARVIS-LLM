# LLM-02 — Planification structurée et port exact fermé par défaut

Branche : `feat/llm-02-runtime`, branche fille de LLM-01 `fa8f9f960964bba7bdfbd02303aeaa7ee530c656`.
Base technique préservée : `8f1c2f5a936d6c01874d7be6bb987f4459b83374` ; fiche planning `fdb71605d91b69484a38fcf0df283365a2493f38`.

## Code livré

`domain/.../prompt/ContextPlanner.kt` ajoute PromptFrame, TemplatePolicy, TemplateBoundTokenizer,
NativeTokenCountPort et ContextPlanner. Le code réutilise **P2Planner/P2Request existants et inchangés** :
SYSTEM, question, documents (référence/révision) et historique restent structurés, contrôlés et distincts.
Aucun rendu Jinja ou template GGUF n'est inventé dans Kotlin. Le backend natif devra appliquer son template exact.

Le planificateur refuse par défaut le dépassement de contexte. Une politique explicite peut enlever les plus
anciennes **paires complètes** d'historique ; chaque requête dérivée doit être réautorisée exactement par CORE.
Question, SYSTEM, sources et budgets restent intacts. Aucun changement du contrat existant n'autorise une
troncature silencieuse. Le résultat indique le nombre de paires écartées et garde la provenance.

L'inégalité est celle du planificateur existant : inputTokens + outputReserve + marge existante <= capacité
réellement admise. Aucune augmentation de seuil ; la réserve suit RuntimeConfig et les limites effectivement
admises, non une valeur documentaire supposée (le profil historique principal peut réserver 256 tokens).
Les sommes sont calculées en Long pour ne pas déborder.

## Blocage natif maintenu

Le port par défaut est **UNSUPPORTED / TOKENIZER_UNAVAILABLE**. Il ne compte pas des caractères, ne lance ni
inférence ni embedding et ne charge rien. Les empreintes modèle/template sont comparées ; un compteur de
fixture est refusé en mode normal. Le port futur devra être lié par le backend de confiance au même modèle,
contexte, template, worker, lease et génération et vérifier annulation/expiration. Cette liaison et le
prepare/consume/discard natif ne sont **pas implémentés**, pas attestés par les champs de cette interface.
Aucun patch F3B, JNI, AAR, admission, signature ou fixture /2 modifié. Aucun raccordement au chat historique,
aucune activation IDENTITY/Athéna. LLM-03 ne peut pas annoncer une récupération native qualifiée sur cette base.

## Preuves

Neuf tests nouveaux de domaine : refus par défaut, rejet simulation en production, Unicode/emoji, contrôle
SYSTEM/provenance, budget/dépassement, réduction explicite de tours, nouvelle autorisation exacte, mauvais
modèle/template/version, UTF-8 invalide/injection, propagation annulation/expiration/UNKNOWN sans réessai.
Les compteurs de ces tests sont **synthétiques**, jamais une preuve de tokenisation native.
Commandes et résultats : `RECEIPT-LLM-02.json`. La garde de campagne préserve 75 fichiers historiques ;
l'échec de périmètre de la garde historique F3A, détaillé dans LLM-01, n'a pas été masqué.

Prochaine action : revue explicite du diff natif F3B/tokenizer et de sa liaison au worker avant activation.
Les profils logiciels/passifs peuvent progresser isolément ; les preuves natives et Android demeurent des gates séparés.
