#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$root"
if [[ -n "$(git status --porcelain --untracked-files=normal)" ]]; then
    echo 'Refusing to label a diagnostic APK with a commit from a dirty source checkout.' >&2
    exit 1
fi
package_dir=build/diagnostic-package
mkdir -p "$package_dir"
cp app/build/outputs/apk/debug/app-debug.apk "$package_dir/JARVIS-LLM-debug.apk"
sed -e 's/TEST_CATALOGUE_003.md/ESSAIS-CATALOGUE.md/g' -e 's/ESSAIS_MEMOIRE_003B.md/ESSAIS-MEMOIRE-003B.md/g' docs/FIRST_DIAGNOSTIC.md > "$package_dir/PREMIER-DIAGNOSTIC.md"
cp docs/TEST_CATALOGUE_003.md "$package_dir/ESSAIS-CATALOGUE.md"
cp docs/ESSAIS_MEMOIRE_003B.md "$package_dir/ESSAIS-MEMOIRE-003B.md"
mkdir -p "$package_dir/mission-003c"
for guide in PROTOCOLE.md REDMI-PAD-SE.md POCO-X8-PRO.md RETOUR-UTILISATEUR.md FORMULAIRE-MESURES.json VERIFIER-APK.html; do
    cp "docs/mission-003c/$guide" "$package_dir/mission-003c/$guide"
done
sed -i 's|../ESSAIS_MEMOIRE_003B.md|../ESSAIS-MEMOIRE-003B.md|g' "$package_dir/mission-003c/PROTOCOLE.md"
./tools/cloud-env.sh python3 tools/apk-identity-003c.py "$package_dir/JARVIS-LLM-debug.apk"
cp build/mission003c-apk-identity.json "$package_dir/APK-IDENTITE.json"
cp build/mission003c-preservation.json "$package_dir/PRESERVATION-003B.json"
cp build/mission003c-validation.json "$package_dir/VALIDATION-CLOUD.json"
mkdir -p "$package_dir/catalogue-fixtures"
cp build/catalogue-fixtures/*.json "$package_dir/catalogue-fixtures/"
git rev-parse HEAD > "$package_dir/SOURCE_COMMIT.txt"
(cd "$package_dir" && sha256sum JARVIS-LLM-debug.apk PREMIER-DIAGNOSTIC.md ESSAIS-CATALOGUE.md ESSAIS-MEMOIRE-003B.md SOURCE_COMMIT.txt APK-IDENTITE.json PRESERVATION-003B.json VALIDATION-CLOUD.json mission-003c/* catalogue-fixtures/*.json > SHA256SUMS && sha256sum -c SHA256SUMS)
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
        echo '### APK de tests locaux'
        echo 'Télécharger **jarvis-llm-debug** dans Artifacts de cette exécution.'
        echo 'Le ZIP contient APK, SHA256SUMS, référence du commit, guides et fixtures SYNTHÉTIQUES.'
        echo 'Aucun modèle à importer ou exécuter pour ce premier diagnostic.'
        echo '```text'
        cat "$package_dir/SHA256SUMS"
        echo '```'
    } >> "$GITHUB_STEP_SUMMARY"
fi

awk 'NR == 1 { printf "::notice title=APK SHA-256::%s\n", $1 }' "$package_dir/SHA256SUMS"
