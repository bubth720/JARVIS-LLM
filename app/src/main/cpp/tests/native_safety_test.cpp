#include "../cancellation.h"
#include "../model_check.h"
#include "../native_configuration.h"
#include "ggml.h"
#include "ggml-cpu.h"
#include <cstdio>
#include <stdexcept>
#include <thread>
#include <vector>

static void require(bool value, const char * message) { if (!value) throw std::runtime_error(message); }
struct AbortAfterFirstNode { Cancellation flag; int calls = 0; };
static bool abortGraph(void * data) {
    auto & state = *static_cast<AbortAfterFirstNode *>(data);
    if (++state.calls >= 2) {
        // Deliver a concurrent stop after the graph has started, before its next CPU node.
        std::thread stopper([&state] { state.flag.request(); });
        stopper.join();
    }
    return Cancellation::abort(&state.flag);
}
int main() {
    try {
        Cancellation flag;
        flag.begin(60'000); require(!flag.stopped(), "fresh operation cancelled");
        std::thread other([&] { flag.request(); }); other.join();
        require(Cancellation::abort(&flag), "concurrent stop not observed");
        require(!Cancellation::progress(0.5f, &flag), "model load did not stop");
        flag.begin(0); require(flag.stopped(), "deadline ignored");
        flag.begin(60'000); require(!flag.stopped(), "reset failed");
        const auto eco = context_params(768, 1, true, &flag);
        require(eco.n_ctx == 768 && GGML_PAD(eco.n_ctx, 256) == 768, "768 context padding incompatibility");
        require(eco.n_batch == 32 && eco.n_ubatch == 8 && eco.n_outputs_max == 1, "eco workspace limits");
        require(maximum_output_tokens(true) == 96 && maximum_output_tokens(false) == 256, "native output limit");
        require(eco.type_k == GGML_TYPE_F16 && eco.type_v == GGML_TYPE_F16 && !eco.offload_kqv && !eco.op_offload, "cache/backend changed");
        require(eco.abort_callback == Cancellation::abort && eco.abort_callback_data == &flag, "abort callback missing");
        const auto tablet = context_params(512, 1, true, &flag);
        require(tablet.n_ctx == 512 && GGML_PAD(tablet.n_ctx, 256) == 512, "tablet context padding");
        require(tablet.n_batch == 16 && tablet.n_ubatch == 4 && tablet.n_outputs_max == 1, "tablet workspace limits");
        require(tablet.n_threads == 1 && tablet.n_threads_batch == 1 && maximum_output_tokens(true, 512) == 64, "tablet work limit");
        require(tablet.abort_callback == Cancellation::abort && tablet.abort_callback_data == &flag, "tablet cancellation lost");
        bool mainRejected = false;
        try { context_params(512, 1, false, &flag); } catch (const std::runtime_error &) { mainRejected = true; }
        require(mainRejected, "main model accepted tablet context");
        bool invalidConfig = false;
        try { context_params(767, 1, true, &flag); } catch (const std::runtime_error &) { invalidConfig = true; }
        require(invalidConfig, "unaligned context accepted");

        auto * meta = gguf_init_empty();
        gguf_set_val_str(meta, "general.architecture", "other");
        gguf_set_val_u32(meta, "general.file_type", 15);
        bool rejected = false;
        try { validate_metadata(meta, true, 15); } catch (const std::runtime_error &) { rejected = true; }
        require(rejected, "unknown architecture accepted");
        gguf_set_val_str(meta, "general.architecture", "qwen3");
        rejected = false;
        try { validate_metadata(meta, true, 15); } catch (const std::runtime_error &) { rejected = true; }
        require(rejected, "missing weights accepted");
        auto * shapes = ggml_init({16 * 1024, nullptr, true});
        auto * weight = ggml_new_tensor_2d(shapes, GGML_TYPE_Q4_K, 1024 * 1024, 512);
        ggml_set_name(weight, "weight"); gguf_add_tensor(meta, weight);
        validate_metadata(meta, true, 15);
        rejected = false;
        try { validate_metadata(meta, false, 15); } catch (const std::runtime_error &) { rejected = true; }
        require(rejected, "wrong model slot accepted");
        gguf_free(meta);
        meta = gguf_init_empty();
        gguf_set_val_str(meta, "general.architecture", "qwen3"); gguf_set_val_u32(meta, "general.file_type", 15);
        auto * huge = ggml_new_tensor_2d(shapes, GGML_TYPE_Q4_K, 3 * 1024 * 1024, 1024);
        ggml_set_name(huge, "huge"); gguf_add_tensor(meta, huge);
        rejected = false;
        try { validate_metadata(meta, false, 15); } catch (const std::runtime_error &) { rejected = true; }
        require(rejected, "oversized model accepted before allocation");
        gguf_free(meta); ggml_free(shapes);

        ggml_init_params params{16 * 1024 * 1024, nullptr, false};
        auto * ctx = ggml_init(params);
        require(ctx != nullptr, "context allocation");
        auto * a = ggml_new_tensor_2d(ctx, GGML_TYPE_F32, 128, 128);
        auto * b = ggml_new_tensor_2d(ctx, GGML_TYPE_F32, 128, 128);
        ggml_set_f32(a, 0.1f); ggml_set_f32(b, 0.2f);
        auto * c = ggml_mul_mat(ctx, a, b);
        auto * d = ggml_mul_mat(ctx, a, c);
        auto * graph = ggml_new_graph(ctx);
        ggml_build_forward_expand(graph, d);
        auto plan = ggml_graph_plan(graph, 2, nullptr);
        std::vector<uint8_t> work(plan.work_size); plan.work_data = work.data();
        AbortAfterFirstNode state; state.flag.begin(60'000);
        plan.abort_callback = abortGraph; plan.abort_callback_data = &state;
        require(ggml_graph_compute(graph, &plan) == GGML_STATUS_ABORTED, "CPU graph did not abort");
        require(state.calls >= 2, "CPU abort callback not exercised");
        plan.abort_callback = nullptr; plan.abort_callback_data = nullptr;
        require(ggml_graph_compute(graph, &plan) == GGML_STATUS_SUCCESS, "CPU graph did not recover after abort");
        ggml_free(ctx);
        std::puts("PASS: 512/768 native configuration, cross-thread cancellation, load callback, deadline, CPU graph abort/recovery");
        return 0;
    } catch (const std::exception & ex) { std::fprintf(stderr, "FAIL: %s\n", ex.what()); return 1; }
}
