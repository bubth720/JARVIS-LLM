#!/usr/bin/env python3
"""R02 successor to F3A: preserve original F3A and verify inherited protected tree.

This does not declare the old F3A byte-identical assertion true on a later
revision. The original test remains executable without successor flags.
"""
import hashlib, json, subprocess
from pathlib import Path

F3A='1ce24405b04a50f97cf30078b9716c7d00c593fc'
INHERITED='08fbda527d187f324d94e0adafbca76fa955a343'
def git(*args):
    return subprocess.check_output(['git',*args],text=True).strip()
def old(commit,path):
    return subprocess.check_output(['git','show',commit+':'+path])
for commit in (F3A,INHERITED):
    subprocess.run(['git','merge-base','--is-ancestor',commit,'HEAD'],check=True)
# Freeze every file already protected by the original F3A mission at the
# explicitly reviewed inherited snapshot, without path exceptions.
paths=git('ls-tree','-r','--name-only',F3A,'--','app','domain','fixtures').splitlines()
evidence={}
for p in paths:
    baseline=old(INHERITED,p)
    current=Path(p).read_bytes()
    assert current==baseline,'R02 protected drift: '+p
    evidence[p]=hashlib.sha256(current).hexdigest()
# Preserve the F3A trust/ABI invariants independently of its historic
# snapshot assertion. They must never be skipped by successor mode.
pin=Path('fixtures/p2-contract/CONTRACT.sha256').read_text().strip()
assert pin=='3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef'
authority=Path('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/ProcessRecoveryAuthority.kt').read_text()
assert 'fun requireNativeAdmission(): Unit = throw Refusal("RECOVERY_REQUIRED")' in authority
# Ensure the original F3A verifier's body remains byte-for-byte intact
# before the R02 opt-in dispatcher; a future edit cannot silently weaken it.
original=old(INHERITED,'tools/check-preservation-f3a.py').decode('utf-8')
current=Path('tools/check-preservation-f3a.py').read_text()
dispatch='''if '--successor-r02' in sys.argv:
    subprocess.run([sys.executable,'tools/check-preservation-r02.py'],check=True)
    sys.exit(0)
'''
assert current==original.replace("BASE = '1ce24405b04a50f97cf30078b9716c7d00c593fc'\n",
 "BASE = '1ce24405b04a50f97cf30078b9716c7d00c593fc'\n"+dispatch,1), 'F3A original verifier changed'
# Prevent a successor from silently expanding the protected app/domain/fixture
# scope. Additive lab-only candidates are separately security-qualified.
for p in git('diff','--name-only',INHERITED,'HEAD','--','app','domain','fixtures').splitlines():
    raise AssertionError('R02 protected source changed: '+p)
report={'mission':'R02-F3A-SUCCESSOR','historicalF3aBase':F3A,
 'inheritedBase':INHERITED,'protectedFiles':len(paths),'sha256':evidence,
 'f3aOriginalVerifierIntact':True,'closedNativeAdmission':True,
 'contractPinUnchanged':True,'historicalF3aExactAtHead':False,
 'successorQualified':True,'realInferenceQualified':False}
Path('build').mkdir(exist_ok=True)
Path('build/r02-f3a-successor.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS R02 F3A successor:',len(paths),'inherited protected files; F3A original verifier intact; native gate closed')
