#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/jni-p2
javac -d build/jni-p2 app/src/main/cpp/tests/jni/NativeBridge.java
timeout 150 java -Djava.library.path=build/native-tests -cp build/jni-p2 fr.jarvis.llm.engine.NativeBridge "$1"
