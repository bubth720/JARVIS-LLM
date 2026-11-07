# M52 CORE réel — stratégie documentaire sans adaptation sensible

M51 n'applique ni le patch LAB_CLOUD ni un changement CORE/LLM. L'autorité LAB ne doit jamais devenir un mandat CORE. Aucun événement réel ne doit être étiqueté fixture/LABORATORY pour contourner une frontière INTERFACE.

1. Épingler CORE PR27, LLM PR12/14/15 et INTERFACE PR16 aux SHAs complets vérifiés, puis vérifier réellement les types SessionCoordinator, ports de génération et limites de visibilité. Les bases actuelles possèdent un coordinateur réel et des clients séparés, sans chaîne native live attestée.
2. Préparer un ADR partagé pour les identités request/session/generation/epoch/mandate/lease/model/prepared/sequence/source_kind. CORE émet et révoque son mandat ; le broker LLM émet les vrais baux et leurs observations, sans transfert de propriété.
3. Décrire deux autorisations successives : droit de charger le GGUF exact, puis droit de générer après preuve de résidence et préparation native exacte. Les hashes de demande logique/rendu/template/vocabulaire ne sont pas interchangeables.
4. Définir les états métier et ressources séparément : admission, load, prefill, streaming, cancelling, terminal ; CONTENT_DONE, DRAINED, UNLOADED, UNKNOWN et CLEANUP_FAILED ne sont pas inférés les uns des autres.
5. Préparer des branches de contrats indépendantes côté CORE, LLM et INTERFACE, avec préimages et validation des deux propriétaires. L'adaptateur UI accepte seulement les événements autorisés et versionnés, sans duplication, texte tardif ou reconstruction totale par token. Aucune source CORE/INTERFACE n'est modifiée ici.
6. Qualifier les refus avant l'optimisation : mandat absent/périmé, modèle/configuration différents, bail fermé/autre propriétaire, préparation périmée, terminal incohérent, stop callback tardif et cleanup échoué. Les cas native réels restent NOT_RUN tant que les gates ne sont pas ouverts.
7. Après accords individuels, utiliser un hôte autorisé, une seule instance/worker, le seul modèle de campagne autorisé, puis comparer ancien/streaming et premiers fragments/callbacks/frame sous horloges corrélées. Le p95 physique exige au moins20échantillons comparables ; aucune projection depuis le cloud.

Prochaine action prioritaire : décision sur les constats de sûreté et préparation de leur nouveau diff exact. Un accord M51 UI ne vaut pas G-A, G-ROOT, G-B, G-C ou G-MERGE.
