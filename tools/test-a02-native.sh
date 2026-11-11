#!/usr/bin/env bash
# A02 host suite: native snapshot adversarial tests + JNI harness on the protected engine.
# Optional: $1 = hash-verified GGUF path, $2 = its sha256 (Linux host only, never Android proof).
set -euo pipefail
root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
"$root/tools/prepare-native.sh"
cmake -S "$root/a02/native" -B "$root/build/a02-native" -DLLAMA_SOURCE_DIR="$root/.deps/llama.cpp" -DCMAKE_BUILD_TYPE=Release
cmake --build "$root/build/a02-native" --target jarvis_llm_a02 a02_snapshot_test -j"$(nproc)"
ctest --test-dir "$root/build/a02-native" --output-on-failure
if nm -D --defined-only "$root/build/a02-native/libjarvis_llm_a02.so" | grep -q 'Java_fr_jarvis_llm_engine_NativeBridge_load$'; then
    echo 'A02_PATH_LOADER_EXPORTED' >&2; exit 1
fi
mkdir -p "$root/build/a02-jni"
javac -d "$root/build/a02-jni" "$root/a02/tests/jni/SealedNativeBridge.java" "$root/a02/tests/jni/legacy/LegacyPathProbe.java"
timeout 600 java --add-opens java.base/java.io=ALL-UNNAMED -Djava.library.path="$root/build/a02-native" \
    -cp "$root/build/a02-jni" fr.jarvis.llm.a02.SealedNativeBridge "$@"
