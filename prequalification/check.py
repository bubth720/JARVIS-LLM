"""Freeze every byte/mode of PERF-03B. Only additive prequalification is allowed."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='059cf9c3040d768e1074f62ec5d174c1a27a2590'
WORKFLOW='.github/workflows/gateb-prequalification.yml'
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT)
def main():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    frozen={}
    for line in git('ls-tree','-r',BASE).decode().splitlines():
        meta,name=line.split('\t',1);mode,kind,blob=meta.split();assert kind=='blob'
        frozen[name]=(mode,git('cat-file','--filters',BASE+':'+name))
    actual=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())
    assert set(frozen)<=actual
    assert all(n.startswith('prequalification/') or n==WORKFLOW for n in actual-set(frozen))
    modes={line.split('\t',1)[1]:line.split()[0] for line in git('ls-files','--stage').decode().splitlines()}
    for n in actual:
        p=ROOT/n;assert not p.is_symlink() and p.is_file()
        assert all(not parent.is_symlink() for parent in p.parents if parent!=ROOT)
        assert modes.get(n)==(frozen[n][0] if n in frozen else '100644'),n
        if n in frozen: assert p.read_bytes()==frozen[n][1],n
    model=json.loads((ROOT/'prequalification/model.json').read_text());assert not model['weights_downloaded']
    out=ROOT/'build/prequalification';out.mkdir(parents=True,exist_ok=True)
    report={'base':BASE,'source_commit':git('rev-parse','HEAD').decode().strip(),'tree':git('rev-parse','HEAD^{tree}').decode().strip(),'frozen_files':len(frozen),'jni_changed':False,'model_downloaded':False}
    (out/'preservation.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
