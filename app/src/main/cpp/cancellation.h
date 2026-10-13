#pragma once
#include <atomic>
#include <chrono>

class Cancellation {
    std::atomic<bool> stop_{false};
    std::atomic<int64_t> deadline_{0};
    static int64_t now() {
        return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    }
public:
    void begin(int64_t timeout_ms) {
        deadline_.store(now() + timeout_ms);
        stop_.store(false);
    }
    void request() { stop_.store(true); }
    bool stopped() const { return stop_.load() || (deadline_.load() > 0 && now() >= deadline_.load()); }
    static bool abort(void * data) { return static_cast<Cancellation *>(data)->stopped(); }
    static bool progress(float, void * data) { return !abort(data); }
};
