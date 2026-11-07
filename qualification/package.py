"""Publish a text-only allowlist, never the private model directory or build binaries."""
import argparse,hashlib,json
from pathlib import Path
def stage(source,target):
    source=source.resolve();target=target.resolve()
    if source==target or source.is_relative_to(target) or target.is_relative_to(source):raise ValueError('SEPARATE_PUBLIC_STAGE_REQUIRED')
    target.mkdir(parents=True,exist_ok=True)
    if any(target.iterdir()):raise ValueError('PUBLIC_STAGE_MUST_BE_FRESH')
    rejected=[];copied=[]
    for p in sorted(source.rglob('*')):
        if p.is_dir() and not p.is_symlink():continue
        name=p.relative_to(source)
        if p.is_symlink() or any(q.is_symlink() for q in p.parents if q!=source) or p.suffix not in ('.json','.txt','.md','.log') or p.stat().st_size>2*1024**2:
            rejected.append(str(name));continue
        data=p.read_bytes()
        if data.startswith(b'GGUF'):
            rejected.append(str(name));continue
        try:data.decode('utf-8')
        except UnicodeDecodeError:rejected.append(str(name));continue
        dest=target/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);copied.append(name.as_posix())
    report={'public_weights':False,'copied_text_files':copied,'rejected_paths':rejected,'status':'PASS' if not rejected else 'FAIL'}
    (target/'publication.json').write_text(json.dumps(report,indent=2)+'\n')
    files=sorted(p for p in target.rglob('*') if p.is_file())
    (target/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(target).as_posix()+'\n' for p in files))
    if rejected:raise ValueError('NONREPORT_DATA_REFUSED_FROM_PUBLIC_ARTIFACT')
    return report
def main():
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--target',type=Path,required=True);a=p.parse_args();print(json.dumps(stage(a.source,a.target)))
if __name__=='__main__':main()
