# Installation et démarrage

Pour les essais demandés actuellement, suivre [FIRST_DIAGNOSTIC.md](FIRST_DIAGNOSTIC.md) : télécharger l'artefact validé puis **diagnostic passif uniquement, sans import ni génération**.
Paquet CI : [jarvis-llm-debug](https://github.com/bubth720/JARVIS-LLM/actions/runs/38465105554/artifacts/11847564585), APK et SHA256SUMS inclus.
Les étapes d'import ci-dessous décrivent une phase ultérieure ; elles ne doivent pas être exécutées lors de ce premier diagnostic.

APK debug : `app/build/outputs/apk/debug/app-debug.apk`. Copier localement et ouvrir sur Android, ou depuis un poste USB autorisé :

```sh
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Application indépendante `fr.jarvis.llm`, API 29+, ARM64. Ne remplace aucun autre JARVIS.
Aucune permission runtime ou réseau. AndroidX ajoute uniquement une permission privée de signature pour protéger ses receivers ; elle ne donne pas accès aux données système.
Signature debug, pas une distribution production.
Clé debug locale dans `.tools/` : deux machines peuvent signer différemment. Une désinstallation efface les modèles privés ; ne pas la faire sans en tenir compte.

## Import sans cloud

Préparer un Qwen3 0.6B ou 1.7B GGUF Q4_K_M depuis une source fiable, avec révision, licence, tokenizer et template identifiés.
Obtenir un SHA-256 indépendant depuis une référence de confiance. Le calcul d'une empreinte sur un fichier inconnu ne prouve pas son authenticité.
Transférer par USB/stockage local. Ne pas sélectionner un fournisseur de documents cloud, qui peut utiliser son propre réseau.

1. Ouvrir : diagnostic passif, aucun modèle chargé.
2. Importer : choisir le modèle, saisir le SHA-256 attendu, sélectionner le fichier local.
3. L'import contrôle format, taille, réserve et empreinte ; aucune inférence lancée.
4. Après validation des protections, ÉCO et une question courte.
5. Arrêter : attendre la disparition de l'état occupé, qui signifie retour effectif du travail.

Bornes de fichier : 800 Mio (0.6B), 1600 Mio (1.7B). Import exige borne maximale +1 Gio libre.
Le modèle est copié en stockage privé ; garder l'original occupe aussi de l'espace.
RAM insuffisante ou fichier invalide : refus, sans contournement.

L'APK n'a aucune permission réseau ni API cloud et ne télécharge rien. Après import, fichiers et API locales uniquement.
Conversation en RAM ; modèles et références persistants privés. Pas de communication entre appareils.
L'inférence complète avec Qwen reste à tester sur appareil qualifié : inspection de l'APK et fonctionnement réel sont deux validations distinctes.
