# Premier diagnostic — aucun modèle, aucune inférence

Mission 002B : APK version 0.2.0, profil ÉCO renforcé. La RAM nécessaire au premier chargement n'a pas été abaissée sans preuves.
Relever aussi le budget estimé, la réserve, le crédit PSS observé, la marge d'admission et le pic PSS échantillonné.
Sur le POCO annoncé à 11,06 Gio total /3,68 Gio disponible, la marge attendue sans modèle résident est environ -0,532 Gio : ne pas forcer un chargement.
Un relevé passif ne mesure pas la consommation du modèle et n'autorise pas un benchmark.

## Télécharger et installer

1. Ouvrir l'exécution GitHub Actions réussie indiquée dans le rapport de validation.
2. Dans **Artifacts**, télécharger **jarvis-llm-debug** (connexion GitHub généralement nécessaire).
3. Extraire le ZIP : `JARVIS-LLM-debug.apk`, `SHA256SUMS`, `SOURCE_COMMIT.txt` et ce guide.
4. Vérifier le SHA-256 de l'APK contre `SHA256SUMS`. Sur Linux/macOS : `shasum -a 256 JARVIS-LLM-debug.apk` ; Windows PowerShell : `Get-FileHash .\JARVIS-LLM-debug.apk -Algorithm SHA256`.
5. Copier l'APK localement sur chaque appareil, ouvrir le fichier et autoriser ponctuellement cette source d'installation si Android le demande.
6. Installer **JARVIS LLM**, identifiant `fr.jarvis.llm`. Aucun autre projet JARVIS n'est remplacé.

APK debug, Android API 29+, ARM64 ; pas une version de production. Une signature debug différente peut empêcher une mise à jour d'une ancienne installation : ne pas désinstaller aveuglément, car ses données privées seraient supprimées.
Une empreinte assure l'identité du fichier téléchargé, pas une certification de sécurité matérielle.

## Diagnostic passif sur POCO et Redmi

1. Ouvrir l'application, sans toucher à **Importer** ni **Envoyer**.
2. Vérifier **Modèles importés : aucun** sur une installation neuve.
3. Lire le panneau : modèle/référence, Android/API, SoC déclaré, ABI, taille de page mémoire, RAM accessible/disponible, stockage libre, batterie, état thermique et PSS.
4. Le texte du panneau est sélectionnable : le copier dans vos notes locales. Aucun envoi automatique n'est effectué.
5. Relever séparément la RAM physique et l'extension mémoire dans les réglages constructeur ; l'extension ne doit pas être ajoutée au budget RAM.
6. Revenir à l'accueil puis rouvrir l'application : aucune génération ni import ne doit démarrer.
7. Fermer l'application. Fournir les deux relevés pour décider des essais suivants.

Ce diagnostic ne charge pas llama.cpp et n'exécute ni modèle ni benchmark. Il ne valide pas le débit de stockage, les capteurs OEM, le GPU/NPU ou l'arrêt d'un vrai Qwen.
Ne pas utiliser PLEINE PUISSANCE ni procéder à un essai prolongé. L'import et la génération feront l'objet d'une étape ultérieure après examen des protections et des relevés.

## Fiche à remplir pour chaque appareil

| Information | Relevé |
|---|---|
| Appareil et référence exacte | |
| Android / API / firmware | |
| SoC déclaré / ABI / page mémoire | |
| RAM physique / extension mémoire séparée | |
| RAM accessible / disponible | |
| Stockage libre | |
| Batterie / état thermique / PSS | |
| Modèles importés | Aucun attendu |
| Ouverture / retour accueil / réouverture | |

Si une information est absente ou surprenante, la signaler ; ne pas forcer un calcul pour la vérifier.
