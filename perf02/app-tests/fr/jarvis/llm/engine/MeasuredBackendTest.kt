package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.llm.ProcessResources
import fr.jarvis.perf.*
import fr.jarvis.perf.Residency as TraceResidency
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real historical Kotlin worker/broker, synthetic NativeApi. NEVER real JNI or Android generation evidence. */
class MeasuredBackendTest {
    private val config = RuntimeConfig(ModelKind.SMALL, 512, 8, 1)
    private val request = GenerationRequest(listOf(Message("user", "SYNTHETIC prompt not logged")), config)
    private fun trace(generation: Long = 1): LatencyTrace {
        val k = TraceKey(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), generation,
            UUID.randomUUID().toString(), "c".repeat(64))
        return LatencyTrace(k, MonotonicClock { System.nanoTime() }, ClockKind.HOST_NANO_TIME, Origin.SYNTHETIC, TraceResidency.UNKNOWN)
            .also { it.record(k, Stage.UI_SEND) }
    }
    private class Native : NativeApi {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        var blocked = false; var creates = 0; var loads = 0; var calls = 0; var cancels = 0; var destroys = 0
        var fail = false; var system: ByteArray? = null
        val workerIds = mutableListOf<Long>()
        override fun create(): Long { creates++; return 1 }
        override fun begin(handle: Long) {}
        override fun cancel(handle: Long) { cancels++ }
        override fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean) { loads++ }
        override fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int): ByteArray {
            calls++; workerIds.add(Thread.currentThread().id); entered.countDown()
            if (blocked) check(release.await(5, TimeUnit.SECONDS))
            check(!fail) { "synthetic fault not exported" }
            return "SYNTHETIC complete response — not tokens".toByteArray()
        }
        override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray {
            this.system = system; return generate(handle, roles, texts, maxTokens)
        }
        override fun allocations(handle: Long) = longArrayOf(1, 2, 3)
        override fun unload(handle: Long) {}
        override fun destroy(handle: Long) { destroys++ }
    }
    @Test fun preservesFinalStringAndNeverManufacturesTokens() = runBlocking {
        val t = trace(); val native = Native(); val actual = LlamaBackend(MeasuredNativeApi(native, t)); val observed = MeasuredBackend(actual, t)
        try {
            observed.prepareOperation(); observed.load("synthetic-path-never-exported", config)
            val result = observed.generate(request)
            assertEquals("SYNTHETIC complete response — not tokens", result)
            val s = t.snapshot(); assertEquals(Origin.SYNTHETIC, s.origin); assertEquals(Status.UNSUPPORTED, s.nativeFirstToken)
            assertTrue(Stage.NATIVE_ENTER in s.events && Stage.NATIVE_RETURN in s.events && Stage.BACKEND_RETURN in s.events)
            assertFalse(s.toString().contains("synthetic-path")); assertFalse(s.toString().contains("response"))
            assertEquals(1, native.creates); assertEquals(1, native.calls)
        } finally { actual.close() }
    }
    @Test fun warmProbeReusesOneEngineWorkerAndNativeLoad() = runBlocking {
        val first = trace(); val slot = AtomicReference(first); val native = Native()
        val actual = LlamaBackend(MeasuredNativeApi(native) { slot.get() })
        try {
            for (t in listOf(first, trace(2))) {
                slot.set(t); val observed = MeasuredBackend(actual, t)
                observed.prepareOperation(); observed.load("synthetic", config); observed.generate(request)
            }
            assertEquals(1, native.creates); assertEquals(1, native.loads); assertEquals(2, native.calls)
            assertEquals(1, native.workerIds.distinct().size)
            assertFalse(Stage.MODEL_LOAD_ENTER in slot.get().snapshot().events)
            assertEquals(Status.NOT_RUN, slot.get().snapshot().duration(Stage.MODEL_LOAD_ENTER, Stage.MODEL_LOAD_RETURN).status)
        } finally { actual.close() }
    }
    @Test fun stopDoesNotPretendNativeReturnedOrReleaseTheExistingLease() = runBlocking {
        val t = trace(); val native = Native().apply { blocked = true }
        val actual = LlamaBackend(MeasuredNativeApi(native, t)); val observed = MeasuredBackend(actual, t)
        try {
            observed.prepareOperation(); observed.load("synthetic", config)
            val result = async(Dispatchers.Default) { runCatching { observed.generate(request) } }
            try {
                assertTrue(native.entered.await(3, TimeUnit.SECONDS)); observed.requestStop()
                assertFalse(Stage.NATIVE_RETURN in t.snapshot().events)
                assertNotNull(ProcessResources.coordinator.snapshot().heavy)
                assertNotNull(ProcessResources.coordinator.snapshot().model)
                assertEquals(Status.UNKNOWN, t.snapshot().nativeDrain)
            } finally { native.release.countDown() }
            result.await(); assertTrue(Stage.NATIVE_RETURN in t.snapshot().events)
            assertNull(ProcessResources.coordinator.snapshot().heavy); assertNotNull(ProcessResources.coordinator.snapshot().model)
            actual.unload(); assertNull(ProcessResources.coordinator.snapshot().model)
            assertEquals(Status.UNKNOWN, t.snapshot().nativeUnload)
        } finally { native.release.countDown(); actual.close() }
    }
    @Test fun nativeErrorRecordsActualReturnButNotSuccessOrText() = runBlocking {
        val t = trace(); val native = Native().apply { fail = true }; val actual = LlamaBackend(MeasuredNativeApi(native, t))
        try {
            val observed = MeasuredBackend(actual, t); observed.prepareOperation(); observed.load("synthetic", config)
            assertTrue(runCatching { observed.generate(request) }.isFailure)
            assertTrue(Stage.NATIVE_RETURN in t.snapshot().events); assertFalse(Stage.COMPLETED in t.snapshot().events)
            assertEquals(TraceState.UNKNOWN, t.snapshot().state); assertEquals(Failure.BACKEND_ERROR, t.snapshot().failure)
        } finally { actual.close() }
    }
    @Test fun observerClockFailureDoesNotReplaceDelegateBehaviorOrStop() = runBlocking {
        val key = trace().key
        val t = LatencyTrace(key, MonotonicClock { error("synthetic clock failure") }, ClockKind.SYNTHETIC, Origin.SYNTHETIC, TraceResidency.UNKNOWN)
        t.record(key, Stage.UI_SEND)
        val native = Native(); val actual = LlamaBackend(MeasuredNativeApi(native, t)); val observed = MeasuredBackend(actual, t)
        try {
            observed.prepareOperation(); observed.load("synthetic", config)
            assertEquals("SYNTHETIC complete response — not tokens", observed.generate(request))
            observed.requestStop(); assertEquals(1, native.cancels)
            assertEquals(TraceState.UNKNOWN, t.snapshot().state)
        } finally { actual.close() }
    }
    @Test fun separateSystemPromptIsNotChangedByInstrumentation() = runBlocking {
        val t = trace(); val native = Native(); val actual = LlamaBackend(MeasuredNativeApi(native, t))
        try {
            val observed = MeasuredBackend(actual, t); observed.prepareOperation(); observed.load("synthetic", config)
            observed.generate(GenerationRequest(request.messages, config, "Synthetic CORE instructions"))
            assertEquals("Synthetic CORE instructions", native.system!!.toString(Charsets.UTF_8))
            assertEquals(1, native.calls)
        } finally { actual.close() }
    }
    @Test fun frameIsNotCountedAtTextAssignment() {
        val t = trace(); listOf(Stage.ENGINE_ENTER, Stage.NATIVE_ENTER, Stage.NATIVE_RETURN, Stage.BACKEND_RETURN).forEach { t.record(t.key, it) }
        var commit: (() -> Unit)? = null
        val probe = FirstRenderProbe(t, FrameCommitPort { commit = it; true })
        assertEquals(Disposition.ACCEPTED, probe.textSet(t.key, true)); assertFalse(Stage.UI_FIRST_RENDER in t.snapshot().events)
        commit!!(); assertTrue(Stage.UI_FIRST_RENDER in t.snapshot().events)
        assertEquals(Disposition.DUPLICATE, probe.textSet(t.key, true))
    }
    @Test fun emptyAckAndUnavailableFrameAreNotFirstDisplay() {
        val t = trace(); listOf(Stage.ENGINE_ENTER, Stage.NATIVE_ENTER, Stage.NATIVE_RETURN).forEach { t.record(t.key, it) }
        val probe = FirstRenderProbe(t, FrameCommitPort { false })
        assertEquals(Disposition.REJECTED, probe.textSet(t.key, false))
        assertEquals(Disposition.UNSUPPORTED, probe.textSet(t.key, true))
        assertEquals(Status.NOT_RUN, t.snapshot().duration(Stage.UI_SEND, Stage.UI_FIRST_RENDER).status)
    }
    @Test fun lateFrameAfterStopCannotBecomeFirstRender() {
        val t = trace(); listOf(Stage.ENGINE_ENTER, Stage.NATIVE_ENTER, Stage.NATIVE_RETURN).forEach { t.record(t.key, it) }
        var commit: (() -> Unit)? = null; val probe = FirstRenderProbe(t, FrameCommitPort { commit = it; true })
        probe.textSet(t.key, true); t.record(t.key, Stage.STOP_REQUESTED); commit!!()
        assertFalse(Stage.UI_FIRST_RENDER in t.snapshot().events)
    }
}
