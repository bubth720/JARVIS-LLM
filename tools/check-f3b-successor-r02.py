#!/usr/bin/env python3
"""R02 successor to historical F3B review. Never applies a sensitive patch."""
import hashlib,json,subprocess,sys,tempfile,zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path('docs/mission-003f-f3b')
BASE='08fbda527d187f324d94e0adafbca76fa955a343'
def original(path, commit=BASE): return subprocess.check_output(['git','show',commit+':'+path])
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
        assert hashlib.sha256(original(p,m['base'])).hexdigest()==before,'F3B historical preimage drift: '+p
# Check every historically protected file at the inherited R02 snapshot.
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app','domain','fixtures'],text=True).splitlines()
for p in paths:
    assert Path(p).read_bytes()==original(p),'R02 inherited runtime drift: '+p
# The original F3B verifier is executed unchanged on its own frozen review
# commit, in a separate detached worktree: it proves the sensitive patch still
# applies to its exact base, was never applied, and the F3A runtime it
# protected is intact. It cannot run on HEAD because PERF-03B (29912e3)
# legitimately changed the native bridge after the F3B review.
REVIEW_COMMIT='8f1c2f5a936d6c01874d7be6bb987f4459b83374'
for path in ['verify.py','build_patch.py','SENSITIVE.patch','PATCH.json']:
    assert Path(ROOT/path).read_bytes()==original('docs/mission-003f-f3b/'+path,REVIEW_COMMIT),'F3B review commit drift: '+path
with tempfile.TemporaryDirectory() as tmp:
    wt=Path(tmp)/'f3b-review'
    subprocess.run(['git','worktree','add','--detach',str(wt),REVIEW_COMMIT],check=True,stdout=subprocess.DEVNULL)
    try:
        out=subprocess.run([sys.executable,'docs/mission-003f-f3b/verify.py'],cwd=wt,check=True,capture_output=True,text=True).stdout
        frozen=json.loads(out.strip().splitlines()[-1])
    finally:
        subprocess.run(['git','worktree','remove','--force',str(wt)],check=True)
assert frozen['sourceCommit']==REVIEW_COMMIT and frozen['baseCommit']==m['base']
assert frozen['patchApplyCheck'] is True and frozen['patchApplied'] is False
assert frozen['workingTreeModified'] is False and frozen['admission2Enabled'] is False
report={'mission':'R02-F3B-SUCCESSOR','originalVerifierUnchanged':True,
        'originalPatchUnchanged':True,'originalPatchApplied':False,
        'nativeAdmissionEnabled':False,'protectedFiles':len(paths),
        'historicalPatchApplyOnCurrentHead':False,'physicalQualification':False,
        'frozenReviewCommit':REVIEW_COMMIT,'frozenVerifierPassed':True,
        'frozenPatchApplyCheckOnBase':True}
if '--results' in sys.argv:
    # Same isolated-test and APK-leak assertions as the original --results mode,
    # evaluated on the current HEAD build outputs.
    t=ET.parse('fixtures/p2-endpoint/build/test-results/testDebugUnitTest/TEST-fr.jarvis.llm.engine.F3CandidateTest.xml').getroot()
    counts={k:int(t.get(k,0)) for k in ['tests','errors','failures','skipped']}
    assert counts['tests']==17 and not any(counts[k] for k in ['errors','failures','skipped']),counts
    for name in ['F3Trace.kt','F3NativeApi.kt']:
        assert (ROOT/'candidate'/name).read_bytes()==Path('fixtures/p2-endpoint/build/f3b-review-sources',name).read_bytes()
    for apk in [Path('app/build/outputs/apk/debug/app-debug.apk'),Path('fixtures/p2-endpoint/build/outputs/apk/debug/p2-endpoint-debug.apk')]:
        with zipfile.ZipFile(apk) as z:
            dex=[n for n in z.namelist() if n.endswith('.dex')]
            assert dex
            for name in dex:
                assert b'Lfr/jarvis/llm/engine/F3Trace;' not in z.read(name),'Review code leaked into APK'
    report.update(isolatedTests=counts,fakeJniOnUnmodifiedWorker=True,observerFaultInjection=True,candidateAbsentFromApks=True)
Path('build').mkdir(exist_ok=True)
Path('build/r02-f3b-successor.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS R02 F3B successor: immutable historical review artifacts and inherited runtime')
