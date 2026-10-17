#!/usr/bin/env python3
"""Offline synthetic commitment checks only: no Binder, engine, grants or repository writes."""
import base64
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent

def canonical(fields):
    if not isinstance(fields, dict) or any(type(k) is not str or type(v) is not str for k,v in fields.items()):
        raise ValueError('string map required')
    out = struct.pack('>I',len(fields))
    for k,v in sorted(fields.items()):
        for s in (k,v):
            b=s.encode('utf-8',errors='strict'); out+=struct.pack('>I',len(b))+b
    return out

def independent(fields):
    def n(v):return int(v).to_bytes(4,'big')
    parts=[n(len(fields))]
    for key in sorted(fields):
        for s in [key,fields[key]]:
            b=s.encode('utf-8');parts.extend([n(len(b)),b])
    return b''.join(parts)

def digest(label,m):return hashlib.sha256(label.encode('ascii')+b'\0'+canonical(m)).hexdigest()
def label(kind):return 'jarvis.fixture.p2/2.'+kind+'.D4E1'
def pairs(items):
    result={}
    for k,v in items:
        if k in result:raise ValueError('duplicate')
        result[k]=v
    return result

def encode(m):return json.dumps(m,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
def decode(s):
    if len(s)>8192:raise ValueError('proof too large')
    b=base64.urlsafe_b64decode(s+'='*((-len(s))%4))
    if len(b)>6144 or base64.urlsafe_b64encode(b).rstrip(b'=').decode()!=s:raise ValueError('base64')
    m=json.loads(b.decode('utf-8'),object_pairs_hook=pairs)
    if encode(m)!=b:raise ValueError('noncanonical JSON')
    canonical(m)
    return m

def verify(commit,local_core,local_llm,tables):
    if set(commit)!=set(tables['fields']['COMMIT']):raise ValueError('closed COMMIT')
    c=decode(commit['core_prepare_b64']);a=decode(commit['llm_attest_b64'])
    for proof,kind in [(c,'CORE_PREPARE'),(a,'ATTEST')]:
        if set(proof)!=set(tables['fields'][kind]) or proof['kind']!=kind:raise ValueError('closed proof')
    if c!=local_core or a!=local_llm:raise ValueError('not the locally authenticated proofs')
    if c['participant']!='core' or a['participant']!='llm':raise ValueError('roles')
    ch=digest(label('core_prepare'),c);ah=digest(label('llm_attest'),a)
    if a['core_prepare_sha256']!=ch:raise ValueError('unlinked ATTEST')
    p=digest(label('prepare_set'),{'core_prepare_sha256':ch,'llm_attest_sha256':ah})
    d=digest(label('decision'),{k:v for k,v in commit.items() if k!='decision_sha256'})
    expected={'core_prepare_sha256':ch,'llm_attest_sha256':ah,'prepare_set_sha256':p,'decision_sha256':d}
    if any(commit[k]!=v for k,v in expected.items()):raise ValueError('membership or decision mismatch')
    for key in ['pilot_id','round_id','session_id','nonce','core_process_epoch','broker_epoch','llm_process_epoch','llm_service_epoch','endpoint_epoch','generation','owner','prior_key_state']:
        if not c[key]==a[key]==commit[key]:raise ValueError('binding mismatch')
    return expected

def main():
    v=json.loads((ROOT/'vectors.json').read_text());t=json.loads((ROOT/'TABLES_PROPOSEES.json').read_text())
    count=0
    for p in v['primitive_vectors']:
        b=canonical(p['fields']);assert b==independent(p['fields']);assert b.hex()==p['canonical_hex'];assert hashlib.sha256(b).hexdigest()==p['sha256'];count+=1
    actual=verify(v['commit'],v['core_prepare'],v['llm_attest'],t)
    assert all(actual[k]==v['expected'][k] for k in actual);count+=1
    assert canonical({'a':'é','b':'x'})==canonical({'b':'x','a':'é'});count+=1
    assert canonical({'a':'é'})!=canonical({'a':'e\u0301'});count+=1
    for field in ['prepare_set_sha256','decision_sha256','core_prepare_sha256','nonce']:
        bad=dict(v['commit']);bad[field]='f'*64
        try:verify(bad,v['core_prepare'],v['llm_attest'],t)
        except ValueError:count+=1
        else:raise AssertionError('tamper accepted')
    forged=dict(v['core_prepare']);forged['core_journal_sha256']='f'*64
    try:verify(v['commit'],forged,v['llm_attest'],t)
    except ValueError:count+=1
    else:raise AssertionError('wrong local proof')
    assert len(encode(v['commit']))==v['expected']['commit_json_bytes']<=24576;count+=1
    for item in v['message_vectors']:
        fields=item['fields'];kind=item.get('table',item['kind'])
        names=set(t['fields'][kind])
        if fields.get('prior_key_state')=='PRESENT':names.update(t['prior_fields_when_PRESENT'])
        assert set(fields)==names and fields['kind']==kind
        assert canonical(fields)==independent(fields)
        assert len(canonical(fields))==item['canonical_bytes']
        assert digest('jarvis.fixture.p2/2.message.D4E1',fields)==item['sha256']
        count+=1
    # No prior-key fields may hide in the NONE variant.
    assert not set(t['prior_fields_when_PRESENT']) & set(v['commit']);count+=1
    print(json.dumps({'checks':count,'result':'OK','android_or_native_proof':False,'hashes':actual},ensure_ascii=False))
if __name__=='__main__':main()
