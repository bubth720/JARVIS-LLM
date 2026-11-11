#!/usr/bin/env python3
"""R02 successor to historical F3B review. Never applies a sensitive patch."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path('docs/mission-003f-f3b')
BASE='08fbda527d187f324d94e0adafbca76fa955a343'
def original(path): return subprocess.check_output(['git','show',BASE+':'+path])
# Original F3B verifier and its review-only patch remain immutable.
for path in ['docs/mission-003f-f3b/verify.py','docs/mission-003f-f3b/build_patch.py',
             'docs/mission-003f-f3b/SENSITIVE.patch','docs/mission-003f-f3b/PATCH.json']:
    assert Path(path).read_bytes()==original(path),'F3B review artifact drift: '+path
m=json.loads((ROOT/'PATCH.json').read_text())
assert m['applied'] is False and m['nativeAdmissionEnabled'] is False
assert hashlib.sha256((ROOT/'SENSITIVE.patch').read_bytes()).hexdigest()==m['patchSha256']
# Historical patch must be verified against the exact original base in its
# own worktree, not falsely expected to apply to a newer protected runtime.
for entry in m['files']:
    p=entry['path']
    before=entry['beforeSha256']
    if before is not None:
        assert hashlib.sha256(original(m['base'],p)).hexdigest()==before,'F3B historical preimage drift: '+p
# Check every historically protected file at the inherited R02 snapshot.
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app','domain','fixtures'],text=True).splitlines()
for p in paths:
    assert Path(p).read_bytes()==original(p),'R02 inherited runtime drift: '+p
report={'mission':'R02-F3B-SUCCESSOR','originalVerifierUnchanged':True,
        'originalPatchUnchanged':True,'originalPatchApplied':False,
        'nativeAdmissionEnabled':False,'protectedFiles':len(paths),
        'historicalPatchApplyOnCurrentHead':False,'physicalQualification':False}
Path('build').mkdir(exist_ok=True)
Path('build/r02-f3b-successor.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS R02 F3B successor: immutable historical review artifacts and inherited runtime')
