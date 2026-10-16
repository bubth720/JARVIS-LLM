#!/usr/bin/env python3
"""Extract build artifact identity without touching signing keys or any device."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

apk = Path(sys.argv[1])
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
cert = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
info = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
fields = dict(re.findall(r"\b(name|versionCode|versionName)='([^']*)'", info.splitlines()[0]))
fingerprint = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})', cert)
if len(fingerprint) != 1 or fields.get('name') != 'fr.jarvis.llm':
    raise SystemExit('Unexpected APK/signature identity')
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
with zipfile.ZipFile(apk) as archive:
    if not any(commit.encode('ascii') in archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes[0-9]*\.dex', n)):
        raise SystemExit('APK DEX does not contain the current source commit; rebuild from a clean checkout')
report = {'package': fields['name'], 'versionName': fields['versionName'], 'versionCode': fields['versionCode'],
          'sourceCommit': commit,
          'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'signerCertificateSha256': fingerprint[0].lower(),
          'signingPurpose': 'ephemeral debug only, not production', 'physicalQualification': False}
out = Path('build/mission003c-apk-identity.json')
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(report, indent=2) + '\n')
print('::notice title=Mission 003C APK identity::' + json.dumps(report, separators=(',', ':')))
