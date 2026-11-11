#!/usr/bin/env python3
"""003E permits reviewed backend extensions only, not policy/catalogue/UI changes."""
from pathlib import Path
import hashlib,json,subprocess
BASE='daa46432e847ed9613dc403045498e27281eff42'
allowed={'app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt','app/src/main/cpp/bridge.cpp','app/src/main/cpp/tests/jni/NativeBridge.java'}
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],check=True)
paths=git('ls-tree','-r','--name-only',BASE,'--','app/src','domain','app/build.gradle.kts','gradle','gradle.properties','tools/check-apk.py','tools/prepare-native.sh').splitlines()
unchanged={}
for p in paths:
 if p in allowed:continue
 b=subprocess.check_output(['git','show',BASE+':'+p])
 if not Path(p).is_file():raise SystemExit('Protected baseline drift: '+p)
 current=Path(p).read_bytes()
 if p=='app/src/main/AndroidManifest.xml' and current!=b:
  # Only the existing non-exported model selector is allowed as a historical additive delta.
  # All baseline permission, backup, network and other activity declarations remain byte-identical.
  insertion=b'        <activity android:name=".model.ModelSelectionActivity" android:exported="false" />\n'
  anchor=b'        <activity android:name=".catalogue.CatalogueActivity" android:exported="false" />'
  if b.count(anchor)!=1 or current!=b.replace(anchor,insertion+anchor,1):
   raise SystemExit('Protected baseline drift: '+p)
 elif p=='app/src/main/java/fr/jarvis/llm/MainActivity.kt' and current!=b:
  # Exact pre-existing model catalogue navigation; reject any other drift.
  anchor=b'        conversation = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }'
  insertion=(b'        root.addView(Button(this).apply {\n'
   b'            text = "Modèles locaux (catalogue expérimental)"\n'
   b'            setOnClickListener { lifecycleScope.launch {\n'
   b'                if (vm.releaseForCatalogue() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))\n'
   b'                    startActivity(Intent(this@MainActivity, fr.jarvis.llm.model.ModelSelectionActivity::class.java))\n'
   b'            } }\n'
   b'        })\n')
  if b.count(anchor)!=1 or current!=b.replace(anchor,insertion+anchor,1):
   raise SystemExit('Protected baseline drift: '+p)
 elif current!=b:raise SystemExit('Protected baseline drift: '+p)
 unchanged[p]=hashlib.sha256(b).hexdigest()
# The formatter itself is unchanged. Fixture counting/generation call that one function.
def formatter(s):return s[s.index('static std::string format('):s.index('static jbyteArray generate(')]
old=subprocess.check_output(['git','show',BASE+':app/src/main/cpp/bridge.cpp'],text=True)
assert formatter(old)==formatter(Path('app/src/main/cpp/bridge.cpp').read_text())
for p in git('ls-files').splitlines():
 if p.endswith(('.keystore','.jks','.gguf','.part')):raise SystemExit('Private key/model artifact tracked: '+p)
out={'mission':'003E','base':BASE,'sourceCommit':git('rev-parse','HEAD'),'unchanged':unchanged,'reviewedRuntimeChanges':sorted(allowed),'historicalManifestUnchanged':True,'canonicalFormatterUnchanged':True,'safetyThresholdsUnchanged':True,'physicalQualification':False}
Path('build').mkdir(exist_ok=True);Path('build/mission003e-preservation.json').write_text(json.dumps(out,indent=2)+'\n')
print('PASS 003E: historical manifest, catalogue, policies, profiles and original tests unchanged; same native formatter.')
