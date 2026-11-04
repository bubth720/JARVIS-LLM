package fr.jarvis.perf

import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Real host monotone measurements of OBSERVER SOFTWARE ONLY. No model, token, UI or Android measurement. */
class ObserverOverheadTest {
    companion object { @Volatile private var sink = 0L }
    @Test fun measureObserverCostSeparatelyFromGeneration() {
        val key = TraceKey(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
            UUID.randomUUID().toString(), "a".repeat(64))
        val scope = UUID.randomUUID().toString()
        fun instrumented(repeats: Int) {
            val t = LatencyTrace(key, MonotonicClock { System.nanoTime() }, ClockKind.HOST_NANO_TIME, Origin.SYNTHETIC, Residency.UNKNOWN, clockScope = scope)
            t.record(key, Stage.UI_SEND); repeat(repeats) { t.record(key, Stage.UI_SEND) }
            sink = t.snapshot().events.size.toLong()
        }
        fun baseline(repeats: Int) { var n = 0L; repeat(repeats) { n++ }; sink = n }
        // Same JVM, alternating lanes; warm up JIT but never call this a warm model.
        repeat(2000) { baseline(128); instrumented(128) }
        val rows = mutableListOf<String>()
        for (size in listOf(16, 128)) {
            val a = mutableListOf<Long>(); val b = mutableListOf<Long>()
            repeat(40) { sample ->
                fun timed(block: () -> Unit): Long { val start = System.nanoTime(); repeat(100) { block() }; return (System.nanoTime() - start) / 100 }
                if (sample % 2 == 0) { a += timed { baseline(size) }; b += timed { instrumented(size) } }
                else { b += timed { instrumented(size) }; a += timed { baseline(size) } }
            }
            fun stats(values: List<Long>) = "{\"n\":${values.size},\"p50_ns\":${Quantiles.nearestRank(values, 50)},\"p95_ns\":${Quantiles.nearestRank(values, 95)},\"max_ns\":${values.max()}}"
            rows += "{\"duplicate_marks_per_request\":$size,\"baseline_no_observer\":${stats(a)},\"candidate_observer\":${stats(b)}}"
            assertEquals(40, a.size); assertEquals(40, b.size)
        }
        val target = File("build/perf02/host-observer-overhead.json"); target.parentFile.mkdirs()
        target.writeText("{\"schema\":\"jarvis.perf02.host-observer-overhead/1\",\"scope\":\"HOST_SOFTWARE_MICROBENCH_ONLY\",\"clock\":\"System.nanoTime\",\"quantile\":\"nearest_rank\",\"real_model_generation\":\"NOT_RUN\",\"physical_android\":\"NOT_RUN\",\"cases\":[${rows.joinToString(",")}]}\n")
    }
}
