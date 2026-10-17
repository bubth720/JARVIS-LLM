#!/usr/bin/env python3
"""Public, SYNTHETIC ONLY cross-language serialization vector. Never an authority grant."""
import hashlib,json,struct
from pathlib import Path
root=Path('fixtures/p2-contract/v1');digest=hashlib.sha256()
for p in sorted([root/'CONTRACT.txt',*root.glob('src/main/aidl/**/*.aidl')]):digest.update(p.relative_to(root).as_posix().encode()+b'\0'+p.read_bytes()+b'\0')
def uuid(n):return '00000000-0000-0000-0000-'+str(n).zfill(12)
f=dict(version='jarvis.fixture.p2/1',contract=digest.hexdigest(),epoch=uuid(1),session_id=uuid(2),operation_id=uuid(3),correlation_id=uuid(4),cancellation_id=uuid(5),authorization_ref=uuid(6),lease_id=uuid(7),generation='1',system='Réponds brièvement en français. /no_think',question='Dis bonjour en français.',model='SMALL',model_sha256='ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a',profile='TABLET_ECO',context_tokens='512',output_tokens='8',remaining_ms='90000',synthetic='true')
body=struct.pack('>I',len(f))
for k,v in sorted(f.items()):
 for item in (k,v):
  b=item.encode('utf-8');body+=struct.pack('>I',len(b))+b
wire=json.dumps(f,sort_keys=True,ensure_ascii=False,separators=(',',':'))+'\n'
for folder in ['docs/mission-003e/examples','fixtures/p2-endpoint/src/test/resources']:
 p=Path(folder);p.mkdir(parents=True,exist_ok=True)
 (p/'p2-request.json').write_text(wire);(p/'p2-request.sha256').write_text(hashlib.sha256(body).hexdigest()+'\n')
print('contract',digest.hexdigest(),'request',hashlib.sha256(body).hexdigest())
