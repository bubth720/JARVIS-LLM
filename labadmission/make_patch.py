"""Generate review data only. Never apply the resulting patch, including in a temporary checkout."""
import difflib,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE='2500a123a69b059bf21ac6bec24be5a52b54a9c5'
POST=ROOT/'labadmission/postimages'
def sha(b):return hashlib.sha256(b).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def build():
    parts=[];entries=[]
    for p in sorted(POST.rglob('*.kt')):
        name=p.relative_to(POST).as_posix();post=p.read_bytes()
        previous=subprocess.run(['git','show',BASE+':'+name],cwd=ROOT,capture_output=True)
        before=previous.stdout if previous.returncode == 0 else b''
        assert before != post
        if previous.returncode:assert name.endswith(('/LabCloudAdmission.kt','/LabHostBudget.kt'))
        parts.append(f'diff --git a/{name} b/{name}\n')
        if previous.returncode:parts.append('new file mode 100644\n')
        old='a/'+name if previous.returncode == 0 else '/dev/null'
        parts+=difflib.unified_diff(before.decode().splitlines(True),post.decode().splitlines(True),fromfile=old,tofile='b/'+name,n=3)
        entries.append({'path':name,'mode':'100644','preimage_sha256':sha(before) if previous.returncode == 0 else None,'preimage_exists':previous.returncode == 0,'postimage_sha256':sha(post),'postimage_bytes':len(post)})
    patch=''.join(parts).encode();(ROOT/'labadmission/LAB-ADMISSION.patch').write_bytes(patch)
    manifest={'schema':'jarvis.lab-admission01.patch/1','base_commit':BASE,'base_tree':git('rev-parse',BASE+'^{tree}').decode().strip(),
              'patch_sha256':sha(patch),'patch_bytes':len(patch),'applied':False,'native_jni_cpp_changed':False,'files':entries}
    (ROOT/'labadmission/PREIMAGES.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (ROOT/'labadmission/PATCH.sha256').write_text(sha(patch)+'  LAB-ADMISSION.patch\n')
    print(json.dumps(manifest,indent=2))
if __name__=='__main__':build()
