#pragma once
// A02 successor OUTPUT policy (mission V1-B, requested by the owner on 2026-11-12).
//
// The protected engine (app/src/main/cpp/native_configuration.h, UNCHANGED) caps Qwen3 0.6B at
// 96 generated tokens for context 768 (64 for context 512). V1-B replaces only that NUMBER, for
// context 768, by a ceiling chosen by the host and bounded here. Nothing else moves:
//   - context size (512|768), exactly one thread, n_batch/n_ubatch, F16 KV cache: unchanged,
//     so resident memory does not depend on the ceiling (the KV cache is sized by n_ctx);
//   - the protected generate() still refuses maxTokens > ceiling and still requires
//     prompt_tokens + maxTokens + 8 <= context (no prompt can push generation past n_ctx);
//   - the protected 90 s cancellation deadline, Stop and end-of-generation tokens: unchanged;
//   - every protected load resets the ceiling to the protected value (fail-safe default):
//     the host must opt in again after each load.
namespace jarvis_a02 {
constexpr int kV1bDefaultOutputTokens = 128;       // V1-B default
constexpr int kV1bExperimentalOutputTokens = 256;  // V1-B experimental option
constexpr int kOutputHardCeiling = 256;            // absolute maximum, whatever the host asks
constexpr int kOutputPolicyContext = 768;          // the only context where the ceiling may rise
constexpr int kMinimumPromptReserve = 256;         // ceiling + reserve + 8 must fit the context

/** True when [requested] is an admissible output ceiling for a loaded context. */
inline bool output_ceiling_admitted(int context, int protectedMaximum, int requested) {
    if (requested < 1 || requested > kOutputHardCeiling) return false;
    if (requested <= protectedMaximum) return true; // lowering the protected ceiling is always allowed
    return context == kOutputPolicyContext && requested + kMinimumPromptReserve + 8 <= context;
}
}
