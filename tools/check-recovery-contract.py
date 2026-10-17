#!/usr/bin/env python3
"""003F candidate contract digest, exact /1 preservation, appended ABI and closed generated tables."""
import hashlib,json,re,struct,subprocess,sys
from pathlib import Path
root=Path('fixtures/p2-contract')
def bundle(base,tables=False):
 files=sorted([base/'CONTRACT.txt',*([base/'TABLES.json'] if tables else []),*base.glob('src/main/aidl/**/*.aidl')])
 d=hashlib.sha256()
 for p in files:d.update(p.relative_to(base).as_posix().encode()+b'\0'+p.read_bytes()+b'\0')
 return d.hexdigest()
legacy=bundle(root/'v1');assert legacy=='fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9'
base='030152a5f3d888017ad7a7fe8026f2e2c48e8fd5'
def old(path):return subprocess.check_output(['git','show',base+':'+str(path)])
assert (root/'v1/CONTRACT.txt').read_bytes()==old(root/'CONTRACT.txt')
assert (root/'v1/reference/Wire.kt.reference').read_bytes()==old(root/'src/main/java/fr/jarvis/fixture/p2/Wire.kt')
for p in (root/'v1/src/main/aidl').rglob('*.aidl'):
 current=root/p.relative_to(root/'v1');assert p.read_bytes()==old(current)
 def methods(text):return re.findall(r'(?:byte\[\]|void)\s+(\w+)\s*\(([^)]*)\)',text)
 original=methods(p.read_text());now=methods(current.read_text());assert now[:len(original)]==original
 if p.stem in ('IP2Endpoint','IP2Arbiter'):assert len(now)==len(original)+1 and now[-1][0]=='recovery'
data=json.loads((root/'TABLES.json').read_text());source=(root/'src/main/java/fr/jarvis/fixture/p2/RecoveryTables.kt').read_text()
for k,v in data['fields'].items():
 match=re.search('"'+k+'" to setOf\\(([^)]*)\\)',source);assert match and re.findall('"([^"]+)"',match[1])==v
for k,v in data['enums'].items():
 match=re.search('"'+k+'" to setOf\\(([^)]*)\\)',source);assert match and re.findall('"([^"]+)"',match[1])==v
for name,values in [('prior',data['prior_fields_when_PRESENT']),('codes',data['codes']),('journalStates',data['journal_states'])]:
 match=re.search('val '+name+'=setOf\\(([^)]*)\\)',source);assert re.findall('"([^"]+)"',match[1])==values
assert hashlib.sha256((root/'TABLES.json').read_bytes()).hexdigest()=='0bca700a2a926b841896c999f89edc7e1b82fdbbd0ced3f5923e5f4cfbbfbe44','Approved table bytes changed'
assert (root/'TABLES.json').read_bytes()==Path('docs/mission-003f-correction/reference/TABLES_PROPOSEES.json').read_bytes()
subprocess.run([sys.executable,'tools/generate-recovery-tables.py','--check'],check=True)
sha=bundle(root,True)
f=dict(version=data['version'],contract=sha,kind='DISCOVER',core_process_epoch='00000000-0000-0000-0000-000000000004',broker_epoch='00000000-0000-0000-0000-000000000005',issued_elapsed_ms='100',expires_elapsed_ms='5000',pilot_id='00000000-0000-0000-0000-000000000001',session_id='00000000-0000-0000-0000-000000000002',round_id='00000000-0000-0000-0000-000000000003',nonce='A'*43)
body=struct.pack('>I',len(f))
for k,v in sorted(f.items()):
 for s in (k,v):b=s.encode();body+=struct.pack('>I',len(b))+b
outputs={root/'CONTRACT.sha256':sha+'\n',Path('docs/mission-003f-correction/examples/discover.json'):json.dumps(f,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n',Path('docs/mission-003f-correction/examples/discover.sha256'):hashlib.sha256(body).hexdigest()+'\n'}
outputs[Path('fixtures/p2-endpoint/src/test/resources/discover-003f.json')]=outputs[Path('docs/mission-003f-correction/examples/discover.json')]
outputs[Path('fixtures/p2-endpoint/src/test/resources/discover-003f.sha256')]=outputs[Path('docs/mission-003f-correction/examples/discover.sha256')]
for path,content in outputs.items():
 if '--write' in sys.argv:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
 else:assert path.read_text()==content,str(path)+' is stale'
print('PASS /1 frozen, append-only ABI, closed tables. Candidate /2 SHA256:',sha,'(CORE REVIEW BLOCKED)')
