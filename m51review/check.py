"""Freeze PR15 completely. Audit is additive; neither productive sources nor proposed postimages can change."""
import hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='69400fe26d61688262b94cc8a2d5efe20d1da4be'
PATCH='8df826451e501a107305f8fcaee5ca3d5334e22de23246d32cc7fd5312da21e8'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def main():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    frozen={}
    for line in git('ls-tree','-r',BASE).decode().splitlines():
        meta,name=line.split('\t',1);mode,kind,blob=meta.split();assert kind=='blob';frozen[name]=(mode,git('cat-file','--filters',BASE+':'+name))
    actual=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())
    assert set(frozen)<=actual
    assert all(n.startswith('m51review/') or n=='.github/workflows/m51-authority-audit.yml' for n in actual-set(frozen))
    modes={l.split('\t',1)[1]:l.split()[0] for l in git('ls-files','--stage').decode().splitlines()}
    for name in actual:
        p=ROOT/name;assert p.is_file() and not p.is_symlink();assert all(not a.is_symlink() for a in p.parents if a!=ROOT)
        assert modes.get(name)==(frozen[name][0] if name in frozen else '100644'),name
        if name in frozen:assert p.read_bytes()==frozen[name][1],name
        else:
            assert p.suffix not in ('.gguf','.jar','.bin','.so','.apk','.pyc','.pem','.key','.mp4','.wav','.png','.jpg'),name
            data=p.read_bytes();assert not data.startswith(b'GGUF');data.decode('utf-8')
            assert (b'-----BEGIN '+b'PRIVATE KEY-----') not in data and (b'-----BEGIN '+b'OPENSSH PRIVATE KEY-----') not in data
    assert hashlib.sha256((ROOT/'labadmission/LAB-ADMISSION.patch').read_bytes()).hexdigest()==PATCH
    assert not (ROOT/'app/src/main/java/fr/jarvis/llm/engine/LabCloudAdmission.kt').exists()
    assert not (ROOT/'app/src/main/java/fr/jarvis/llm/engine/LabHostBudget.kt').exists()
    dry=subprocess.run(['git','apply','--check','labadmission/LAB-ADMISSION.patch'],cwd=ROOT,capture_output=True,text=True);assert dry.returncode==0,dry.stderr
    result={'schema':'jarvis.m51.authority-preservation/1','base':BASE,'base_tree':git('rev-parse',BASE+'^{tree}').decode().strip(),
            'commit':git('rev-parse','HEAD').decode().strip(),'tree':git('rev-parse','HEAD^{tree}').decode().strip(),'worktree_dirty':bool(git('status','--porcelain')),
            'frozen_files':len(frozen),'original_patch_sha256':PATCH,'dry_run':'PASS','original_patch_changed':False,'productive_sources_changed':False,'jni_changed':False,'patch_applied':False,'operator_root_provisioned':False,'model_acquired':False}
    out=ROOT/'build/m51review';out.mkdir(parents=True,exist_ok=True);(out/'preservation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
