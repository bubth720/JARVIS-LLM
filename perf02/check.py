#!/usr/bin/env python3
"""PERF-02: exact additive test/review scope, unchanged runtime/JNI/contracts and safety."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = '1b62a5c50eb979d82ef8eb0b488ab38c6fe462ce'
M50 = 'b670181987d39d27e2d0d9574fa448397a38fe42'
BRANCH = 'codex/llm-perf-ttft-3s'
MISSION = 'mission/llm-perf-ttft-3s-20261104'
WORKFLOW = '.github/workflows/android.yml'
ADDITIONS = {
    '.github/workflows/perf02.yml', 'perf02/check.py', 'perf02/test_preservation.py',
    'perf02/benchmark.py', 'perf02/test_benchmark.py', 'perf02/evidence.py', 'perf02/test_evidence.py', 'perf02/qualification.init.gradle',
    'perf02/README.md', 'perf02/REPORT.md', 'perf02/PROTOCOL.md', 'perf02/NATIVE-STREAMING-PROPOSAL.md',
    'perf02/MEASUREMENT-TEMPLATE.json', 'perf02/LOCAL-RESULTS.json',
    'perf02/src/fr/jarvis/perf/LatencyTrace.kt', 'perf02/src/fr/jarvis/perf/StreamingCandidate.kt',
    'perf02/src/fr/jarvis/perf/TraceExport.kt', 'perf02/app/fr/jarvis/llm/engine/MeasuredBackend.kt',
    'perf02/tests/fr/jarvis/perf/LatencyTraceTest.kt', 'perf02/tests/fr/jarvis/perf/StreamingCandidateTest.kt',
    'perf02/tests/fr/jarvis/perf/TraceExportTest.kt', 'perf02/tests/fr/jarvis/perf/ObserverOverheadTest.kt',
    'perf02/app-tests/fr/jarvis/llm/engine/MeasuredBackendTest.kt',
}


def git(*args, root=ROOT):
    return subprocess.check_output(['git', *args], cwd=root)


def route(event, base, head, ref, same_repo):
    if event == 'pull_request' and base == MISSION and head == BRANCH and same_repo:
        return
    if event == 'push' and not base and not head and ref == BRANCH:
        return
    raise ValueError('UNREVIEWED_PERF02_ROUTE')


def verify_files(root, frozen, actual, modes, required=ADDITIONS):
    if set(actual) - set(frozen) != required or set(frozen) - set(actual):
        raise ValueError('EXACT_ADDITIVE_SCOPE_REQUIRED')
    for name in actual:
        p = root/name
        if p.is_symlink() or any(x.is_symlink() for x in p.parents if x != root) or not p.is_file():
            raise ValueError('SYMLINK_OR_NONREGULAR_FILE')
        mode = frozen[name][0] if name in frozen else '100644'
        if modes.get(name) != mode:
            raise ValueError('MODE_OR_INDEX_CHANGED')
        if name in frozen:
            if p.read_bytes() != frozen[name][1]:
                raise ValueError('FROZEN_BASELINE_CHANGED:' + name)


def historical():
    # Old guards keep their original scope and SHA. Never edit a receipt to make HEAD pass.
    with tempfile.TemporaryDirectory(prefix='jarvis-perf02-historical-') as d:
        subprocess.run(['git', 'clone', '--quiet', '--shared', '--no-checkout', str(ROOT), d], check=True)
        subprocess.run(['git', 'checkout', '--quiet', '--detach', M50], cwd=d, check=True)
        env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1', M50_EVENT='push', M50_BASE='', M50_HEAD='',
                   M50_REF='codex/m50-s1-llm-ci-20261029', M50_SAME_REPO='true')
        subprocess.run(['python3', 'tools/check-m50-s1.py'], cwd=d, env=env, check=True)


def main():
    p = argparse.ArgumentParser(); p.add_argument('--ci', action='store_true'); p.add_argument('--historical', action='store_true')
    args = p.parse_args()
    if args.ci:
        route(os.environ.get('PERF02_EVENT', ''), os.environ.get('PERF02_BASE', ''), os.environ.get('PERF02_HEAD_REF', ''),
              os.environ.get('PERF02_REF', ''), os.environ.get('PERF02_SAME_REPO') == 'true')
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], cwd=ROOT, check=True)
    if git('diff', '--name-status', M50, BASE).decode().splitlines() != ['A\tmissions/LLM-PERF-TTFT-3S.md']:
        raise ValueError('MISSION_BASE_DRIFT')
    frozen = {}
    for line in git('ls-tree', '-r', BASE).decode().splitlines():
        meta, name = line.split('\t', 1); mode, kind, _ = meta.split()
        if kind != 'blob' or mode not in ('100644', '100755'):
            raise ValueError('BASELINE_MODE')
        frozen[name] = (mode, git('cat-file', '--filters', BASE+':'+name))
    actual = git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines()
    modes = {}
    for line in git('ls-files', '--stage').decode().splitlines():
        meta, name = line.split('\t', 1); mode, _, stage = meta.split()
        if stage != '0': raise ValueError('UNMERGED_INDEX')
        modes[name] = mode
    verify_files(ROOT, frozen, actual, modes)
    # CI must match the exact checked-out commit, including the newly added instruments.
    subprocess.run(['git', 'diff', '--exit-code', 'HEAD'] if args.ci else ['git', 'diff', '--exit-code'], cwd=ROOT, check=True)
    if args.historical: historical()
    inputs = {name: hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in sorted(ADDITIONS)
              if name.endswith(('.kt', '.gradle', '.py', '.yml'))}
    output = ROOT/'build/perf02/preservation.json'; output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'schema': 'jarvis.perf02.preservation/1', 'base_commit': BASE,
        'source_commit': git('rev-parse', 'HEAD').decode().strip(), 'tree': git('rev-parse', 'HEAD^{tree}').decode().strip(),
        'frozen_paths': len(frozen), 'reviewed_ci_route_changes': 0, 'production_runtime_changed': False,
        'jni_abi_changed': False, 'core_interface_contracts_changed': False, 'model_or_safety_changed': False,
        'new_sources_are_test_only': True, 'input_sha256': inputs, 'physical_android': 'NOT_RUN',
        'native_streaming': 'NOT_APPLIED', 'sla_lt_3s': 'NON_QUALIFIE'}, indent=2)+'\n')
    print('PASS PERF-02 exact additive scope; runtime/JNI/contracts/safety frozen; first native token UNSUPPORTED.')


if __name__ == '__main__': main()
