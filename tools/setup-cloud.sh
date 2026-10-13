#!/usr/bin/env bash
# Reproducible Linux x86_64 development setup; no models and no device benchmarks.
set -euo pipefail
root="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$root"
[[ "$(uname -m)" == x86_64 ]] || { echo 'This bootstrap targets Linux x86_64.' >&2; exit 1; }
mkdir -p .tools/downloads .tools/android-user-home .tools/gradle-home
fetch() {
    local url="$1" file="$2" algorithm="$3" checksum="$4"
    if [[ ! -f "$file" ]]; then curl --fail --location --retry 2 "$url" -o "$file.part"; mv "$file.part" "$file"; fi
    printf '%s  %s\n' "$checksum" "$file" | "$algorithm" -c -
}
if [[ ! -x .tools/jdk-21.0.12.1+1/bin/javac ]]; then
    fetch 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz' .tools/downloads/jdk.tar.gz sha256sum ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94
    tar -xzf .tools/downloads/jdk.tar.gz -C .tools
fi
if [[ ! -x .tools/android-sdk/cmdline-tools/19.0/bin/sdkmanager ]]; then
    # Preserve the checksum published by Google's HTTPS repository manifest.
    fetch https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip .tools/downloads/sdk.zip sha1sum 5fdcc763663eefb86a5b8879697aa6088b041e70
    mkdir -p .tools/android-sdk/cmdline-tools
    unzip -q .tools/downloads/sdk.zip -d .tools/android-sdk/cmdline-tools
    mv .tools/android-sdk/cmdline-tools/cmdline-tools .tools/android-sdk/cmdline-tools/19.0
fi
export JAVA_HOME="$root/.tools/jdk-21.0.12.1+1"
export ANDROID_HOME="$root/.tools/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$root/.tools/android-user-home"
# Java does not automatically consume HTTPS_PROXY. Store machine-specific settings only in ignored files.
python3 - <<'PY'
import os, urllib.parse
root=os.getcwd(); props=[]
proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:
    if proxy.username or proxy.password: raise SystemExit('Authenticated proxy requires supported external Java configuration.')
    for scheme in ('http','https'):
        props += [f'systemProp.{scheme}.proxyHost={proxy.hostname}', f'systemProp.{scheme}.proxyPort={proxy.port or 80}']
trust='/etc/ssl/certs/java/cacerts'
opts='-Xmx3g -Dfile.encoding=UTF-8'
if os.path.isfile(trust): opts += ' -Djavax.net.ssl.trustStore='+trust
props += ['org.gradle.jvmargs='+opts, 'systemProp.http.nonProxyHosts=localhost|127.*']
with open('.tools/gradle-home/gradle.properties','w') as f: f.write('\n'.join(props)+'\n')
with open('.tools/sdk-java-opts','w') as f:
    opts=''
    if proxy.hostname: opts += f'-Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port or 80} '
    if os.path.isfile(trust): opts += '-Djavax.net.ssl.trustStore='+trust
    f.write(opts)
PY
export JAVA_OPTS
JAVA_OPTS="$(cat .tools/sdk-java-opts)"
sdkmanager="$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager"
# Supply finite affirmative input; preserve sdkmanager's exit status.
python3 - "$sdkmanager" "$ANDROID_HOME" <<'PY'
import subprocess,sys
base=[sys.argv[1], '--sdk_root='+sys.argv[2]]
for args in [['--licenses'], ['platform-tools','platforms;android-36','build-tools;36.0.0','ndk;28.1.13356709','cmake;3.22.1']]:
    subprocess.run(base+args,input='y\n'*100,text=True,check=True)
PY
./tools/prepare-native.sh
echo 'Setup complete. Use tools/cloud-env.sh for subsequent build commands.'
