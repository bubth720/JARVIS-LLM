#!/usr/bin/env bash
# Source this file, or execute it with a command: tools/cloud-env.sh ./gradlew :domain:test
jarvis_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$jarvis_root/.tools/jdk-21.0.12.1+1"
export ANDROID_HOME="$jarvis_root/.tools/android-sdk"
export ANDROID_USER_HOME="$jarvis_root/.tools/android-user-home"
export GRADLE_USER_HOME="$jarvis_root/.tools/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmake/3.22.1/bin:$ANDROID_HOME/platform-tools:$PATH"
if [[ -f "$jarvis_root/.tools/sdk-java-opts" ]]; then export JAVA_OPTS; JAVA_OPTS="$(cat "$jarvis_root/.tools/sdk-java-opts")"; fi
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then cd "$jarvis_root"; exec "$@"; fi
