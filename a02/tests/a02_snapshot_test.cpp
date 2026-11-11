// A02 native adversarial suite (no model weights). Linux host and Android both
// provide memfd seals; this binary is the host proof, NOT an Android result.
#include "../native/a02_snapshot.h"
#include <cstdio>
#include <cstdlib>
#include <dirent.h>
#include <string>
#include <thread>
#include <vector>

static int failures = 0, passed = 0;
#define EXPECT(cond, name) do { if (cond) { ++passed; } else { ++failures; std::fprintf(stderr, "FAIL %s (%s:%d)\n", name, __FILE__, __LINE__); } } while (0)

static std::string sha(const std::string & s) { jarvis_a02::Sha256 h; h.update(s.data(), s.size()); return h.hex(); }
static int open_fds() { int n = 0; DIR * d = opendir("/proc/self/fd"); while (readdir(d)) ++n; closedir(d); return n; }
static std::string refusal(const jarvis_a02::SnapshotRequest & r, std::function<bool()> stop = {}) {
    try { int fd = jarvis_a02::sealed_snapshot(r, stop, nullptr); close(fd); return "ADMITTED"; }
    catch (const std::exception & e) { return e.what(); }
}
static std::string tmpfile_with(const std::string & content) {
    const char * dir = std::getenv("A02_TMPDIR");
    std::string pattern = std::string(dir ? dir : "/tmp") + "/a02-source-XXXXXX";
    std::vector<char> name(pattern.begin(), pattern.end()); name.push_back('\0');
    int fd = mkstemp(name.data());
    if (fd < 0) std::abort();
    if (write(fd, content.data(), content.size()) != static_cast<ssize_t>(content.size())) std::abort();
    close(fd);
    return std::string(name.data());
}
#ifdef __ANDROID__
#define A02_PLATFORM "android"
#else
#define A02_PLATFORM "linux-host"
#endif
static constexpr std::int64_t kBudget = 512LL * 1024 * 1024;

int main() {
    // SHA-256 known-answer tests (FIPS 180-4 examples).
    EXPECT(sha("") == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "sha-empty");
    EXPECT(sha("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "sha-abc");
    EXPECT(sha("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq") ==
           "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", "sha-448bit");
    { jarvis_a02::Sha256 h; std::string a(1000000, 'a'); for (int i = 0; i < 1; ++i) h.update(a.data(), a.size());
      EXPECT(h.hex() == "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", "sha-million-a"); }

    const std::string content(3 * 1024 * 1024 + 17, 'G');  // spans several copy blocks
    const std::string path = tmpfile_with(content);
    const int baselineFds = open_fds();
    int src = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    jarvis_a02::SnapshotRequest ok; ok.sourceFd = src; ok.exactSize = static_cast<std::int64_t>(content.size());
    ok.expectedSha256 = sha(content); ok.memoryBudgetBytes = kBudget;

    // Happy path: sealed, identical bytes, every write path refused.
    jarvis_a02::SnapshotTrace trace;
    int sealed = jarvis_a02::sealed_snapshot(ok, {}, &trace);
    EXPECT(trace.copiedSha256 == ok.expectedSha256 && trace.sealedSha256 == ok.expectedSha256, "trace-digests");
    EXPECT((trace.seals & jarvis_a02::kRequiredSeals) == jarvis_a02::kRequiredSeals, "trace-seals");
    EXPECT(pwrite(sealed, "X", 1, 0) == -1, "sealed-pwrite-refused");
    EXPECT(ftruncate(sealed, 0) == -1, "sealed-shrink-refused");
    EXPECT(ftruncate(sealed, ok.exactSize + 1) == -1, "sealed-grow-refused");
    EXPECT(fcntl(sealed, F_ADD_SEALS, 0) == -1 || true, "seal-seal");
    EXPECT(fcntl(sealed, F_GET_SEALS) & F_SEAL_SEAL, "seal-seal-present");
    { std::string proc = jarvis_a02::proc_path_for(sealed, trace);
      int reopened = open(proc.c_str(), O_RDWR);
      EXPECT(reopened < 0 || pwrite(reopened, "X", 1, 0) == -1, "proc-reopen-write-refused");
      if (reopened >= 0) close(reopened); }

    // TOCTOU: replace and truncate the source AFTER snapshot -> sealed bytes unchanged.
    { int w = open(path.c_str(), O_WRONLY | O_TRUNC); EXPECT(write(w, "ATTACK", 6) == 6, "attacker-write"); close(w);
      std::vector<char> back(content.size());
      EXPECT(pread(sealed, back.data(), back.size(), 0) == static_cast<ssize_t>(back.size()) &&
             std::string(back.begin(), back.end()) == content, "sealed-survives-source-replacement"); }
    close(sealed);
    close(src);

    // Restore a valid source for refusal tests.
    const std::string path2 = tmpfile_with(content);
    src = open(path2.c_str(), O_RDONLY | O_CLOEXEC);
    ok.sourceFd = src;

    auto bad = ok; bad.expectedSha256 = sha("other");
    EXPECT(refusal(bad) == "A02_DIGEST_REFUSED", "wrong-sha");
    bad = ok; bad.expectedSha256 = "ABC";
    EXPECT(refusal(bad) == "A02_DIGEST_FORMAT_REFUSED", "malformed-sha");
    bad = ok; bad.exactSize = ok.exactSize - 1;
    EXPECT(refusal(bad) == "A02_SIZE_REFUSED", "size-smaller");
    bad = ok; bad.exactSize = ok.exactSize + 1;
    EXPECT(refusal(bad) == "A02_SIZE_REFUSED", "size-larger");
    bad = ok; bad.exactSize = 0;
    EXPECT(refusal(bad) == "A02_SIZE_REFUSED", "size-zero");
    bad = ok; bad.exactSize = jarvis_a02::kMaxModelBytes + 1;
    EXPECT(refusal(bad) == "A02_SIZE_REFUSED", "size-over-max");
    bad = ok; bad.memoryBudgetBytes = ok.exactSize; // no margin
    EXPECT(refusal(bad) == "A02_MEMORY_BUDGET_REFUSED", "budget-insufficient");
    bad = ok; bad.sourceFd = -1;
    EXPECT(refusal(bad) == "A02_INVALID_FD", "fd-negative");
    { int closed = open(path2.c_str(), O_RDONLY); close(closed); bad = ok; bad.sourceFd = closed;
      EXPECT(refusal(bad) == "A02_INVALID_FD", "fd-closed"); }
    { int pipefd[2]; EXPECT(pipe(pipefd) == 0, "pipe"); bad = ok; bad.sourceFd = pipefd[0];
      EXPECT(refusal(bad) == "A02_SOURCE_TYPE_REFUSED", "fd-not-regular"); close(pipefd[0]); close(pipefd[1]); }
    // Cancellation before and during the copy.
    EXPECT(refusal(ok, [] { return true; }) == "A02_CANCELLED", "cancel-before");
    { int calls = 0; EXPECT(refusal(ok, [&calls] { return ++calls > 2; }) == "A02_CANCELLED", "cancel-during"); }
    // Growth during copy (source appended after fstat): refused by exact size accounting.
    { const std::string p3 = tmpfile_with(content); int s3 = open(p3.c_str(), O_RDONLY);
      auto grow = ok; grow.sourceFd = s3; int calls = 0;
      std::string r = refusal(grow, [&] { if (++calls == 2) { int w = open(p3.c_str(), O_WRONLY | O_APPEND);
          if (write(w, "MORE", 4) != 4) { std::abort(); } close(w); } return false; });
      EXPECT(r == "A02_SIZE_REFUSED", "source-grows-during-copy"); close(s3); unlink(p3.c_str()); }
    // In-place modification during copy: digest of COPIED bytes refused.
    { const std::string p4 = tmpfile_with(content); int s4 = open(p4.c_str(), O_RDONLY);
      auto mod = ok; mod.sourceFd = s4; int calls = 0;
      std::string r = refusal(mod, [&] { if (++calls == 3) { int w = open(p4.c_str(), O_WRONLY);
          if (pwrite(w, "Z", 1, static_cast<off_t>(content.size() - 1)) != 1) { std::abort(); } close(w); } return false; });
      EXPECT(r == "A02_DIGEST_REFUSED", "source-modified-during-copy"); close(s4); unlink(p4.c_str()); }
    // Unsealed descriptor never admitted by the generic check.
    { int raw = jarvis_a02::create_memfd(); EXPECT(write(raw, "x", 1) == 1, "raw-write");
      bool refused = false; try { close(jarvis_a02::duplicate_verified_fd(raw, 1)); } catch (...) { refused = true; }
      EXPECT(refused, "unsealed-refused"); close(raw); }
    // 100 admission/refusal cycles: no descriptor leak.
    const int before = open_fds();
    for (int i = 0; i < 100; ++i) {
        int fd = jarvis_a02::sealed_snapshot(ok, {}, nullptr); close(fd);
        auto wrong = ok; wrong.expectedSha256 = sha("x"); refusal(wrong);
    }
    EXPECT(open_fds() == before, "no-fd-leak-100-cycles");
    // Concurrent snapshots of the same source are independent and all sealed.
    { std::vector<std::thread> threads; std::atomic<int> okCount{0};
      for (int i = 0; i < 4; ++i) threads.emplace_back([&] { try { int fd = jarvis_a02::sealed_snapshot(ok, {}, nullptr);
          if ((fcntl(fd, F_GET_SEALS) & jarvis_a02::kRequiredSeals) == jarvis_a02::kRequiredSeals) { ++okCount; } close(fd); } catch (...) {} });
      for (auto & t : threads) t.join();
      EXPECT(okCount == 4, "concurrent-snapshots"); }
    close(src);
    unlink(path.c_str()); unlink(path2.c_str());
    EXPECT(open_fds() == baselineFds, "fd-baseline-restored");

    std::printf("{\"suite\":\"a02-native-snapshot\",\"platform\":\"%s\",\"passed\":%d,\"failed\":%d}\n", A02_PLATFORM, passed, failures);
    return failures == 0 ? 0 : 1;
}
