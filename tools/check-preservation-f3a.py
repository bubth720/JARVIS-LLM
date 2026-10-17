#!/usr/bin/env python3
"""F3A is additive only: freeze the entire historical implementation and both wire bundles."""
from pathlib import Path
import json, subprocess
BASE = '1ce24405b04a50f97cf30078b9716c7d00c593fc'
paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE, '--',
                                 'app', 'domain', 'fixtures'], text=True).splitlines()
for path in paths:
    assert Path(path).read_bytes() == subprocess.check_output(['git', 'show', BASE + ':' + path]), path
pin = Path('fixtures/p2-contract/CONTRACT.sha256').read_text().strip()
assert pin == '3deeaf3565f00c6b0b5380877bcf2ccf79ab495895a5bc627371ea774db4e8ef'
source = Path('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/ProcessRecoveryAuthority.kt').read_text()
assert 'fun requireNativeAdmission(): Unit = throw Refusal("RECOVERY_REQUIRED")' in source
allowed = ['fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/f3/',
           'fixtures/p2-endpoint/src/test/java/fr/jarvis/fixture/llm/f3/',
           'fixtures/p2-endpoint/src/test/java/fr/jarvis/llm/engine/F3WorkerTest.kt',
           'docs/mission-003f-f3a/', 'tools/check-preservation-f3a.py',
           'tools/publish-validation-f3a.py', '.github/workflows/android.yml']
changed = subprocess.check_output(['git', 'diff', '--name-only', BASE], text=True).splitlines()
added = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], text=True).splitlines()
for path in set(changed + added):
    assert any(path.startswith(prefix) if prefix.endswith('/') else path == prefix for prefix in allowed), path
report = {'base': BASE, 'protectedFiles': len(paths), 'historicalRuntimeChanged': False,
          'contractSha256': pin, 'nativeAdmissionEnabled': False, 'F3Proven': False, 'core004DStarted': False}
Path('build').mkdir(exist_ok=True)
Path('build/mission003f-f3a-preservation.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
