#!/usr/bin/env python3
"""Compare actual fresh baseline/candidate JUnit IDs, bind test-only source hashes, exclude model claims."""
import hashlib
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT/'build/perf02'


def cases(directory, started_ns=None):
    result = set()
    paths = sorted(Path(directory).glob('TEST-*.xml'))
    if not paths: raise ValueError('MISSING_JUNIT')
    for p in paths:
        data = p.read_bytes()
        if p.is_symlink() or len(data) > 8*1024*1024 or b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper(): raise ValueError('INVALID_XML')
        if started_ns is not None and p.stat().st_mtime_ns < started_ns: raise ValueError('STALE_JUNIT')
        root = ET.fromstring(data); count = 0
        for case in root.findall('testcase'):
            key = (case.attrib['classname'], case.attrib['name'])
            if key in result or any(case.find(k) is not None for k in ('failure', 'error', 'skipped')): raise ValueError('NONPASSING_OR_DUPLICATE_CASE')
            result.add(key); count += 1
        if root.tag != 'testsuite' or int(root.get('tests')) != count: raise ValueError('INVALID_JUNIT_COUNT')
    return result


def inputs():
    paths = sorted((ROOT/'perf02').rglob('*')) + [ROOT/'.github/workflows/perf02.yml']
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths
            if p.is_file() and p.suffix in {'.kt', '.py', '.gradle', '.yml'}}


def validate_binding(marker, actual_commit, actual_inputs):
    if marker['source_commit'] != actual_commit or marker['source_sha256'] != actual_inputs:
        raise ValueError('SOURCE_CHANGED_DURING_RUN')


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--start', action='store_true'); parser.add_argument('--require-marker', action='store_true')
    args = parser.parse_args()
    commit = subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    code = inputs(); marker_file = OUTPUT/'perf-marker.json'
    OUTPUT.mkdir(parents=True,exist_ok=True)
    if args.start:
        marker_file.write_text(json.dumps({'source_commit':commit,'source_sha256':code,'started_ns':time.time_ns()},indent=2)+'\n')
        print('PERF source hashes pinned before fresh candidate tests; no model invoked.'); return
    marker = json.loads(marker_file.read_text()) if marker_file.exists() else None
    if args.require_marker and marker is None: raise ValueError('MISSING_FRESH_RUN_MARKER')
    if marker is not None: validate_binding(marker, commit, code)
    baseline_dirs = {'domain': OUTPUT/'baseline-xml/domain', 'app': OUTPUT/'baseline-xml/app', 'endpoint': OUTPUT/'baseline-xml/endpoint'}
    candidate_dirs = {'domain': ROOT/'domain/build/test-results/test', 'app': ROOT/'app/build/test-results/testDebugUnitTest',
                      'endpoint': ROOT/'fixtures/p2-endpoint/build/test-results/testDebugUnitTest'}
    suites = {}
    for name in baseline_dirs:
        before, after = cases(baseline_dirs[name]), cases(candidate_dirs[name], marker['started_ns'] if marker else None)
        if not before <= after: raise ValueError('HISTORICAL_TEST_MISSING')
        suites[name] = {'baseline': len(before), 'candidate': len(after), 'new': len(after-before), 'failures': 0, 'errors': 0, 'skipped': 0,
                        'new_ids': sorted([list(x) for x in after-before])}
    if {k:v['baseline'] for k,v in suites.items()} != {'domain':145, 'app':40, 'endpoint':118}: raise ValueError('BASELINE_NOT_303')
    apk = ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if name.endswith('.dex') and any(prefix in z.read(name) for prefix in (b'Lfr/jarvis/perf/', b'Lfr/jarvis/llm/engine/MeasuredBackend;', b'Lfr/jarvis/llm/engine/MeasuredNativeApi;')):
                raise ValueError('PERF_TEST_CODE_IN_PRODUCTION_APK')
    overhead = ROOT/'domain/build/perf02/host-observer-overhead.json'
    if marker is not None and overhead.stat().st_mtime_ns < marker['started_ns']: raise ValueError('STALE_MICROBENCH')
    measured = json.loads(overhead.read_text())
    if measured['scope'] != 'HOST_SOFTWARE_MICROBENCH_ONLY' or measured['real_model_generation'] != 'NOT_RUN': raise ValueError('FALSE_MODEL_MEASUREMENT')
    result = {'schema':'jarvis.perf02.software-evidence/1',
        'source_commit':commit, 'fresh_marker_verified':marker is not None,
        'source_tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip(),
        'worktree_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT)),
        'suites':suites, 'source_sha256':code, 'observer_overhead':measured,
        'production_apk_has_perf_code':False, 'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'native_generation':'NOT_RUN', 'jni_streaming':'NOT_APPLIED', 'physical_android':'NOT_RUN', 'sla_lt_3s':'NON_QUALIFIE'}
    OUTPUT.mkdir(parents=True,exist_ok=True); (OUTPUT/'results.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'suites':{k:{a:b for a,b in v.items() if a!='new_ids'} for k,v in suites.items()}, 'physical_android':'NOT_RUN','sla':'NON_QUALIFIE'}))


if __name__ == '__main__': main()
