#!/usr/bin/env python3
"""Inspect the built model-free laboratory APK; never install or launch it."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT/'build/perf03b/lab-apk'
PACKAGE = 'fr.jarvis.llm.perf03b.lab'
STREAM_SYMBOL = 'Java_fr_jarvis_llm_engine_NativeBridge_generateStream'


def command(*args):
    return subprocess.check_output([str(arg) for arg in args], cwd=ROOT, text=True)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def signer(tools, apk):
    raw = command(tools/'apksigner', 'verify', '--verbose', '--print-certs', apk)
    found = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})', raw)
    if len(found) != 1:
        raise ValueError('EXACTLY_ONE_VALID_APK_SIGNER_REQUIRED')
    return found[0], raw


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    matches = sorted((ROOT/'perf03b/android-lab/build/outputs/apk/debug').glob('*.apk'))
    if len(matches) != 1:
        raise ValueError('EXACTLY_ONE_LABORATORY_APK_REQUIRED')
    apk = matches[0]
    sdk = Path(os.environ['ANDROID_HOME'])
    tools = sdk/'build-tools/36.0.0'
    llvm = sdk/'ndk/28.1.13356709/toolchains/llvm/prebuilt/linux-x86_64/bin'
    badging = command(tools/'aapt', 'dump', 'badging', apk)
    permissions = command(tools/'aapt', 'dump', 'permissions', apk)
    manifest = command(tools/'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
    if "package: name='"+PACKAGE+"'" not in badging or "sdkVersion:'29'" not in badging or "targetSdkVersion:'36'" not in badging:
        raise ValueError('LAB_PACKAGE_OR_ANDROID_RANGE_DRIFT')
    if re.search(r'(?m)^\s*(?:uses-permission|permission):', permissions):
        raise ValueError('LAB_MUST_HAVE_NO_PERMISSIONS')
    if not re.search(r'testOnly[^\n]*0xffffffff', manifest):
        raise ValueError('LAB_MUST_BE_TEST_ONLY')
    if not re.search(r'allowBackup[^\n]*0x0\b', manifest) or not re.search(r'usesCleartextTraffic[^\n]*0x0\b', manifest):
        raise ValueError('LAB_BACKUP_OR_CLEARTEXT_DRIFT')
    blocks = re.split(r'(?m)^\s*E: ', manifest)
    activities = [b for b in blocks if b.startswith('activity ')]
    if len(activities) != 1 or PACKAGE+'.LabActivity' not in activities[0] or not re.search(r'exported[^\n]*0xffffffff', activities[0]):
        raise ValueError('EXACT_LAB_ACTIVITY_REQUIRED')
    if any(b.startswith(('service ', 'receiver ', 'provider ')) for b in blocks):
        raise ValueError('NO_LAB_BACKGROUND_COMPONENT_OR_TRANSPORT')
    certificate, signature_raw = signer(tools, apk)
    historical = ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    endpoint = ROOT/'fixtures/p2-endpoint/build/outputs/apk/debug/p2-endpoint-debug.apk'
    historical_certificate, _ = signer(tools, historical)
    endpoint_certificate, _ = signer(tools, endpoint)
    if certificate in {historical_certificate, endpoint_certificate}:
        raise ValueError('LAB_SIGNER_MUST_BE_DISTINCT')
    command(tools/'zipalign', '-c', '-P', '16', '4', apk)
    baseline = json.loads((ROOT/'build/perf03b/apk-baseline.json').read_text())
    expected = set(baseline['jni_exports']) | {STREAM_SYMBOL}
    symbols = set()
    libraries = {}
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix='jarvis-perf03b-apk-') as tmp:
        names = archive.namelist()
        if any(n.lower().endswith(('.gguf', '.safetensors', '.onnx')) for n in names):
            raise ValueError('NO_MODEL_MAY_BE_BUNDLED')
        native = [n for n in names if n.startswith('lib/') and n.endswith('.so')]
        if not native or any(not n.startswith('lib/arm64-v8a/') for n in native):
            raise ValueError('ARM64_ONLY_NATIVE_REQUIRED')
        dex = b''.join(archive.read(n) for n in names if n.endswith('.dex'))
        for required in (b'Lfr/jarvis/llm/engine/LlamaBackend;', b'Lfr/jarvis/llm/engine/OwnedStreamCallback;', b'Lfr/jarvis/llm/engine/OwnedStreamBuffer;'):
            if required not in dex:
                raise ValueError('APPLIED_BACKEND_MISSING_FROM_LAB_DEX')
        if b'Lfr/jarvis/llm/MainActivity;' in dex or b'Lfr/jarvis/perf/' in dex:
            raise ValueError('HISTORICAL_UI_OR_PERF02_REVIEW_CODE_IN_LAB')
        for name in native:
            data = archive.read(name)
            extracted = Path(tmp)/Path(name).name
            extracted.write_bytes(data)
            elf = command(llvm/'llvm-readelf', '-lW', extracted)
            loads = [line for line in elf.splitlines() if line.strip().startswith('LOAD')]
            if not loads or any(int(line.split()[-1], 16) < 16384 for line in loads):
                raise ValueError('LAB_NATIVE_ELF_NOT_16_KIB_ALIGNED')
            listing = command(llvm/'llvm-nm', '-D', '--defined-only', extracted)
            symbols.update(line.split()[-1] for line in listing.splitlines() if line.split() and line.split()[-1].startswith('Java_'))
            libraries[name] = sha(data)
            (OUTPUT/(Path(name).name+'-elf.txt')).write_text(elf)
    if symbols != expected or len(baseline['jni_exports']) != 12:
        raise ValueError('LEGACY_OR_APPROVED_STREAM_JNI_EXPORT_DRIFT')
    copied = ROOT/'perf03b/android-lab/build/applied-engine-sources'
    source_hashes = {}
    for relative in ('fr/jarvis/llm/engine/LlamaBackend.kt', 'fr/jarvis/llm/engine/OwnedStreamContract.kt', 'fr/jarvis/llm/ProcessResources.kt'):
        applied = (ROOT/'app/src/main/java'/relative).read_bytes()
        if (copied/relative).read_bytes() != applied:
            raise ValueError('LAB_MUST_COMPILE_EXACT_APPLIED_ENGINE_SOURCES')
        source_hashes[relative] = sha(applied)
    for name, text in [('badging.txt', badging), ('permissions.txt', permissions), ('manifest.txt', manifest), ('apksigner.txt', signature_raw), ('jni-exports.txt', '\n'.join(sorted(symbols))+'\n')]:
        (OUTPUT/name).write_text(text)
    report = {'schema': 'jarvis.perf03b.lab-apk/1', 'status': 'PASS',
        'source_commit': command('git', 'rev-parse', 'HEAD').strip(),
        'source_tree': command('git', 'rev-parse', 'HEAD^{tree}').strip(),
        'worktree_dirty': bool(command('git', 'status', '--porcelain').strip()),
        'apk': apk.relative_to(ROOT).as_posix(), 'apk_sha256': sha(apk.read_bytes()),
        'application_id': PACKAGE, 'test_only': True, 'permissions': [], 'model_bundled': False,
        'signer_certificate_sha256': certificate, 'signer_distinct_from_historical_and_fixture': True,
        'abi': ['arm64-v8a'], 'elf_and_zip_alignment': 16384, 'libraries_sha256': libraries,
        'jni_exports': sorted(symbols), 'legacy_exports_preserved': True,
        'applied_kotlin_source_sha256': source_hashes, 'native_source': 'app/src/main/cpp/bridge.cpp',
        'native_execution_from_apk': 'NOT_RUN', 'real_model_generation': 'NOT_RUN',
        'core_authority': 'NOT_CONNECTED', 'physical_android': 'NOT_RUN', 'ttft_p95_ms': None,
        'sla_lt_3s': 'NOT_QUALIFIED', 'personal_installation': False}
    (ROOT/'build/perf03b/lab-apk.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps({k: report[k] for k in ('status', 'source_commit', 'apk_sha256', 'application_id', 'test_only', 'permissions', 'legacy_exports_preserved', 'real_model_generation')}))


if __name__ == '__main__':
    main()
