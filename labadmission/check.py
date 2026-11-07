"""Freeze every base source and independently reconstruct unified hunks in memory. Dry-run only."""
import hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='2500a123a69b059bf21ac6bec24be5a52b54a9c5'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def sha(b):return hashlib.sha256(b).hexdigest()
def reconstruct(before,lines):
    old=before.decode().splitlines(True);out=[];pos=0;i=0
    while i<len(lines):
        line=lines[i]
        if not line.startswith('@@ '):i+=1;continue
        match=re.fullmatch(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@\n',line);assert match
        start=int(match[1]);nold=int(match[2] or '1');nnew=int(match[4] or '1');target=max(0,start-1)
        assert target>=pos;out+=old[pos:target];pos=target;used=added=0;i+=1
        while i<len(lines) and lines[i][:1] in (' ','+','-'):
            mark=lines[i][0];value=lines[i][1:]
            if mark in (' ','-'):assert pos<len(old) and old[pos]==value;pos+=1;used+=1
            if mark in (' ','+'):out.append(value);added+=1
            i+=1
        assert used==nold and added==nnew
    out+=old[pos:];return ''.join(out).encode()
def main():
    subprocess.run(['git','merge-base','--is-ancestor',BASE,'HEAD'],cwd=ROOT,check=True)
    frozen={}
    for line in git('ls-tree','-r',BASE).decode().splitlines():
        meta,name=line.split('\t',1);mode,kind,blob=meta.split();assert kind=='blob';frozen[name]=(mode,git('cat-file','--filters',BASE+':'+name))
    actual=set(git('ls-files','--cached','--others','--exclude-standard').decode().splitlines())
    assert set(frozen)<=actual
    assert all(n.startswith('labadmission/') or n=='.github/workflows/lab-admission-review.yml' for n in actual-set(frozen))
    modes={l.split('\t',1)[1]:l.split()[0] for l in git('ls-files','--stage').decode().splitlines()}
    for n in actual:
        p=ROOT/n;assert p.is_file() and not p.is_symlink();assert all(not a.is_symlink() for a in p.parents if a!=ROOT)
        assert modes.get(n)==(frozen[n][0] if n in frozen else '100644'),n
        if n in frozen:assert p.read_bytes()==frozen[n][1],n
        else:
            assert p.suffix not in ('.gguf','.bin','.jar','.so','.apk','.safetensors','.pyc') and not p.read_bytes().startswith(b'GGUF')
            p.read_bytes().decode('utf-8')
    manifest=json.loads((ROOT/'labadmission/PREIMAGES.json').read_text());patch=(ROOT/'labadmission/LAB-ADMISSION.patch').read_bytes()
    assert manifest['base_commit']==BASE and manifest['patch_sha256']==sha(patch) and manifest['patch_bytes']==len(patch)
    assert (ROOT/'labadmission/PATCH.sha256').read_text()==sha(patch)+'  LAB-ADMISSION.patch\n'
    chunks=re.split(r'(?m)^diff --git ',patch.decode())[1:];assert len(chunks)==len(manifest['files'])==5
    for entry,chunk in zip(manifest['files'],chunks):
        name=entry['path'];assert chunk.splitlines()[0]==f'a/{name} b/{name}' and not name.endswith(('.cpp','.h'))
        pre=frozen[name][1] if entry['preimage_exists'] else b''
        assert (sha(pre) if pre else None)==entry['preimage_sha256']
        post=(ROOT/'labadmission/postimages'/name).read_bytes();assert sha(post)==entry['postimage_sha256']
        assert reconstruct(pre,chunk.splitlines(True))==post,name
    dry=subprocess.run(['git','apply','--check','labadmission/LAB-ADMISSION.patch'],cwd=ROOT,capture_output=True,text=True)
    assert dry.returncode==0,dry.stderr
    out=ROOT/'build/labadmission/reports';out.mkdir(parents=True,exist_ok=True)
    result={'base':BASE,'commit':git('rev-parse','HEAD').decode().strip(),'tree':git('rev-parse','HEAD^{tree}').decode().strip(),
            'worktree_dirty':bool(git('status','--porcelain')),'frozen_files':len(frozen),'patch_sha256':sha(patch),'hunks_reconstructed_in_memory':True,
            'dry_run':'PASS','patch_applied':False,'productive_sources_changed':False,'jni_cpp_changed':False,'weights_downloaded':False,'real_generation':'NOT_RUN'}
    (out/'preservation.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
