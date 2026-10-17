#!/usr/bin/env python3
import json,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
def junit(folder,minimum):
 roots=[ET.parse(p).getroot() for p in Path(folder).glob('TEST-*.xml')]
 r={k:sum(int(s.get(k,0)) for s in roots) for k in ('tests','failures','errors','skipped')}
 assert r['tests']>=minimum and not any(r[k] for k in ('failures','errors','skipped')),r
 return r
revision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
if subprocess.check_output(['git','status','--porcelain'],text=True).strip():revision+=' + working-tree'
report={'mission':'003F-D4E1','sourceCommit':revision,
 'domain':junit('domain/build/test-results/test',87),'app':junit('app/build/test-results/testDebugUnitTest',27),
 'fixture':junit('fixtures/p2-endpoint/build/test-results/testDebugUnitTest',67),'lint':{},'androidCrossApkExecuted':False,'physicalQualification':False,'realArbiterAvailable':False,'bootstrapProductionReady':False,'core004DStarted':False,'pendingContractClarification':[],'controlProtocolImplemented':True,'F3Implemented':False,'nativeAdmissionEnabled':False,'bundleReviewRequired':True,'candidateContractSha256':Path('fixtures/p2-contract/CONTRACT.sha256').read_text().strip()}
for pattern in ['app/build/reports/lint-results-debug.xml','fixtures/p2-endpoint/build/reports/lint-results-debug.xml','fixtures/p2-client/build/reports/lint-results-*.xml']:
 paths=list(Path('.').glob(pattern));assert paths,pattern
 for p in paths:
  counts={}
  for item in ET.parse(p).getroot().findall('issue'):
   severity=item.get('severity');counts[severity]=counts.get(severity,0)+1
  assert not counts.get('Error') and not counts.get('Fatal'),counts
  report['lint'][str(p)]=counts
Path('build/mission003f-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print('::notice title=003F cloud validation::'+json.dumps(report,separators=(',',':')))
