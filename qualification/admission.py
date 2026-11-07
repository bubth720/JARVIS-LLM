"""Fail closed. An explicit download consent is not a fabricated CORE grant or lease."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FILES=['app/src/main/java/fr/jarvis/llm/engine/OwnedStreamContract.kt','app/src/main/java/fr/jarvis/llm/engine/LlamaBackend.kt','domain/src/main/kotlin/fr/jarvis/domain/resources/ResourceCoordinator.kt','domain/src/main/kotlin/fr/jarvis/domain/client/GenerationClientContract.kt']
def inspect(root):
    texts={p:(root/p).read_text() for p in FILES}
    checks={
      'authority_contract_requires_core_mandate':'coreMandateSha256' in texts[FILES[0]],
      'backend_requires_authorize_before_stream':'CORE_ADMISSION_UNQUALIFIED' in texts[FILES[1]],
      'broker_identity_private':'private var nextId' in texts[FILES[2]],
      'productive_native_port_closed':'nativeReady: Boolean get() = false' in texts[FILES[3]],
      'tokenizer_structured_unsupported':'exactTokenCount: TokenCountStatus get() = TokenCountStatus.UNSUPPORTED' in texts[FILES[3]],
    }
    return {'status':'BLOCKED','reason':'QUALIFIED_LAB_AUTHORITY_AND_BROKER_ATTESTATION_MISSING' if all(checks.values()) else 'SOURCE_REVIEW_REQUIRED',
      'observations':checks,'source_sha256':{p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in FILES},
      'model_download_authorized':True,'generation_admitted':False,'model_loaded':False,
      'fake_core_mandate_created':False,'fake_lease_created':False,'direct_jni_generation_bypass':False,
      'generation':'NOT_RUN','token_stream':'NOT_RUN','cancellation_prefill_decode':'NOT_RUN','backpressure_real_tokens':'NOT_RUN',
      'loaded_model_cleanup':'NOT_RUN','resident_memory':'NOT_RUN','generation_latency':'NOT_RUN','android':'NOT_RUN',
      'performance':{'comparable_trials':0,'p50_ms':None,'p95_ms':None,'maximum_ms':None,'tokens_per_second':None},
      'gate_b':'NOT_VALIDATED'}
def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    report=inspect(ROOT);report['source_commit']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    (a.output/'admission.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
