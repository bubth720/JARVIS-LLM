#!/usr/bin/env python3
"""Campaign preservation guard: historical runtime/native/safety/wire files remain exact."""
import json, pathlib, subprocess
base = '8f1c2f5a936d6c01874d7be6bb987f4459b83374'
paths = subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--','app/src/main/cpp','app/src/main/java/fr/jarvis/llm/engine','app/src/main/java/fr/jarvis/llm/ChatViewModel.kt','app/src/main/java/fr/jarvis/llm/ModelStore.kt','app/src/main/java/fr/jarvis/llm/ProcessResources.kt','app/src/main/java/fr/jarvis/llm/DeviceMonitor.kt','domain/src/main/kotlin/fr/jarvis/domain/SafetyPolicy.kt','domain/src/main/kotlin/fr/jarvis/domain/VerifiedCopy.kt','domain/src/main/kotlin/fr/jarvis/domain/generation','domain/src/main/kotlin/fr/jarvis/domain/resources','fixtures'],text=True).splitlines()
for p in paths:
    assert pathlib.Path(p).read_bytes() == subprocess.check_output(['git','show',base+':'+p]), p
manifest = pathlib.Path('app/src/main/AndroidManifest.xml').read_text()
assert 'android.permission.INTERNET" tools:node="remove"' in manifest
assert '.model.ModelSelectionActivity" android:exported="false"' in manifest
workflow = pathlib.Path('.github/workflows/android.yml').read_text()
assert "if: github.event_name == 'workflow_dispatch' && inputs.run_model_probe" in workflow
print(json.dumps({'base':base,'protected_files':len(paths),'native_changed':False,'fixtures_changed':False,'automatic_model_download':False}))
