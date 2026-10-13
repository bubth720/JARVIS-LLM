#!/usr/bin/env python3
"""Check the produced artifact, not merely the source manifest."""
import os, pathlib, re, subprocess, sys, zipfile

apk=pathlib.Path(sys.argv[1]).resolve()
sdk=pathlib.Path(os.environ['ANDROID_HOME'])
tools=sdk/'build-tools'/'36.0.0'
permissions=subprocess.check_output([str(tools/'aapt'), 'dump', 'permissions', str(apk)],text=True)
private_permission='fr.jarvis.llm.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
used=re.findall(r"uses-permission: name='([^']+)'",permissions)
if any(name != private_permission for name in used):
    raise SystemExit('Unexpected data/network/runtime permission: '+permissions)
if private_permission in used:
    manifest=subprocess.check_output([str(tools/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
    blocks=re.split(r'(?m)^\s*E: ',manifest)
    if not any(b.startswith('permission ') and private_permission in b and
               re.search(r'protectionLevel.*0x2\b',b) for b in blocks):
        raise SystemExit('AndroidX private receiver permission must remain signature protected')
subprocess.run([str(tools/'apksigner'),'verify','--verbose',str(apk)],check=True)
subprocess.run([str(tools/'zipalign'),'-c','-P','16','-v','4',str(apk)],check=True,stdout=subprocess.DEVNULL)
readelf=sdk/'ndk'/'28.1.13356709'/'toolchains'/'llvm'/'prebuilt'/'linux-x86_64'/'bin'/'llvm-readelf'
with zipfile.ZipFile(apk) as z:
    libraries=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
    if not libraries or any(not n.startswith('lib/arm64-v8a/') for n in libraries):
        raise SystemExit('ARM64 native library missing or unexpected ABI')
    out=apk.parent/'inspection';out.mkdir(exist_ok=True)
    for name in libraries:
        path=out/pathlib.Path(name).name;path.write_bytes(z.read(name))
        headers=subprocess.check_output([str(readelf),'-lW',str(path)],text=True)
        loads=[l for l in headers.splitlines() if l.strip().startswith('LOAD')]
        if not loads or any(int(l.split()[-1],16)<16384 for l in loads):
            raise SystemExit('ELF not aligned for 16 KiB: '+name)
print('PASS: no network or data-access permissions; only AndroidX private signature permission; APK signature, ARM64, ELF and ZIP 16 KiB alignment')
