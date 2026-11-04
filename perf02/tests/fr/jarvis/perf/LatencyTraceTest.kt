package fr.jarvis.perf

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class LatencyTraceTest {
    private fun key(generation: Long = 1) = TraceKey(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        UUID.randomUUID().toString(), generation, UUID.randomUUID().toString(), "a".repeat(64))
    private fun trace(key: TraceKey = key(), clock: MonotonicClock = MonotonicClock { System.nanoTime() }, stream: Boolean = false,
                      core: Boolean = false, origin: Origin = Origin.SYNTHETIC) =
        LatencyTrace(key, clock, ClockKind.SYNTHETIC, origin, Residency.UNKNOWN, stream, fixtureCore = core)
    private fun sequence(t: LatencyTrace) = listOf(Stage.UI_SEND, Stage.ENGINE_ENTER, Stage.NATIVE_ENTER,
        Stage.NATIVE_RETURN, Stage.BACKEND_RETURN, Stage.UI_TEXT_SET, Stage.UI_FIRST_RENDER, Stage.COMPLETED)
        .forEach { assertEquals(it.name, Disposition.ACCEPTED, t.record(t.key, it)) }

    @Test fun monotoneUsesDurationsAndAllowsNegativeNanoTimeOrigin() {
        var n = -100L; val t = trace(clock = MonotonicClock { n++ }); sequence(t)
        assertEquals(DurationValue(Status.OBSERVED, 7), t.snapshot().duration(Stage.UI_SEND, Stage.COMPLETED))
    }
    @Test fun backwardsClockInvalidatesWithoutZero() {
        var n = 10L; val t = trace(clock = MonotonicClock { n }); t.record(t.key, Stage.UI_SEND); n--
        assertEquals(Disposition.REJECTED, t.record(t.key, Stage.ENGINE_ENTER))
        assertEquals(DurationValue(Status.UNKNOWN), t.snapshot().duration(Stage.UI_SEND, Stage.COMPLETED))
    }
    @Test fun brokenClockIsUnknown() {
        val t = trace(clock = MonotonicClock { error("not exported") }); t.record(t.key, Stage.UI_SEND)
        assertEquals(Failure.CLOCK_ERROR, t.snapshot().failure)
    }
    @Test fun overflowClockIsUnknown() {
        var n = Long.MIN_VALUE; val t = trace(clock = MonotonicClock { n }); t.record(t.key, Stage.UI_SEND); n = Long.MAX_VALUE
        assertEquals(Disposition.REJECTED, t.record(t.key, Stage.ENGINE_ENTER))
    }
    @Test fun legacyCannotInventNativeFirstTokenFromFinalString() {
        val t = trace(origin = Origin.REAL_MODEL); sequence(t)
        assertEquals(Status.UNSUPPORTED, t.snapshot().nativeFirstToken)
        assertEquals(DurationValue(Status.UNSUPPORTED), t.snapshot().duration(Stage.UI_SEND, Stage.NATIVE_FIRST_TOKEN))
    }
    @Test fun unsupportedCoreIsNeverAnAck() {
        val t = trace(); t.record(t.key, Stage.UI_SEND)
        assertEquals(Disposition.UNSUPPORTED, t.record(t.key, Stage.CORE_ACCEPT))
        assertEquals(Status.UNSUPPORTED, t.snapshot().coreAcceptance)
    }
    @Test fun noTokenIsNotZero() {
        val t = trace(stream = true); t.record(t.key, Stage.UI_SEND)
        assertEquals(Status.NOT_RUN, t.snapshot().nativeFirstToken)
        assertNull(t.snapshot().duration(Stage.UI_SEND, Stage.NATIVE_FIRST_TOKEN).nanos)
    }
    @Test fun fixtureCoreIsExplicitAndOrdered() {
        val t = trace(core = true); t.record(t.key, Stage.UI_SEND)
        assertEquals(Disposition.REJECTED, t.record(t.key, Stage.ENGINE_ENTER))
    }
    @Test fun realOriginCannotEnableFixtureStream() {
        assertThrows(IllegalArgumentException::class.java) { trace(origin = Origin.REAL_MODEL, stream = true) }
    }
    @Test fun realOriginCannotInventCore() {
        assertThrows(IllegalArgumentException::class.java) { trace(origin = Origin.REAL_MODEL, core = true) }
    }
    @Test fun renderNeedsActualAvailableText() {
        val t = trace(); t.record(t.key, Stage.UI_SEND); t.record(t.key, Stage.ENGINE_ENTER)
        assertEquals(Disposition.REJECTED, t.record(t.key, Stage.UI_FIRST_RENDER))
    }
    @Test fun duplicateDoesNotResetTime() {
        val n = AtomicLong(); val t = trace(clock = MonotonicClock { n.incrementAndGet() })
        t.record(t.key, Stage.UI_SEND); repeat(2000) { assertEquals(Disposition.DUPLICATE, t.record(t.key, Stage.UI_SEND)) }
        assertEquals(1, t.snapshot().events.size); assertEquals(1L, n.get())
    }
    @Test fun concurrentDuplicateEmitHasOneTimestamp() {
        val t = trace(); val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(4)
        val results = (1..16).map { pool.submit<Disposition> { start.await(); t.record(t.key, Stage.UI_SEND) } }
        start.countDown(); assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) == Disposition.ACCEPTED })
        pool.shutdownNow(); assertEquals(1, t.snapshot().events.size)
    }
    @Test fun oldEpochAndGenerationCannotChangeCurrentTrace() {
        val t = trace(); assertEquals(Disposition.STALE, t.record(key(2), Stage.UI_SEND)); assertTrue(t.snapshot().events.isEmpty())
    }
    @Test fun stopIsNotDrainUnloadOrCompletion() {
        val t = trace(); t.record(t.key, Stage.UI_SEND); t.record(t.key, Stage.ENGINE_ENTER); t.record(t.key, Stage.NATIVE_ENTER)
        assertEquals(Disposition.ACCEPTED, t.record(t.key, Stage.STOP_REQUESTED))
        assertEquals(Disposition.TERMINAL, t.record(t.key, Stage.UI_TEXT_SET))
        assertEquals(Disposition.ACCEPTED, t.record(t.key, Stage.NATIVE_RETURN))
        assertEquals(Disposition.UNSUPPORTED, t.record(t.key, Stage.DRAIN_RETURN))
        assertEquals(Status.UNKNOWN, t.snapshot().nativeDrain); assertEquals(Status.UNKNOWN, t.snapshot().nativeUnload)
        assertFalse(t.snapshot().mayAdmitAnotherEngine)
    }
    @Test fun noOutputAfterCompletion() {
        val t = trace(); sequence(t); assertEquals(Disposition.TERMINAL, t.record(t.key, Stage.STOP_REQUESTED))
    }
    @Test fun fixtureCannotPretendFirstTokenAfterCompleteNativeReturn() {
        val t = trace(stream = true)
        listOf(Stage.UI_SEND, Stage.ENGINE_ENTER, Stage.NATIVE_ENTER, Stage.NATIVE_RETURN).forEach { t.record(t.key, it) }
        assertEquals(Disposition.REJECTED, t.record(t.key, Stage.NATIVE_FIRST_TOKEN))
    }
    @Test fun unknownNeverBecomesCompletion() {
        val t = trace(); t.failed(t.key, Failure.MEMORY_LIMIT)
        assertEquals(Disposition.TERMINAL, t.record(t.key, Stage.COMPLETED)); assertEquals(TraceState.UNKNOWN, t.snapshot().state)
    }
    @Test fun snapshotCannotMutateCollector() {
        val t = trace(); t.record(t.key, Stage.UI_SEND)
        assertThrows(UnsupportedOperationException::class.java) { (t.snapshot().events as MutableMap).clear() }
        assertEquals(1, t.snapshot().events.size)
    }
    @Test fun exactTokenizerStaysUnknown() {
        assertEquals(Status.UNKNOWN, trace().snapshot().exactInputTokens); assertEquals(Status.UNKNOWN, trace().snapshot().exactOutputTokens)
    }
    @Test fun redactionContainsNeitherIdsNorPayload() {
        val t = trace(); assertFalse(t.toString().contains(t.key.request))
        assertFalse(t.snapshot().toString().contains(t.key.lease)); assertFalse(t.key.toString().contains(t.key.request))
    }
    @Test fun percentileUsesNearestRank() {
        val samples = (1L..20L).reversed().toList(); assertEquals(10L, Quantiles.nearestRank(samples, 50)); assertEquals(19L, Quantiles.nearestRank(samples, 95))
    }
    @Test fun tooFewSamplesCannotClaimP95() { assertNull(Quantiles.nearestRank((1L..19L).toList(), 95)) }
    @Test fun emptyAndInvalidSamplesAreNotZero() {
        assertNull(Quantiles.nearestRank(emptyList(), 50))
        assertThrows(IllegalArgumentException::class.java) { Quantiles.nearestRank(listOf(-1), 95) }
    }
}
