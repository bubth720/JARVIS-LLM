#include <jni.h>
#include "llama.h"
#include "llama-ext.h"
#include "cancellation.h"
#include "model_check.h"
#include "native_configuration.h"
#include "stream_support.h"
#include <chrono>
#include <ctime>
#include <thread>
#include <unordered_map>
#include <optional>
#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

struct Engine {
    Cancellation cancel;
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    int context = 0;
    int batch = 64;
    int max_output = 256;
    void unload() {
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
        ctx = nullptr; model = nullptr; context = 0;
    }
    ~Engine() { unload(); }
};
// Opaque monotonically increasing handles: a freed address can never resurrect an old handle.
// Every call still obeys the existing backend worker/handleLock ownership contract.
struct RegisteredEngine { std::unique_ptr<Engine> value; bool busy = false; bool streaming = false; };
static std::mutex stream_registry_mutex;
static std::unordered_map<jlong, RegisteredEngine> stream_registry;
static jlong next_engine_id = 1;
static void require_not_streaming(jlong h) {
    std::lock_guard<std::mutex> lock(stream_registry_mutex);
    auto found = stream_registry.find(h);
    if (found == stream_registry.end()) throw std::runtime_error("Handle inconnu");
    if (found->second.streaming) throw std::runtime_error("BUSY_STREAM");
}
class EngineCallPin {
    jlong handle_;
public:
    explicit EngineCallPin(jlong h, bool streaming = false) : handle_(h) {
        std::lock_guard<std::mutex> lock(stream_registry_mutex);
        auto found = stream_registry.find(handle_);
        if (found == stream_registry.end() || found->second.busy) throw std::runtime_error("STALE_OR_BUSY_ENGINE");
        found->second.busy = true; found->second.streaming = streaming;
    }
    ~EngineCallPin() {
        std::lock_guard<std::mutex> lock(stream_registry_mutex);
        auto found = stream_registry.find(handle_);
        if (found != stream_registry.end()) { found->second.busy = false; found->second.streaming = false; }
    }
    EngineCallPin(const EngineCallPin &) = delete;
    EngineCallPin & operator=(const EngineCallPin &) = delete;
};
static Engine & engine(jlong handle) {
    std::lock_guard<std::mutex> lock(stream_registry_mutex);
    auto found = stream_registry.find(handle);
    if (found == stream_registry.end()) throw std::runtime_error("Moteur fermé ou handle périmé");
    return *found->second.value;
}
static void check(Engine & e) { if(e.cancel.stopped()) throw std::runtime_error("Calcul natif arrêté"); }
static void error(JNIEnv * env, const std::exception & ex) {
    if (env->ExceptionCheck()) return;
    auto cls = env->FindClass("java/lang/IllegalStateException");
    if (!cls || env->ExceptionCheck()) return;
    env->ThrowNew(cls, ex.what());
    env->DeleteLocalRef(cls);
}
// Strict UTF-8 and controls: never let a C-string terminator hide trailing instructions.
static void validate_p2(const std::string & s, bool system = false) {
    for (size_t i = 0; i < s.size();) {
        const auto first = static_cast<unsigned char>(s[i++]);
        if (first < 0x80) {
            if ((first < 32 && first != 9 && first != 10 && first != 13) || first == 127)
                throw std::runtime_error("Contrôle P2 interdit");
            continue;
        }
        int count; unsigned value, minimum;
        if (first >= 0xc2 && first <= 0xdf) { count = 1; value = first & 31; minimum = 0x80; }
        else if (first >= 0xe0 && first <= 0xef) { count = 2; value = first & 15; minimum = 0x800; }
        else if (first >= 0xf0 && first <= 0xf4) { count = 3; value = first & 7; minimum = 0x10000; }
        else throw std::runtime_error("UTF-8 P2 invalide");
        while (count--) {
            if (i >= s.size()) throw std::runtime_error("UTF-8 P2 tronqué");
            const auto next = static_cast<unsigned char>(s[i++]);
            if ((next & 0xc0) != 0x80) throw std::runtime_error("UTF-8 P2 invalide");
            value = (value << 6) | (next & 63);
        }
        if (value < minimum || value > 0x10ffff || (value >= 0xd800 && value <= 0xdfff))
            throw std::runtime_error("UTF-8 P2 non canonique");
    }
    for (const auto * token : {"/think", "[INST]", "[/INST]", "<<SYS>>"})
        if (s.find(token) != std::string::npos) throw std::runtime_error("Délimiteur P2 interdit");
    if (!system && s.find("/no_think") != std::string::npos) throw std::runtime_error("Directive système hors rôle");
    if (s.find("<|") != std::string::npos || s.find("<think>") != std::string::npos || s.find("</think>") != std::string::npos)
        throw std::runtime_error("Délimiteur P2 interdit");
}
static std::string bytes(JNIEnv * env, jbyteArray array) {
    if (!array) throw std::runtime_error("Entrée absente");
    const auto size = env->GetArrayLength(array);
    if(size > 128 * 1024) throw std::runtime_error("Entrée trop longue");
    std::string s(size, '\0');
    env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte *>(s.data()));
    return s;
}
extern "C" JNIEXPORT jlong JNICALL Java_fr_jarvis_llm_engine_NativeBridge_create(JNIEnv * env, jobject) {
    try {
        static std::once_flag init;
        std::call_once(init, [] { llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr); llama_backend_init(); });
        auto result = std::make_unique<Engine>();
        std::lock_guard<std::mutex> lock(stream_registry_mutex);
        if (next_engine_id <= 0 || next_engine_id == INT64_MAX) throw std::runtime_error("ENGINE_ID_EXHAUSTED");
        const auto id = next_engine_id++;
        stream_registry.emplace(id, RegisteredEngine{std::move(result), false, false});
        return id;
    } catch (const std::exception & ex) { error(env, ex); return 0; }
}
extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_llm_engine_NativeBridge_begin(JNIEnv * env, jobject, jlong h) {
    try {
        std::lock_guard<std::mutex> lock(stream_registry_mutex);
        auto found = stream_registry.find(h);
        if (found == stream_registry.end() || found->second.busy) throw std::runtime_error("STALE_OR_BUSY_ENGINE");
        found->second.value->cancel.begin(90'000);
    } catch (const std::exception & ex) { error(env, ex); }
}
extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_llm_engine_NativeBridge_cancel(JNIEnv * env, jobject, jlong h) {
    try {
        std::lock_guard<std::mutex> lock(stream_registry_mutex);
        auto found = stream_registry.find(h);
        if (found == stream_registry.end()) throw std::runtime_error("Handle périmé");
        found->second.value->cancel.request();
    } catch (const std::exception & ex) { error(env, ex); }
}
extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_llm_engine_NativeBridge_load(
    JNIEnv * env, jobject, jlong h, jbyteArray path, jint context, jint threads, jboolean small) {
    std::optional<EngineCallPin> pin;
    bool entered = false;
    try {
        pin.emplace(h);
        require_not_streaming(h);
        entered = true;
        auto & e = engine(h);
        auto cp = context_params(context, threads, small, &e.cancel);
        check(e); e.unload();
        const auto filename = bytes(env, path);
        // Inspect shape/type without allocating weights or a KV cache.
        std::unique_ptr<gguf_context, decltype(&gguf_free)> meta(
            gguf_init_from_file(filename.c_str(), {true, nullptr}), gguf_free);
        validate_metadata(meta.get(), small, LLAMA_FTYPE_MOSTLY_Q4_K_M);
        meta.reset(); check(e);
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.load_mode = LLAMA_LOAD_MODE_MMAP;
        if (small) params.use_extra_bufts = false; // avoid optional persistent repacked weight buffers
        params.progress_callback = Cancellation::progress;
        params.progress_callback_user_data = &e.cancel;
        e.model = llama_model_load_from_file(filename.c_str(), params);
        if (!e.model) throw std::runtime_error("Chargement refusé, interrompu ou GGUF incompatible");
        check(e);
        char arch[32] = {};
        llama_model_meta_val_str(e.model, "general.architecture", arch, sizeof(arch));
        const auto count = llama_model_n_params(e.model);
        if (std::string(arch) != "qwen3" || (small ? (count < 400'000'000 || count > 850'000'000) : (count < 1'300'000'000 || count > 2'100'000'000)))
            throw std::runtime_error("Seuls Qwen3 0.6B et 1.7B sont admis dans ce prototype");
        e.ctx = llama_init_from_model(e.model, cp);
        if (!e.ctx) throw std::runtime_error("Création du contexte impossible");
        e.context = llama_n_ctx(e.ctx); e.batch = llama_n_batch(e.ctx);
        e.max_output = maximum_output_tokens(small, context); check(e);
    } catch (const std::exception & ex) { if (entered && h) engine(h).unload(); error(env, ex); }
}
static std::string format(Engine & e, const std::vector<std::string> & roles, const std::vector<std::string> & texts, size_t start, const std::string * custom = nullptr) {
    const std::string system = "Tu es JARVIS, un assistant local. Réponds en français, brièvement. Admets tes incertitudes. Tu ne peux exécuter aucune action. /no_think";
    const std::string effective = custom ? system + "\nInstructions supplémentaires (subordonnées aux règles fixes) :\n" + *custom : system;
    std::vector<llama_chat_message> msgs{{"system", effective.c_str()}};
    for (size_t i = start; i < roles.size(); ++i) msgs.push_back({roles[i].c_str(), texts[i].c_str()});
    const char * tmpl = llama_model_chat_template(e.model, nullptr);
    if (!tmpl) throw std::runtime_error("Template officiel absent du GGUF");
    std::vector<char> buffer(16 * 1024);
    int size = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buffer.data(), buffer.size());
    if (size < 0) throw std::runtime_error("Template non pris en charge");
    if (size > static_cast<int>(buffer.size())) {
        buffer.resize(size + 1);
        size = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buffer.data(), buffer.size());
    }
    if (size < 0 || size > static_cast<int>(buffer.size())) throw std::runtime_error("Template invalide");
    std::string result(buffer.data(), size);
    // Qwen3 hybrid template: prefill an empty reasoning block rather than allow a long thinking stream.
    result += "<think>\n\n</think>\n\n";
    return result;
}

class StreamCancelled final : public std::runtime_error {
public: StreamCancelled() : std::runtime_error("STREAM_CANCELLED") {}
};
class JavaCallbackPending final : public std::runtime_error {
public: JavaCallbackPending() : std::runtime_error("JAVA_CALLBACK_EXCEPTION") {}
};
class StreamLocalFrame {
    JNIEnv * env_;
public:
    explicit StreamLocalFrame(JNIEnv * env) : env_(env) {
        if (env_->PushLocalFrame(8) != JNI_OK) throw JavaCallbackPending();
    }
    ~StreamLocalFrame() { env_->PopLocalFrame(nullptr); }
};
static int64_t stream_nanos() {
    timespec stamp{};
#ifdef __ANDROID__
    constexpr auto domain = CLOCK_BOOTTIME; // same domain as elapsedRealtimeNanos, qualification still required
#else
    constexpr auto domain = CLOCK_MONOTONIC; // host only; never mix with Android timestamps
#endif
    if (clock_gettime(domain, &stamp) != 0 || stamp.tv_sec < 0 || stamp.tv_nsec < 0 || stamp.tv_nsec >= 1'000'000'000 ||
        stamp.tv_sec > INT64_MAX / 1'000'000'000 ||
        (stamp.tv_sec == INT64_MAX / 1'000'000'000 && stamp.tv_nsec > INT64_MAX % 1'000'000'000))
        throw std::runtime_error("STREAM_CLOCK_UNAVAILABLE");
    return static_cast<int64_t>(stamp.tv_sec) * 1'000'000'000 + stamp.tv_nsec;
}
class SynchronousStream {
    JNIEnv * env_;
    jobject callback_; // JNI argument local ref, rooted for this synchronous call only; NEVER retained
    jbyteArray binding_;
    jmethodID prepared_, event_;
    Engine & engine_;
    jarvis::perf03::Utf8Assembler utf8_;
    int sequence_ = 0, sampled_ = 0, terminal_ = 4;
    int64_t first_ = -1;
    bool ended_ = false, cancelled_ = false;
    jbyteArray array(const char * data, size_t size) {
        if (size > 128 * 1024) throw std::runtime_error("STREAM_ARRAY_LIMIT");
        auto result = env_->NewByteArray(static_cast<jsize>(size));
        if (!result || env_->ExceptionCheck()) throw JavaCallbackPending();
        if (size) env_->SetByteArrayRegion(result, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte *>(data));
        if (env_->ExceptionCheck()) throw JavaCallbackPending();
        return result;
    }
    void send(int kind, const std::string & text, bool terminal = false) {
        if (ended_) throw std::runtime_error("STREAM_ALREADY_TERMINAL");
        StreamLocalFrame frame(env_);
        auto payload = array(text.data(), text.size()); // one bounded copy; same payload/sequence on RETRY
        while (true) {
            if ((!terminal || kind == 2) && engine_.cancel.stopped()) { cancelled_ = true; throw StreamCancelled(); }
            auto accepted = env_->CallIntMethod(callback_, event_, binding_, static_cast<jint>(kind),
                static_cast<jint>(sequence_), payload, static_cast<jint>(sampled_), static_cast<jlong>(first_),
#ifdef __ANDROID__
                static_cast<jint>(0));
#else
                static_cast<jint>(1));
#endif
            if (env_->ExceptionCheck()) throw JavaCallbackPending(); // preserve original Java throwable
            if (accepted == 0) { ++sequence_; if (terminal) { terminal_ = kind; ended_ = true; } return; }
            if (accepted == 2 && kind != 3 && kind != 4) { cancelled_ = true; throw StreamCancelled(); }
            if (accepted != 1 || terminal) throw std::runtime_error("STREAM_CALLBACK_REJECTED");
            // Backpressure on the existing worker; no registry/handle/broker lock is held.
            // Existing cancellation deadline (90s) is authoritative, not reset here.
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    }
public:
    SynchronousStream(JNIEnv * env, jobject callback, jbyteArray binding, Engine & value)
        : env_(env), callback_(callback), binding_(binding), engine_(value) {
        if (!callback_ || !binding_ || env_->GetArrayLength(binding_) != 64) throw std::runtime_error("STREAM_BINDING_INVALID");
        const auto digest = bytes(env_, binding_);
        if (!std::all_of(digest.begin(), digest.end(), [](char c) { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'); }))
            throw std::runtime_error("STREAM_BINDING_INVALID");
        auto cls = env_->GetObjectClass(callback_);
        if (!cls || env_->ExceptionCheck()) throw JavaCallbackPending();
        prepared_ = env_->GetMethodID(cls, "onNativePrepared", "([B[BI)Z");
        if (env_->ExceptionCheck()) { env_->DeleteLocalRef(cls); throw JavaCallbackPending(); }
        event_ = env_->GetMethodID(cls, "onNativeEvent", "([BII[BIJI)I");
        env_->DeleteLocalRef(cls);
        if (!prepared_ || !event_ || env_->ExceptionCheck()) throw JavaCallbackPending();
    }
    void prepared(const std::string & prompt, const char * tmpl, int count) {
        StreamLocalFrame frame(env_);
        if (!tmpl) throw std::runtime_error("STREAM_TEMPLATE_ABSENT");
        const std::string official(tmpl);
        auto rendered = array(prompt.data(), prompt.size()), templ = array(official.data(), official.size());
        auto accepted = env_->CallBooleanMethod(callback_, prepared_, rendered, templ, static_cast<jint>(count));
        if (env_->ExceptionCheck()) throw JavaCallbackPending();
        if (!accepted) throw std::runtime_error("STREAM_PREPARED_MISMATCH_OR_REVOKED");
        send(0, ""); // ACK is control; never a first generated token
    }
    void sampled() { if (first_ < 0) first_ = stream_nanos(); ++sampled_; }
    void piece(const char * data, size_t size) {
        for (const auto & chunk : utf8_.accept(data, size)) send(1, chunk);
    }
    void complete() { utf8_.finish(); send(2, "", true); }
    void failed() noexcept {
        if (ended_ || env_->ExceptionCheck()) return; // no Java/JNI work under a pending callback exception
        try { send(cancelled_ || engine_.cancel.stopped() ? 3 : 4, "", true); } catch (...) { /* original exception/ERROR result retained */ }
    }
    int terminal() const { return terminal_; }
};

static jbyteArray generate(JNIEnv * env, jlong h, jobjectArray roleArray, jobjectArray textArray, jint maxTokens, const std::string * custom, int contextLimit = 0, SynchronousStream * stream = nullptr) {
    try {
        if (!stream) require_not_streaming(h);
        auto & e = engine(h); check(e);
        if (!e.ctx || maxTokens < 1 || maxTokens > e.max_output) throw std::runtime_error("Moteur non prêt ou sortie hors budget");
        if (!roleArray || !textArray) throw std::runtime_error("Historique absent");
        const int effective = contextLimit == 0 ? e.context : contextLimit;
        if (effective < 512 || effective > e.context || effective % 256 != 0) throw std::runtime_error("Contexte P2 invalide");
        const int size = env->GetArrayLength(roleArray);
        if (size < 1 || size > 8 || size != env->GetArrayLength(textArray)) throw std::runtime_error("Historique invalide");
        std::vector<std::string> roles, texts;
        for (int i = 0; i < size; ++i) {
            auto role = static_cast<jbyteArray>(env->GetObjectArrayElement(roleArray, i));
            auto text = static_cast<jbyteArray>(env->GetObjectArrayElement(textArray, i));
            roles.push_back(bytes(env, role)); texts.push_back(bytes(env, text));
            // A user message must not inject ChatML role delimiters into the formatted prompt.
            if (custom) validate_p2(texts.back());
            if (custom && (texts.back().find("<|") != std::string::npos || texts.back().find("<think>") != std::string::npos || texts.back().find("</think>") != std::string::npos || texts.back().find('\0') != std::string::npos))
                throw std::runtime_error("Délimiteur P2 interdit");
            if (custom && (roles.back() != (i % 2 == 0 ? "user" : "assistant"))) throw std::runtime_error("Alternance P2 invalide");
            size_t position = 0;
            while ((position = texts.back().find("<|", position)) != std::string::npos) {
                texts.back().replace(position, 2, "< |"); position += 3;
            }
            env->DeleteLocalRef(role); env->DeleteLocalRef(text);
            if (roles.back() != "user" && roles.back() != "assistant") throw std::runtime_error("Rôle invalide");
        }
        if (roles.back() != "user") throw std::runtime_error("Question utilisateur requise");
        const auto * vocab = llama_model_get_vocab(e.model);
        std::vector<llama_token> tokens(e.context);
        int n = -1;
        size_t start = 0;
        while (true) {
            check(e);
            const auto prompt = format(e, roles, texts, start, custom);
            n = llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), tokens.size(), true, true);
            if (n > 0 && n + maxTokens + 8 <= effective) {
                if (stream) stream->prepared(prompt, llama_model_chat_template(e.model, nullptr), n);
                break;
            }
            if (custom) throw std::runtime_error("P2_CONTEXT_EXCEEDED");
            if (start + 2 < roles.size()) start += 2;
            else throw std::runtime_error("Question trop longue pour le contexte : raccourcis-la");
        }
        llama_memory_clear(llama_get_memory(e.ctx), true);
        for (int offset = 0; offset < n; offset += e.batch) {
            check(e);
            auto batch = llama_batch_get_one(tokens.data() + offset, std::min(e.batch, n - offset));
            if (llama_decode(e.ctx, batch) != 0) throw std::runtime_error("Préparation interrompue ou impossible");
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(), llama_sampler_free);
        std::string output;
        std::vector<char> streamPiece; // reuse scratch ONLY on the candidate streaming path
        if (stream) streamPiece.resize(256);
        for (int i = 0; i < maxTokens; ++i) {
            check(e);
            llama_token token = llama_sampler_sample(sampler.get(), e.ctx, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            if (stream) stream->sampled(); // exact first non-EOG token, before piece conversion or callback
            std::vector<char> legacyPiece;
            if (!stream) legacyPiece.resize(256);
            auto & piece = stream ? streamPiece : legacyPiece;
            int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (length < 0) {
                // Reject output overflow before allocation; never silently drop a token.
                if (stream && (length == INT32_MIN || -length > static_cast<int>(jarvis::perf03::kMaxTextBytes)))
                    throw std::runtime_error("STREAM_PIECE_LIMIT");
                piece.resize(-length); length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            }
            if (length < 0) throw std::runtime_error("Token invalide");
            if (stream) stream->piece(piece.data(), static_cast<size_t>(length));
            else output.append(piece.data(), length);
            if (i + 1 < maxTokens) {
                auto batch = llama_batch_get_one(&token, 1);
                if (llama_decode(e.ctx, batch) != 0) throw std::runtime_error("Génération interrompue ou impossible");
            }
        }
        check(e);
        if (stream) { stream->complete(); return nullptr; }
        auto result = env->NewByteArray(output.size());
        if (result) env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception & ex) {
        if (stream) { stream->failed(); return nullptr; }
        error(env, ex); return nullptr;
    }
}
// Separate entry point: legacy ABI and its history policy remain unchanged.
extern "C" JNIEXPORT jbyteArray JNICALL Java_fr_jarvis_llm_engine_NativeBridge_generate(
    JNIEnv * env, jobject, jlong h, jobjectArray roles, jobjectArray texts, jint maxTokens) {
    try { EngineCallPin pin(h); return generate(env, h, roles, texts, maxTokens, nullptr); }
    catch (const std::exception & ex) { error(env, ex); return nullptr; }
}
extern "C" JNIEXPORT jint JNICALL Java_fr_jarvis_llm_engine_NativeBridge_countP2Tokens(
    JNIEnv * env, jobject, jlong h, jbyteArray system, jbyteArray question) {
    try {
        EngineCallPin pin(h);
        require_not_streaming(h);
        auto & e = engine(h); check(e);
        if (!e.ctx) throw std::runtime_error("Moteur non prêt");
        const auto custom = bytes(env, system), user = bytes(env, question);
        validate_p2(custom, true); validate_p2(user);
        if (custom.size() > 8192 || custom.find("<|") != std::string::npos || user.find("<|") != std::string::npos || custom.find('\0') != std::string::npos || user.find('\0') != std::string::npos)
            throw std::runtime_error("Prompt P2 invalide");
        const auto prompt = format(e, {"user"}, {user}, 0, &custom);
        const int n = llama_tokenize(llama_model_get_vocab(e.model), prompt.data(), prompt.size(), nullptr, 0, true, true);
        return n < 0 ? -n : n;
    } catch (const std::exception & ex) { error(env, ex); return -1; }
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_fr_jarvis_llm_engine_NativeBridge_generateP2(
    JNIEnv * env, jobject, jlong h, jobjectArray roles, jobjectArray texts, jint maxTokens, jbyteArray system) {
    try {
        EngineCallPin pin(h);
        const auto prompt = bytes(env, system);
        validate_p2(prompt, true);
        if (prompt.size() > 8192 || prompt.find("<|") != std::string::npos || prompt.find("<think>") != std::string::npos || prompt.find("</think>") != std::string::npos || prompt.find('\0') != std::string::npos)
            throw std::runtime_error("Prompt système P2 invalide");
        return generate(env, h, roles, texts, maxTokens, &prompt);
    } catch (const std::exception & ex) { error(env, ex); return nullptr; }
}
// Fixture-only calls use the SAME canonical formatter and actual tokenizer as generate().
extern "C" JNIEXPORT jobjectArray JNICALL Java_fr_jarvis_llm_engine_NativeBridge_measureFixtureP2(
    JNIEnv * env, jobject, jlong h, jbyteArray system, jbyteArray question) {
    try {
        EngineCallPin pin(h);
        require_not_streaming(h);
        auto & e = engine(h); check(e);
        if (!e.ctx) throw std::runtime_error("Moteur non prêt");
        const auto custom = bytes(env, system), user = bytes(env, question);
        validate_p2(custom, true); validate_p2(user);
        if (custom.size() > 8192) throw std::runtime_error("Système hors limites");
        const auto prompt = format(e, {"user"}, {user}, 0, &custom);
        const auto * tmpl = llama_model_chat_template(e.model, nullptr);
        const int n = llama_tokenize(llama_model_get_vocab(e.model), prompt.data(), prompt.size(), nullptr, 0, true, true);
        const std::vector<std::string> values{std::to_string(n < 0 ? -n : n), prompt, tmpl};
        auto result = env->NewObjectArray(3, env->FindClass("[B"), nullptr);
        for (int i = 0; i < 3; ++i) {
            auto value = env->NewByteArray(values[i].size());
            env->SetByteArrayRegion(value, 0, values[i].size(), reinterpret_cast<const jbyte *>(values[i].data()));
            env->SetObjectArrayElement(result, i, value); env->DeleteLocalRef(value);
        }
        return result;
    } catch (const std::exception & ex) { error(env, ex); return nullptr; }
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_fr_jarvis_llm_engine_NativeBridge_generateFixtureP2(
    JNIEnv * env, jobject, jlong h, jbyteArray system, jbyteArray question, jint maxTokens, jint contextLimit) {
    try {
        EngineCallPin pin(h);
        require_not_streaming(h);
        auto & e = engine(h);
        if (contextLimit < 512 || contextLimit > e.context || contextLimit % 256 != 0)
            throw std::runtime_error("Contexte P2 invalide");
        const auto custom = bytes(env, system), user = bytes(env, question);
        validate_p2(custom, true); validate_p2(user);
        if (custom.size() > 8192) throw std::runtime_error("Système hors limites");
        auto cls = env->FindClass("[B");
        auto roles = env->NewObjectArray(1, cls, nullptr), texts = env->NewObjectArray(1, cls, nullptr);
        auto role = env->NewByteArray(4); env->SetByteArrayRegion(role, 0, 4, reinterpret_cast<const jbyte *>("user"));
        env->SetObjectArrayElement(roles, 0, role); env->SetObjectArrayElement(texts, 0, question);
        auto result = generate(env, h, roles, texts, maxTokens, &custom, contextLimit);
        env->DeleteLocalRef(role); env->DeleteLocalRef(roles); env->DeleteLocalRef(texts);
        return result;
    } catch (const std::exception & ex) { error(env, ex); return nullptr; }
}

// Candidate ABI, opt-in only: legacy generate/generateP2 remain present and return complete bytes.
extern "C" JNIEXPORT jint JNICALL Java_fr_jarvis_llm_engine_NativeBridge_generateStream(
    JNIEnv * env, jobject, jlong h, jbyteArray system, jbyteArray question, jint maxTokens,
    jint contextLimit, jbyteArray binding, jobject callback) {
    try {
        EngineCallPin pin(h, true); // callback, Engine/model/context live through the LAST synchronous callback
        auto & e = engine(h);
        SynchronousStream stream(env, callback, binding, e);
        try {
            if (contextLimit < 512 || contextLimit > e.context || contextLimit % 256 != 0)
                throw std::runtime_error("Contexte P2 invalide");
            const auto custom = bytes(env, system), user = bytes(env, question);
            if (env->ExceptionCheck()) throw JavaCallbackPending();
            validate_p2(custom, true); validate_p2(user);
            if (custom.size() > 8192) throw std::runtime_error("Système hors limites");
            StreamLocalFrame frame(env);
            auto cls = env->FindClass("[B");
            if (!cls || env->ExceptionCheck()) throw JavaCallbackPending();
            auto roles = env->NewObjectArray(1, cls, nullptr);
            if (!roles || env->ExceptionCheck()) throw JavaCallbackPending();
            auto texts = env->NewObjectArray(1, cls, nullptr);
            if (!texts || env->ExceptionCheck()) throw JavaCallbackPending();
            auto role = env->NewByteArray(4);
            if (!role || env->ExceptionCheck()) throw JavaCallbackPending();
            env->SetByteArrayRegion(role, 0, 4, reinterpret_cast<const jbyte *>("user"));
            if (env->ExceptionCheck()) throw JavaCallbackPending();
            env->SetObjectArrayElement(roles, 0, role);
            if (env->ExceptionCheck()) throw JavaCallbackPending();
            env->SetObjectArrayElement(texts, 0, question);
            if (env->ExceptionCheck()) throw JavaCallbackPending();
            generate(env, h, roles, texts, maxTokens, &custom, contextLimit, &stream);
        } catch (const std::exception &) { stream.failed(); }
        return stream.terminal(); // CONTENT terminal only, NEVER an unload/drain receipt
    } catch (const std::exception & ex) { error(env, ex); return 4; }
}

extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_llm_engine_NativeBridge_unload(JNIEnv * env, jobject, jlong h) {
    try { EngineCallPin pin(h); require_not_streaming(h); engine(h).unload(); } catch (const std::exception & ex) { error(env, ex); }
}
extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_llm_engine_NativeBridge_destroy(JNIEnv * env, jobject, jlong h) {
    if (!h) return;
    try {
        std::unique_ptr<Engine> value;
        { std::lock_guard<std::mutex> lock(stream_registry_mutex);
          auto found = stream_registry.find(h);
          if (found == stream_registry.end() || found->second.busy) throw std::runtime_error("STALE_OR_BUSY_ENGINE");
          value = std::move(found->second.value); stream_registry.erase(found); }
        // Native cleanup outside registry lock, after invalidating the opaque handle.
        value.reset();
    } catch (const std::exception & ex) { error(env, ex); }
}

// Pinned llama.cpp C++ diagnostic API. Buffer sizes are not process PSS.
extern "C" JNIEXPORT jlongArray JNICALL Java_fr_jarvis_llm_engine_NativeBridge_allocations(JNIEnv * env, jobject, jlong h) {
    try {
        EngineCallPin pin(h);
        require_not_streaming(h);
        auto & e = engine(h);
        if (!e.ctx) throw std::runtime_error("Contexte absent");
        jlong values[3] = {};
        for (const auto & entry : llama_get_memory_breakdown(e.ctx)) {
            values[0] += entry.second.model; values[1] += entry.second.context; values[2] += entry.second.compute;
        }
        auto result = env->NewLongArray(3);
        if (result) env->SetLongArrayRegion(result, 0, 3, values);
        return result;
    } catch (const std::exception & ex) { error(env, ex); return nullptr; }
}

#ifdef JARVIS_HOST_TESTS
// Not compiled into Android. Inspect the actual shared formatter, using a loaded real GGUF.
extern "C" JNIEXPORT jbyteArray JNICALL Java_fr_jarvis_llm_engine_NativeBridge_renderForTest(
    JNIEnv * env, jobject, jlong h, jbyteArray system, jbyteArray question) {
    try {
        EngineCallPin pin(h);
        require_not_streaming(h);
        auto & e = engine(h);
        if (!e.ctx) throw std::runtime_error("Moteur non prêt");
        const auto custom = bytes(env, system), user = bytes(env, question);
        validate_p2(custom, true); validate_p2(user);
        const auto prompt = format(e, {"user"}, {user}, 0, &custom);
        auto result = env->NewByteArray(prompt.size());
        env->SetByteArrayRegion(result, 0, prompt.size(), reinterpret_cast<const jbyte *>(prompt.data()));
        return result;
    } catch (const std::exception & ex) { error(env, ex); return nullptr; }
}
#endif
