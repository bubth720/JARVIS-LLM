#!/usr/bin/env python3
"""Render review patch from immutable preimages and candidate files. NEVER applies it."""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = '09e278b54b8be58a3d665d2d91de10f8869e40bb'
TREE = '975fc71395ac7758678cb5af1b76858ee614c9b2'
PAIRS = (
 ('app/src/main/cpp/bridge.cpp', 'perf03/candidate/native/bridge.cpp'),
 ('app/src/main/cpp/stream_support.h', 'perf03/candidate/native/stream_support.h'),
 ('app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt', 'perf03/candidate/engine/LlamaBackend.kt'),
 ('app/src/main/java/fr/jarvis/llm/engine/OwnedStreamContract.kt', 'perf03/candidate/engine/OwnedStreamContract.kt'),
)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def blob(data):
    return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()

def build():
    if subprocess.check_output(['git', 'cat-file', '-t', TREE], cwd=ROOT).strip() != b'tree':
        raise ValueError('EXACT_REFERENCE_TREE_REQUIRED')
    chunks, files = [], []
    for target, candidate in PAIRS:
        preimage = subprocess.run(['git', 'cat-file', 'blob', TREE+':'+target], cwd=ROOT,
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        before = preimage.stdout if preimage.returncode == 0 else None
        actual = (ROOT/target).read_bytes() if (ROOT/target).exists() else None
        if actual != before:
            raise ValueError('RUNTIME_PREIMAGE_CHANGED_OR_PATCH_APPLIED: '+target)
        after = (ROOT/candidate).read_bytes()
        for data in (before, after):
            if data is not None and (not data.endswith(b'\n') or b'\r' in data):
                raise ValueError('Expected exact LF pre/postimage: '+target)
        old = before.decode().splitlines(keepends=True) if before is not None else []
        new = after.decode().splitlines(keepends=True)
        header = 'diff --git a/'+target+' b/'+target+'\n'
        if before is None:
            header += 'new file mode 100644\n'
        header += 'index '+(blob(before)[:12] if before is not None else '000000000000')+'..'+blob(after)[:12]+' 100644\n'
        diff = ''.join(difflib.unified_diff(old, new, fromfile='a/'+target if before is not None else '/dev/null', tofile='b/'+target, n=3))
        chunks.append(header+diff)
        files.append({'path': target, 'sha256': sha(before) if before is not None else None,
            'git_blob': blob(before) if before is not None else None, 'mode': '100644',
            'candidate_path': candidate, 'after_sha256': sha(after), 'after_git_blob': blob(after)})
    patch = ''.join(chunks).encode()
    return patch, {'schema': 'jarvis.perf03.native-review/1', 'base_commit': BASE,
        'base_tree': TREE, 'applied': False, 'files': files, 'patch_sha256': sha(patch)}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    patch, manifest = build()
    manifest_bytes = (json.dumps(manifest, indent=2)+'\n').encode()
    patch_path, manifest_path = ROOT/'perf03/NATIVE-STREAMING.patch', ROOT/'perf03/PREIMAGES.json'
    if args.check:
        if patch_path.read_bytes() != patch or manifest_path.read_bytes() != manifest_bytes:
            raise ValueError('PATCH_OR_MANIFEST_NOT_REPRODUCIBLE')
    else:
        patch_path.write_bytes(patch); manifest_path.write_bytes(manifest_bytes)
    subprocess.run(['git','apply','--check','perf03/NATIVE-STREAMING.patch'], cwd=ROOT, check=True)
    print('PASS rendered exact patch; git apply --check only; SHA256='+sha(patch))
