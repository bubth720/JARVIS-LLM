#!/usr/bin/env python3
"""Fresh software evidence for PERF-03A; no model, native-streaming or device attestation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT/'build/perf03'
SUITES = {'domain': 'domain/build/test-results/test', 'app': 'app/build/test-results/testDebugUnitTest',
          'endpoint': 'fixtures/p2-endpoint/build/test-results/testDebugUnitTest'}
BASE = '09e278b54b8be58a3d665d2d91de10f8869e40bb'
EXPECTED_GUARD = 'Protected baseline drift: app/src/main/AndroidManifest.xml'


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def sources(root):
    names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard'], cwd=root, text=True).splitlines()
    return {name: sha(root/name) for name in sorted(set(names)) if (root/name).is_file()}


def passing_cases(directory, started_ns):
    paths = sorted(Path(directory).glob('TEST-*.xml'))
    if not 0 < len(paths) <= 256: raise ValueError('MISSING_OR_EXCESSIVE_JUNIT')
    result = set()
    hashes = {}
    for p in paths:
        data = p.read_bytes()
        if p.is_symlink() or p.stat().st_mtime_ns < started_ns or len(data) > 8*1024*1024:
            raise ValueError('STALE_OR_INVALID_JUNIT')
        if b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper(): raise ValueError('UNSAFE_XML')
        node = ET.fromstring(data)
        cases = node.findall('testcase')
        if node.tag != 'testsuite' or int(node.get('tests')) != len(cases) or not cases: raise ValueError('JUNIT_COUNT')
        for case in cases:
            key = (case.attrib['classname'], case.attrib['name'])
            if key in result or any(case.find(x) is not None for x in ('failure', 'error', 'skipped')):
                raise ValueError('NONPASSING_OR_DUPLICATE_CASE')
            result.add(key)
        hashes[p.name] = hashlib.sha256(data).hexdigest()
    return {'tests': len(result), 'failures': 0, 'errors': 0, 'skipped': 0,
            'ids': sorted([list(x) for x in result]), 'xml_sha256': hashes}


def marker_path(lane): return OUTPUT/('marker-'+lane+'.json')


def start(root, lane):
    report = {'schema': 'jarvis.perf03a.run/1', 'lane': lane, 'source_commit': git(root, 'rev-parse', 'HEAD'),
              'source_tree': git(root, 'rev-parse', 'HEAD^{tree}'), 'worktree_dirty': bool(git(root, 'status', '--porcelain')),
              'sources_sha256': sources(root), 'started_ns': time.time_ns()}
    if report['worktree_dirty']: raise ValueError('RUN_REQUIRES_CLEAN_COMMIT')
    if lane == 'baseline' and report['source_commit'] != BASE: raise ValueError('BASELINE_NOT_EXACT_PERF02')
    marker_path(lane).write_text(json.dumps(report, indent=2)+'\n')


def finish(root, lane):
    marker = json.loads(marker_path(lane).read_text())
    if marker['source_commit'] != git(root, 'rev-parse', 'HEAD') or marker['sources_sha256'] != sources(root):
        raise ValueError('SOURCES_CHANGED_DURING_RUN')
    if git(root, 'status', '--porcelain'): raise ValueError('DIRTY_RESULT')
    paths = {'kotlin_review': 'perf03/kotlin-review/build/test-results/test'} if lane == 'review' else SUITES
    suites = {key: passing_cases(root/path, marker['started_ns']) for key, path in paths.items()}
    if lane == 'baseline' and {key: value['tests'] for key, value in suites.items()} != {'domain': 190, 'app': 49, 'endpoint': 118}:
        raise ValueError('PERF02_FINAL_BASELINE_NOT_357')
    report = {'schema': 'jarvis.perf03a.jvm-evidence/1', 'run': marker, 'suites': suites,
              'native_streaming': 'NOT_APPLIED', 'real_generation': 'NOT_RUN', 'physical_android': 'NOT_RUN',
              'ttft_ms': None, 'ttft_status': 'NOT_RUN', 'sla_lt_3s': 'NOT_QUALIFIED'}
    (OUTPUT/(lane+'-results.json')).write_text(json.dumps(report, indent=2)+'\n')
    dest = OUTPUT/(lane+'-xml')
    for key, path in paths.items():
        target = dest/key
        target.mkdir(parents=True, exist_ok=True)
        for p in (root/path).glob('TEST-*.xml'): shutil.copy2(p, target/p.name)
    print(json.dumps({key: value['tests'] for key, value in suites.items()}))


def compare():
    baseline = json.loads((OUTPUT/'baseline-results.json').read_text())
    candidate = json.loads((OUTPUT/'candidate-results.json').read_text())
    suites = {}
    for name, before in baseline['suites'].items():
        after = candidate['suites'][name]
        old, new = {tuple(x) for x in before['ids']}, {tuple(x) for x in after['ids']}
        if not old <= new: raise ValueError('HISTORICAL_TEST_MISSING:'+name)
        suites[name] = {'baseline': len(old), 'candidate': len(new), 'new': len(new-old), 'failures': 0, 'errors': 0, 'skipped': 0}
    report = {'schema': 'jarvis.perf03a.regression/1', 'baseline_commit': baseline['run']['source_commit'],
              'candidate_commit': candidate['run']['source_commit'], 'candidate_tree': candidate['run']['source_tree'],
              'suites': suites, 'all_historical_ids_preserved': True, 'performance_before_after': 'NOT_RUN',
              'native_streaming': 'NOT_APPLIED', 'sla_lt_3s': 'NOT_QUALIFIED'}
    (OUTPUT/'regression.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report))


def inherited(root, lane):
    result = subprocess.run(['python3', 'tools/check-preservation-003e.py'], cwd=root, text=True, capture_output=True)
    raw = result.stdout + result.stderr
    (OUTPUT/('inherited-003e-'+lane+'.log')).write_text(raw)
    report = {'schema': 'jarvis.perf03a.inherited-guard/1', 'source_commit': git(root, 'rev-parse', 'HEAD'),
              'guard': 'tools/check-preservation-003e.py', 'guard_sha256': sha(root/'tools/check-preservation-003e.py'),
              'exit_code': result.returncode, 'status': 'FAIL', 'last_line': raw.strip().splitlines()[-1] if raw.strip() else '',
              'expected_historical_failure': EXPECTED_GUARD, 'failure_hidden': False, 'guard_changed': False}
    (OUTPUT/('inherited-003e-'+lane+'.json')).write_text(json.dumps(report, indent=2)+'\n')
    if result.returncode == 0 or report['last_line'] != EXPECTED_GUARD:
        raise ValueError('INHERITED_GUARD_RESULT_CHANGED')
    print('Historical guard FAIL retained verbatim: '+EXPECTED_GUARD)


def inspect_apk(root, lane):
    apk = root/'app/build/outputs/apk/debug/app-debug.apk'
    sdk = Path(os.environ['ANDROID_HOME'])
    nm = sdk/'ndk/28.1.13356709/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
    forbidden = (b'Lfr/jarvis/perf/', b'Lfr/jarvis/perf03/', b'Lfr/jarvis/llm/engine/MeasuredBackend;',
                 b'Lfr/jarvis/llm/engine/MeasuredNativeApi;', b'Lfr/jarvis/llm/engine/OwnedStreamContract;')
    symbols = set()
    lib_sha = {}
    out = OUTPUT/('apk-'+lane)
    out.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.endswith('.dex') and any(prefix in z.read(name) for prefix in forbidden):
                raise ValueError('REVIEW_CODE_IN_PRODUCTION_APK')
            if name.startswith('lib/') and name.endswith('.so'):
                data = z.read(name)
                lib_sha[name] = hashlib.sha256(data).hexdigest()
                p = out/Path(name).name
                p.write_bytes(data)
                listing = subprocess.check_output([str(nm), '-D', '--defined-only', str(p)], text=True)
                symbols.update(line.split()[-1] for line in listing.splitlines() if line.split()[-1].startswith('Java_'))
    if not symbols or any('generateStream' in name for name in symbols): raise ValueError('UNAPPROVED_STREAMING_SYMBOL')
    report = {'schema': 'jarvis.perf03a.apk-boundary/1', 'source_commit': git(root, 'rev-parse', 'HEAD'),
              'apk_sha256': sha(apk), 'libraries_sha256': lib_sha, 'jni_exports': sorted(symbols),
              'review_code_in_dex': False, 'stream_jni_present': False,
              'safety_permission_alignment': 'SEE_CHECK_APK_RAW_LOG', 'lab_streaming_apk': 'NOT_BUILT_GATE_A'}
    (OUTPUT/('apk-'+lane+'.json')).write_text(json.dumps(report, indent=2)+'\n')
    if lane == 'candidate':
        baseline = json.loads((OUTPUT/'apk-baseline.json').read_text())
        if baseline['jni_exports'] != report['jni_exports']: raise ValueError('LEGACY_JNI_SIGNATURES_CHANGED')
        before = json.loads((OUTPUT/'inherited-003e-baseline.json').read_text())
        after = json.loads((OUTPUT/'inherited-003e-candidate.json').read_text())
        if (before['exit_code'], before['last_line'], before['guard_sha256']) != (after['exit_code'], after['last_line'], after['guard_sha256']):
            raise ValueError('INHERITED_GUARD_CHANGED')
    print('PASS APK boundary and historical JNI exports: '+str(len(symbols))+' signatures')


def package():
    # Preserve raw XML/reports/logs and their checksums before Actions retention expires.
    roots = [('current', ROOT)]
    if os.environ.get('RUNNER_TEMP'):
        baseline = Path(os.environ['RUNNER_TEMP'])/'perf03-baseline'
        if baseline.is_dir(): roots.append(('baseline', baseline))
    for lane, root in roots:
        for label, source in SUITES.items():
            directory = root/source
            if directory.is_dir(): shutil.copytree(directory, OUTPUT/'raw-junit'/lane/label, dirs_exist_ok=True)
    for label, source in [('domain', ROOT/'domain/build/reports'), ('app', ROOT/'app/build/reports'),
                          ('endpoint', ROOT/'fixtures/p2-endpoint/build/reports'), ('client', ROOT/'fixtures/p2-client/build/reports'),
                          ('kotlin-review', ROOT/'perf03/kotlin-review/build/reports')]:
        if source.exists(): shutil.copytree(source, OUTPUT/'reports'/label, dirs_exist_ok=True)
    review_xml = ROOT/'perf03/kotlin-review/build/test-results'
    if review_xml.exists(): shutil.copytree(review_xml, OUTPUT/'raw-junit/kotlin-review', dirs_exist_ok=True)
    for source in [ROOT/'build/native-tests/Testing/Temporary/LastTest.log', ROOT/'build/mission003f-apks.json',
                   ROOT/'build/fusion-02/native-review.json']:
        if source.is_file(): shutil.copy2(source, OUTPUT/source.name)
    for source in [ROOT/'perf03/NATIVE-STREAMING.patch', ROOT/'perf03/PREIMAGES.json', ROOT/'.github/workflows/perf03.yml']:
        if source.is_file(): shutil.copy2(source, OUTPUT/source.name)
    files = sorted(p for p in OUTPUT.rglob('*') if p.is_file() and p.name != 'SHA256SUMS')
    (OUTPUT/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.relative_to(OUTPUT).as_posix()+'\n' for p in files))
    print('Archived %d files with SHA256; no model output or physical measurement.' % len(files))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['start', 'finish', 'compare', 'inherited', 'apk', 'package'])
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--lane', choices=['baseline', 'candidate', 'review'], default='candidate')
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if args.action == 'start': start(args.root.resolve(), args.lane)
    elif args.action == 'finish': finish(args.root.resolve(), args.lane)
    elif args.action == 'compare': compare()
    elif args.action == 'inherited': inherited(args.root.resolve(), args.lane)
    elif args.action == 'apk': inspect_apk(args.root.resolve(), args.lane)
    else: package()


if __name__ == '__main__': main()
