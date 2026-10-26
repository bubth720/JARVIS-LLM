# L3 — protocole futur Android29+ et Linux local, NON EXÉCUTÉ

Ce guide prépare les preuves ; ce n'est pas une autorisation de tester les appareils.
Aucun modèle, installation ou génération ne fait partie de cette livraison.

## Portes obligatoires

1. Validation humaine spécifique de l'adaptation native, de ses ABI, des imports,
   des risques UAF, du modèle public/licence/SHA et du laboratoire. Pas de poids
   personnel téléchargé, ni de modèle inclus dans les logs/APK/Git.
2. Compiler et qualifier d'abord la petite adaptation isolée, puis vrai JNI Linux
   avec modèle choisi, ensuite JNI Android ARM64/API29+ sur laboratoire autorisé.
   Tests négatifs et chemins de nettoyage avant essai de génération.
3. Qualifier le comptage exact lié à prepare/consume/discard et au même template.
   Pas d'admission si tokenizer indisponible, état inconnu ou seuil existant refusé.
4. Obtenir un accord d'installation/essai physique distinct pour POCO/Redmi. La CI,
   les mocks et l'émulateur ne qualifient pas la température, l'autonomie ou la RAM
   de ces appareils. Aucun stress ni boucle automatique de générations.

## Budgets existants à conserver, pas des consommations mesurées

| Profil existant | Modèle admissible selon politique | Contexte/sortie/threads | Budget estimé |
|---|---|---|---|
| TABLETTE ÉCO | Qwen3 0.6B Q4_K_M SMALL | 512/64/1, batch16/micro4 | 2Gio |
| ÉCO / repli SMALL | Qwen3 0.6B Q4_K_M | 768/96/1, batch32/micro8 | 2Gio |
| ÉQUILIBRÉ / AUTO | MAIN si admission naturelle, sinon SMALL | MAIN2048/256/1..4, batch64/micro32 | MAIN4Gio, SMALL2Gio |
| FULL_POWER Android | Aucun | Toujours refusé | Aucune augmentation |
| Linux mini-PC futur | À qualifier explicitement | Non défini, aucun backend activé | Non mesuré/non autorisé |

Réserve système existante : max(1Gio, RAM totale/5, seuil lowMemory Android+256Mio).
Le crédit PSS existant reste borné ; aucune modification de seuil mémoire/thermique/
batterie/contexte/batch/threads. Configuration candidate ≠ configuration qualifiée.
À ce stade, **aucune nouvelle configuration sûre démontrée sur appareil**.

## Essai court futur, une requête synthétique

A. Vérifier APK/commit/signature/permissions, modèle GGUF complet/SHA et provenance.
Ne pas écraser les données privées, le catalogue ou les modèles déjà importés.
Consigner Android, ABI, appareil/émulateur, profil, paramètres, alimentation,
charge batterie, état thermique, capteurs réellement disponibles.

B. Démarrer sans modèle. Relever repos et diagnostic passif ; contrôler le refus
ou l'admission avec SafetyPolicy inchangée. En cas de refus : conserver et arrêter
l'essai de charge. Ne pas alléger une réserve pour obtenir PASS.

C. Si autorisé : charger un seul modèle, noter début/fin monotones et ressources.
Soumettre « Donne une phrase courte en français sur une lampe bleue. », sans
historique, document, mémoire ou donnée personnelle. Limites du profil existant,
aucun prompt qui déclenche une commande ou un outil.

D. Relever acceptation, entrée JNI, premier/dernier token **réellement observable**,
fin réelle JNI et motifs de refus. Le backend historique renvoie aujourd'hui une
chaîne finale : premier token indisponible doit rester null ; ne pas le déduire
d'un chunk de fixture ou de l'ACK. Une instrumentation supplémentaire exige revue.

E. Demander l'arrêt exact, observer STOP_REQUESTED puis retours effectifs, drain,
unload/destroy et libération des baux liés à la même opération/incarnation. Pas de
confirmation à partir d'un timeout, coroutine annulée, Binder mort ou processus neuf.
En cas d'incertitude, UNKNOWN et aucune deuxième résidence, y compris VOICE.

F. Vérifier retour au repos, capteurs et mémoire ; arrière-plan puis reprise sans
génération automatique. Arrêter si chauffe, pression mémoire, batterie insuffisante,
instabilité, capteur de sécurité indisponible ou comportement inhabituel.
Répéter au maximum deux fois supplémentaires **seulement si le premier essai est
stable et la campagne courte autorisée** ; pas d'enchaînement intensif.

## Mesures et limites

PSS/RSS par phase sont des vues du processus qui se recouvrent ; ne pas les additionner
entre elles ou aux buffers llama.cpp. VmHWM/peak RSS seulement si exposé ; PSS
échantillonné = pic échantillonné, pas maximum garanti. Comptabiliser la méthode,
intervalle, privilèges et valeurs indisponibles. Réserve/budget sont calculés,
buffers sont déclarés, mémoire disponible est un capteur Android, aucun n'est un
reçu de fin native. Arrêter les captures en arrière-plan, pas de polling au repos.

Mesurer temps de chargement et génération via horloge monotone même processus ;
les horloges de deux APK ou Linux/Android ne sont pas directement soustractibles.
Conserver les latences brutes et n. Les trois essais courts ne justifient aucun
p95 robuste : valeur null. Une future campagne de répétitions distinctement approuvée
devra publier méthode de quantile, taille d'échantillon et conditions (même modèle,
graine/paramètres, chaud/froid), sans extrapoler des chiffres cloud au téléphone.
Batterie/température/énergie uniquement si exposées ; null si absentes, jamais zéro.

Formulaire technique vide : [MEASUREMENT-TEMPLATE.json](MEASUREMENT-TEMPLATE.json).
Aucun texte de prompt/réponse, chemin privé, profil vocal, certificat secret ou ID
matériel personnel n'y est prévu. Les identifiants opération/bail des essais futurs
restent corrélés dans une exportation technique revue, pas des données personnelles.
