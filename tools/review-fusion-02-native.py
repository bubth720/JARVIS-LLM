#!/usr/bin/env python3
"""Offline review only: checks preimages and applicability, NEVER applies the patch."""
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
manifest = json.loads((root/'docs/mission-003f-f3b/PATCH.json').read_text())
patch = root/'docs/mission-003f-f3b/SENSITIVE.patch'
expected = 'be4bcce5ec7066851a09e2e372edca562a45dad3a3a88e35b6a7d5792c07b374'
assert hashlib.sha256(patch.read_bytes()).hexdigest() == manifest['patchSha256'] == expected
files = []
for f in manifest['files']:
    p = root/f['path']
    actual = hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None
    assert actual == f['beforeSha256'], f['path']
    files.append(dict(f, observedSha256=actual, production_file_present=p.exists()))
result = subprocess.run(['git','apply','--check',str(patch)], cwd=root,capture_output=True,text=True)
assert result.returncode == 0, result.stderr
report = {'format': 'jarvis.llm.fusion-02.native-review/1', 'patch_sha256': expected,
          'dry_check_command': ['git','apply','--check','docs/mission-003f-f3b/SENSITIVE.patch'],
          'dry_check_exit': result.returncode, 'preimages_match': True, 'files': files,
          'patch_applied': False, 'native_generation_executed': False,
          'review_status': 'SPECIFIC_APPROVAL_REQUIRED', 'token_count': 'UNSUPPORTED', 'admission_v2': 'CLOSED'}
out=root/'build/fusion-02/native-review.json';out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
