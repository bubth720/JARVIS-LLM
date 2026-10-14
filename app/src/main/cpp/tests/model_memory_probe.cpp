// Short cloud functional comparison, never a mobile admission calibration.
#include "../native_configuration.h"
#include "llama-ext.h"
#include <sys/resource.h>
#include <cstdio>
#include <algorithm>
#include <memory>
#include <string>
#include <vector>

int main(int argc, char ** argv) {
    if (argc != 3) return 2;
    try {
        const int context = std::stoi(argv[2]);
        Cancellation stop; stop.begin(60'000);
        auto cp = context_params(context, 1, true, &stop);
        llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        llama_backend_init();
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0; mp.load_mode = LLAMA_LOAD_MODE_MMAP; mp.use_extra_bufts = false;
        mp.progress_callback = Cancellation::progress; mp.progress_callback_user_data = &stop;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(llama_model_load_from_file(argv[1], mp), llama_model_free);
        if (!model) throw std::runtime_error("model load failed");
        char arch[32] = {}; llama_model_meta_val_str(model.get(), "general.architecture", arch, sizeof(arch));
        if (std::string(arch) != "qwen3" && std::string(arch) != "qwen2") throw std::runtime_error("unsupported probe architecture");
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
        if (!ctx || llama_n_ctx(ctx.get()) != unsigned(context)) throw std::runtime_error("context mismatch");
        llama_chat_message msgs[] = {{"system", "Réponds en français, brièvement. /no_think"}, {"user", "Que signifie bonjour ?"}};
        const char * tmpl = llama_model_chat_template(model.get(), nullptr);
        if (!tmpl) throw std::runtime_error("template missing");
        std::vector<char> text(8192);
        int length = llama_chat_apply_template(tmpl, msgs, 2, true, text.data(), text.size());
        if (length <= 0 || length >= int(text.size())) throw std::runtime_error("template invalid");
        std::string prompt(text.data(), length);
        if (std::string(arch) == "qwen3") prompt += "<think>\n\n</think>\n\n";
        const auto * vocab = llama_model_get_vocab(model.get());
        std::vector<llama_token> tokens(context);
        int n = llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), tokens.size(), true, true);
        if (n <= 0 || n + 24 + 8 > context) throw std::runtime_error("token budget");
        for (int offset = 0; offset < n; offset += cp.n_batch) {
            if (stop.stopped() || llama_decode(ctx.get(), llama_batch_get_one(tokens.data() + offset, std::min(int(cp.n_batch), n-offset))) != 0) throw std::runtime_error("prefill failed");
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(), llama_sampler_free);
        std::string output;
        for (int i = 0; i < 24; ++i) {
            if (stop.stopped()) throw std::runtime_error("probe deadline");
            auto token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            char piece[1024]; int size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (size < 0) throw std::runtime_error("token too large");
            output.append(piece, size);
            if (i < 23 && llama_decode(ctx.get(), llama_batch_get_one(&token, 1)) != 0) throw std::runtime_error("decode failed");
        }
        if (output.empty()) throw std::runtime_error("empty response");
        size_t modelBytes=0, contextBytes=0, computeBytes=0;
        for (const auto & e : llama_get_memory_breakdown(ctx.get())) {
            modelBytes+=e.second.model; contextBytes+=e.second.context; computeBytes+=e.second.compute;
        }
        rusage usage{}; if (getrusage(RUSAGE_SELF, &usage)) throw std::runtime_error("getrusage failed");
        std::printf("{\"context\":%d,\"batch\":%u,\"micro_batch\":%u,\"model_buffer_bytes\":%zu,\"context_buffer_bytes\":%zu,\"compute_buffer_bytes\":%zu,\"linux_peak_rss_bytes\":%lld,\"response_utf8_hex\":\"", context, cp.n_batch, cp.n_ubatch, modelBytes, contextBytes, computeBytes, static_cast<long long>(usage.ru_maxrss)*1024);
        for (unsigned char c : output) std::printf("%02x", c);
        std::puts("\"}");
        return 0;
    } catch (const std::exception & ex) { std::fprintf(stderr, "%s\n", ex.what()); return 1; }
}
