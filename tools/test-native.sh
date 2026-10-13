#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
"$root/tools/prepare-native.sh"
cmake -S "$root/app/src/main/cpp" -B "$root/build/native-tests" -DLLAMA_SOURCE_DIR="$root/.deps/llama.cpp" -DCMAKE_BUILD_TYPE=Release
cmake --build "$root/build/native-tests" --target native_safety_test -j2
ctest --test-dir "$root/build/native-tests" --output-on-failure
