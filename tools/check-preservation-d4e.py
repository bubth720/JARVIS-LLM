#!/usr/bin/env python3
"""Fix scope: /1, historical runtime, all AIDL, birth registry and existing native gate stay exact."""
from pathlib import Path
import subprocess
BASE='111be993e970051686bd5c27b3115320513a4be7'
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'--','app','domain','fixtures/p2-contract/v1','fixtures/p2-contract/src/main/aidl','fixtures/p2-contract/src/main/java/fr/jarvis/fixture/p2/Wire.kt','fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/AndroidRuntime.kt','fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/FixtureApplication.kt','fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/BootstrapRegistry.kt','fixtures/p2-endpoint/src/main/AndroidManifest.xml','fixtures/p2-endpoint/src/test/java/fr/jarvis/fixture/llm/EndpointTest.kt','fixtures/p2-endpoint/src/test/java/fr/jarvis/fixture/llm/AndroidIdentityTest.kt','fixtures/p2-endpoint/src/test/java/fr/jarvis/fixture/llm/BootstrapApplicationTest.kt'],text=True).splitlines()
for p in paths:assert Path(p).read_bytes()==subprocess.check_output(['git','show',BASE+':'+p]),'Protected drift: '+p
source=Path('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/ProcessRecoveryAuthority.kt').read_text()
assert 'trustedPeerEpochs' not in source
assert 'fun requireNativeAdmission(): Unit = throw Refusal("RECOVERY_REQUIRED")' in source
assert Path('fixtures/p2-endpoint/src/test/java/fr/jarvis/fixture/llm/RecoveryTest.kt').read_text().count('@Test')>=23
print('PASS D4E1:',len(paths),'protected files byte-identical; no test peer epoch provider; native gate closed.')
