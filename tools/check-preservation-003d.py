#!/usr/bin/env python3
"""003D permits only NEW isolated contract files; every existing runtime file stays identical to 003C."""
import hashlib
import json
from pathlib import Path
import subprocess
BASE = '43503f59f530f2bb48955bce9d7d71e2470fb668'
PATHS = ['app/src/main','domain/src/main','app/build.gradle.kts','domain/build.gradle.kts',
         'build.gradle.kts','settings.gradle.kts','gradle.properties','gradle','gradlew',
         'tools/prepare-native.sh','tools/check-apk.py','tools/test-native.sh']
PREFIX = 'domain/src/main/kotlin/fr/jarvis/domain/generation/'
def git(*args): return subprocess.check_output(['git',*args],text=True).strip()
subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],check=True)
changes = git('diff','--name-status',BASE,'--',*PATHS).splitlines()
for change in changes:
    status, path = change.split('\t',1)
    if status != 'A' or not path.startswith(PREFIX) or not path.endswith('.kt'):
        raise SystemExit('Existing runtime/security drift forbidden: '+change)
for path in git('ls-files','--others','--exclude-standard','--',*PATHS).splitlines():
    if not path.startswith(PREFIX) or not path.endswith('.kt'): raise SystemExit('Unexpected runtime addition: '+path)
# Also prove the new contract has no production call site/import outside its isolated directory.
for path in git('ls-files','--','app/src/main','domain/src/main').splitlines():
    if path.endswith(('.kt','.java','.cpp')) and not path.startswith(PREFIX):
        content=Path(path).read_text()
        if 'fr.jarvis.domain.generation' in content: raise SystemExit('P2 activation forbidden: '+path)
baseline_tests = git('ls-tree','-r','--name-only',BASE,'--','domain/src/test','app/src/test').splitlines()
for path in baseline_tests:
    original = subprocess.check_output(['git','show',BASE+':'+path])
    if not Path(path).is_file() or Path(path).read_bytes() != original:
        raise SystemExit('Baseline test changed or removed: '+path)
files = git('ls-tree','-r','--name-only',BASE,'--',*PATHS).splitlines()
report={'mission':'003D','base':BASE,'sourceCommit':git('rev-parse','HEAD'),
        'existingRuntimeSourcesUnchanged':True,'p2NativeEnabled':False,'ipcAdded':False,
        'baselineTestFilesUnchanged':True,'baselineTestFileCount':len(baseline_tests),
        'core004bParityVerified':False,'physicalQualification':False,
        'filesSha256':{p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in files}}
out=Path('build/mission003d-preservation.json');out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,indent=2)+'\n')
print('PASS: existing',len(files),'runtime/security files unchanged; P2 contracts isolated, no production activation.')
