#!/usr/bin/env python3
"""Report executed F3A checks without promoting fakes/reviews into native evidence."""
from pathlib import Path
import json, subprocess, xml.etree.ElementTree as ET
folder = Path('fixtures/p2-endpoint/build/test-results/testDebugUnitTest')
classes = ['fr.jarvis.fixture.llm.f3.ObservationTest', 'fr.jarvis.fixture.llm.f3.BarrierTest',
           'fr.jarvis.fixture.llm.f3.ReviewJournalTest', 'fr.jarvis.llm.engine.F3WorkerTest']
results = {}
for name in classes:
    root = ET.parse(folder / ('TEST-' + name + '.xml')).getroot()
    counts = {key: int(root.get(key, '0')) for key in ('tests', 'errors', 'failures', 'skipped')}
    assert counts['tests'] > 0 and not any(counts[k] for k in ('errors', 'failures', 'skipped')), counts
    results[name] = counts
report = {'mission': '003F-F3A', 'sourceCommit': subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
          'workingTreeModified': bool(subprocess.check_output(['git','status','--porcelain'],text=True).strip()),
          'tests': results, 'newTests': sum(x['tests'] for x in results.values()),
          'realHistoricalWorkerFakeNativeApi': True, 'f3aRealJniExecuted': False,
          'terminalReceiptProduced': False, 'productionPortWired': False, 'nativeAdmissionEnabled': False,
          'F3Proven': False, 'core004DStarted': False, 'androidCrossApkExecuted': False,
          'physicalQualification': False, 'directoryFsyncInReviewTests': 'TEST_DOUBLE',
          'reviewJournalIsNativeEvidence': False,
          'contractSha256': Path('fixtures/p2-contract/CONTRACT.sha256').read_text().strip()}
Path('build/mission003f-f3a-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
