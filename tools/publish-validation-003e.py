#!/usr/bin/env python3
import json,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
def junit(folder,minimum):
 roots=[ET.parse(p).getroot() for p in Path(folder).glob('TEST-*.xml')]
 r={k:sum(int(s.get(k,0)) for s in roots) for k in ('tests','failures','errors','skipped')}
 assert r['tests']>=minimum and not any(r[k] for k in ('failures','errors','skipped')),r
 return r
report={'mission':'003E','sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
 'domain':junit('domain/build/test-results/test',87),'app':junit('app/build/test-results/testDebugUnitTest',27),
 'fixture':junit('fixtures/p2-endpoint/build/test-results/testDebugUnitTest',19),'lint':{},'androidCrossApkExecuted':False,'physicalQualification':False,'realArbiterAvailable':False}
for pattern in ['app/build/reports/lint-results-debug.xml','fixtures/p2-endpoint/build/reports/lint-results-debug.xml','fixtures/p2-client/build/reports/lint-results-*.xml']:
 paths=list(Path('.').glob(pattern));assert paths,pattern
 for p in paths:
  counts={}
  for item in ET.parse(p).getroot().findall('issue'):
   severity=item.get('severity');counts[severity]=counts.get(severity,0)+1
  assert not counts.get('Error') and not counts.get('Fatal'),counts
  report['lint'][str(p)]=counts
Path('build/mission003e-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print('::notice title=003E cloud validation::'+json.dumps(report,separators=(',',':')))
