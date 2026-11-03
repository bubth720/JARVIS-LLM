#!/usr/bin/env python3
"""M50-S1 additive CI only; no runtime, manifest, ABI or receipt changes."""
from pathlib import Path
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = '6ae8a4f5c2490f6c9814e08957909a8e3a807180'
MISSION = '615a2f165d6479c0bbb7a7daf2281677884b494f'
HISTORICAL_003E = '030152a5f3d888017ad7a7fe8026f2e2c48e8fd5'
BRANCH = 'codex/m50-s1-llm-ci-20261029'
BASE_BRANCH = 'mission/m50-integrated-beta-20261029'
ADDITIONS = {
    'tools/check-m50-s1.py',
    'tests/m50/test_preservation.py',
    '.github/workflows/m50-s1.yml',
    'docs/m50/M50-S1-A2.md',
}


def git(*args, root=ROOT):
    return subprocess.check_output(['git', *args], cwd=root)


def verify_route(event, base, head, ref, same_repo=False):
    if event == 'pull_request' and base == BASE_BRANCH and head == BRANCH and same_repo:
        return
    if event == 'push' and not base and not head and ref == BRANCH:
        return
    raise ValueError('Unreviewed M50 event/base/head/ref route')


def verify_files(root, frozen, actual, modes):
    """Exact additions; reject mutation, deletion, mode changes and symlinks."""
    if set(actual) - set(frozen) - ADDITIONS:
        raise ValueError('Undeclared addition')
    if set(frozen) - set(actual):
        raise ValueError('Baseline path removed')
    if ADDITIONS - set(actual):
        raise ValueError('Required M50 file removed')
    for name in set(actual) | set(frozen):
        path = root / name
        if path.is_symlink() or any(p.is_symlink() for p in path.parents if p != root):
            raise ValueError('Symlink forbidden: ' + name)
        if not path.is_file():
            raise ValueError('Missing/non-regular file: ' + name)
        expected_mode = frozen[name][0] if name in frozen else '100644'
        if modes.get(name) != expected_mode:
            raise ValueError('Git mode changed: ' + name)
        if name in frozen and path.read_bytes() != frozen[name][1]:
            raise ValueError('Frozen file changed: ' + name)


def clone_guard(sha, command):
    with tempfile.TemporaryDirectory(prefix='llm-m50-history-') as temp:
        subprocess.run(['git', 'clone', '--quiet', '--shared', '--no-checkout', str(ROOT), temp], check=True)
        subprocess.run(['git', 'checkout', '--quiet', '--detach', sha], cwd=temp, check=True)
        subprocess.run(['python3', command], cwd=temp, check=True)


def main():
    verify_route(os.environ.get('M50_EVENT', ''), os.environ.get('M50_BASE', ''),
                 os.environ.get('M50_HEAD', ''), os.environ.get('M50_REF', ''),
                 os.environ.get('M50_SAME_REPO', '') == 'true')
    for sha in (SOURCE, MISSION, HISTORICAL_003E):
        subprocess.run(['git', 'merge-base', '--is-ancestor', sha, 'HEAD'], cwd=ROOT, check=True)
    frozen = {}
    for entry in git('ls-tree', '-r', MISSION).decode().splitlines():
        meta, name = entry.split('\t', 1)
        mode, kind, _ = meta.split()
        if kind != 'blob' or mode not in ('100644', '100755'):
            raise ValueError('Unexpected baseline tree mode: ' + name)
        frozen[name] = (mode, git('cat-file', '--filters', MISSION + ':' + name))
    # M50 mission may only add its document to source #6; baseline SHA is immutable.
    delta = git('diff', '--name-status', '--no-renames', SOURCE, MISSION).decode().splitlines()
    if delta != ['A\tmissions/M50-INTEGRATION-2026-10-29.md']:
        raise ValueError('Mission/source delta changed')
    actual = git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines()
    modes = {}
    for entry in git('ls-files', '--stage').decode().splitlines():
        meta, name = entry.split('\t', 1)
        mode, _, stage = meta.split()
        if stage != '0':
            raise ValueError('Unmerged index')
        modes[name] = mode
    verify_files(ROOT, frozen, actual, modes)
    clone_guard(HISTORICAL_003E, 'tools/check-preservation-003e.py')
    clone_guard(SOURCE, 'tools/check-prefusion.py')
    report = {
        'schema': 'jarvis.llm.m50-s1-preservation/1',
        'source_sha': SOURCE, 'mission_sha': MISSION,
        'historical_003e_sha': HISTORICAL_003E,
        'head_sha': git('rev-parse', 'HEAD').decode().strip(),
        'tree_sha': git('rev-parse', 'HEAD^{tree}').decode().strip(),
        'frozen_files': len(frozen), 'runtime_manifest_abi_changed': False,
        'native_generation': 'NOT_RUN', 'android_instrumentation': 'NOT_RUN', 'physical': 'NOT_RUN',
    }
    out = ROOT / 'build/m50-s1/preservation.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
