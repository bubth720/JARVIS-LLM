#!/usr/bin/env python3
"""Fresh, bounded software evidence. Never attests native execution or device safety."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import time
import uuid
import xml.etree.ElementTree as ET

BASE = 'e0b781442b21034a251f6af450396faabc5f667d'
ROOT = Path(__file__).resolve().parent.parent
SUITES = {'domain': 'domain/build/test-results/test', 'app': 'app/build/test-results/testDebugUnitTest',
          'endpoint': 'fixtures/p2-endpoint/build/test-results/testDebugUnitTest'}

def require(condition, reason):
    if not condition:
        raise ValueError(reason)

def parse_xml(data):
    require(len(data) <= 8 * 1024 * 1024, 'XML_TOO_LARGE')
    require(b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper(), 'XML_DECLARATION_FORBIDDEN')
    root = ET.fromstring(data)
    require(root.tag == 'testsuite', 'XML_SUITE_REQUIRED')
    cases = root.findall('testcase')
    require(bool(cases), 'EMPTY_SUITE')
    identities = [(c.attrib['classname'], c.attrib['name']) for c in cases]
    require(len(set(identities)) == len(identities), 'DUPLICATE_CASE')
    actual = {'tests': len(cases), 'failures': sum(c.find('failure') is not None for c in cases),
              'errors': sum(c.find('error') is not None for c in cases), 'skipped': sum(c.find('skipped') is not None for c in cases)}
    require(all(int(root.get(k, '0')) == n for k, n in actual.items()), 'COUNT_MISMATCH')
    require(not any(actual[k] for k in ('failures', 'errors', 'skipped')), 'NON_PASSING_SUITE')
    return identities

def inspect_suite(directory, baseline, started_ns):
    files = sorted(Path(directory).glob('TEST-*.xml'))
    require(0 < len(files) <= 128, 'MISSING_OR_EXCESSIVE_RESULTS')
    identities = set()
    hashes = {}
    for p in files:
        require(not p.is_symlink() and p.stat().st_mtime_ns >= started_ns, 'STALE_OR_SYMLINK_RESULT')
        data = p.read_bytes()
        cases = parse_xml(data)
        require(not identities.intersection(cases), 'DUPLICATE_CASE_ACROSS_FILES')
        identities.update(cases)
        hashes[p.name] = hashlib.sha256(data).hexdigest()
    require(set(baseline).issubset(identities), 'BASELINE_CASE_MISSING')
    return {'tests': len(identities), 'failures': 0, 'errors': 0, 'skipped': 0,
            'baseline_cases': len(baseline), 'new_cases': len(identities) - len(baseline), 'xml_sha256': hashes}

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

def source_inputs():
    names = git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines()
    extensions = {'.kt', '.kts', '.java', '.cpp', '.h', '.py', '.sh', '.yml', '.yaml', '.properties'}
    return {p: hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sorted(set(names))
            if Path(p).suffix in extensions and (p.startswith(('app/', 'domain/', 'fixtures/', 'tools/', '.github/', 'gradle/'))
                                                or '/' not in p)}

def baseline_cases():
    data = git('show', BASE + ':docs/fusion-01/evidence/junit-local.tar.gz')
    result = {k: set() for k in SUITES}
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as tar:
        members = tar.getmembers()
        require(len(members) <= 128, 'BASELINE_ARCHIVE_TOO_LARGE')
        for m in members:
            require(m.isfile() and not m.issym() and m.size <= 8*1024*1024, 'INVALID_BASELINE_MEMBER')
            parts = Path(m.name).parts
            require(len(parts) == 2 and parts[0] in result and parts[1].startswith('TEST-') and parts[1].endswith('.xml'), 'INVALID_BASELINE_PATH')
            cases = parse_xml(tar.extractfile(m).read())
            require(not result[parts[0]].intersection(cases), 'DUPLICATE_BASELINE')
            result[parts[0]].update(cases)
    require({k: len(v) for k, v in result.items()} == {'domain': 127, 'app': 40, 'endpoint': 118}, 'BASELINE_NOT_285')
    return result

def main():
    p = argparse.ArgumentParser()
    p.add_argument('action', choices=['start', 'finish'])
    p.add_argument('--marker', default='build/fusion-02/run.json')
    p.add_argument('--output', default='build/fusion-02/results.json')
    args = p.parse_args()
    marker = ROOT/args.marker
    if args.action == 'start':
        marker.parent.mkdir(parents=True, exist_ok=True)
        marker.write_text(json.dumps({'format': 'jarvis.llm.fusion-02.run/1', 'run_id': str(uuid.uuid4()),
            'source_commit': git('rev-parse', 'HEAD').decode().strip(), 'source_inputs': source_inputs(),
            'started_ns': time.time_ns()}, indent=2)+'\n')
        print('Software run marker created; --rerun-tasks required')
        return
    run = json.loads(marker.read_text())
    require(run['format'] == 'jarvis.llm.fusion-02.run/1', 'UNSUPPORTED_RUN_VERSION')
    require(run['source_commit'] == git('rev-parse', 'HEAD').decode().strip(), 'COMMIT_CHANGED_DURING_RUN')
    require(run['source_inputs'] == source_inputs(), 'SOURCE_CHANGED_DURING_RUN')
    baseline = baseline_cases()
    suites = {k: inspect_suite(ROOT/v, baseline[k], run['started_ns']) for k,v in SUITES.items()}
    trace = ROOT/'domain/build/fusion-02/client-traces.json'
    require(trace.stat().st_mtime_ns >= run['started_ns'], 'STALE_TRACE')
    traced = json.loads(trace.read_text())
    require(traced['origin'] == 'TEST_ONLY' and traced['traces'] == 4096 and traced['native_ready'] is False, 'INVALID_TRACE')
    report = {'format': 'jarvis.llm.fusion-02.validation/1', 'scope': 'SOFTWARE_TEST_ONLY',
              'run': run, 'suites': suites, 'fixture_trace': traced, 'success': True,
              'native_generation': 'NOT_RUN', 'native_unload_qualified': False,
              'android_instrumented': 'NOT_RUN', 'physical': 'NOT_RUN'}
    out = ROOT/args.output; out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps({k: {a:b for a,b in v.items() if a!='xml_sha256'} for k,v in suites.items()}))

if __name__ == '__main__':
    main()
