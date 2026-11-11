#pragma once
// A02 sealed snapshot: copy a caller-supplied read-only source descriptor into a
// private memfd, hash the copied bytes, seal it, re-hash the sealed bytes and
// return the ONLY descriptor the native loader may consume. Fail-closed: every
// refusal closes what it opened and never falls back to a pathname.
#include "a02_sha256.h"
#include "../../perf03/candidate/native/a02_sealed_fd.h"
#include <algorithm>
#include <atomic>
#include <fcntl.h>
#include <vector>
#include <cerrno>
#include <cstdint>
#include <functional>
#include <stdexcept>
#include <string>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif
#ifndef MFD_ALLOW_SEALING
#define MFD_ALLOW_SEALING 0x0002U
#endif

namespace jarvis_a02 {

constexpr std::int64_t kMaxModelBytes = 2LL * 1024 * 1024 * 1024;  // Qwen3 1.7B Q4_K_M < 1.3 GB
constexpr std::int64_t kBudgetMarginBytes = 64LL * 1024 * 1024;     // copy buffer + bookkeeping
constexpr const char * kMemfdName = "jarvis-a02-model";

struct SnapshotRequest {
    int sourceFd = -1;              // read-only descriptor chosen by the host; never reopened by path
    std::int64_t exactSize = 0;     // trusted size from the verified catalogue
    std::string expectedSha256;     // trusted lowercase hex digest
    std::int64_t memoryBudgetBytes = 0; // bytes the host reserves for the sealed copy
};

struct SnapshotTrace {
    std::int64_t size = 0;
    std::string copiedSha256;
    std::string sealedSha256;
    int seals = 0;
    unsigned long long inode = 0;
    unsigned long long device = 0;
};

inline int create_memfd() {
    const long fd = syscall(__NR_memfd_create, kMemfdName, MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (fd < 0) throw std::runtime_error("A02_MEMFD_UNAVAILABLE");
    return static_cast<int>(fd);
}

inline bool is_lower_hex64(const std::string & s) {
    if (s.size() != 64) return false;
    for (char c : s) if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
    return true;
}

/** Reserve-before-allocate: refuse before any memfd page exists. */
inline void require_budget(std::int64_t size, std::int64_t budget) {
    if (size <= 0 || size > kMaxModelBytes) throw std::runtime_error("A02_SIZE_REFUSED");
    if (budget < size + kBudgetMarginBytes) throw std::runtime_error("A02_MEMORY_BUDGET_REFUSED");
}

/**
 * Returns an owned, CLOEXEC, fully sealed memfd whose bytes hash to expectedSha256.
 * `stopped` is polled between blocks (cancellation before/while copying).
 */
inline int sealed_snapshot(const SnapshotRequest & r, const std::function<bool()> & stopped, SnapshotTrace * trace) {
    if (r.sourceFd < 0) throw std::runtime_error("A02_INVALID_FD");
    if (!is_lower_hex64(r.expectedSha256)) throw std::runtime_error("A02_DIGEST_FORMAT_REFUSED");
    require_budget(r.exactSize, r.memoryBudgetBytes);
    struct stat source {};
    if (fstat(r.sourceFd, &source) != 0) throw std::runtime_error("A02_INVALID_FD");
    if (!S_ISREG(source.st_mode)) throw std::runtime_error("A02_SOURCE_TYPE_REFUSED");
    if (source.st_size != r.exactSize) throw std::runtime_error("A02_SIZE_REFUSED");
    if (stopped && stopped()) throw std::runtime_error("A02_CANCELLED");

    const int fd = create_memfd();
    bool fdOwned = true;
    try {
        if (ftruncate(fd, 0) != 0) throw std::runtime_error("A02_MEMFD_UNAVAILABLE");
        Sha256 copied;
        std::vector<unsigned char> block(1 << 20);
        std::int64_t offset = 0;
        for (;;) {
            if (stopped && stopped()) throw std::runtime_error("A02_CANCELLED");
            // pread: the caller's file offset is irrelevant and untouched.
            const ssize_t n = pread(r.sourceFd, block.data(), block.size(), offset);
            if (n < 0) { if (errno == EINTR) continue; throw std::runtime_error("A02_SOURCE_READ_REFUSED"); }
            if (n == 0) break;
            if (offset + n > r.exactSize) throw std::runtime_error("A02_SIZE_REFUSED");
            std::size_t written = 0;
            while (written < static_cast<std::size_t>(n)) {
                const ssize_t w = write(fd, block.data() + written, static_cast<std::size_t>(n) - written);
                if (w < 0) { if (errno == EINTR) continue; throw std::runtime_error("A02_SNAPSHOT_WRITE_REFUSED"); }
                if (w == 0) throw std::runtime_error("A02_SNAPSHOT_WRITE_REFUSED");
                written += static_cast<std::size_t>(w);
            }
            copied.update(block.data(), static_cast<std::size_t>(n));
            offset += n;
        }
        if (offset != r.exactSize) throw std::runtime_error("A02_SIZE_REFUSED");
        const std::string copiedHex = copied.hex();
        if (copiedHex != r.expectedSha256) throw std::runtime_error("A02_DIGEST_REFUSED");
        if (fcntl(fd, F_ADD_SEALS, kRequiredSeals) != 0) throw std::runtime_error("A02_SEALS_REFUSED");

        // Re-verify through the generic admission check, then hash the sealed bytes the loader will map.
        const int owned = duplicate_verified_fd(fd, r.exactSize);
        close(fd); fdOwned = false;
        try {
            Sha256 sealed;
            std::int64_t pos = 0;
            while (pos < r.exactSize) {
                if (stopped && stopped()) throw std::runtime_error("A02_CANCELLED");
                const std::size_t want = static_cast<std::size_t>(std::min<std::int64_t>(block.size(), r.exactSize - pos));
                const ssize_t n = pread(owned, block.data(), want, pos);
                if (n < 0 && errno == EINTR) continue;
                if (n <= 0) throw std::runtime_error("A02_SEALED_READ_REFUSED");
                sealed.update(block.data(), static_cast<std::size_t>(n));
                pos += n;
            }
            const std::string sealedHex = sealed.hex();
            if (sealedHex != r.expectedSha256) throw std::runtime_error("A02_SEALED_DIGEST_REFUSED");
            struct stat st {};
            if (fstat(owned, &st) != 0) throw std::runtime_error("A02_SEALED_READ_REFUSED");
            if (trace) {
                trace->size = r.exactSize; trace->copiedSha256 = copiedHex; trace->sealedSha256 = sealedHex;
                trace->seals = fcntl(owned, F_GET_SEALS);
                trace->inode = static_cast<unsigned long long>(st.st_ino);
                trace->device = static_cast<unsigned long long>(st.st_dev);
            }
            return owned;
        } catch (...) { close(owned); throw; }
    } catch (...) {
        if (fdOwned) close(fd);
        throw;
    }
}

/**
 * The only pathname ever given to llama.cpp: /proc/self/fd/<owned>. Proves before
 * loading that this name resolves to the very sealed inode we verified.
 */
inline std::string proc_path_for(int owned, const SnapshotTrace & trace) {
    const std::string path = "/proc/self/fd/" + std::to_string(owned);
    struct stat resolved {};
    if (stat(path.c_str(), &resolved) != 0) throw std::runtime_error("A02_PROC_FD_UNAVAILABLE");
    if (static_cast<unsigned long long>(resolved.st_ino) != trace.inode ||
        static_cast<unsigned long long>(resolved.st_dev) != trace.device ||
        resolved.st_size != trace.size) throw std::runtime_error("A02_PROC_FD_IDENTITY_REFUSED");
    if ((fcntl(owned, F_GET_SEALS) & kRequiredSeals) != kRequiredSeals) throw std::runtime_error("A02_UNSEALED_REFUSED");
    return path;
}
}
