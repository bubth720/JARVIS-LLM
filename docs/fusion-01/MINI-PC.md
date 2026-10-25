# L4 — portabilité locale, sans activation

ExecutionTarget distingue explicitement ANDROID_LOCAL de LINUX_LOCAL. Le domaine
Kotlin/JVM et GenerationClientPort n'ont aucune dépendance Android, Binder, HTTP ou
chemin de fichier. Une future composition Linux pourra fournir le **même** port et
un seul backend llama.cpp qualifié, sans recopier une implémentation du moteur.
Ce lot fournit uniquement UnavailableGenerationClient ; toute cible reste refusée.

Étapes futures : choisir matériel/OS mesurés et modèle importé volontairement ;
qualifier tokenizer/template/worker et reçus natifs ; définir mapping d'identités et
permissions hôte ; décider explicitement transport local, chiffrement et appairage
si plusieurs processus/machines ; qualifier CPU/RAM/thermique puis budgets.
Aucun endpoint réseau, téléchargement, connexion mini-PC ou admission supplémentaire
n'est ajouté. Le choix d'une enum Linux ne constitue ni qualification ni autorisation.

TABLETTE ÉCO, ÉCO, ÉQUILIBRÉ, AUTO restent mobiles. FULL_POWER est un identifiant
conservé pour matériel dédié futur ; refusé par SafetyPolicy Android et le client
indisponible. Aucun basculement automatique téléphone/tablette/PC, aucune augmentation
RAM/threads/contexte, aucun fonctionnement autonome inter-applications revendiqué.
