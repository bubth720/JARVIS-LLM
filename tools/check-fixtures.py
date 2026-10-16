#!/usr/bin/env python3
"""Inspect actual fixture APKs, separate certificates, exported service and native alignment."""
import hashlib,json,os,re,subprocess,zipfile
from pathlib import Path
sdk=Path(os.environ['ANDROID_HOME']);bt=sdk/'build-tools/36.0.0';permission='fr.jarvis.fixture.llm.permission.BIND_P2'
def cmd(*a):return subprocess.check_output([str(x) for x in a],text=True)
def inspect(path,package,native=False):
 permissions=cmd(bt/'aapt','dump','permissions',path)
 used=re.findall(r"uses-permission: name='([^']+)'",permissions)
 assert set(used)<= {permission},permissions
 badging=cmd(bt/'aapt','dump','badging',path);assert "package: name='"+package+"'" in badging
 signer=cmd(bt/'apksigner','verify','--print-certs',path)
 cert=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',signer).group(1)
 subprocess.run([str(bt/'zipalign'),'-c','-P','16','4',str(path)],check=True)
 with zipfile.ZipFile(path) as z:
  dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
  assert digest.hexdigest().encode() in dex,'stale APK contract digest'
  libs=[n for n in z.namelist() if n.endswith('.so')]
  if native:
   assert libs and all(n.startswith('lib/arm64-v8a/') for n in libs)
   out=path.parent/'inspection';out.mkdir(exist_ok=True)
   readelf=sdk/'ndk/28.1.13356709/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
   for name in libs:
    f=out/Path(name).name;f.write_bytes(z.read(name));headers=cmd(readelf,'-lW',f)
    loads=[l for l in headers.splitlines() if l.strip().startswith('LOAD')];assert loads and all(int(l.split()[-1],16)>=16384 for l in loads)
   nm=sdk/'ndk/28.1.13356709/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
   symbols=cmd(nm,'-D',out/'libjarvis_llm.so');assert 'generateFixtureP2' in symbols and 'measureFixtureP2' in symbols and 'renderForTest' not in symbols
  else:assert not libs,'Client must not package an LLM engine'
 return dict(package=package,apkSha256=hashlib.sha256(path.read_bytes()).hexdigest(),signerCertificateSha256=cert,permissions=used)
root=Path('fixtures/p2-contract');files=sorted([root/'CONTRACT.txt',*root.glob('src/main/aidl/**/*.aidl')]);digest=hashlib.sha256()
for p in files:digest.update(p.relative_to(root).as_posix().encode()+b'\0'+p.read_bytes()+b'\0')
endpoint=Path('fixtures/p2-endpoint/build/outputs/apk/debug/p2-endpoint-debug.apk')
results=[inspect(endpoint,'fr.jarvis.fixture.llm',True)]
manifest=cmd(bt/'aapt','dump','xmltree',endpoint,'AndroidManifest.xml')
blocks=re.split(r'(?m)^\s*E: ',manifest)
assert any(b.startswith('permission ') and permission in b and re.search(r'protectionLevel.*0x2\b',b) for b in blocks)
services=[b for b in blocks if b.startswith('service ')]
assert len(services)==1 and 'P2EndpointService' in services[0] and permission in services[0] and re.search(r'exported.*0xffffffff',services[0])
for flavor,package in [('authorized','fr.jarvis.fixture.core'),('wrongPackage','fr.jarvis.fixture.unknown'),('wrongSignature','fr.jarvis.fixture.badclient')]:
 p=Path(f'fixtures/p2-client/build/outputs/apk/{flavor}/debug/p2-client-{flavor}-debug.apk');results.append(inspect(p,package))
assert results[0]['signerCertificateSha256']==results[1]['signerCertificateSha256']==results[2]['signerCertificateSha256']
assert results[0]['signerCertificateSha256']!=results[3]['signerCertificateSha256']
legacy=Path('app/build/outputs/apk/debug/app-debug.apk')
legacycert=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',cmd(bt/'apksigner','verify','--print-certs',legacy)).group(1)
assert legacycert!=results[0]['signerCertificateSha256']
shared=Path('fixtures/p2-endpoint/build/generated/shared-runtime/fr/jarvis/llm')
for p in shared.rglob('*.kt'):assert p.read_bytes()==(Path('app/src/main/java/fr/jarvis/llm')/p.relative_to(shared)).read_bytes(),'Shared runtime drift'
report=dict(mission='003E',sourceCommit=('working-tree' if cmd('git','status','--porcelain').strip() else cmd('git','rev-parse','HEAD').strip()),contractSha256=digest.hexdigest(),apks=results,historicalSignerUnchangedAndDistinct=True,sharedRuntimeExact=True,androidCrossApkExecuted=False,physicalQualification=False)
Path('build/mission003e-apks.json').write_text(json.dumps(report,indent=2)+'\n')
print('::notice title=003E fixture APK verification::'+json.dumps(report,separators=(',',':')))
