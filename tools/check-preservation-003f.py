#!/usr/bin/env python3
"""003F must preserve every byte of historical app/domain and all prior fixture tests."""
import hashlib,json,subprocess
from pathlib import Path
BASE='030152a5f3d888017ad7a7fe8026f2e2c48e8fd5'
def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],check=True)
paths=git('ls-tree','-r','--name-only',BASE,'--','app','domain','fixtures/p2-endpoint/src/test','fixtures/p2-client','gradle','gradle.properties','tools/prepare-native.sh','tools/test-native.sh','tools/test-jni-p2.sh','tools/compare-model-memory.py','tools/check-apk.py').splitlines()
unchanged={}
for path in paths:
 raw=subprocess.check_output(['git','show',BASE+':'+path]);assert Path(path).is_file() and Path(path).read_bytes()==raw,'003E baseline drift: '+path
 unchanged[path]=hashlib.sha256(raw).hexdigest()
for path in git('ls-files','--cached','--others','--exclude-standard').splitlines():
 assert not path.endswith(('.keystore','.jks','.gguf','.part')),'private artifact '+path
runtime=Path('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/AndroidRuntime.kt').read_text()
assert runtime.count('recovery.requireNativeAdmission()')==3
assert runtime.index('app.bootstrapRegistry.beforeNativeOrImport(this)')<runtime.index('val path=ProcessResources.requireHeavy')
service=Path('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/P2EndpointService.kt').read_text()
assert 'AndroidRuntime(' not in service and 'LlamaBackend(' not in service and 'EndpointController(' not in service
out=dict(mission='003F',base=BASE,sourceCommit=git('rev-parse','HEAD'),unchanged=unchanged,safetyThresholdsUnchanged=True,historicalAppEntirelyUnchanged=True,physicalQualification=False,core004DStarted=False,bootstrapProductionReady=False)
Path('build').mkdir(exist_ok=True);Path('build/mission003f-preservation.json').write_text(json.dumps(out,indent=2)+'\n')
print('PASS 003F historical app/domain, catalogue, safety, JNI, native engine and all baseline tests byte-identical:',len(paths),'files')
