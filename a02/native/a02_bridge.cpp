// A02 native library for the V1 host.
//
// Compiles the EXACT protected engine (app/src/main/cpp/bridge.cpp, unchanged)
// and exposes it under fr.jarvis.llm.a02.SealedNativeBridge with one difference:
// the pathname loader is renamed at compile time so no Java class can bind it.
// The only way to load weights through this library is loadSealedFd(), whose
// sole pathname is /proc/self/fd/<sealed memfd> after identity verification.
#define Java_fr_jarvis_llm_engine_NativeBridge_load jarvis_a02_protected_path_load_not_exported_to_java
#include "../../app/src/main/cpp/bridge.cpp"
#undef Java_fr_jarvis_llm_engine_NativeBridge_load

#include "a02_snapshot.h"
#include <cstdio>
#include <cstring>
#include <sys/mman.h>
#include <fstream>
#include <sstream>

// ---------------------------------------------------------------------------------------------
// Sealed-descriptor open (linked with -Wl,--wrap=fopen, A02 library only).
// Android SELinux denies an app re-opening its own memfd through /proc/self/fd/N (emulator
// API 29, run 43093246716: "Métadonnées GGUF invalides" = gguf open returned null). For the ONE
// path registered by the current loadSealedFd call on this thread, fopen returns a read-only
// FILE* over a duplicate of the very same sealed descriptor: no procfs re-open, no other file.
// Every other path goes to the real fopen unchanged; write/append modes are refused.
// ---------------------------------------------------------------------------------------------
extern "C" FILE * __real_fopen(const char * path, const char * mode);
extern "C" void * __real_mmap(void * addr, size_t length, int prot, int flags, int fd, off_t offset);
namespace {
thread_local int t_sealed_fd = -1;
thread_local char t_sealed_path[48] = {0};
thread_local int t_sealed_opens = 0;
thread_local unsigned long long t_sealed_ino = 0, t_sealed_dev = 0;
thread_local int t_private_maps = 0;
}
// Read-only mapping of the sealed copy (linked with -Wl,--wrap=mmap, A02 library only).
// llama.cpp maps weights with MAP_SHARED|PROT_READ. Kernels before 6.7 (Android API 29 emulator,
// run 43118616305: "mmap failed: Operation not permitted") refuse ANY shared mapping of a
// F_SEAL_WRITE memfd opened read-write, even PROT_READ. For the sealed inode of the current load
// only, and only for mappings WITHOUT PROT_WRITE, request MAP_PRIVATE: same page-cache pages, no
// copy, no way to modify the file; the seals stay in force. Everything else is untouched.
extern "C" void * __wrap_mmap(void * addr, size_t length, int prot, int flags, int fd, off_t offset) {
    if (t_sealed_ino != 0 && fd >= 0 && !(prot & PROT_WRITE) && (flags & MAP_SHARED)) {
        struct stat st {};
        if (fstat(fd, &st) == 0 && static_cast<unsigned long long>(st.st_ino) == t_sealed_ino &&
            static_cast<unsigned long long>(st.st_dev) == t_sealed_dev &&
            (fcntl(fd, F_GET_SEALS) & jarvis_a02::kRequiredSeals) == jarvis_a02::kRequiredSeals) {
            flags = (flags & ~MAP_SHARED) | MAP_PRIVATE;
            ++t_private_maps;
        }
    }
    return __real_mmap(addr, length, prot, flags, fd, offset);
}
extern "C" FILE * __wrap_fopen(const char * path, const char * mode) {
    if (t_sealed_fd >= 0 && path && std::strcmp(path, t_sealed_path) == 0) {
        if (!mode || mode[0] != 'r' || std::strchr(mode, '+') || std::strchr(mode, 'w') || std::strchr(mode, 'a')) {
            errno = EACCES; return nullptr;
        }
        const int dup = fcntl(t_sealed_fd, F_DUPFD_CLOEXEC, 3);
        if (dup < 0) return nullptr;
        if (lseek(dup, 0, SEEK_SET) != 0) { close(dup); return nullptr; }
        FILE * f = fdopen(dup, "rb");
        if (!f) { close(dup); return nullptr; }
        ++t_sealed_opens;
        return f;
    }
    return __real_fopen(path, mode);
}
// ---------------------------------------------------------------------------------------------
// Load diagnostics (A02 library only). The protected bridge installs a no-op llama logger; during
// a sealed load we capture llama.cpp's own messages (bounded tail, no prompt or user content: only
// model metadata, sizes and the /proc/self/fd/<n> name), forward WARN/ERROR to logcat on Android,
// and append the tail to a refused load's exception so the exact failing step is visible.
// ---------------------------------------------------------------------------------------------
#ifdef __ANDROID__
#include <android/log.h>
#endif
namespace {
thread_local bool t_capture_llama = false;
thread_local std::string t_llama_log;
void a02_llama_log(ggml_log_level level, const char * text, void *) {
    if (!text) return;
#ifdef __ANDROID__
    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN)
        __android_log_print(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, "JARVIS-A02", "%s", text);
#endif
    if (!t_capture_llama || level == GGML_LOG_LEVEL_DEBUG) return;
    t_llama_log += text;
    if (t_llama_log.size() > 3000) t_llama_log.erase(0, t_llama_log.size() - 3000);
}
struct LlamaLogCapture {
    LlamaLogCapture() { t_llama_log.clear(); t_capture_llama = true; llama_log_set(a02_llama_log, nullptr); }
    ~LlamaLogCapture() { t_capture_llama = false; }
};
/** Re-throws a pending protected-loader exception with the captured llama.cpp tail appended. */
void rethrow_with_llama_tail(JNIEnv * env) {
    jthrowable pending = env->ExceptionOccurred();
    if (!pending) return;
    env->ExceptionClear();
    std::string original = "Chargement refusé";
    jclass throwable = env->FindClass("java/lang/Throwable");
    jmethodID getMessage = throwable ? env->GetMethodID(throwable, "getMessage", "()Ljava/lang/String;") : nullptr;
    if (getMessage) {
        auto msg = static_cast<jstring>(env->CallObjectMethod(pending, getMessage));
        if (!env->ExceptionCheck() && msg) { const char * c = env->GetStringUTFChars(msg, nullptr); if (c) { original = c; env->ReleaseStringUTFChars(msg, c); } }
        env->ExceptionClear();
    }
    std::string tail = t_llama_log;
    for (auto & ch : tail) if (ch == '\n' || ch == '\r') ch = ' ';
    std::string combined = original + (tail.empty() ? std::string() : " | llama: " + tail);
    jclass ise = env->FindClass("java/lang/IllegalStateException");
    if (ise) env->ThrowNew(ise, combined.c_str()); else env->Throw(pending);
}
struct SealedOpenScope {
    SealedOpenScope(int fd, const std::string & path, unsigned long long ino, unsigned long long dev) {
        if (path.size() >= sizeof(t_sealed_path)) throw std::runtime_error("A02_PROC_PATH_TOO_LONG");
        std::memcpy(t_sealed_path, path.c_str(), path.size() + 1); t_sealed_fd = fd; t_sealed_opens = 0;
        t_sealed_ino = ino; t_sealed_dev = dev; t_private_maps = 0;
    }
    ~SealedOpenScope() { t_sealed_fd = -1; t_sealed_path[0] = 0; t_sealed_ino = 0; t_sealed_dev = 0; }
};
}

namespace {
std::string json_escape(const std::string & s) {
    std::string o;
    for (char c : s) {
        if (c == '"' || c == '\\') { o += '\\'; o += c; }
        else if (static_cast<unsigned char>(c) < 0x20) { char b[8]; std::snprintf(b, sizeof b, "\\u%04x", c); o += b; }
        else o += c;
    }
    return o;
}
struct MapsProof { bool sealedMapped = false; bool sourceMapped = false; };
/** Inspect /proc/self/maps by inode: did llama.cpp map the sealed copy, never the source? */
MapsProof inspect_maps(unsigned long long sealedInode, unsigned long long sourceInode) {
    MapsProof proof;
    std::ifstream maps("/proc/self/maps");
    std::string line;
    while (std::getline(maps, line)) {
        std::istringstream in(line);
        std::string range, perms, offset, dev; unsigned long long inode = 0;
        in >> range >> perms >> offset >> dev >> inode;
        if (inode == 0) continue;
        if (inode == sealedInode && line.find(jarvis_a02::kMemfdName) != std::string::npos) proof.sealedMapped = true;
        if (inode == sourceInode && line.find("memfd:") == std::string::npos) proof.sourceMapped = true;
    }
    return proof;
}
jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(s.size()));
    if (out) env->SetByteArrayRegion(out, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return out;
}
}

#define A02_FWD(ret, name, params, args) \
    extern "C" JNIEXPORT ret JNICALL Java_fr_jarvis_llm_a02_SealedNativeBridge_##name params { \
        return Java_fr_jarvis_llm_engine_NativeBridge_##name args; }

A02_FWD(jlong, create, (JNIEnv * env, jobject o), (env, o))
A02_FWD(void, begin, (JNIEnv * env, jobject o, jlong h), (env, o, h))
A02_FWD(void, cancel, (JNIEnv * env, jobject o, jlong h), (env, o, h))
A02_FWD(jbyteArray, generateP2, (JNIEnv * env, jobject o, jlong h, jobjectArray r, jobjectArray t, jint m, jbyteArray s), (env, o, h, r, t, m, s))
A02_FWD(jint, countP2Tokens, (JNIEnv * env, jobject o, jlong h, jbyteArray s, jbyteArray q), (env, o, h, s, q))
A02_FWD(jlongArray, allocations, (JNIEnv * env, jobject o, jlong h), (env, o, h))
// Real token streaming through the protected SynchronousStream (callback: onNativePrepared/onNativeEvent).
A02_FWD(jint, generateStream, (JNIEnv * env, jobject o, jlong h, jbyteArray s, jbyteArray q, jint m, jint c, jbyteArray b, jobject cb), (env, o, h, s, q, m, c, b, cb))
A02_FWD(void, unload, (JNIEnv * env, jobject o, jlong h), (env, o, h))
A02_FWD(void, destroy, (JNIEnv * env, jobject o, jlong h), (env, o, h))

/**
 * Snapshot -> seal -> re-hash -> identity check -> protected loader on /proc/self/fd/N.
 * Returns a non-sensitive JSON trace (no pathname, no content). Throws IllegalStateException
 * with an A02_* code on every refusal; the caller's source descriptor is never closed here.
 */
extern "C" JNIEXPORT jbyteArray JNICALL Java_fr_jarvis_llm_a02_SealedNativeBridge_loadSealedFd(
    JNIEnv * env, jobject receiver, jlong h, jint sourceFd, jlong exactSize, jbyteArray expectedSha,
    jlong memoryBudgetBytes, jint context, jint threads, jboolean small) {
    int owned = -1;
    try {
        jarvis_a02::SnapshotRequest request;
        request.sourceFd = sourceFd; request.exactSize = exactSize;
        request.expectedSha256 = bytes(env, expectedSha); request.memoryBudgetBytes = memoryBudgetBytes;
        struct stat source {};
        if (sourceFd < 0 || fstat(sourceFd, &source) != 0) throw std::runtime_error("A02_INVALID_FD");
        jarvis_a02::SnapshotTrace trace;
        {
            EngineCallPin pin(h);
            require_not_streaming(h);
            auto & e = engine(h);
            check(e);
            e.unload(); // never keep a previous model resident while allocating the new snapshot
            owned = jarvis_a02::sealed_snapshot(request, [&e] { return e.cancel.stopped(); }, &trace);
        }
        const std::string procPath = jarvis_a02::proc_path_for(owned, trace);
        jbyteArray path = to_bytes(env, procPath);
        if (!path) throw std::runtime_error("A02_JNI_ALLOCATION_REFUSED");
        int sealedOpens = 0, privateMaps = 0;
        {
            SealedOpenScope scope(owned, procPath, trace.inode, trace.device);
            LlamaLogCapture capture;
            jarvis_a02_protected_path_load_not_exported_to_java(env, receiver, h, path, context, threads, small);
            sealedOpens = t_sealed_opens; privateMaps = t_private_maps;
            if (env->ExceptionCheck()) rethrow_with_llama_tail(env);
        }
        env->DeleteLocalRef(path);
        if (env->ExceptionCheck()) { close(owned); return nullptr; } // protected loader refused: fail closed
        const auto proof = inspect_maps(trace.inode, static_cast<unsigned long long>(source.st_ino));
        close(owned); owned = -1;
        if (proof.sourceMapped) {
            try { EngineCallPin pin(h); engine(h).unload(); } catch (...) {}
            throw std::runtime_error("A02_SOURCE_MAPPED_REFUSED");
        }
        std::ostringstream json;
        json << "{\"a02\":\"v1\",\"size\":" << trace.size
             << ",\"copiedSha256\":\"" << json_escape(trace.copiedSha256) << "\""
             << ",\"sealedSha256\":\"" << json_escape(trace.sealedSha256) << "\""
             << ",\"seals\":" << trace.seals
             << ",\"loaderPath\":\"/proc/self/fd/<sealed>\""
             << ",\"loaderOpensServedFromSealedFd\":" << sealedOpens
             << ",\"readOnlyPrivateMapsOfSealedFd\":" << privateMaps
             << ",\"sealedInodeMapped\":" << (proof.sealedMapped ? "true" : "false")
             << ",\"sourceInodeMapped\":false"
             << ",\"pathLoaderExported\":false}";
        return to_bytes(env, json.str());
    } catch (const std::exception & ex) {
        if (owned >= 0) close(owned);
        error(env, ex);
        return nullptr;
    }
}
