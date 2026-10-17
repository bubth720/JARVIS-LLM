#!/usr/bin/env python3
"""Review-only checks: NEVER apply the sensitive patch. No native release proof."""
from pathlib import Path
import hashlib,json,subprocess,sys,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parent
BASE='aa85ad4b506bb655dbe5b50ec4b125d19b48e81e'
subprocess.run([sys.executable,str(ROOT/'build_patch.py'),'--check'],check=True)
m=json.loads((ROOT/'PATCH.json').read_text())
assert m['base']==BASE and not m['applied'] and not m['nativeAdmissionEnabled']
assert hashlib.sha256((ROOT/'SENSITIVE.patch').read_bytes()).hexdigest()==m['patchSha256']
subprocess.run(['git','apply','--check',str(ROOT/'SENSITIVE.patch')],check=True)
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app','domain','fixtures'],text=True).splitlines()
for path in paths:
 assert Path(path).read_bytes()==subprocess.check_output(['git','show',BASE+':'+path]),'Historical mutation: '+path
allowed=['docs/mission-003f-f3b/','.github/workflows/android.yml','tools/check-preservation-f3a.py']
changes=subprocess.check_output(['git','diff','--name-only',BASE],text=True).splitlines()
changes+=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],text=True).splitlines()
for path in changes:
 assert any(path.startswith(p) if p.endswith('/') else path==p for p in allowed),'Out of scope: '+path
for entry in m['files']:
 path=Path(entry['path'])
 assert (not path.exists()) if entry['beforeSha256'] is None else hashlib.sha256(path.read_bytes()).hexdigest()==entry['beforeSha256']
pin=Path('fixtures/p2-contract/CONTRACT.sha256').read_text().strip()
assert pin=='3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef'
report={'mission':'003F-F3B','baseCommit':BASE,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
 'workingTreeModified':bool(subprocess.check_output(['git','status','--porcelain'],text=True).strip()),
 'protectedFiles':len(paths),'patchSha256':m['patchSha256'],'patchApplyCheck':True,'patchApplied':False,
 'patchedBackendCompiled':False,'newJniProbeExecuted':False,'nativeReleaseProven':False,
 'admission2Enabled':False,'CORE004DStarted':False,'contract2Sha256':pin}
if '--results' in sys.argv:
 p=Path('fixtures/p2-endpoint/build/test-results/testDebugUnitTest/TEST-fr.jarvis.llm.engine.F3CandidateTest.xml')
 r=ET.parse(p).getroot();counts={k:int(r.get(k,0)) for k in ['tests','errors','failures','skipped']}
 assert counts['tests']==17 and not any(counts[k] for k in ['errors','failures','skipped']),counts
 for name in ['F3Trace.kt','F3NativeApi.kt']:
  assert (ROOT/'candidate'/name).read_bytes()==Path('fixtures/p2-endpoint/build/f3b-review-sources',name).read_bytes()
 report['isolatedTests']=counts
 report['fakeJniOnUnmodifiedWorker']=True
 report['observerFaultInjection']=True
 report['candidateAbsentFromApks']=True
 for apk in [Path('app/build/outputs/apk/debug/app-debug.apk'),Path('fixtures/p2-endpoint/build/outputs/apk/debug/p2-endpoint-debug.apk')]:
  with zipfile.ZipFile(apk) as z:
   dex=[n for n in z.namelist() if n.endswith('.dex')]
   assert dex
   for name in dex:
    assert b'Lfr/jarvis/llm/engine/F3Trace;' not in z.read(name),'Review code leaked into APK'
Path('build').mkdir(exist_ok=True)
Path('build/mission003f-f3b-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
