#!/usr/bin/env python3
"""PERF-03A: freeze the exact PERF-02 tree; review a patch, never apply it."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = '09e278b54b8be58a3d665d2d91de10f8869e40bb'
TREE = '975fc71395ac7758678cb5af1b76858ee614c9b2'
BRANCH = 'codex/llm-perf-03a-native-review'
TARGET = 'codex/llm-perf-ttft-3s'
WORKFLOW = '.github/workflows/perf03.yml'
PATCH_TARGETS = {
    'app/src/main/cpp/bridge.cpp', 'app/src/main/cpp/stream_support.h',
    'app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt',
    'app/src/main/java/fr/jarvis/llm/engine/OwnedStreamContract.kt',
}


def git(*args, root=ROOT):
    return subprocess.check_output(['git', *args], cwd=root)


def route(event, base, head, ref, same_repo):
    if event == 'push' and base == head == '' and ref == BRANCH:
        return
    if event == 'pull_request' and base == TARGET and head == BRANCH and same_repo:
        return
    raise ValueError('UNREVIEWED_PERF03A_ROUTE')


def addition(name):
    return name == WORKFLOW or (name.startswith('perf03/') and '..' not in Path(name).parts)


def verify_files(root, frozen, actual, modes):
    actual = set(actual)
    if set(frozen) - actual or any(not addition(p) for p in actual - set(frozen)):
        raise ValueError('ADDITIVE_SCOPE_REQUIRED')
    for name in actual:
        p = root/name
        if p.is_symlink() or any(x.is_symlink() for x in p.parents if x != root) or not p.is_file():
            raise ValueError('SYMLINK_OR_NONREGULAR_FILE:' + name)
        expected_mode = frozen[name][0] if name in frozen else '100644'
        if modes.get(name) != expected_mode:
            raise ValueError('MODE_OR_INDEX_CHANGED:' + name)
        if name in frozen and p.read_bytes() != frozen[name][1]:
            raise ValueError('FROZEN_PERF02_CHANGED:' + name)


def patch_entries(data):
    if not data or b'GIT binary patch' in data or b'rename from ' in data or b'deleted file mode ' in data:
        raise ValueError('PATCH_FORMAT_OR_DELETION')
    paths = []
    for line in data.decode('utf-8').splitlines():
        if line.startswith('diff --git '):
            parts = line.split(' ')
            if len(parts) != 4 or not parts[2].startswith('a/') or not parts[3].startswith('b/') or parts[2][2:] != parts[3][2:]:
                raise ValueError('PATCH_PATH_FORMAT')
            name = parts[2][2:]
            if name not in PATCH_TARGETS or name in paths:
                raise ValueError('UNREVIEWED_OR_DUPLICATE_PATCH_PATH')
            paths.append(name)
        if line.startswith(('old mode ', 'new mode ')) or (line.startswith('new file mode ') and line != 'new file mode 100644'):
            raise ValueError('PATCH_MODE_CHANGE')
    if set(paths) != PATCH_TARGETS:
        raise ValueError('INCOMPLETE_PATCH_TARGET_SET')
    return paths


def review_patch(root):
    patch = root/'perf03/NATIVE-STREAMING.patch'
    manifest = json.loads((root/'perf03/PREIMAGES.json').read_text())
    if (manifest.get('schema') != 'jarvis.perf03.native-review/1' or manifest.get('base_commit') != BASE
            or manifest.get('base_tree') != TREE or manifest.get('applied') is not False):
        raise ValueError('UNREVIEWED_PATCH_BASE_OR_STATE')
    data = patch.read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    if manifest['patch_sha256'] != sha:
        raise ValueError('PATCH_DIGEST_MISMATCH')
    # Rendering a diff is not application: bind its full contents to the reviewed postimages.
    spec = importlib.util.spec_from_file_location('perf03_patch_generator', root/'perf03/make_patch.py')
    generator = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(generator)
    expected_patch, expected_manifest = generator.build()
    if data != expected_patch or manifest != expected_manifest:
        raise ValueError('PATCH_DOES_NOT_MATCH_REVIEWED_CANDIDATES')
    paths = patch_entries(data)
    entries = manifest['files']
    if len(entries) != len(paths) or {f['path'] for f in entries} != set(paths):
        raise ValueError('MANIFEST_PATH_MISMATCH')
    for f in entries:
        p = root/f['path']
        if p.is_symlink() or f['mode'] != '100644':
            raise ValueError('PREIMAGE_MODE')
        observed = hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None
        blob = git('hash-object', f['path'], root=root).decode().strip() if p.exists() else None
        if observed != f['sha256'] or blob != f['git_blob']:
            raise ValueError('PREIMAGE_CHANGED:' + f['path'])
        if 'candidate_path' in f:
            c = root/f['candidate_path']
            if (not str(f['candidate_path']).startswith('perf03/candidate/') or '..' in Path(f['candidate_path']).parts
                    or c.is_symlink() or any(x.is_symlink() for x in c.parents if x != root)):
                raise ValueError('UNREVIEWED_CANDIDATE_PATH')
            if hashlib.sha256(c.read_bytes()).hexdigest() != f['after_sha256']:
                raise ValueError('CANDIDATE_POSTIMAGE_CHANGED')
    # --check is intentionally the ONLY apply invocation in this review lane.
    result = subprocess.run(['git', 'apply', '--check', str(patch)], cwd=root, capture_output=True, text=True)
    if result.returncode:
        raise ValueError('PATCH_DRY_RUN_FAILED:' + result.stderr)
    return {'patch_sha256': sha, 'preimages_match': True, 'dry_run_exit': 0,
            'patch_applied': False, 'files': entries, 'approval': 'REQUIRED_BEFORE_APPLICATION'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ci', action='store_true')
    parser.add_argument('--export-tree', action='store_true', help='Local connector export only: no claim HEAD equals reference.')
    parser.add_argument('--scope-only', action='store_true', help='Prepare preservation before the review patch exists.')
    args = parser.parse_args()
    if args.ci and (args.export_tree or args.scope_only):
        raise ValueError('CI_REQUIRES_COMMIT_AND_PATCH')
    if args.ci:
        route(os.environ.get('PERF03_EVENT', ''), os.environ.get('PERF03_BASE', ''), os.environ.get('PERF03_HEAD_REF', ''),
              os.environ.get('PERF03_REF', ''), os.environ.get('PERF03_SAME_REPO') == 'true')
        if git('rev-parse', 'HEAD').decode().strip() != os.environ.get('PERF03_CODE_SHA'):
            raise ValueError('CI_CHECKOUT_NOT_EXACT_HEAD')
    if not args.export_tree:
        if git('rev-parse', BASE+'^{tree}').decode().strip() != TREE:
            raise ValueError('PERF02_REFERENCE_TREE_MISMATCH')
        subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], cwd=ROOT, check=True)
    frozen = {}
    for line in git('ls-tree', '-r', TREE).decode().splitlines():
        meta, name = line.split('\t', 1)
        mode, kind, _ = meta.split()
        if kind != 'blob' or mode not in ('100644', '100755'):
            raise ValueError('UNSUPPORTED_BASELINE_MODE')
        frozen[name] = (mode, git('cat-file', '--filters', TREE+':'+name))
    actual = git('ls-files', '--cached', '--others', '--exclude-standard').decode().splitlines()
    modes = {}
    for line in git('ls-files', '--stage').decode().splitlines():
        meta, name = line.split('\t', 1)
        mode, _, stage = meta.split()
        if stage != '0': raise ValueError('UNMERGED_INDEX')
        modes[name] = mode
    verify_files(ROOT, frozen, actual, modes)
    if args.ci:
        subprocess.run(['git', 'diff', '--exit-code', 'HEAD'], cwd=ROOT, check=True)
        if git('ls-files', '--others', '--exclude-standard').strip():
            raise ValueError('UNTRACKED_CI_SOURCES')
    review = review_patch(ROOT) if not args.scope_only else {'status': 'NOT_RUN', 'patch_applied': False}
    report = {'schema': 'jarvis.perf03a.preservation/1', 'reference_commit': BASE, 'reference_tree': TREE,
        'source_commit': git('rev-parse', 'HEAD').decode().strip(), 'source_tree': git('rev-parse', 'HEAD^{tree}').decode().strip(),
        'local_tree_export': args.export_tree, 'worktree_dirty': bool(git('status', '--porcelain')),
        'frozen_paths': len(frozen), 'new_files': sorted(set(actual)-set(frozen)), 'native_review': review,
        'runtime_changed': False, 'safety_changed': False, 'historical_guards_changed': False,
        'core_interface_changed': False, 'physical_android': 'NOT_RUN', 'real_generation': 'NOT_RUN',
        'ttft_p95_ms': None, 'ttft_status': 'NOT_RUN', 'sla_lt_3s': 'NOT_QUALIFIED'}
    output = ROOT/'build/perf03/preservation.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2)+'\n')
    print('PASS PERF-03A scope: %d frozen paths; runtime/JNI/safety unchanged; patch NOT_APPLIED.' % len(frozen))


if __name__ == '__main__': main()
