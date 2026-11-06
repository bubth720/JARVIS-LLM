#!/usr/bin/env python3
"""PERF-03B: only the exact GATE-A patch and additive laboratory qualification."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = 'ca9d8f060856e134d793d0c23764fae45ce2cc53'
TREE = '75798737dfc4a618f213ae299b0e467e7547032a'
BRANCH = 'codex/llm-perf-03b-native-stream'
TARGET = 'codex/llm-perf-03a-native-review'
PATCH_SHA = '747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460'
WORKFLOW = '.github/workflows/perf03b.yml'
TARGETS = {
    'app/src/main/cpp/bridge.cpp', 'app/src/main/cpp/stream_support.h',
    'app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt',
    'app/src/main/java/fr/jarvis/llm/engine/OwnedStreamContract.kt',
}


def git(*args, root=ROOT):
    return subprocess.check_output(['git', *args], cwd=root)


def digest(data): return hashlib.sha256(data).hexdigest()


def route(event, base, head, ref, same_repo):
    if event == 'push' and base == head == '' and ref == BRANCH:
        return
    if event == 'pull_request' and base == TARGET and head == BRANCH and same_repo:
        return
    raise ValueError('UNREVIEWED_PERF03B_ROUTE')


def addition(name):
    return name == WORKFLOW or (name.startswith('perf03b/') and '..' not in Path(name).parts)


def verify_files(root, expected, actual, modes):
    actual = set(actual)
    if set(expected) - actual or any(not addition(p) for p in actual - set(expected)):
        raise ValueError('ONLY_APPROVED_PATCH_AND_ADDITIONS')
    for name in actual:
        p = root/name
        if p.is_symlink() or any(x.is_symlink() for x in p.parents if x != root) or not p.is_file():
            raise ValueError('SYMLINK_OR_NONREGULAR_FILE:' + name)
        mode = expected[name][0] if name in expected else '100644'
        if modes.get(name) != mode:
            raise ValueError('MODE_OR_INDEX_CHANGED:' + name)
        if name in expected and p.read_bytes() != expected[name][1]:
            raise ValueError('UNAPPROVED_CONTENT:' + name)


def approved_images(root, manifest, base=BASE):
    if manifest.get('patch_sha256') != PATCH_SHA or len(manifest.get('files', [])) != 4:
        raise ValueError('UNAPPROVED_PATCH_MANIFEST')
    if {f['path'] for f in manifest['files']} != TARGETS:
        raise ValueError('UNAPPROVED_PATCH_TARGETS')
    expected = {}
    for f in manifest['files']:
        path = f['path']
        before = subprocess.run(['git', 'cat-file', 'blob', base+':'+path], cwd=root, capture_output=True)
        if f['sha256'] is None:
            if before.returncode == 0:
                raise ValueError('NEW_TARGET_EXISTED_BEFORE_APPROVAL:' + path)
        else:
            if before.returncode or digest(before.stdout) != f['sha256']:
                raise ValueError('APPROVED_PREIMAGE_MISMATCH:' + path)
            observed_blob = git('rev-parse', base+':'+path, root=root).decode().strip()
            if observed_blob != f['git_blob']:
                raise ValueError('APPROVED_PREIMAGE_BLOB_MISMATCH')
        candidate = f['candidate_path']
        if not candidate.startswith('perf03/candidate/') or '..' in Path(candidate).parts:
            raise ValueError('UNREVIEWED_POSTIMAGE_LOCATION')
        after = git('cat-file', 'blob', base+':'+candidate, root=root)
        if digest(after) != f['after_sha256']:
            raise ValueError('APPROVED_POSTIMAGE_DIGEST_MISMATCH')
        if git('rev-parse', base+':'+candidate, root=root).decode().strip() != f['after_git_blob']:
            raise ValueError('APPROVED_POSTIMAGE_BLOB_MISMATCH')
        if f['mode'] != '100644':
            raise ValueError('UNAPPROVED_PATCH_MODE')
        expected[path] = ('100644', after)
    return expected


def verify_application(proof, manifest):
    if (proof.get('schema') != 'jarvis.perf03b.approved-application/1'
            or proof.get('authorized_base_commit') != BASE or proof.get('authorized_base_tree') != TREE
            or proof.get('patch_sha256') != PATCH_SHA or proof.get('branch') != BRANCH
            or proof.get('preapply_worktree_clean') is not True
            or proof.get('dry_run_exit') != 0 or proof.get('apply_exit') != 0
            or proof.get('postimages_verified') is not True
            or any(proof.get(k) is not False for k in ('historical_f3b_applied', 'production_activation', 'model_downloaded'))):
        raise ValueError('UNQUALIFIED_APPLICATION_PROOF')
    entries = proof.get('preimages', [])
    if len(entries) != 4 or {f['path'] for f in entries} != TARGETS:
        raise ValueError('APPLICATION_PREIMAGE_SET')
    by_path = {f['path']: f for f in manifest['files']}
    for item in entries:
        expected = by_path[item['path']]
        if (item.get('mode') != expected['mode'] or item.get('preimage_sha256') != expected['sha256']
                or item.get('preimage_git_blob') != expected['git_blob'] or item.get('preimage_verified') is not True):
            raise ValueError('APPLICATION_PREIMAGE_RECEIPT_MISMATCH')


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--ci', action='store_true')
    args = parser.parse_args()
    if git('rev-parse', BASE+'^{tree}').decode().strip() != TREE:
        raise ValueError('GATE_A_BASE_TREE_CHANGED')
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], cwd=ROOT, check=True)
    if args.ci:
        route(os.environ.get('PERF03B_EVENT', ''), os.environ.get('PERF03B_BASE', ''), os.environ.get('PERF03B_HEAD_REF', ''),
              os.environ.get('PERF03B_REF', ''), os.environ.get('PERF03B_SAME_REPO') == 'true')
        if git('rev-parse', 'HEAD').decode().strip() != os.environ.get('PERF03B_CODE_SHA'):
            raise ValueError('CI_CHECKOUT_NOT_EXACT_HEAD')
    if digest((ROOT/'perf03/NATIVE-STREAMING.patch').read_bytes()) != PATCH_SHA:
        raise ValueError('AUTHORIZED_PATCH_BYTES_CHANGED')
    manifest = json.loads(git('cat-file', 'blob', BASE+':perf03/PREIMAGES.json'))
    expected = {}
    for line in git('ls-tree', '-r', BASE).decode().splitlines():
        meta, name = line.split('\t', 1); mode, kind, _ = meta.split()
        if kind != 'blob' or mode not in ('100644', '100755'):
            raise ValueError('UNSUPPORTED_BASELINE_MODE')
        expected[name] = (mode, git('cat-file', '--filters', BASE+':'+name))
    frozen = len(expected)
    expected.update(approved_images(ROOT, manifest))
    actual = git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines()
    modes = {}
    for line in git('ls-files', '--stage').decode().splitlines():
        meta, name = line.split('\t', 1); mode, _, stage = meta.split()
        if stage != '0': raise ValueError('UNMERGED_INDEX')
        modes[name] = mode
    verify_files(ROOT, expected, actual, modes)
    verify_application(json.loads((ROOT/'perf03b/APPLICATION.json').read_text()), manifest)
    # Reverse --check confirms exact postimages. Never apply another native diff here.
    subprocess.run(['git', 'apply', '--reverse', '--check', 'perf03/NATIVE-STREAMING.patch'], cwd=ROOT, check=True)
    if args.ci:
        subprocess.run(['git', 'diff', '--exit-code', 'HEAD'], cwd=ROOT, check=True)
        if git('ls-files', '--others', '--exclude-standard').strip(): raise ValueError('UNTRACKED_CI_SOURCES')
    output = ROOT/'build/perf03b'; output.mkdir(parents=True, exist_ok=True)
    report = {'schema': 'jarvis.perf03b.preservation/1', 'module': 'LLM', 'mission': 'PERF-03B',
        'repository': 'bubth720/JARVIS-LLM', 'branch': BRANCH, 'authorized_base_commit': BASE, 'authorized_base_tree': TREE,
        'source_commit': git('rev-parse', 'HEAD').decode().strip(), 'source_tree': git('rev-parse', 'HEAD^{tree}').decode().strip(),
        'worktree_dirty': bool(git('status', '--porcelain')), 'frozen_base_paths': frozen, 'authorized_patch_sha256': PATCH_SHA,
        'approved_runtime_targets': sorted(TARGETS), 'all_other_base_paths_unchanged': True,
        'added_lab_paths': sorted(set(actual)-set(expected)), 'postimages_exact': True, 'reverse_check_exit': 0,
        'safety_changed': False, 'historical_guards_changed': False, 'core_interface_changed': False,
        'historical_f3b_applied': False, 'model_downloaded': False, 'physical_android': 'NOT_RUN',
        'real_generation': 'NOT_RUN', 'ttft_p95_ms': None, 'sla_lt_3s': 'NOT_QUALIFIED', 'gate_b': 'PENDING_USER_VALIDATION'}
    (output/'preservation.json').write_text(json.dumps(report, indent=2)+'\n')
    print('PASS PERF-03B: exact approved patch only; all other %d base paths and protections preserved.' % (frozen-2))


if __name__ == '__main__': main()
