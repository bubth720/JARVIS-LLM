#!/usr/bin/env python3
"""M55-B study run on the Linux host for ONE authorized model manifest and one engine configuration.
Download is verified against the manifest AND the repository LFS metadata at the pinned revision;
any mismatch fails (no fallback). Evidence: build/m55b-<label>.json. NOT Android, NOT device."""
import argparse, hashlib, json, os, pathlib, subprocess, urllib.request
root = pathlib.Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser()
p.add_argument("--manifest", required=True); p.add_argument("--label", required=True)
p.add_argument("--small", choices=["true", "false"], required=True)
p.add_argument("--ctx", type=int, default=768); p.add_argument("--threads", type=int, default=1)
p.add_argument("--ceiling", type=int, default=128)
a = p.parse_args()
auth = json.loads((root / a.manifest).read_text()); src = auth["source"]
api = "https://huggingface.co/api/models/%s/revision/%s?blobs=true" % (src["repository"], src["revision"])
with urllib.request.urlopen(api, timeout=60) as r: info = json.load(r)
assert info["sha"] == src["revision"], "Revision not served as pinned"
entry = next(x for x in info["siblings"] if x["rfilename"] == src["filename"])
assert entry["lfs"]["sha256"] == src["sha256"] and entry["lfs"]["size"] == src["bytes"], "Repository metadata differs from authorization"
license_ = (info.get("cardData") or {}).get("license")
assert license_ == auth["licenseExpected"], "Declared license differs: %r" % license_
path = root / "build" / (src["sha256"][:16] + ".gguf"); path.parent.mkdir(parents=True, exist_ok=True)
if not (path.is_file() and path.stat().st_size == src["bytes"]):
    digest = hashlib.sha256(); total = 0
    with urllib.request.urlopen(src["url"], timeout=600) as stream, path.open("wb") as dest:
        while chunk := stream.read(1 << 20):
            total += len(chunk); assert total <= src["bytes"], "Oversized download"
            digest.update(chunk); dest.write(chunk)
    assert total == src["bytes"] and digest.hexdigest() == src["sha256"], "GGUF SHA256 mismatch"
bench = json.loads((root / "a02/tests/bench/fr-bench-30.json").read_text())
tsv = root / "build/bench30.tsv"
tsv.write_text("".join(i["id"] + "\t" + "\x1f".join(i["turns"]) + "\n" for i in bench["items"]), encoding="utf-8")
env = dict(os.environ, A02_STUDY="1", A02_SMALL=a.small, A02_CTX=str(a.ctx), A02_THREADS=str(a.threads),
           A02_CEILING=str(a.ceiling), A02_BENCH=str(tsv))
run = subprocess.run([str(root / "tools/test-a02-native.sh"), str(path), src["sha256"]], cwd=root, env=env, capture_output=True, text=True, timeout=5400)
lines = run.stdout.splitlines()
evidence = {"platform": "Linux x86_64 host (NOT Android, NOT device)", "label": a.label, "authorization": auth["id"], "model": src,
            "licenseDeclared": license_, "config": {"small": a.small == "true", "context": a.ctx, "threads": a.threads, "ceiling": a.ceiling},
            "exit": run.returncode, "passed": [l[5:] for l in lines if l.startswith("PASS ")],
            "trace": next((json.loads(l[6:]) for l in lines if l.startswith("TRACE ")), None),
            "bench": [json.loads(l[8:]) for l in lines if l.startswith("BENCH30 ")],
            "memory": next((json.loads(l[10:]) for l in lines if l.startswith("STUDY_MEM ")), None),
            "stderrTail": run.stderr[-1500:], "androidQualified": False, "deviceQualified": False}
out = root / ("build/m55b-%s.json" % a.label); out.write_text(json.dumps(evidence, indent=1, ensure_ascii=False) + "\n")
print(json.dumps({k: evidence[k] for k in ("label", "config", "exit", "memory")}, ensure_ascii=False))
assert run.returncode == 0, "study run failed: " + run.stderr[-800:]
