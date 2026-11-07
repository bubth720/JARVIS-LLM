# Décision M51.07 — NO-GO avant application

Le lot de revue peut être livré avec ses preuves ; le lot d'application/inférence ne peut pas avancer. Le texte maître M51–M54, §2.2, §4.8 et §10.1, exige une décision séparée sur le patch et la racine. La mission actuelle n'accorde ni G-A ni G-ROOT.

## Arbre de décision

1. Patch/SHA/préimages identiques ? Sinon STOP, produire un diff séparé avec empreinte neuve. Le patch examiné conserve 8df826451e501a107305f8fcaee5ca3d5334e22de23246d32cc7fd5312da21e8.
2. L'utilisateur a-t-il approuvé l'application exacte sur une branche isolée ? Non aujourd'hui : G-A fermé. La revue n'applique rien, même dans un checkout temporaire.
3. Les nouvelles limites du dispatch de watchers, du scheduling de deadline et de la composition/expurgation sont-elles corrigées ou explicitement bornées et revues ? Non qualifié : NO-GO de sûreté avant campagne réelle, correction proposée sous un nouveau diff.
4. Le risque pathname/same-UID est-il accepté pour un hôte cloud éphémère, exclusif, sans données personnelles ni écrivain non maîtrisé ? Décision utilisateur manquante. Un mode 0400 n'est pas une preuve de l'absence d'un FD d'écriture déjà ouvert. Sans cette hypothèse : voie FD/reçu fort à préparer, aucun contournement.
5. Une racine d'opérateur indépendante, publique, vérifiée et approuvée a-t-elle été provisionnée par un diff distinct ? Non : G-ROOT fermé, aucune admission possible.
6. Un bootstrap/harness approuvé lie-t-il un challenge réel du backend au run/attempt/job/commit et à l'artefact construit, sous cette racine ? Non : ne pas remplacer par un contexte manuel ou un signer local auto-généré.
7. L'acquisition de la campagne exacte et son unique GGUF sont-elles autorisées, licence/hash/métadonnées/template contrôlés ? Pas d'autorisation d'acquisition dans M51 ; le modèle précédent a été supprimé. NOT_RUN.
8. Seulement après tous ces points : campagne JNI réelle bornée, avec vrais tokens/annulation/backpressure/cleanup. G-B reste à examiner humainement ; aucun PASS automatique.

## Frontière acceptable seulement sous réserve

Un cloud éphémère non partagé peut rendre une hypothèse coopérative raisonnable si tous les processus et descripteurs du même UID sont contrôlés et si les poids ne sont jamais publiés. Le fait que GitHub fournit une VM ne prouve pas à lui seul ces conditions pour n'importe quel workflow : il faut auditer les actions, dépendances et sous-processus, figer les SHAs, minimiser les droits et supprimer les références privées.

Cette acceptation ne peut pas être copiée sur un téléphone privé, un répertoire partagé, un environnement multi-tenant non maîtrisé ou un runner self-hosted sans preuve distincte. Un problème de refus d'admission n'autorise ni second moteur ni API native directe hors autorité.

## Critères séparés

- Préservation et exécution du banc de revue : PASS seulement avec les preuves du head final.
- Reproduction des limites : PASS de reproduction, **pas PASS de sûreté**.
- Admission, mémoire résidente, génération réelle, Stop JNI, libération native : NOT_RUN.
- Android/POCO/Redmi, co-résidence VOICE, performance TTFT : NOT_RUN.
- G-A/G-ROOT : fermés ; G-B/G-C : NON VALIDÉS.

Le cockpit M51 et ses données locales restent indépendants de ces gates. LLM doit y afficher code disponible / BLOCKED-OFF, connecté=false et prêt=false. Aucune disponibilité de JNI, test JVM ou PR draft ne justifie ACTIVE_VERIFIED.
