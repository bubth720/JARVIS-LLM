#!/usr/bin/env bash
set -euo pipefail
task_root=$(cd "$(dirname "$0")/../.." && pwd)
task_out="$task_root/build/perf03/helper-tests"
mkdir -p "$task_out"
task_compiler=${CXX:-g++}
task_detect_leaks=${PERF03_DETECT_LEAKS:-1}
case "$task_detect_leaks" in 0|1) ;; *) exit 2 ;; esac
"$task_compiler" --version > "$task_out/compiler.txt"
task_flags=(-std=c++17 -O1 -g -Wall -Wextra -Werror -pthread -fno-omit-frame-pointer)
"$task_compiler" "${task_flags[@]}" "$task_root/perf03/tests/stream_support_test.cpp" -o "$task_out/plain"
"$task_out/plain" | tee "$task_out/plain.log"
tail -n 1 "$task_out/plain.log" > "$task_out/plain.json"
"$task_compiler" "${task_flags[@]}" -fsanitize=address,undefined "$task_root/perf03/tests/stream_support_test.cpp" -o "$task_out/asan-ubsan"
ASAN_OPTIONS="detect_leaks=$task_detect_leaks:halt_on_error=1" UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1 \
  "$task_out/asan-ubsan" 2>&1 | tee "$task_out/asan-ubsan.log"
tail -n 1 "$task_out/asan-ubsan.log" > "$task_out/asan-ubsan.json"
python3 -c 'import json,sys; print(json.dumps({"asan":"PASS","ubsan":"PASS","lsan":"PASS" if sys.argv[1] == "1" else "NOT_RUN", "detect_leaks":sys.argv[1] == "1"}))' "$task_detect_leaks" > "$task_out/sanitizers.json"
sha256sum "$task_root/perf03/candidate/native/stream_support.h" \
  "$task_root/perf03/tests/review_queue.h" "$task_root/perf03/tests/stream_support_test.cpp" \
  "$task_root/perf03/tests/run.sh" > "$task_out/sources.sha256"
