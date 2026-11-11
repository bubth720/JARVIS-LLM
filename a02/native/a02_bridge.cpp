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
#include <fstream>
#include <sstream>

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
        jarvis_a02_protected_path_load_not_exported_to_java(env, receiver, h, path, context, threads, small);
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
