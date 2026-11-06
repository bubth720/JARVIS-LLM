#!/usr/bin/env python3
"""PERF-03B fresh evidence; actual model-free JNI is distinct from GGUF generation."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT/'build/perf03b'
BASE = 'ca9d8f060856e134d793d0c23764fae45ce2cc53'
SUITES = {'domain': 'domain/build/test-results/test', 'app': 'app/build/test-results/testDebugUnitTest',
          'endpoint': 'fixtures/p2-endpoint/build/test-results/testDebugUnitTest'}
STREAM_EXPORT = 'Java_fr_jarvis_llm_engine_NativeBridge_generateStream'
EXPECTED_GUARD = 'Protected baseline drift: app/src/main/AndroidManifest.xml'


def load_frozen(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT/path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


frozen = load_frozen('perf03a_evidence_parser', 'perf03/evidence.py')
strict_xml = load_frozen('fusion02_evidence_parser', 'tools/fusion02_evidence.py')


def git(root, *args): return subprocess.check_output(['git', *args], cwd=root, text=True).strip()
def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def provenance(root):
    return {'module': 'LLM', 'mission': 'PERF-03B', 'repository': 'bubth720/JARVIS-LLM',
        'source_commit': git(root, 'rev-parse', 'HEAD'), 'source_tree': git(root, 'rev-parse', 'HEAD^{tree}'),
        'worktree_dirty': bool(git(root, 'status', '--porcelain'))}


def sources(root):
    names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard'], cwd=root, text=True).splitlines()
    return {name: sha(root/name) for name in sorted(set(names)) if (root/name).is_file()}


def passing_cases(directory, started_ns):
    result = frozen.passing_cases(directory, started_ns)
    for path in Path(directory).glob('TEST-*.xml'):
        strict_xml.parse_xml(path.read_bytes())
    return result


def start(root, lane):
    report = {'schema': 'jarvis.perf03b.run/1', 'lane': lane, **provenance(root),
              'sources_sha256': sources(root), 'started_ns': time.time_ns()}
    if report['worktree_dirty']: raise ValueError('RUN_REQUIRES_CLEAN_COMMIT')
    if lane == 'baseline' and report['source_commit'] != BASE: raise ValueError('BASELINE_NOT_EXACT_GATE_A')
    (OUTPUT/('marker-'+lane+'.json')).write_text(json.dumps(report, indent=2)+'\n')


def finish(root, lane):
    marker = json.loads((OUTPUT/('marker-'+lane+'.json')).read_text())
    if marker['source_commit'] != git(root, 'rev-parse', 'HEAD') or marker['sources_sha256'] != sources(root):
        raise ValueError('SOURCES_CHANGED_DURING_RUN')
    if git(root, 'status', '--porcelain'): raise ValueError('DIRTY_RESULT')
    paths = ({'runtime': 'perf03b/runtime-review/build/test-results/test'} if lane == 'runtime' else
             {'lab': 'perf03b/android-lab/build/test-results/testDebugUnitTest'} if lane == 'lab' else SUITES)
    suites = {key: passing_cases(root/path, marker['started_ns']) for key, path in paths.items()}
    if lane == 'baseline' and {k:v['tests'] for k,v in suites.items()} != {'domain': 190, 'app': 49, 'endpoint': 118}:
        raise ValueError('GATE_A_BASELINE_NOT_357')
    report = {'schema': 'jarvis.perf03b.jvm-evidence/1', 'run': marker, 'suites': suites,
              'approved_patch_applied': lane != 'baseline', 'real_generation': 'NOT_RUN', 'physical_android': 'NOT_RUN',
              'ttft_ms': None, 'ttft_status': 'NOT_RUN', 'sla_lt_3s': 'NOT_QUALIFIED', 'gate_b': 'PENDING_USER_VALIDATION'}
    (OUTPUT/(lane+'-results.json')).write_text(json.dumps(report, indent=2)+'\n')
    for key, path in paths.items():
        target = OUTPUT/(lane+'-xml')/key; target.mkdir(parents=True, exist_ok=True)
        for p in (root/path).glob('TEST-*.xml'): shutil.copy2(p, target/p.name)
    print(json.dumps({k:v['tests'] for k,v in suites.items()}))


def compare_reports(baseline, candidate):
    if baseline['run']['source_commit'] != BASE: raise ValueError('COMPARISON_BASE_NOT_GATE_A')
    suites = {}
    if set(baseline['suites']) != set(candidate['suites']): raise ValueError('SUITE_SET_CHANGED')
    for name, before in baseline['suites'].items():
        after = candidate['suites'][name]
        old, new = {tuple(x) for x in before['ids']}, {tuple(x) for x in after['ids']}
        if not old <= new: raise ValueError('HISTORICAL_TEST_MISSING:'+name)
        for entry in (before, after):
            if entry['tests'] != len(entry['ids']) or len(entry['ids']) != len({tuple(x) for x in entry['ids']}):
                raise ValueError('COMPARISON_CASE_COUNT_OR_DUPLICATE')
            if any(entry[k] for k in ('failures', 'errors', 'skipped')): raise ValueError('COMPARISON_NONPASSING')
        suites[name] = {'baseline': len(old), 'candidate': len(new), 'new': len(new-old), 'failures': 0, 'errors': 0, 'skipped': 0}
    return {'schema': 'jarvis.perf03b.regression/1', 'baseline_commit': BASE,
        'candidate_commit': candidate['run']['source_commit'], 'candidate_tree': candidate['run']['source_tree'],
        'suites': suites, 'all_historical_ids_preserved': True, 'performance_before_after': 'NOT_RUN',
        'real_generation': 'NOT_RUN', 'sla_lt_3s': 'NOT_QUALIFIED'}


def compare():
    result = compare_reports(json.loads((OUTPUT/'baseline-results.json').read_text()), json.loads((OUTPUT/'candidate-results.json').read_text()))
    (OUTPUT/'regression.json').write_text(json.dumps(result, indent=2)+'\n'); print(json.dumps(result))


def inherited(root, lane):
    result = subprocess.run(['python3', 'tools/check-preservation-003e.py'], cwd=root, text=True, capture_output=True)
    raw = result.stdout + result.stderr
    (OUTPUT/('inherited-003e-'+lane+'.log')).write_text(raw)
    report = {'schema': 'jarvis.perf03b.inherited-guard/1', **provenance(root),
        'guard': 'tools/check-preservation-003e.py', 'guard_sha256': sha(root/'tools/check-preservation-003e.py'),
        'exit_code': result.returncode, 'status': 'FAIL', 'last_line': raw.strip().splitlines()[-1] if raw.strip() else '',
        'failure_hidden': False, 'guard_changed': False}
    (OUTPUT/('inherited-003e-'+lane+'.json')).write_text(json.dumps(report, indent=2)+'\n')
    if result.returncode != 1 or report['last_line'] != EXPECTED_GUARD: raise ValueError('INHERITED_GUARD_RESULT_CHANGED')
    print('Historical guard FAIL retained verbatim: '+EXPECTED_GUARD)


def validate_exports(before, after):
    if len(before) != 12 or STREAM_EXPORT in before: raise ValueError('BASELINE_JNI_EXPORT_SET')
    if set(after) != set(before) | {STREAM_EXPORT}: raise ValueError('UNREVIEWED_JNI_EXPORT_CHANGE')


def inspect_apk(root, lane):
    apk = root/'app/build/outputs/apk/debug/app-debug.apk'
    sdk = Path(os.environ['ANDROID_HOME']); bt = sdk/'build-tools/36.0.0'
    nm = sdk/'ndk/28.1.13356709/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
    badging = subprocess.check_output([str(bt/'aapt'), 'dump', 'badging', str(apk)], text=True)
    if "package: name='fr.jarvis.llm'" not in badging: raise ValueError('HISTORICAL_APPLICATION_ID_CHANGED')
    permissions = subprocess.check_output([str(bt/'aapt'), 'dump', 'permissions', str(apk)], text=True)
    used = sorted(re.findall(r"uses-permission: name='([^']+)'", permissions))
    certs = subprocess.check_output([str(bt/'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    match = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', certs)
    if not match: raise ValueError('MISSING_SIGNER_DIGEST')
    symbols = set(); lib_sha = {}; out = OUTPUT/('apk-'+lane); out.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.endswith('.dex') and any(prefix in z.read(name) for prefix in (b'Lfr/jarvis/perf/', b'Lfr/jarvis/perf03/',
                    b'Lfr/jarvis/perf03b/', b'Lfr/jarvis/llm/perf03b/', b'Lfr/jarvis/llm/engine/MeasuredBackend;',
                    b'Lfr/jarvis/llm/engine/MeasuredNativeApi;')):
                raise ValueError('LAB_OR_REVIEW_CODE_IN_HISTORICAL_APK')
            if name.startswith('lib/') and name.endswith('.so'):
                data = z.read(name); lib_sha[name] = hashlib.sha256(data).hexdigest()
                p = out/Path(name).name; p.write_bytes(data)
                listing = subprocess.check_output([str(nm), '-D', '--defined-only', str(p)], text=True)
                symbols.update(line.split()[-1] for line in listing.splitlines() if line.split()[-1].startswith('Java_'))
    if not symbols: raise ValueError('MISSING_JNI_EXPORTS')
    report = {'schema': 'jarvis.perf03b.apk-boundary/1', **provenance(root),
        'apk_sha256': sha(apk), 'libraries_sha256': lib_sha, 'jni_exports': sorted(symbols), 'permissions': used,
        'application_id': 'fr.jarvis.llm', 'certificate_sha256': match[1], 'review_or_lab_classes_in_historical_dex': False,
        'stream_jni_present': STREAM_EXPORT in symbols, 'streaming_historical_ui_enabled': False,
        'safety_permission_alignment': 'SEE_CHECK_APK_RAW_LOG', 'physical_android': 'NOT_RUN'}
    (OUTPUT/('apk-'+lane+'.json')).write_text(json.dumps(report, indent=2)+'\n')
    if lane == 'baseline':
        if len(symbols) != 12 or STREAM_EXPORT in symbols: raise ValueError('UNEXPECTED_BASELINE_EXPORTS')
    else:
        baseline = json.loads((OUTPUT/'apk-baseline.json').read_text()); validate_exports(baseline['jni_exports'], report['jni_exports'])
        if baseline['certificate_sha256'] != report['certificate_sha256'] or baseline['permissions'] != report['permissions']:
            raise ValueError('HISTORICAL_SIGNER_OR_PERMISSIONS_CHANGED')
        before = json.loads((OUTPUT/'inherited-003e-baseline.json').read_text()); after = json.loads((OUTPUT/'inherited-003e-candidate.json').read_text())
        if (before['exit_code'], before['last_line'], before['guard_sha256']) != (after['exit_code'], after['last_line'], after['guard_sha256']):
            raise ValueError('INHERITED_GUARD_CHANGED')
    print('PASS historical APK: %d JNI signatures; approved stream export=%s' % (len(symbols), STREAM_EXPORT in symbols))


def package():
    roots = [('candidate', ROOT)]
    if os.environ.get('RUNNER_TEMP'):
        baseline = Path(os.environ['RUNNER_TEMP'])/'perf03b-baseline'
        if baseline.is_dir(): roots.append(('baseline', baseline))
    for lane, root in roots:
        for label, path in SUITES.items():
            directory = root/path
            if directory.is_dir(): shutil.copytree(directory, OUTPUT/'raw-junit'/lane/label, dirs_exist_ok=True)
    for label, source in [('runtime', ROOT/'perf03b/runtime-review/build'), ('lab', ROOT/'perf03b/android-lab/build')]:
        for kind in ('test-results', 'reports'):
            path = source/kind
            if path.is_dir(): shutil.copytree(path, OUTPUT/'raw-review'/label/kind, dirs_exist_ok=True)
    for source in [ROOT/'build/native-tests/Testing/Temporary/LastTest.log', ROOT/'build/mission003f-apks.json',
                   ROOT/'perf03/NATIVE-STREAMING.patch', ROOT/'perf03/PREIMAGES.json', ROOT/'perf03b/APPLICATION.json',
                   ROOT/'.github/workflows/perf03b.yml']:
        if source.is_file(): shutil.copy2(source, OUTPUT/source.name)
    files = sorted(p for p in OUTPUT.rglob('*') if p.is_file() and p.name != 'SHA256SUMS')
    (OUTPUT/'SHA256SUMS').write_text(''.join(sha(p)+'  '+p.relative_to(OUTPUT).as_posix()+'\n' for p in files))
    print('Archived %d evidence files; a missing downstream result remains NOT_RUN.' % len(files))


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['start','finish','compare','inherited','apk','package'])
    parser.add_argument('--root', type=Path, default=ROOT); parser.add_argument('--lane', choices=['baseline','candidate','runtime','lab'], default='candidate')
    args = parser.parse_args(); OUTPUT.mkdir(parents=True, exist_ok=True)
    if args.action == 'start': start(args.root.resolve(), args.lane)
    elif args.action == 'finish': finish(args.root.resolve(), args.lane)
    elif args.action == 'compare': compare()
    elif args.action == 'inherited': inherited(args.root.resolve(), args.lane)
    elif args.action == 'apk': inspect_apk(args.root.resolve(), args.lane)
    else: package()


if __name__ == '__main__': main()
