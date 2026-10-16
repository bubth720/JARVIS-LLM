#!/usr/bin/env python3
"""Publish actual cloud checks, never fabricate Android hardware measurements."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--mission', choices=('003B', '003C'), default='003B')
args = parser.parse_args()

def junit(folder):
    suites = [ET.parse(p).getroot() for p in sorted(Path(folder).glob('TEST-*.xml'))]
    if not suites:
        raise SystemExit('Missing JUnit results: ' + folder)
    result = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    if result['failures'] or result['errors']:
        raise SystemExit('Failed tests: ' + folder)
    return result

lint = ET.parse('app/build/reports/lint-results-debug.xml').getroot()
severities = {}
for issue in lint.findall('issue'):
    key = issue.get('severity', 'Unknown')
    severities[key] = severities.get(key, 0) + 1
if severities.get('Error', 0) or severities.get('Fatal', 0):
    raise SystemExit('Lint errors')
native_passes = Path('build/native-tests/Testing/Temporary/LastTest.log').read_text().count('Test Passed.')
if native_passes < 1:
    raise SystemExit('Missing native pass evidence')
report = {
    'mission': args.mission, 'platform': 'cloud-linux-jvm-robolectric-not-android-hardware',
    'sourceCommit': ('working-tree' if subprocess.check_output(['git', 'status', '--porcelain'], text=True).strip()
                     else subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()),
    'domain': junit('domain/build/test-results/test'),
    'simulatedAndroid': junit('app/build/test-results/testDebugUnitTest'),
    'nativePassed': native_passes, 'lint': severities,
    'safetyPolicySha256': hashlib.sha256(Path('domain/src/main/kotlin/fr/jarvis/domain/SafetyPolicy.kt').read_bytes()).hexdigest(),
    'tabletMemoryMeasurements': None, 'newAdmissionBudgetQualified': False,
}
output = Path('build/mission' + args.mission.lower() + '-validation.json')
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n')
print('::notice title=Mission ' + args.mission + ' cloud validation::' + json.dumps(report, separators=(',', ':')))
