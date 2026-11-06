#!/usr/bin/env bash
# Separate build of the actual approved JNI/llama sources. No model and no retry.
set -euo pipefail
task_root=$(cd "$(dirname "$0")/../.." && pwd)
task_build="$task_root/build/perf03b-native-sanitized"
task_logs="$task_root/build/perf03b/sanitized-build"
mkdir -p "$task_logs"
task_phase=preconditions
task_fail() {
  task_status=$?
  trap - ERR
  python3 - "$task_root" "$task_status" "$task_phase" <<'PY'
from pathlib import Path
import json,sys,shutil
root,status,phase=Path(sys.argv[1]),int(sys.argv[2]),sys.argv[3]
ctest=root/'build/perf03b-native-sanitized/Testing/Temporary/LastTest.log'
if ctest.is_file():
    shutil.copy2(ctest,root/'build/perf03b/sanitized-build/LastTest.log')
target=root/'build/perf03b/CRITICAL-STOP.json'
if not target.exists():
    target.write_text(json.dumps({'reason':'SANITIZED_NATIVE_QUALIFICATION_FAILED',
        'phase':phase,'exit_code':status,'qualification':'STOPPED','no_retry':True,
        'native_drain':'UNKNOWN','model_unload':'UNKNOWN'},indent=2)+'\n')
PY
  exit "$task_status"
}
trap task_fail ERR
test ! -e "$task_root/build/perf03b/CRITICAL-STOP.json"
test -n "${JAVA_HOME:-}"
test -f "$task_root/.deps/llama.cpp/include/llama.h"
task_flags='-fsanitize=address,undefined -fno-omit-frame-pointer'
task_phase=configure
cmake -S "$task_root/app/src/main/cpp" -B "$task_build" \
  -DLLAMA_SOURCE_DIR="$task_root/.deps/llama.cpp" -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_C_FLAGS="$task_flags" -DCMAKE_CXX_FLAGS="$task_flags" \
  -DCMAKE_SHARED_LINKER_FLAGS='-fsanitize=address,undefined' \
  -DCMAKE_EXE_LINKER_FLAGS='-fsanitize=address,undefined' 2>&1 | tee "$task_logs/configure.log"
task_phase=build
cmake --build "$task_build" --target native_safety_test jarvis_llm -j2 2>&1 | tee "$task_logs/build.log"
task_phase=ctest
ASAN_OPTIONS=detect_leaks=1:halt_on_error=1 UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1 \
  ctest --test-dir "$task_build" --output-on-failure 2>&1 | tee "$task_logs/ctest.log"
cp "$task_build/Testing/Temporary/LastTest.log" "$task_logs/LastTest.log"
task_phase=jvm_sanitized
task_asan_runtime=$("${CXX:-g++}" -print-file-name=libasan.so)
task_cxx_runtime=$("${CXX:-g++}" -print-file-name=libstdc++.so)
test -f "$task_asan_runtime"
test -f "$task_cxx_runtime"
python3 "$task_root/perf03b/native-tests/run_jni_host.py" \
  --library "$task_build/libjarvis_llm.so" --jdk "$JAVA_HOME" --lane sanitized \
  --asan-runtime "$task_asan_runtime" --cxx-runtime "$task_cxx_runtime"
