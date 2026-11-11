#!/usr/bin/env python3
"""A02 real-GGUF Linux host probe. Official Qwen repository ONLY (no fallback source).

Downloads to build/ (git-ignored), verifies size + SHA-256 against the repository's
published LFS metadata at a pinned revision, then runs the A02 JNI harness which
loads the model exclusively from a sealed memfd. Linux x86_64 evidence, NOT Android.
"""
import hashlib, json, pathlib, re, subprocess, sys, urllib.parse, urllib.request
REPO = "Qwen/Qwen3-0.6B-GGUF"
root = pathlib.Path(__file__).resolve().parents[2]
out = root / "build/a02-real-evidence.json"
out.parent.mkdir(parents=True, exist_ok=True)
with urllib.request.urlopen("https://huggingface.co/api/models/" + REPO + "?blobs=true", timeout=60) as r:
    info = json.load(r)
revision = info["sha"]
assert re.fullmatch("[a-f0-9]{40}", revision)
matches = [x for x in info["siblings"] if x["rfilename"].lower().endswith("-q4_k_m.gguf")]
if len(matches) != 1:
    out.write_text(json.dumps({"status": "BLOCKED", "reason": "official Q4_K_M not uniquely present; no fallback source authorized", "repo": REPO, "revision": revision}, indent=2) + "\n")
    sys.exit("BLOCKED: official Q4_K_M absent; unauthorized fallback refused")
entry = matches[0]; size = entry["lfs"]["size"]; expected = entry["lfs"]["sha256"]
assert re.fullmatch("[a-f0-9]{64}", expected) and 0 < size <= 800 * 1024 * 1024
url = "https://huggingface.co/" + REPO + "/resolve/" + revision + "/" + urllib.parse.quote(entry["rfilename"])
path = root / "build" / "a02-real.gguf"
if not (path.is_file() and path.stat().st_size == size):
    digest = hashlib.sha256(); total = 0
    with urllib.request.urlopen(url, timeout=300) as stream, path.open("wb") as dest:
        while chunk := stream.read(1 << 20):
            total += len(chunk); assert total <= size, "Oversized download"
            digest.update(chunk); dest.write(chunk)
    assert total == size and digest.hexdigest() == expected, "GGUF SHA256 mismatch"
run = subprocess.run([str(root / "tools/test-a02-native.sh"), str(path), expected], cwd=root, capture_output=True, text=True, timeout=1500)
lines = run.stdout.splitlines()
evidence = {
    "platform": "Linux x86_64 host (NOT Android, NOT device)",
    "model": {"repo": REPO, "revision": revision, "filename": entry["rfilename"], "bytes": size, "sha256": expected, "url": url},
    "exit": run.returncode,
    "passed": [l[5:] for l in lines if l.startswith("PASS ")],
    "trace": next((json.loads(l[6:]) for l in lines if l.startswith("TRACE ")), None),
    "memory": next((json.loads(l)["a02Memory"] for l in lines if l.startswith('{"a02Memory"')), None),
    "stderrTail": run.stderr[-2000:],
    "androidQualified": False, "deviceQualified": False,
}
out.write_text(json.dumps(evidence, indent=2, ensure_ascii=False) + "\n")
print(json.dumps(evidence, indent=2, ensure_ascii=False))
assert run.returncode == 0, "A02 real JNI probe failed"
