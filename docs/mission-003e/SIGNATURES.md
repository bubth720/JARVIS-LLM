# Identité et clés de fixtures

La clé dédiée commune ne remplace **aucune** clé historique. `tools/fixture-keys.sh` crée uniquement `.tools/fixture-common.keystore` et `.tools/fixture-refused.keystore` s'ils n'existent pas, avec permissions privées (umask077). `.tools/`, *.keystore et *.jks restent ignorés. Aucun secret n'est inclus dans les artefacts ; seules les empreintes des certificats publics sont publiées.

Ces clés de test locales/éphémères ne sont pas des clés de production. Les mots de passe Android publics du prototype ne rendent pas la clé distribuable. Ne jamais envoyer le keystore en Git, conversation, APK assets ou artefact Actions. Les clés CI disparaissent avec le runner ; on ne peut donc pas signer plus tard un vrai CORE avec la clé d'un ancien run. Le certificat d'un run n'est pas un ancrage durable.

Pour une future paire CORE004C/LLM expérimentale, préparer **hors Git**, dans un canal de secrets autorisé, une clé commune de laboratoire, puis la provisionner dans les environnements de compilation de la paire avant génération des APK. Les chemins de clés dédiées peuvent être provisionnés sans toucher `.tools/debug.keystore`. Ne pas remplacer une clé déjà installée ni tenter une migration. Cette mission n'a configuré aucun secret d'un autre dépôt et n'a transféré aucune clé.

| APK | ApplicationId | Signature attendue |
|---|---|---|
| Endpoint | fr.jarvis.fixture.llm | commune dédiée |
| Sonde authorized | fr.jarvis.fixture.core | même certificat |
| Sonde wrongPackage | fr.jarvis.fixture.unknown | même certificat ; refus applicatif UID/package |
| Sonde wrongSignature | fr.jarvis.fixture.badclient | certificat différent ; permission Android doit refuser |
| Application historique | fr.jarvis.llm | clé historique inchangée et distincte |

La sonde authorized n'est pas un broker CORE. Aucun appairage implicite d'une application personnelle avec cette clé. Les vérifications d'identité portent sur UID réel capturé, liste exacte des packages de cet UID, UID du package attendu, seul certificat courant, pas de rotation automatique. Les mêmes contrôles s'appliquent à la provenance des callbacks du broker.

Le contrôle des APK compile les variantes et vérifie signatures, applicationId, permissions et séparation de certificats. Les tests Robolectric couvrent le vrai résolveur PackageManager/Binder.getCallingUid avec données Android simulées. **Aucun contrôle du noyau Binder entre APK n'a été exécuté** : émulateur absent, aucune installation autorisée sur appareil. Un futur laboratoire doit vérifier les trois liaisons, refus avant lecture du contenu, décès des processus et quarantaine. Ne pas transformer une comparaison de certificats de fichiers APK en preuve d'authentification IPC exécutée.
