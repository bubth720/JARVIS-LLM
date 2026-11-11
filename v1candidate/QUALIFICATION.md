# V1-C01 — Android offline chat candidate (NOT qualified)

## Scope
Reuse the existing Android MainActivity, ChatViewModel, ModelStore, SafetyPolicy, and native llama.cpp backend, without altering any historically protected app/domain/fixtures source. This is an independently packaged **candidate** of the existing application, not an integrated JARVIS CORE V1.

## Expected UX
- Install debug APK manually only after user approval.
- Import a user-supplied local Qwen3 GGUF (0.6B Q4_K_M preferred for first POCO trial) using Android's document picker.
- Require a trusted SHA-256 of that exact GGUF; never invent the digest.
- Choose ECO/AUTO; send a short French message; inspect whether a **real** local answer arrives.
- Stop/unload on heat, memory pressure, background or user cancellation. No cloud inference or bundled GGUF.
- Use 'Mesures' to capture 90 seconds and export JSON, then report PSS/RSS peak, battery/thermal, latency, crashes.

## Acceptance gates
1. CI preservation gates pass without exceptions or main merge.
2. APK compiles; APK ZIP integrity, DEX, manifest, and arm64 native library pass structural verification.
3. On physical POCO: model SHA-256 validated, import succeeds, local answer produced in airplane mode, no fabricated/synthetic reply, stop works.
4. Peak PSS/RSS, thermal status, battery, generation time recorded and reviewed; refuse unsafe configurations.
5. Native sealed-FD A02 and A01 trust admission separately qualified before declaring security-sensitive integration complete.
6. CORE->LLM->INTERFACE real end-to-end path is still required for **JARVIS V1**.

## No false claims
CI build and structural checks alone do not prove actual local inference, acceptable latency, memory safety or CORE integration. This candidate does not provision keys, modify main or install anything.
