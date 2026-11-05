#include "review_queue.h"
#include <chrono>
#include <future>
#include <iostream>
#include <random>
#include <stdexcept>
#include <thread>

using namespace jarvis::perf03;
using namespace jarvis::perf03::review;
using namespace std::chrono_literals;

#define REQUIRE(value) do { if (!(value)) throw std::runtime_error(#value); } while (false)
template<class E, class F> void throws(F f) {
  try { f(); } catch (const E&) { return; }
  throw std::runtime_error("expected exception absent");
}
static std::string join(const std::vector<std::string>& chunks) {
  std::string out;
  for (const auto& chunk : chunks) {
    REQUIRE(!chunk.empty()); REQUIRE(chunk.size() <= kMaxChunkBytes); out += chunk;
  }
  return out;
}
static Identity identity() {
  return {"request-1", "session-1", "epoch-1", "lease-1", std::string(64, 'a'),
          "mandate-1", std::string(64, 'b'), 1};
}
static Event event(std::uint64_t sequence, Kind kind = Kind::Token, std::string bytes = "x") {
  return {identity(), sequence, kind, std::move(bytes)};
}
static void ack(Queue& q) { REQUIRE(q.offer(event(0, Kind::Ack, "")) == Offer::Accepted); }
static void fill_bytes(Queue& q) {
  ack(q);
  for (std::uint64_t i = 1; i <= 4; ++i)
    REQUIRE(q.offer(event(i, Kind::Token, std::string(4096, 'x'))) == Offer::Accepted);
}
static void ascii_exact() {
  Utf8Assembler a; REQUIRE(join(a.accept("bonjour\t\n\r", 10)) == "bonjour\t\n\r"); a.finish();
}
static void every_unicode_split() {
  const std::string text = "A\xC3\xA9\xE2\x82\xAC\xF0\x9F\xA7\xA0Z";
  for (std::size_t split = 0; split <= text.size(); ++split) {
    Utf8Assembler a;
    auto out = join(a.accept(text.data(), split)); REQUIRE(a.pending_bytes() <= 3);
    out += join(a.accept(text.data() + split, text.size() - split));
    a.finish(); REQUIRE(out == text); REQUIRE(a.accepted_bytes() == text.size());
  }
}
static void byte_by_byte_unicode() {
  Utf8Assembler a; std::string out;
  const std::string text = "\xF0\x9F\xA7\xA0\xE2\x82\xAC\xC3\xA9";
  for (const char& c : text) { out += join(a.accept(&c, 1)); REQUIRE(a.pending_bytes() <= 3); }
  a.finish(); REQUIRE(out == text);
}
static void no_text_for_incomplete_piece() {
  Utf8Assembler a; REQUIRE(a.accept("\xF0\x9F", 2).empty());
  REQUIRE(a.pending_bytes() == 2);
  REQUIRE(join(a.accept("\xA7\xA0", 2)) == "\xF0\x9F\xA7\xA0"); a.finish();
}
static void chunk_boundary_respects_codepoint() {
  Utf8Assembler a; const std::string text = std::string(4095, 'a') + "\xF0\x9F\xA7\xA0";
  const auto chunks = a.accept(text.data(), text.size()); a.finish();
  REQUIRE(chunks.size() == 2); REQUIRE(chunks[0].size() == 4095); REQUIRE(chunks[1].size() == 4);
  REQUIRE(join(chunks) == text);
}
static void exact_text_limit() {
  Utf8Assembler a; const std::string text(kMaxTextBytes, 'a');
  REQUIRE(join(a.accept(text.data(), text.size())) == text); a.finish();
}
static void excess_checked_before_read() {
  Utf8Assembler a; throws<std::length_error>([&] { a.accept(nullptr, kMaxTextBytes + 1); });
  throws<std::logic_error>([&] { a.accept("a", 1); });
}
static void cumulative_limit() {
  Utf8Assembler a; std::string text(kMaxTextBytes - 1, 'a');
  a.accept(text.data(), text.size());
  throws<std::length_error>([&] { a.accept("ab", 2); });
}
static void malformed_utf8() {
  for (const std::string& text : {std::string("\x80"), std::string("\xC0\xAF"),
      std::string("\xE0\x80\xAF"), std::string("\xED\xA0\x80"),
      std::string("\xF4\x90\x80\x80"), std::string("\xF5\x80\x80\x80"),
      std::string("\xC2x"), std::string("\xE0\x80")}) {
    Utf8Assembler a; throws<std::invalid_argument>([&] { a.accept(text.data(), text.size()); });
    throws<std::logic_error>([&] { a.accept("a", 1); });
  }
}
static void controls_rejected() {
  for (const unsigned char c : {0, 1, 8, 11, 12, 31, 127}) {
    Utf8Assembler a; const char text = static_cast<char>(c);
    throws<std::invalid_argument>([&] { a.accept(&text, 1); });
  }
}
static void truncated_terminal() {
  Utf8Assembler a; a.accept("\xE2\x82", 2);
  throws<std::invalid_argument>([&] { a.finish(); });
  throws<std::logic_error>([&] { a.accept("\xAC", 1); });
}
static void terminal_and_null_contract() {
  Utf8Assembler a; REQUIRE(a.accept(nullptr, 0).empty()); a.finish();
  throws<std::logic_error>([&] { a.accept("a", 1); });
  throws<std::logic_error>([&] { a.finish(); });
  Utf8Assembler b; throws<std::invalid_argument>([&] { b.accept(nullptr, 1); });
}
static void seeded_fragment_property() {
  std::mt19937 rng(503);
  const std::vector<std::string> alphabet = {"a", " ", "\n", "\xC3\xA9", "\xE2\x82\xAC", "\xF0\x9F\xA7\xA0"};
  for (int trial = 0; trial < 1000; ++trial) {
    std::string text;
    for (int i = 0; i < 40; ++i) text += alphabet[rng() % alphabet.size()];
    Utf8Assembler a; std::string out;
    for (std::size_t offset = 0; offset < text.size();) {
      const auto length = std::min<std::size_t>(1 + rng() % 7, text.size() - offset);
      out += join(a.accept(text.data() + offset, length)); offset += length;
      REQUIRE(a.pending_bytes() <= 3);
    }
    a.finish(); REQUIRE(out == text);
  }
}
static void correlation_all_fields() {
  for (int field = 0; field < 8; ++field) {
    Queue q(identity()); ack(q); auto changed = event(1);
    switch (field) {
      case 0: changed.identity.request += "x"; break;
      case 1: changed.identity.session += "x"; break;
      case 2: changed.identity.epoch += "x"; break;
      case 3: changed.identity.lease += "x"; break;
      case 4: changed.identity.model[0] = 'b'; break;
      case 5: changed.identity.mandate += "x"; break;
      case 6: changed.identity.prepared[0] = 'c'; break;
      case 7: ++changed.identity.generation; break;
    }
    REQUIRE(q.offer(changed) == Offer::Invalid); REQUIRE(q.unknown()); REQUIRE(q.pending_count() == 0);
  }
}
static void ack_and_order_required() {
  Queue q(identity()); REQUIRE(q.offer(event(1)) == Offer::Invalid);
  Queue q2(identity()); ack(q2); REQUIRE(q2.offer(event(2)) == Offer::Invalid);
}
static void exact_duplicate_only() {
  Queue q(identity()); ack(q); REQUIRE(q.offer(event(1)) == Offer::Accepted);
  REQUIRE(q.offer(event(1)) == Offer::Duplicate); REQUIRE(q.pending_count() == 2);
  REQUIRE(q.offer(event(1, Kind::Token, "changed")) == Offer::Invalid);
}
static void stale_replay_is_unknown() {
  Queue q(identity()); ack(q); q.offer(event(1)); q.offer(event(2));
  REQUIRE(q.offer(event(1)) == Offer::Invalid); REQUIRE(q.unknown());
}
static void event_limit_reserves_terminal() {
  Queue q(identity()); ack(q);
  for (std::uint64_t i = 1; i < 15; ++i) REQUIRE(q.offer(event(i)) == Offer::Accepted);
  REQUIRE(q.pending_count() == 15); REQUIRE(q.offer(event(15)) == Offer::Backpressure);
  REQUIRE(q.offer(event(15, Kind::Completed, "")) == Offer::Accepted);
  REQUIRE(q.pending_count() == 16); REQUIRE(q.offer(event(16)) == Offer::Terminal);
}
static void byte_limit_and_retry_exact() {
  Queue q(identity()); fill_bytes(q); REQUIRE(q.pending_bytes() == 16 * 1024);
  REQUIRE(q.offer(event(5)) == Offer::Backpressure);
  Event out; REQUIRE(q.poll(out)); REQUIRE(q.poll(out));
  REQUIRE(q.offer(event(5)) == Offer::Accepted); REQUIRE(q.pending_bytes() == 12289);
}
static void oversized_and_empty_chunk() {
  Queue q(identity()); ack(q); REQUIRE(q.offer(event(1, Kind::Token, std::string(4097, 'x'))) == Offer::Invalid);
  Queue q2(identity()); ack(q2); REQUIRE(q2.offer(event(1, Kind::Token, "")) == Offer::Invalid);
}
static void cumulative_queue_limit() {
  Queue q(identity()); ack(q); Event out; REQUIRE(q.poll(out));
  for (std::uint64_t i = 1; i <= 16; ++i) {
    REQUIRE(q.offer(event(i, Kind::Token, std::string(4096, 'x'))) == Offer::Accepted);
    REQUIRE(q.poll(out));
  }
  REQUIRE(q.cumulative_bytes() == 65536); REQUIRE(q.offer(event(17)) == Offer::Invalid);
}
static void interrupt_backpressure() {
  Queue q(identity()); fill_bytes(q); Cancellation cancellation;
  auto task = std::async(std::launch::async, [&] { return q.offer_wait(event(5), cancellation, std::chrono::steady_clock::now() + 2s); });
  cancellation.request(); cancellation.request();
  REQUIRE(task.wait_for(500ms) == std::future_status::ready); REQUIRE(task.get() == Offer::Stopped);
  REQUIRE(q.pending_count() == 0); REQUIRE(q.pending_bytes() == 0);
}
static void consumer_unblocks_producer() {
  Queue q(identity()); fill_bytes(q); Cancellation cancellation;
  auto task = std::async(std::launch::async, [&] { return q.offer_wait(event(5), cancellation, std::chrono::steady_clock::now() + 2s); });
  Event out; REQUIRE(q.poll(out)); REQUIRE(q.poll(out));
  REQUIRE(task.wait_for(500ms) == std::future_status::ready); REQUIRE(task.get() == Offer::Accepted);
}
static void backpressure_deadline_no_drop() {
  Queue q(identity()); fill_bytes(q); Cancellation cancellation;
  REQUIRE(q.offer_wait(event(5), cancellation, std::chrono::steady_clock::now()) == Offer::TimedOut);
  REQUIRE(q.pending_bytes() == 16384); REQUIRE(q.cumulative_bytes() == 16384);
}
static void stop_after_queued_terminal() {
  for (const auto terminal : {Kind::Completed, Kind::Cancelled, Kind::Error}) {
    Queue q(identity()); ack(q); q.offer(event(1));
    REQUIRE(q.offer(event(2, terminal, "")) == Offer::Accepted); q.stop(); q.stop();
    REQUIRE(q.pending_count() == 0); REQUIRE(q.offer(event(3)) == Offer::Terminal);
  }
}
static void stop_then_cancel_terminal() {
  Queue q(identity()); ack(q); q.offer(event(1)); q.stop();
  REQUIRE(q.offer(event(2)) == Offer::Stopped);
  REQUIRE(q.offer(event(2, Kind::Cancelled, "")) == Offer::Accepted);
  REQUIRE(q.pending_bytes() == 0);
}
static void process_death_quarantines_late_events() {
  Queue q(identity()); ack(q); q.offer(event(1)); q.process_died();
  REQUIRE(q.unknown()); REQUIRE(q.pending_count() == 0);
  REQUIRE(q.offer(event(2, Kind::Completed, "")) == Offer::Invalid);
}
static void callback_exception_is_not_a_clean_receipt() {
  ReceiptModel state; state.process_died(); state.content_done();
  state.native_and_callbacks_returned(); state.verified_handle_empty_and_cleanup_succeeded();
  REQUIRE(state.state() == Lifecycle::Unknown);
}
static void lifecycle_facts_remain_distinct() {
  ReceiptModel state; state.content_done(); REQUIRE(state.state() == Lifecycle::ContentDone);
  state.native_and_callbacks_returned(); REQUIRE(state.state() == Lifecycle::Drained);
  state.verified_handle_empty_and_cleanup_succeeded(); REQUIRE(state.state() == Lifecycle::Unloaded);
}
static void cleanup_failed_is_sticky() {
  ReceiptModel state; state.content_done(); state.native_and_callbacks_returned(); state.cleanup_failed();
  state.native_and_callbacks_returned(); state.verified_handle_empty_and_cleanup_succeeded(); state.process_died();
  REQUIRE(state.state() == Lifecycle::CleanupFailed);
}
static void many_requests_are_isolated() {
  for (int i = 0; i < 100; ++i) {
    auto id = identity(); id.generation += i;
    Queue q(id); auto e = event(0, Kind::Ack, ""); e.identity = id;
    REQUIRE(q.offer(e) == Offer::Accepted);
    e.sequence = 1; e.kind = Kind::Token; e.bytes = "x"; REQUIRE(q.offer(e) == Offer::Accepted);
    q.stop(); e.sequence = 2; e.kind = Kind::Cancelled; e.bytes.clear();
    REQUIRE(q.offer(e) == Offer::Accepted);
  }
}
int main() {
  int passed = 0;
  try {
#define RUN(name) do { name(); ++passed; std::cout << "PASS " #name "\n"; } while (false)
    RUN(ascii_exact); RUN(every_unicode_split); RUN(byte_by_byte_unicode);
    RUN(no_text_for_incomplete_piece); RUN(chunk_boundary_respects_codepoint);
    RUN(exact_text_limit); RUN(excess_checked_before_read); RUN(cumulative_limit);
    RUN(malformed_utf8); RUN(controls_rejected); RUN(truncated_terminal);
    RUN(terminal_and_null_contract); RUN(seeded_fragment_property);
    RUN(correlation_all_fields); RUN(ack_and_order_required); RUN(exact_duplicate_only);
    RUN(stale_replay_is_unknown); RUN(event_limit_reserves_terminal);
    RUN(byte_limit_and_retry_exact); RUN(oversized_and_empty_chunk);
    RUN(cumulative_queue_limit); RUN(interrupt_backpressure);
    RUN(consumer_unblocks_producer); RUN(backpressure_deadline_no_drop);
    RUN(stop_after_queued_terminal); RUN(stop_then_cancel_terminal);
    RUN(process_death_quarantines_late_events); RUN(callback_exception_is_not_a_clean_receipt);
    RUN(lifecycle_facts_remain_distinct); RUN(cleanup_failed_is_sticky);
    RUN(many_requests_are_isolated);
    std::cout << "{\"schema\":\"jarvis.perf03.helper-tests/1\",\"passed\":" << passed
              << ",\"failed\":0,\"jni\":\"NOT_RUN\",\"model\":\"NOT_RUN\",\"ttft\":\"NOT_RUN\"}\n";
    return 0;
  } catch (const std::exception& error) {
    std::cerr << "FAIL after " << passed << " cases: " << error.what() << '\n'; return 1;
  }
}
