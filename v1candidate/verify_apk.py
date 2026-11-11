#!/usr/bin/env python3
"""Structural smoke gate, NOT proof of inference or device compatibility."""
import sys, zipfile, pathlib, hashlib
p=pathlib.Path(sys.argv[1])
assert p.is_file() and p.stat().st_size>100_000, "APK missing or unexpectedly small"
with zipfile.ZipFile(p) as apk:
    names=set(apk.namelist())
    assert apk.testzip() is None, "APK CRC error"
    assert "AndroidManifest.xml" in names, "No Android manifest"
    assert any(x.startswith("classes") and x.endswith(".dex") for x in names), "No DEX"
    assert "lib/arm64-v8a/libjarvis_llm.so" in names, "Missing native ARM64 inference engine"
    assert not any(x.startswith("assets/") and x.endswith(".gguf") for x in names), "Unexpected bundled model"
    assert not any(x.startswith("lib/") and "x86" in x for x in names), "Unexpected x86 binary"
print("PASS structural APK:",p.stat().st_size,"bytes; SHA256",hashlib.sha256(p.read_bytes()).hexdigest())
print("NOT QUALIFIED: no actual GGUF loading, memory peak, offline generation or Android hardware test")
