#!/usr/bin/env python3
"""Real pinned-revision GGUF Linux JNI smoke test; never claims Android qualification."""
import hashlib,json,pathlib,re,subprocess,urllib.parse,urllib.request
repo="Qwen/Qwen3-0.6B-GGUF"
root=pathlib.Path(__file__).resolve().parents[1]
out=root/"build/v1-qwen-probe.json"
out.parent.mkdir(parents=True,exist_ok=True)
def catalog(url):
 with urllib.request.urlopen(url,timeout=60) as r:return json.load(r)
info=catalog("https://huggingface.co/api/models/"+repo+"?blobs=true")
revision=info["sha"]
assert re.fullmatch("[a-f0-9]{40}",revision)
matches=[x for x in info["siblings"] if x["rfilename"].lower().endswith("-q4_k_m.gguf")]
if not matches:
 repo="unsloth/Qwen3-0.6B-GGUF"
 info=catalog("https://huggingface.co/api/models/"+repo+"?blobs=true")
 revision=info["sha"]
 assert re.fullmatch("[a-f0-9]{40}",revision)
 matches=[x for x in info["siblings"] if x["rfilename"].lower().endswith("-q4_k_m.gguf")]
assert len(matches)==1,"Expected unique Q4_K_M GGUF in pinned checked repositories"
entry=matches[0];size=entry["lfs"]["size"];expected=entry["lfs"]["sha256"]
assert re.fullmatch("[a-f0-9]{64}",expected) and 0<size<=800*1024*1024
url="https://huggingface.co/"+repo+"/resolve/"+revision+"/"+urllib.parse.quote(entry["rfilename"])
path=root/"build"/"v1-qwen-test.gguf"
digest=hashlib.sha256();total=0
try:
 with urllib.request.urlopen(url,timeout=120) as stream,path.open("wb") as dest:
  while True:
   chunk=stream.read(1024*1024)
   if not chunk:break
   total+=len(chunk)
   assert total<=size,"Oversized download"
   digest.update(chunk);dest.write(chunk)
 assert total==size and digest.hexdigest()==expected,"GGUF SHA256 mismatch"
 run=subprocess.run(["./tools/test-jni-p2.sh",str(path)],cwd=root,capture_output=True,text=True,timeout=180)
 result={"platform":"Linux x86_64 (NOT Android)","model_repo":repo,"revision":revision,
 "filename":entry["rfilename"],"bytes":size,"sha256":expected,
 "jni_exit":run.returncode,"jni_stdout_tail":run.stdout[-3000:],
 "jni_stderr_tail":run.stderr[-3000:],"android_qualified":False,"a02_qualified":False}
 out.write_text(json.dumps(result,indent=2)+"\n")
 print(json.dumps(result,indent=2))
 assert run.returncode==0,"Real JNI probe failed"
finally:
 path.unlink(missing_ok=True)
