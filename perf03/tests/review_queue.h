#pragma once
// Nonproductive reference model for the candidate contract. This is not a JNI
// implementation, engine admission authority, runtime adapter or benchmark.
#include "../candidate/native/stream_support.h"
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <tuple>

namespace jarvis::perf03::review {
struct Identity {
  std::string request, session, epoch, lease, model, mandate, prepared;
  std::uint64_t generation;
  bool operator==(const Identity& other) const {
    return std::tie(request, session, epoch, lease, model, mandate, prepared, generation) ==
           std::tie(other.request, other.session, other.epoch, other.lease,
                    other.model, other.mandate, other.prepared, other.generation);
  }
};
enum class Kind { Ack, Token, Completed, Cancelled, Error };
struct Event {
  Identity identity;
  std::uint64_t sequence;
  Kind kind;
  std::string bytes;
  bool operator==(const Event& other) const {
    return identity == other.identity && sequence == other.sequence &&
           kind == other.kind && bytes == other.bytes;
  }
};
enum class Offer { Accepted, Duplicate, Backpressure, Stopped, Terminal, Invalid, TimedOut };

class Queue {
 public:
  explicit Queue(Identity expected) : expected_(std::move(expected)) {}
  Offer offer(const Event& event) {
    std::lock_guard<std::mutex> guard(mutex_);
    return locked_offer(event);
  }
  Offer offer_wait(const Event& event, const Cancellation& cancellation,
                   std::chrono::steady_clock::time_point deadline) {
    std::unique_lock<std::mutex> lock(mutex_);
    while (true) {
      if (cancellation.stopped()) { locked_stop(); return Offer::Stopped; }
      const auto result = locked_offer(event);
      if (result != Offer::Backpressure) return result;
      const auto now = std::chrono::steady_clock::now();
      if (now >= deadline) return Offer::TimedOut;
      // No engine/model ownership mutex is held. Interrupt latency is bounded
      // by this polling interval if an external stop does not signal this CV.
      const auto next = now + std::chrono::milliseconds(5);
      changed_.wait_until(lock, next < deadline ? next : deadline);
    }
  }
  bool poll(Event& event) {
    std::lock_guard<std::mutex> guard(mutex_);
    if (queue_.empty()) return false;
    event = std::move(queue_.front()); queue_.pop_front();
    pending_ -= event.bytes.size(); changed_.notify_all(); return true;
  }
  void stop() { std::lock_guard<std::mutex> guard(mutex_); locked_stop(); }
  void process_died() {
    std::lock_guard<std::mutex> guard(mutex_); invalidate();
  }
  std::size_t pending_bytes() const {
    std::lock_guard<std::mutex> guard(mutex_); return pending_;
  }
  std::size_t pending_count() const {
    std::lock_guard<std::mutex> guard(mutex_); return queue_.size();
  }
  std::size_t cumulative_bytes() const {
    std::lock_guard<std::mutex> guard(mutex_); return cumulative_;
  }
  bool unknown() const {
    std::lock_guard<std::mutex> guard(mutex_); return unknown_;
  }
 private:
  Offer locked_offer(const Event& event) {
    if (unknown_) return Offer::Invalid;
    if (!(event.identity == expected_)) return invalidate();
    if (has_last_ && event.sequence == last_.sequence)
      return event == last_ ? Offer::Duplicate : invalidate();
    if (terminal_) return Offer::Terminal;
    if (stopped_ && event.kind != Kind::Cancelled && event.kind != Kind::Error)
      return Offer::Stopped;
    if (event.sequence != next_ || (next_ == 0) != (event.kind == Kind::Ack))
      return invalidate();
    const bool text = event.kind == Kind::Token;
    const bool terminal = event.kind == Kind::Completed ||
                          event.kind == Kind::Cancelled || event.kind == Kind::Error;
    if ((text && (event.bytes.empty() || event.bytes.size() > kMaxChunkBytes)) ||
        (!text && !event.bytes.empty()) ||
        event.bytes.size() > kMaxTextBytes - cumulative_ ||
        event.sequence == UINT64_MAX) return invalidate();
    if (!terminal && (queue_.size() >= kMaxPendingEvents - 1 ||
                      event.bytes.size() > kMaxPendingBytes - pending_))
      return Offer::Backpressure;
    if (terminal && queue_.size() >= kMaxPendingEvents) return invalidate();
    queue_.push_back(event); pending_ += event.bytes.size();
    cumulative_ += event.bytes.size(); last_ = event; has_last_ = true;
    ++next_; terminal_ = terminal; changed_.notify_all();
    return Offer::Accepted;
  }
  Offer invalidate() {
    unknown_ = true; queue_.clear(); pending_ = 0; changed_.notify_all();
    return Offer::Invalid;
  }
  void locked_stop() { stopped_ = true; queue_.clear(); pending_ = 0; changed_.notify_all(); }
  Identity expected_;
  mutable std::mutex mutex_;
  std::condition_variable changed_;
  std::deque<Event> queue_;
  Event last_{};
  std::uint64_t next_ = 0;
  std::size_t pending_ = 0, cumulative_ = 0;
  bool has_last_ = false, terminal_ = false, stopped_ = false, unknown_ = false;
};

enum class Lifecycle { Active, ContentDone, Drained, Unloaded, Unknown, CleanupFailed };
class ReceiptModel {
 public:
  void content_done() { if (state_ == Lifecycle::Active) state_ = Lifecycle::ContentDone; }
  void native_and_callbacks_returned() {
    if (state_ == Lifecycle::CleanupFailed) return;
    if (state_ == Lifecycle::ContentDone) state_ = Lifecycle::Drained;
    else if (state_ != Lifecycle::Drained && state_ != Lifecycle::Unloaded) state_ = Lifecycle::Unknown;
  }
  void verified_handle_empty_and_cleanup_succeeded() {
    if (state_ == Lifecycle::CleanupFailed) return;
    if (state_ == Lifecycle::Drained) state_ = Lifecycle::Unloaded;
    else if (state_ != Lifecycle::Unloaded) state_ = Lifecycle::Unknown;
  }
  void cleanup_failed() { state_ = Lifecycle::CleanupFailed; }
  void process_died() { if (state_ != Lifecycle::CleanupFailed) state_ = Lifecycle::Unknown; }
  Lifecycle state() const { return state_; }
 private:
  Lifecycle state_ = Lifecycle::Active;
};
} // namespace jarvis::perf03::review
