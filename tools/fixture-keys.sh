#!/usr/bin/env bash
set -euo pipefail
umask 077
cd -- "$(dirname -- "$0")/.."
mkdir -p .tools
# Separate keys: never read, replace or reuse .tools/debug.keystore.
for suffix in common refused; do
  target=".tools/fixture-$suffix.keystore"
  if [[ ! -f "$target" ]]; then
    keytool -genkeypair -keystore "$target" -storepass android -keypass android -alias fixture -keyalg RSA -keysize 2048 -validity 365 -dname "CN=JARVIS isolated fixture $suffix" >/dev/null 2>&1
  fi
done
