#pragma once
// Linux/Android-only A02 native admission boundary. No GGUF path accepted.
#include <cerrno>
#include <cstdint>
#include <fcntl.h>
#include <stdexcept>
#include <sys/stat.h>
#include <unistd.h>

namespace jarvis_a02 {
constexpr int kRequiredSeals = F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_SEAL;
inline int duplicate_verified_fd(int supplied, std::int64_t exact_size) {
    if (supplied < 0 || exact_size <= 0) throw std::runtime_error("A02_INVALID_FD");
    // Duplicate first: caller closing its own handle cannot change our identity.
    const int owned = fcntl(supplied, F_DUPFD_CLOEXEC, 3);
    if (owned < 0) throw std::runtime_error("A02_DUP_REFUSED");
    try {
        struct stat st {};
        if (fstat(owned, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size != exact_size)
            throw std::runtime_error("A02_SIZE_OR_TYPE_REFUSED");
        const int seals = fcntl(owned, F_GET_SEALS);
        if (seals < 0 || (seals & kRequiredSeals) != kRequiredSeals)
            throw std::runtime_error("A02_UNSEALED_REFUSED");
        return owned;
    } catch (...) { close(owned); throw; }
}
}
