# Protocole PERF-02

## État actuel

Le banc est non productif. Aucune donnée réelle de génération n’est disponible. Le backend historique retourne le `ByteArray`/`String` complet ; son premier token est `UNSUPPORTED`. CORE/INTERFACE ne sont pas raccordés par cette mission, donc `t_CORE_accept` est aussi `UNSUPPORTED`, jamais remplacé par un ACK de fixture. Aucun poids n’est téléchargé ni installé par les outils PERF.

## Définitions temporelles

| Marqueur | Point proposé / observateur disponible | Limite |
|---|---|---|
| `UI_SEND` | Acceptation d’un envoi valide, après les contrôles existants busy/foreground/taille, avant routage catalogue | La fixture le marque explicitement ; aucun hook ajouté au ViewModel main |
| `CORE_ACCEPT` | Réception d’un mandat CORE vérifié, même requête/session/epoch/génération/bail | Transport actuel non connecté : UNSUPPORTED |
| `ENGINE_ENTER` | Entrée dans generate du décorateur du même backend | Délai avant JNI = dispatch + contrôles + encodage ; pas une attente pure de file |
| `LOAD_REQUEST/LOAD_RETURN` | Appel/retour `LanguageModel.load` | Peut être un no-op pour un modèle résident, ne pas le déclarer chargement natif |
| `MODEL_LOAD_ENTER/RETURN` | Appel/retour effectif de `NativeApi.load` sur le worker existant | Wrapper test seulement, pas de factory supplémentaire |
| `NATIVE_ENTER/RETURN` | Appel/retour réel de generate/generateP2, y compris retour en erreur | Une exception/coroutine ne vaut pas reçu de drain/unload |
| `NATIVE_FIRST_TOKEN` | Futur événement du worker au premier véritable token du modèle | ABI historique absente : UNSUPPORTED, aucune division du String final |
| `BACKEND_RETURN` | Retour du suspend generate côté appelant | Distinct du retour natif ; ne prouve aucun nettoyage |
| `UI_TEXT_SET` | Texte modèle non vide remis au rendu, provenance et clé exactes | Exclut état prêt, statut, catalogue, ACK et animation |
| `UI_FIRST_RENDER` | Commit de frame Android API29+ de la vue associée | Pas une simple affectation de TextView ni une mesure photon ; port non exercé sur matériel |
| `COMPLETED` | Contenu complet rendu et retour natif observé | Pas un unload ni une libération de bail |
| `STOP_REQUESTED` | Avant délégation au stop existant | Ne fabrique ni fin, ni drain, ni PSS zéro |
| `DRAIN_RETURN` | Futur reçu exhaustif queue/imports/appels, lié à la même incarnation | UNKNOWN aujourd’hui, fixture explicite seulement |

Chaque marqueur est lu avec **la même instance d’horloge monotone**, sous un scope UUID. Android : `SystemClock.elapsedRealtimeNanos`; JVM : `System.nanoTime`. Les origines ne sont pas mélangées et les durées sont soustraites à l’intérieur d’une trace seulement. Une valeur négative initiale de nanoTime est légale ; régression, exception et overflow rendent la mesure UNKNOWN. Double émission identique : pas de nouvel horodatage. Identité périmée : ignorée. Sortie hors ordre : fermée.

Les wrappers ne possèdent ni modèle, ni worker, ni bail. La phase load est mesurée séparément ; la construction et son JNI create ne sont pas individuellement chronométrés par ce décorateur. Le banc conserve le même `LlamaBackend` et le même worker pour les essais chauds ; seul le slot d’observation doit changer **après le vrai retour de l’opération précédente sous le contrôle de son propriétaire**. Une erreur/absence de retour n’autorise aucune réaffectation ou deuxième résidence.

## Baseline/variante reproductibles

1. Lire `missions/LLM-PERF-TTFT-3S.md`, les contrats gelés et le dossier natif. Vérifier SHA/tree du banc, modèle importé volontairement, template, tokenizer, configuration réellement admise et corpus synthétique fixé. Aucun modèle personnel, catalogue privé ou prompt/réponse dans les logs.
2. Séparer trois états : **COLD_PROCESS** (processus neuf, pas de modèle résident), **COLD_MODEL** (processus existant sans résidence attestée), **WARM_VERIFIED** (même modèle/configuration et bail encore valides). UNKNOWN ne devient pas warm. Une sélection de modèle ne prouve pas sa résidence.
3. Deux cas de sortie **cap 8 et cap 24**, mêmes prompts/corpus et même configuration avant/après. Il s’agit de bornes, pas de nombres de tokens observés. Les comptes exacts restent UNKNOWN sans tokenizer et flux qualifiés. Changer de cap peut changer le `RuntimeConfig` résident : vérifier la vraie recharge et ne pas comparer ce cas à du warm.
4. Au moins **20 tentatives comparables par cellule** (profil, état froid/chaud, cap, matériel, modèle, configuration, corpus), ordre baseline/variante alterné, repos naturel entre séries. La campagne matérielle exige une autorisation distincte ; aucun script fourni ne boucle des générations ou maintient un modèle artificiellement actif. Ne pas contourner l’idle unload existant.
5. Arrêter la campagne dès refus sécurité, chauffe, batterie/pression mémoire, capteur requis indisponible, absence de retour ou instabilité. Conserver les refus/annulations dans le dénominateur ; ne pas recommencer silencieusement jusqu’à obtenir de « bons » échantillons.
6. p50/p95 par rang le plus proche, sans interpolation ; p95 non publié pour n<20. Conserver max, n, erreurs, paramètres et latences brutes. Aucun pool de profils, machines, modèles, caps, températures incompatibles ou états froid/chaud. Le hash d’environnement est non identifiant ; le hash de configuration doit couvrir les vrais paramètres admis.
7. Avant/après doit être sur le même matériel, même modèle/SHA, même template/corpus, même configuration autorisée et mêmes limites thermiques/RAM. Une comparaison entre deux horloges/processus non qualifiés n’est jamais une latence.

## Mesures de ressources et qualification

Durée de load réellement entré, attente worker, durée native et rendu sont distincts. RSS/PSS, pic et sampling sont distincts des buffers llama.cpp et du budget estimé ; ne pas les additionner ni interpréter un unload comme PSS zéro. Sans collecteur connecté/mesure réelle : `null`/UNKNOWN. Thermique, batterie et énergie restent inconnues ici ; aucune consommation calculée à partir d’un pourcentage approximatif.

Les décorateurs et le port de commit sont compilés/exercés via tests JVM et ports de frames synthétiques. Cela ne prouve pas un vrai rendu Android, JNI GGUF, TTFT, drain ou autonomie. Le validateur `benchmark.py` reste volontairement fermé à toute qualification de streaming réel dans son schéma /1. Après accord natif, une version distincte et revue devra intégrer les vraies preuves et instruments.

## Retour arrière

Le lot est additif, les sources de test ne sont pas dans les APK main. Abandonner/revert le dossier PERF et son workflow n’altère aucun store, modèle, signature, contrat ni bail. Aucun patch natif n’est à défaire. Pour une future version native, annulation sous mandat exact et fin/unload attestés précèdent toute réadmission ; un revert de code, une file vide ou un PID neuf ne valent pas preuve de libération.
