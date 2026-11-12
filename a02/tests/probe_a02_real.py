#!/usr/bin/env python3
"""A02 real-GGUF Linux host probe for the AUTHORIZED model only (a02/models/qwen3-0.6b-q4km.json).

Downloads the exact pinned revision to build/ (git-ignored), verifies size + SHA-256 against the
authorization manifest AND the repository's LFS metadata at that revision, records the declared
license, then runs the A02 JNI harness which loads the model exclusively from a sealed memfd.
Linux x86_64 evidence, NOT Android, NOT device. Any mismatch -> failure, never a fallback.
"""
import hashlib, json, pathlib, subprocess, urllib.request
root = pathlib.Path(__file__).resolve().parents[2]
auth = json.loads((root / "a02/models/qwen3-0.6b-q4km.json").read_text())
src = auth["source"]
out = root / "build/a02-real-evidence.json"
out.parent.mkdir(parents=True, exist_ok=True)
api = "https://huggingface.co/api/models/%s/revision/%s?blobs=true" % (src["repository"], src["revision"])
with urllib.request.urlopen(api, timeout=60) as r:
    info = json.load(r)
assert info["sha"] == src["revision"], "Revision not served as pinned"
entry = next(x for x in info["siblings"] if x["rfilename"] == src["filename"])
assert entry["lfs"]["sha256"] == src["sha256"] and entry["lfs"]["size"] == src["bytes"], "Repository metadata differs from authorization"
license_ = (info.get("cardData") or {}).get("license")
path = root / "build" / "a02-real.gguf"
if not (path.is_file() and path.stat().st_size == src["bytes"]):
    digest = hashlib.sha256(); total = 0
    with urllib.request.urlopen(src["url"], timeout=300) as stream, path.open("wb") as dest:
        while chunk := stream.read(1 << 20):
            total += len(chunk); assert total <= src["bytes"], "Oversized download"
            digest.update(chunk); dest.write(chunk)
    assert total == src["bytes"] and digest.hexdigest() == src["sha256"], "GGUF SHA256 mismatch"
run = subprocess.run([str(root / "tools/test-a02-native.sh"), str(path), src["sha256"]], cwd=root, capture_output=True, text=True, timeout=1500)
lines = run.stdout.splitlines()
evidence = {
    "platform": "Linux x86_64 host (NOT Android, NOT device)",
    "authorization": auth["id"], "model": src, "licenseDeclared": license_, "licenseExpected": auth["licenseExpected"],
    "exit": run.returncode,
    "passed": [l[5:] for l in lines if l.startswith("PASS ")],
    "trace": next((json.loads(l[6:]) for l in lines if l.startswith("TRACE ")), None),
    "memory": next((json.loads(l)["a02Memory"] for l in lines if l.startswith('{"a02Memory"')), None),
    "v1bDialogues": [json.loads(l[9:]) for l in lines if l.startswith("DIALOGUE ")],
    "v1bPromptStudy": [json.loads(l[8:]) for l in lines if l.startswith("EXPLORE ")],
    "v1bBench": [json.loads(l[6:]) for l in lines if l.startswith("BENCH ")],
    "v1bCancel": next((json.loads(l[7:]) for l in lines if l.startswith("CANCEL ")), None),
    "stderrTail": run.stderr[-2000:],
    "androidQualified": False, "deviceQualified": False,
}
out.write_text(json.dumps(evidence, indent=2, ensure_ascii=False) + "\n")
print(json.dumps(evidence, indent=2, ensure_ascii=False))
assert license_ == auth["licenseExpected"], "Declared license differs from authorization: %r" % license_
assert run.returncode == 0, "A02 real JNI probe failed"
