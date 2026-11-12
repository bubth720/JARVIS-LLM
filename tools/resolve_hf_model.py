#!/usr/bin/env python3
"""Read-only Hugging Face metadata resolution (no weight download): pins the immutable revision,
LFS SHA-256, exact size and declared license of one GGUF file, for a model-authorization manifest."""
import json, sys, urllib.request
repo, filename = sys.argv[1], sys.argv[2]
with urllib.request.urlopen(f"https://huggingface.co/api/models/{repo}?blobs=true", timeout=60) as r:
    info = json.load(r)
entry = next(x for x in info["siblings"] if x["rfilename"] == filename)
print(json.dumps({"repository": repo, "revision": info["sha"], "filename": filename,
    "url": f"https://huggingface.co/{repo}/resolve/{info['sha']}/{filename}",
    "bytes": entry["lfs"]["size"], "sha256": entry["lfs"]["sha256"],
    "licenseDeclared": (info.get("cardData") or {}).get("license"),
    "lastModified": info.get("lastModified")}, indent=2))
