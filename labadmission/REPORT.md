# Rapport LAB-ADMISSION-01

Module LLM ; mission PERF-03B / LAB-ADMISSION-01 ; dépôt bubth720/JARVIS-LLM ; branche codex/llm-lab-admission-review. Base exacte 2500a123a69b059bf21ac6bec24be5a52b54a9c5, tree 34e16b628a577e9cfae0633785502da742da9287. Le commit/tree livré, les liens CI et les résultats finaux du head testé sont inscrits dans le complément de la PR ; ils ne sont pas remplacés par un SHA de base.

Livraison : proposition d'admission signée LAB_CLOUD séparée de CORE ; diff exact avec manifeste de 5 postimages (3 sources sensibles modifiées dans le patch, 2 nouvelles), documentation de sûreté/contrats/CORE et tests isolés. Les seules modifications effectives sont des **ajouts** sous labadmission/ et le nouveau workflow ; les 457 fichiers préexistants restent identiques. La liste exhaustive est donnée par le diff Git et le rapport de préservation du commit testé.

## Résultats à compléter après campagne finale

Les chiffres intermédiaires locaux (178 historiques ; jusqu'à 238 au candidat de travail) sont des contrôles pendant préparation, **pas** la preuve du head final. La CI doit réexécuter la base/candidat et les rejets de contrat avec les préimages et le SHA exact ; ses JSON, logs et empreintes sont publiés en artefact texte. La description de PR portera les nombres exécutés, échecs, commits/tree et archives vérifiées. Aucun résultat PENDING ne sera transformé automatiquement en PASS.

Mesures réelles : 0 essai génératif exécuté ; TTFT p50/p95/max, charge, premier token, premier texte, completion, débit et mémoire d'un modèle : **null / NOT_RUN**. Aucun téléchargement de modèle, génération native, JNI chargé, appareil personnel ou test Android. Les tests de file/signature/broker sont des fixtures, pas une simulation présentée comme inférence. Tests natifs ASan/UBSan, compilation ARM64/APK et régressions Android complètes : **NOT_RUN** dans cette revue ; preuves historiques des PR #12/#13/#14 restent attachées à leurs commits.

## Blocages et risques

Application du patch non autorisée ; racine opérateur et permis signés réels non provisionnés ; hash exact du template signé encore à obtenir dans une campagne autorisée. L'hypothèse de snapshot privé coopératif exclut un acteur hostile du même UID/root et ne remplace pas une preuve native de FD ; la rejeter exige un autre diff à approuver. Mapping cgroup/support hôte, latence de révocation, budget/pic et cycle de vie avec poids réels restent à qualifier. Le type interne de grant/buffer et la sémantique des baux épinglés changeraient si ce patch était appliqué. Ces risques sont détaillés dans RISK-MATRIX.md et CONTRACTS.md.

Le FAIL historique du manifeste n'est pas modifié ; une CI dédiée verte n'implique pas toutes les CI historiques vertes. Les dépendances C++/modèle et les gardes Android ne sont pas détournés. Le laboratoire M50 synthétique n'est pas une preuve d'intégration réelle.

## Acceptation et décision

Critères de préparation : patch identifiable/reconstructible/dry-run, préimages et modes, architecture et risques, tests non sensibles, préservation, PR brouillon et arrêt avant application. Les preuves finales doivent permettre de les juger séparément.

Critères GATE-B : **NON VALIDÉS**. Aucune inférence, sûreté de sampling ni performance Android attestée ici. Prochaine action : revue utilisateur du diff, de son SHA exact, du changement de contrats et des hypothèses/racine de confiance. Une décision d'application devra viser explicitement ce SHA et une branche isolée ; elle ne couvrira ni modifications supplémentaires, ni CORE/INTERFACE, ni appareils personnels, ni merge. Si l'hypothèse de fichier coopératif ou la racine d'autorité n'est pas acceptable, bloquer et préparer une autre proposition, sans contournement.
