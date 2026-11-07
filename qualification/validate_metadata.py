"""Invoke existing native validators only after an independent exact byte verification."""
import argparse,hashlib,json,resource,subprocess
from pathlib import Path
from download import NAME,SHA,SIZE
def bounded():
    resource.setrlimit(resource.RLIMIT_AS,(2*1024**3,2*1024**3))
    resource.setrlimit(resource.RLIMIT_CPU,(30,30))
def main():
    p=argparse.ArgumentParser();p.add_argument('--private-dir',type=Path,required=True);p.add_argument('--probe',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    report={'status':'FAIL','model_loaded':False,'generation':'NOT_RUN','metadata_address_space_limit_bytes':2*1024**3}
    try:
        path=a.private_dir/NAME;assert path.stat().st_size==SIZE
        digest=hashlib.sha256()
        with path.open('rb') as f:
            for b in iter(lambda:f.read(1024*1024),b''):digest.update(b)
        assert digest.hexdigest()==SHA
        run=subprocess.run([str(a.probe.resolve()),str(path.resolve())],capture_output=True,text=True,timeout=45,preexec_fn=bounded)
        (a.output/'metadata-stderr.log').write_text(run.stderr.replace(str(path.resolve()),'<authorized-model>'))
        if run.returncode:raise ValueError('EXISTING_NATIVE_VALIDATOR_REJECTED_'+str(run.returncode))
        data=json.loads(run.stdout);assert data['status']=='PASS_METADATA_ONLY' and not data['model_loaded']
        assert 400_000_000<=data['parameter_elements']<=850_000_000 and data['context']==512 and data['threads']==1 and data['native_max_output']>=24
        report.update(status='PASS',actual_sha256=SHA,metadata=data,probe_sha256=hashlib.sha256(a.probe.read_bytes()).hexdigest(),resident_budget_qualification='NOT_RUN_REQUIRES_ADMISSION')
    except Exception as e:report.update(error_type=type(e).__name__,reason=str(e),loading_prohibited=True)
    (a.output/'metadata.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
    return 0 if report['status']=='PASS' else 1
if __name__=='__main__':raise SystemExit(main())
