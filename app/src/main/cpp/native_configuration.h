#pragma once
#include "llama.h"
#include "cancellation.h"
#include <stdexcept>

inline int maximum_output_tokens(bool small) { return small ? 96 : 256; }

inline llama_context_params context_params(int context, int threads, bool small, Cancellation * stop) {
    if (context < 768 || context > 2048 || context % 256 != 0 || threads < 1 || threads > 4 ||
        (small && (context != 768 || threads != 1))) throw std::runtime_error("Configuration native refusée");
    auto cp = llama_context_default_params();
    cp.n_ctx = context;
    cp.n_batch = small ? 32 : 64;
    cp.n_ubatch = small ? 8 : 32;
    if (small) cp.n_outputs_max = 1; // only final-token logits are requested by our batches
    cp.n_threads = threads; cp.n_threads_batch = threads;
    cp.type_k = GGML_TYPE_F16; cp.type_v = GGML_TYPE_F16;
    cp.offload_kqv = false; cp.op_offload = false;
    cp.abort_callback = Cancellation::abort; cp.abort_callback_data = stop;
    cp.no_perf = true;
    return cp;
}
