"""All historical content and modes frozen. No GGUF is allowed in Git or evidence sources."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='95bad9cac56dd30cc67eacc7239cf48ffb9dcac3'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def main():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    frozen={}
    for line in git('ls-tree','-r',BASE).decode().splitlines():
        meta,name=line.split('\t',1);mode,kind,blob=meta.split();assert kind=='blob'
        frozen[name]=(mode,git('cat-file','--filters',BASE+':'+name))
    actual=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())
    assert set(frozen)<=actual
    assert all(n.startswith('qualification/') or n=='.github/workflows/gateb-qualification.yml' for n in actual-set(frozen))
    modes={l.split('\t',1)[1]:l.split()[0] for l in git('ls-files','--stage').decode().splitlines()}
    for name in actual:
        p=ROOT/name;assert p.is_file() and not p.is_symlink()
        assert all(not q.is_symlink() for q in p.parents if q!=ROOT)
        assert modes.get(name)==(frozen[name][0] if name in frozen else '100644')
        if name in frozen:assert p.read_bytes()==frozen[name][1],name
        else:
            assert p.suffix.lower() not in ('.gguf','.safetensors','.bin','.onnx'),name
            assert not p.read_bytes().startswith(b'GGUF'),name
    out=ROOT/'build/gateb-evidence';out.mkdir(parents=True,exist_ok=True)
    report={'base':BASE,'runtime_reference':'059cf9c3040d768e1074f62ec5d174c1a27a2590','commit':git('rev-parse','HEAD').decode().strip(),'tree':git('rev-parse','HEAD^{tree}').decode().strip(),'worktree_dirty':bool(git('status','--porcelain')),'frozen_paths':len(frozen),'runtime_changed':False,'jni_changed':False,'other_repos_changed':False,'weight_files_in_source':False}
    (out/'preservation.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
