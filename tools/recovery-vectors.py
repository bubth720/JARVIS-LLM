#!/usr/bin/env python3
"""Publish real-bundle vectors, independently of Kotlin. Sentinel reference is never a transport pin."""
import base64,hashlib,json,struct,sys
from pathlib import Path
root=Path('docs/mission-003f-correction');v=json.loads((root/'reference/vectors.json').read_text());sha=Path('fixtures/p2-contract/CONTRACT.sha256').read_text().strip()
def canonical(f):
 out=struct.pack('>I',len(f))
 for k,val in sorted(f.items()):
  for s in (k,val):b=s.encode('utf-8',errors='strict');out+=struct.pack('>I',len(b))+b
 return out
def h(label,f):return hashlib.sha256(('jarvis.fixture.p2/2.'+label+'.D4E1\0').encode()+canonical(f)).hexdigest()
def body(f):return json.dumps(f,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
def b64(f):return base64.urlsafe_b64encode(body(f)).rstrip(b'=').decode()
c={**v['core_prepare'],'contract':sha};ch=h('core_prepare',c)
a={**v['llm_attest'],'contract':sha,'core_prepare_sha256':ch};ah=h('llm_attest',a)
p=h('prepare_set',{'core_prepare_sha256':ch,'llm_attest_sha256':ah})
m={**v['commit'],'contract':sha,'core_prepare_b64':b64(c),'llm_attest_b64':b64(a),'core_prepare_sha256':ch,'llm_attest_sha256':ah,'prepare_set_sha256':p};m['decision_sha256']=h('decision',{k:val for k,val in m.items() if k!='decision_sha256'})
v.update(core_prepare=c,llm_attest=a,commit=m,contract_zero_is_test_sentinel_not_a_bundle=False)
v['expected'].update(core_prepare_sha256=ch,llm_attest_sha256=ah,prepare_set_sha256=p,decision_sha256=m['decision_sha256'],commit_json_bytes=len(body(m)))
for item in v['message_vectors']:
 f={**item['fields'],'contract':sha};kind=f['kind']
 if kind=='CORE_PREPARE':f=c
 if kind=='ATTEST':f=a
 if kind=='CHALLENGE':f.update(core_prepare_b64=b64(c),core_prepare_sha256=ch)
 if kind=='COMMIT':f=m
 if kind=='COMMIT_ACK':f.update(core_prepare_sha256=ch,llm_attest_sha256=ah,prepare_set_sha256=p,decision_sha256=m['decision_sha256'])
 item.update(fields=f,canonical_bytes=len(canonical(f)),sha256=h('message',f))
text=json.dumps(v,ensure_ascii=False,indent=2)+'\n'
outputs={root/'examples/vectors.json':text,Path('fixtures/p2-endpoint/src/test/resources/d4e-actual.json'):text,Path('fixtures/p2-endpoint/src/test/resources/d4e-reference.json'):(root/'reference/vectors.json').read_text()}
for path,content in outputs.items():
 if '--write' in sys.argv:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
 else:assert path.read_text()==content,'stale vector '+str(path)
print('PASS real bundle vectors',sha,'core',ch,'llm',ah,'set',p,'decision',m['decision_sha256'])
