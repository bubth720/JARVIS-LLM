#!/usr/bin/env python3
"""Fail closed on any 003B runtime/security drift; tests/docs/packaging may evolve."""
import hashlib
import json
from pathlib import Path
import subprocess

BASE = '1756e29557287550b5cfdffdd911cada2a302db6'
PATHS = ['app/src/main', 'domain/src/main', 'app/build.gradle.kts', 'domain/build.gradle.kts',
         'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle', 'gradlew',
         'tools/prepare-native.sh', 'tools/check-apk.py', 'tools/test-native.sh']
def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()
subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, 'HEAD'], check=True)
changed = git('diff', '--name-only', BASE, '--', *PATHS)
untracked = git('ls-files', '--others', '--exclude-standard', '--', *PATHS)
if changed or untracked:
    raise SystemExit('003B preservation failed: runtime or security files changed\n' + changed + '\n' + untracked)
files = git('ls-files', '--', *PATHS).splitlines()
fingerprints = {p: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in files}
report = {'mission': '003C', 'base': BASE, 'sourceCommit': git('rev-parse', 'HEAD'),
          'workingTreeDirty': bool(git('status', '--porcelain')),
          'runtimeIdenticalTo003B': True, 'filesSha256': fingerprints,
          'physicalQualification': 'EN ATTENTE UTILISATEUR', 'newBudgetApproved': False}
out = Path('build/mission003c-preservation.json')
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(report, indent=2) + '\n')
print('PASS: 003B runtime/security preserved across', len(files), 'files; no hardware qualification.')
