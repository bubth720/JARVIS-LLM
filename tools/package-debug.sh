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
sed 's/TEST_CATALOGUE_003.md/ESSAIS-CATALOGUE.md/g' docs/FIRST_DIAGNOSTIC.md > "$package_dir/PREMIER-DIAGNOSTIC.md"
cp docs/TEST_CATALOGUE_003.md "$package_dir/ESSAIS-CATALOGUE.md"
mkdir -p "$package_dir/catalogue-fixtures"
cp build/catalogue-fixtures/*.json "$package_dir/catalogue-fixtures/"
git rev-parse HEAD > "$package_dir/SOURCE_COMMIT.txt"
(cd "$package_dir" && sha256sum JARVIS-LLM-debug.apk ESSAIS-CATALOGUE.md catalogue-fixtures/*.json > SHA256SUMS && sha256sum -c SHA256SUMS)
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
