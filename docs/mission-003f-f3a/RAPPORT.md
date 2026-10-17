# JARVIS LLM — 003F-F3A

## Résultat

**Socle isolé livré ; F3 native non vérifiée.** Aucun reçu terminal natif produit.
Le port réel et son raccordement sont suspendus avant modification sensible du backend,
conformément à l'issue2. L'admission native /2 et CORE004D restent bloquées.

- Dépôt : bubth720/JARVIS-LLM seulement.
- Base : fix/003f-d4-02e1-commit, `1ce24405b04a50f97cf30078b9716c7d00c593fc`.
- Branche : `feat/003f-f3-native-observation`, sans fusion ni installation.
- Références immuables : [SOURCES.json](SOURCES.json).
- Audit et mapping exact : [AUDIT.md](AUDIT.md).
- Composants, barrière et futur reçu : [PORT-ET-RECUS.md](PORT-ET-RECUS.md).
- Périmètre sensible suspendu : [ADAPTATION-SENSIBLE.md](ADAPTATION-SENSIBLE.md),
  [diff conceptuel non applicable](BACKEND-PROPOSE.diff.txt).
- Exécution/CI : [VALIDATION.md](VALIDATION.md), run39072313347 entièrement réussi.
- Code livré/testé : `1abf3264a7051cf4b8f25976b301938fa728af5a` ;
  clôture documentaire dans le commit suivant, aucun code supplémentaire.

## Changements réellement implémentés

Trois fichiers internes sous fixtures/p2-endpoint/.../f3/ : port de lecture indisponible
par défaut et vérificateur de cohérence, ledger de participation/freeze, journal de
revues distinct sans autorité native. Aucun appelant de production n'est ajouté.
Quatre classes de tests ajoutées, dont huit tests du vrai LlamaBackend et de son worker
avec un **NativeApi factice**. Deux scripts ajoutent les preuves de préservation et le
rapport de tests, et la CI les exécute/collecte. Les fichiers existants app/domain/
fixtures, tous les tests antérieurs et tous les contrats sont inchangés.

Le journal de revue conserve uniquement des résultats d'algorithme et digests de
clés. Ce n'est pas le reçu terminal durable attendu par D4-02E.1. Il ne peut jamais
être envoyé comme terminal_record_ref/terminal_receipt_sha256 ; l'autorité actuelle
ne le lit pas. Une revue cohérente sur des données synthétiques ne qualifie rien.

## Préservation

Le contrôle F3A compare **101 fichiers** existants de app/domain/fixtures au commit
base : identité octet pour octet. Les contrôles 003E/003F/D4E1 et les vecteurs /2
continuent de passer. Cela couvre SafetyPolicy, réserves mémoire, seuils thermiques
et batterie, modes et paramètres existants, catalogue SQLite, import sécurisé,
annulation JNI, worker, ProcessResources, manifests et endpoints existants.
Aucun nouveau modèle, moteur, permission ou service n'est ajouté.
PLEINE PUISSANCE n'est pas activée ; aucun arbitrage inter-APK n'est revendiqué.

/1 : `fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9`.

/2 : `3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef`.

Le second SHA est toujours celui des six fichiers normatifs CONTRACT/TABLES/AIDL
existants ; aucun changement normatif ni nouvelle adoption CORE n'est effectué.

## Limites et décision suivante

Les vrais pointeurs, files et générations de baux ne sont pas encore exposés en
snapshot cohérent corrélé à une clé réelle. Le capteur indisponible conserve UNKNOWN.
Le worker fakeJNI démontre des propriétés de contrôle JVM, pas un retour JNI réel
ni un déchargement Android. Les interruptions injectées ne sont pas des crashs
natifs ; les recréations simulées ne sont pas des morts Android observées.
Les tests Linux/GGUF historiques de CI ne prouvent pas ce port F3 absent.

Pour poursuivre : validation ciblée du périmètre d'instrumentation proposé, puis
mission F3 suivante sans ouvrir /2, avec diff concret final revu avant modification
sensible. Réunir ensuite les preuves JNI et laboratoire Android autorisé et revoir
le bundle exact. **Ne pas lancer CORE004D** sur la seule base de cette livraison.
