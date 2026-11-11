#!/usr/bin/env python3
"""Fail-closed A02 qualification ledger: evidence is derived, never invented."""
import hashlib,json,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
files=[
 "tools/test-a02-sealed-snapshot.py",
 "perf03/candidate/native/a02_sealed_fd.h",
 "perf03/tests/a02_sealed_fd_test.cpp",
 "perf03/candidate/native/bridge.cpp",
 "labadmission/postimages/app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt",
 "app/src/main/cpp/bridge.cpp",
 "app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt",
]
digests={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in files}
# Candidate and production must not be confused. An unused candidate JNI method
# is not proof that production's loader receives a verified sealed descriptor.
production_native=(root/"app/src/main/cpp/bridge.cpp").read_text()
production_kotlin=(root/"app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt").read_text()
candidate_native=(root/"perf03/candidate/native/bridge.cpp").read_text()
candidate_kotlin=(root/"labadmission/postimages/app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt").read_text()
candidate_present="loadSealedFd" in candidate_native and "loadSealedFd" in candidate_kotlin
production_present="loadSealedFd" in production_native and "loadSealedFd" in production_kotlin
report={
 "schema":"jarvis.a02.evidence/1",
 "commit":subprocess.check_output(["git","rev-parse","HEAD"],cwd=root,text=True).strip(),
 "file_sha256":digests,
 "candidate_sealed_fd_jni_present":candidate_present,
 "production_sealed_fd_jni_present":production_present,
 "linux_adversarial_tests":"run in separate CI job; see Actions result, not asserted here",
 "android_sealed_fd_loader_test":"NOT_RUN",
 "android_real_gguf_generation":"NOT_RUN",
 "android_peak_pss_rss":"NOT_MEASURED",
 "native_fd_to_model_identity_proven":False,
 "a02_qualified":False,
 "decision":"NO_GO until exact sealed FD is hash-verified and consumed by real native Android loader and physical device measurements pass"
}
out=root/"build/a02-evidence.json";out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,indent=2)+"\n")
print(json.dumps(report,indent=2))
assert candidate_present,"Candidate sealed-FD JNI hook missing"
