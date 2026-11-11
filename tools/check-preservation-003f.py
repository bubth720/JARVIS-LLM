#!/usr/bin/env python3
"""003F must preserve every byte of historical app/domain and all prior fixture tests."""
import hashlib,json,subprocess
from pathlib import Path
BASE='030152a5f3d888017ad7a7fe8026f2e2c48e8fd5'
# R02 inherited this reviewed repository snapshot; the original 003F baseline
# predates approved catalogue/stream additions. Preserve all inherited bytes.
INHERITED='08fbda527d187f324d94e0adafbca76fa955a343'
R02_RESOURCE='domain/src/main/kotlin/fr/jarvis/domain/resources/ResourceCoordinator.kt'
def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],check=True)
paths=git('ls-tree','-r','--name-only',BASE,'--','app','domain','fixtures/p2-endpoint/src/test','fixtures/p2-client','gradle','gradle.properties','tools/prepare-native.sh','tools/test-native.sh','tools/test-jni-p2.sh','tools/compare-model-memory.py','tools/check-apk.py').splitlines()
unchanged={}
for path in paths:
 raw=subprocess.check_output(['git','show',INHERITED+':'+path])
 assert Path(path).is_file(),'Inherited file missing: '+path
 current=Path(path).read_bytes()
 if path==R02_RESOURCE:
  original=raw.decode('utf-8')
  marker='        notify.forEach { it() }'
  replacement='''        // Deliver every revocation even when one observer throws. Keep the first failure
        // visible to the caller, and attach later failures without masking the first.
        var firstFailure: Throwable? = null
        notify.forEach { callback ->
            try { callback() } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
                else if (firstFailure !== failure) firstFailure!!.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw it }'''
  assert original.count(marker)==1,'Resource watcher preimage mismatch'
  assert current==original.replace(marker,replacement,1).encode('utf-8'),'R02 resource delta mismatch'
 else:
  assert current==raw,'Inherited baseline drift: '+path
 unchanged[path]=hashlib.sha256(current).hexdigest()
for path in git('ls-files','--cached','--others','--exclude-standard').splitlines():
 assert not path.endswith(('.keystore','.jks','.gguf','.part')),'private artifact '+path
runtime=Path('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/AndroidRuntime.kt').read_text()
assert runtime.count('recovery.requireNativeAdmission()')==3
assert runtime.index('app.bootstrapRegistry.beforeNativeOrImport(this)')<runtime.index('val path=ProcessResources.requireHeavy')
service=Path('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/P2EndpointService.kt').read_text()
assert 'AndroidRuntime(' not in service and 'LlamaBackend(' not in service and 'EndpointController(' not in service
out=dict(mission='003F',base=BASE,sourceCommit=git('rev-parse','HEAD'),unchanged=unchanged,safetyThresholdsUnchanged=True,historicalAppEntirelyUnchangedFromInherited=True,historicalAppEntirelyUnchangedFrom003F=False,inheritedBaseline=INHERITED,r02ResourceDeltaExact=True,physicalQualification=False,core004DStarted=False,bootstrapProductionReady=False)
Path('build').mkdir(exist_ok=True);Path('build/mission003f-preservation.json').write_text(json.dumps(out,indent=2)+'\n')
print('PASS 003F: inherited app/domain/fixtures exact, except verified R02 watcher delta:',len(paths),'files')
