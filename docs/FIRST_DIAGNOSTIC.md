# Premier diagnostic Mission 003C

**QUALIFICATION PHYSIQUE EN ATTENTE UTILISATEUR.** Aucun téléphone n'est déclaré qualifié par les tests cloud.

1. Consulter le [rapport 003C en ligne](https://github.com/bubth720/JARVIS-LLM/blob/mission-003c-device-qualification/docs/mission-003c/RAPPORT.md) : exécution réussie, commit source, SHA-256 APK et certificat.
2. Lire [le protocole utilisateur](mission-003c/PROTOCOLE.md), dans l'ordre 1 à 10. Vérifier APK puis sauvegarder catalogue et GGUF **avant** toute installation incompatible.
3. Commencer sans modèle résident, par une capture passive d'environ 10 s via **Mesures** ; terminer et exporter. La capture ne lance aucun modèle.
4. Remplir la fiche [Redmi Pad SE](mission-003c/REDMI-PAD-SE.md) ou [POCO X8 Pro](mission-003c/POCO-X8-PRO.md), puis le [retour simple](mission-003c/RETOUR-UTILISATEUR.md).
5. Si l'admission reste refusée, conserver le refus et ne pas générer. Aucun seuil ne peut être abaissé pour cet essai.

L'APK conserve version **0.3.1-mission003b / code 5**, avec la provenance du nouveau commit ; les moteurs, profils et protections 003B sont inchangés. Une signature debug différente peut empêcher une mise à jour : ne pas désinstaller sans sauvegardes. Identifiant `fr.jarvis.llm`, Android API 29+, ARM64.

Le paquet contient `APK-IDENTITE.json`, `SOURCE_COMMIT.txt`, `SHA256SUMS`, `VALIDATION-CLOUD.json`, `PRESERVATION-003B.json` et les guides. Le vérificateur HTML hors ligne `mission-003c/VERIFIER-APK.html` ne contrôle que le SHA du fichier ; signature/permissions sont vérifiées en CI, et sa compatibilité navigateur Android reste à constater. Si le navigateur local ne permet pas le calcul, utiliser un vérificateur fiable existant ou un ordinateur avant installation.

Les [essais catalogue Mission 003](TEST_CATALOGUE_003.md) et [protocole mémoire 003B](ESSAIS_MEMOIRE_003B.md) restent disponibles. Aucune importation du catalogue SYNTHÉTIQUE n'est nécessaire aux essais matériels. Ne publier ni catalogue personnel ni modèle. Pas d'IPC, pas de connexion CORE, pas de PLEINE PUISSANCE, pas de benchmark intensif.
