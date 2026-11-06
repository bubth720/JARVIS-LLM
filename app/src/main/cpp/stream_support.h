#pragma once
// PERF-03A review source only. No JNI or llama.cpp dependency; never linked by
// the productive build until the exact containing patch is explicitly approved.
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace jarvis::perf03 {
constexpr std::size_t kMaxChunkBytes = 4096;
constexpr std::size_t kMaxTextBytes = 64 * 1024;
constexpr std::size_t kMaxPendingEvents = 16;
constexpr std::size_t kMaxPendingBytes = 16 * 1024;

class Cancellation final {
 public:
  void request() noexcept { stopped_.store(true, std::memory_order_release); }
  bool stopped() const noexcept {
    return stopped_.load(std::memory_order_acquire);
  }
 private:
  std::atomic<bool> stopped_{false};
};

// Raw bytes must originate from actual sampled llama token pieces. The caller
// records token sampling separately: an empty/incomplete UTF-8 piece is not yet
// usable visible text. This helper never fabricates text or slices final output.
class Utf8Assembler final {
 public:
  std::vector<std::string> accept(const char* data, std::size_t size) {
    if (failed_ || finished_) throw std::logic_error("UTF-8 stream closed");
    if (size > kMaxTextBytes - accepted_) {
      failed_ = true;
      throw std::length_error("stream text limit");
    }
    if (size && !data) {
      failed_ = true;
      throw std::invalid_argument("null token piece");
    }
    try {
      std::string bytes = pending_;
      if (size) bytes.append(data, size);
      accepted_ += size;
      pending_.clear();
      std::vector<std::string> chunks;
      std::string chunk;
      chunk.reserve(kMaxChunkBytes);
      std::size_t offset = 0;
      while (offset < bytes.size()) {
        const auto lead = static_cast<unsigned char>(bytes[offset]);
        std::size_t count;
        std::uint32_t value;
        if (lead < 0x80) { count = 1; value = lead; }
        else if (lead >= 0xC2 && lead <= 0xDF) { count = 2; value = lead & 0x1F; }
        else if (lead >= 0xE0 && lead <= 0xEF) { count = 3; value = lead & 0x0F; }
        else if (lead >= 0xF0 && lead <= 0xF4) { count = 4; value = lead & 0x07; }
        else throw std::invalid_argument("invalid UTF-8 lead");
        const auto available = bytes.size() - offset;
        const auto present = available < count ? available : count;
        for (std::size_t i = 1; i < present; ++i) {
          const auto next = static_cast<unsigned char>(bytes[offset + i]);
          if ((next & 0xC0) != 0x80)
            throw std::invalid_argument("invalid UTF-8 continuation");
          if (i == 1 && ((lead == 0xE0 && next < 0xA0) ||
                         (lead == 0xED && next >= 0xA0) ||
                         (lead == 0xF0 && next < 0x90) ||
                         (lead == 0xF4 && next >= 0x90)))
            throw std::invalid_argument("non-scalar UTF-8 prefix");
          value = (value << 6) | (next & 0x3F);
        }
        if (available < count) {
          pending_.assign(bytes, offset, available); // at most three bytes
          break;
        }
        if ((value < 0x20 && value != 9 && value != 10 && value != 13) || value == 0x7F)
          throw std::invalid_argument("unsupported text control");
        if (chunk.size() + count > kMaxChunkBytes) {
          chunks.push_back(std::move(chunk));
          chunk.clear();
          chunk.reserve(kMaxChunkBytes);
        }
        chunk.append(bytes, offset, count);
        offset += count;
      }
      if (!chunk.empty()) chunks.push_back(std::move(chunk));
      return chunks;
    } catch (...) {
      failed_ = true;
      pending_.clear();
      throw;
    }
  }
  void finish() {
    if (failed_ || finished_) throw std::logic_error("UTF-8 stream closed");
    if (!pending_.empty()) {
      failed_ = true;
      pending_.clear();
      throw std::invalid_argument("truncated UTF-8 terminal");
    }
    finished_ = true;
  }
  std::size_t accepted_bytes() const noexcept { return accepted_; }
  std::size_t pending_bytes() const noexcept { return pending_.size(); }
 private:
  std::string pending_;
  std::size_t accepted_ = 0;
  bool failed_ = false;
  bool finished_ = false;
};
} // namespace jarvis::perf03
