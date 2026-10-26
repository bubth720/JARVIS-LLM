#!/usr/bin/env python3
"""Freeze FUSION-01, including client/1 and historical evidence; test-only additions."""
from pathlib import Path
import hashlib
import json
import subprocess

BASE='e0b781442b21034a251f6af450396faabc5f667d'
BRANCH='feat/fusion-02-llm-prequalification'
ROOT=Path(__file__).resolve().parent.parent

def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)

paths=git('ls-tree','-r','--name-only',BASE).decode().splitlines()
for p in paths:
    before=git('show',BASE+':'+p)
    if p=='.github/workflows/android.yml':
        expected=before.decode().replace("branches-ignore: ['feat/fusion-01-llm-integration']",
            "branches-ignore: ['feat/fusion-01-llm-integration', '"+BRANCH+"']")
        expected=expected.replace("github.ref_name != 'feat/fusion-01-llm-integration'",
            "github.ref_name != 'feat/fusion-01-llm-integration' && github.head_ref != '"+BRANCH+"' && github.ref_name != '"+BRANCH+"'")
        assert (ROOT/p).read_text()==expected,'Only the reviewed CI route may differ'
    else:assert (ROOT/p).read_bytes()==before,p
allowed_files={'.github/workflows/fusion-02-llm.yml','tools/check-fusion-02.py',
    'tools/fusion02_evidence.py','tools/review-fusion-02-native.py','tools/tests/fusion02/test_evidence.py'}
allowed_prefixes=('docs/fusion-02/','domain/src/test/kotlin/fr/jarvis/domain/client/qualification/')
for p in set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())-set(paths):
    assert p in allowed_files or p.startswith(allowed_prefixes),('Out of test/review scope',p)
subprocess.run(['python3','tools/check-fusion-01.py'],cwd=ROOT,check=True)
print(json.dumps({'base':BASE,'frozen_files':len(paths)-1,'reviewed_ci_route_changes':1,
    'production_source_changed':False,'client_v1_changed':False,'jni_changed':False,
    'contract_sha256':hashlib.sha256((ROOT/'docs/fusion-01/CLIENT-CONTRACT.json').read_bytes()).hexdigest()}))
