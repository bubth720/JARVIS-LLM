package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.llm.ProcessResources
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Nonproductive JVM fault injection. Never JNI, model generation, Android latency or proof of CORE authority. */
class OwnedStreamBackendTest {
    private val config = RuntimeConfig(ModelKind.SMALL, 512, 8, 1)
    private val request = GenerationRequest(listOf(Message("user", "Synthetic public fixture")), config, "Synthetic instructions")
    private fun id() = UUID.randomUUID().toString()
    private fun authority(generation: Long = 1): StreamOwnerAuthority = object : StreamOwnerAuthority {
        override fun authorize(prepared: StreamPrepared, resident: StreamResident) =
            StreamGrant(id(), id(), id(), generation, id(), "a".repeat(64), "b".repeat(64), prepared, resident, StreamRevocation())
    }
    private class Native : NativeApi {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        var block = false; var corruptPrepared = false; var oom = false; var stop = false
        var skipCallbacks = false; var mismatchedTerminal = false; var callbackFaultAfterChunk = false
        var retainedCallback: OwnedStreamCallback? = null; var retainedBinding: ByteArray? = null
        var creates = 0; var loads = 0; var streamCalls = 0; var legacyCalls = 0; var cancels = 0; var destroys = 0
        val threads = mutableListOf<Long>()
        private val prompt = "SYNTHETIC canonical prompt".toByteArray()
        private val template = "SYNTHETIC template".toByteArray()
        override fun create(): Long { creates++; return 1 }
        override fun begin(handle: Long) { stop = false }
        override fun cancel(handle: Long) { cancels++; stop = true }
        override fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean) { loads++ }
        override fun measureFixtureP2(handle: Long, system: ByteArray, question: ByteArray) = arrayOf("10".toByteArray(), prompt.copyOf(), template.copyOf())
        override fun generateStream(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int,
                                    binding: ByteArray, callback: OwnedStreamCallback): Int {
            streamCalls++; threads.add(Thread.currentThread().id)
            retainedCallback = callback; retainedBinding = binding.copyOf()
            if (skipCallbacks) return 2
            if (oom) throw OutOfMemoryError("SYNTHETIC fault")
            if (!callback.onNativePrepared(if (corruptPrepared) "other".toByteArray() else prompt, template, 10)) {
                assertEquals(0, callback.onNativeEvent(binding, 4, 0, byteArrayOf(), 0, -1, 1)); return 4
            }
            assertEquals(0, callback.onNativeEvent(binding, 0, 0, byteArrayOf(), 0, -1, 1))
            entered.countDown()
            if (block) check(release.await(5, TimeUnit.SECONDS))
            if (stop) {
                assertEquals(0, callback.onNativeEvent(binding, 3, 1, byteArrayOf(), 0, -1, 1)); return 3
            }
            assertEquals(0, callback.onNativeEvent(binding, 1, 1, "SYNTHETIC é".toByteArray(), 1, 100, 1))
            if (callbackFaultAfterChunk) callback.onNativeEvent(byteArrayOf(1), 1, 2, "SYNTHETIC invalid binding".toByteArray(), 2, 100, 1)
            assertEquals(0, callback.onNativeEvent(binding, 2, 2, byteArrayOf(), 1, 100, 1)); return if (mismatchedTerminal) 4 else 2
        }
        override fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int): ByteArray {
            legacyCalls++; return "SYNTHETIC complete legacy".toByteArray()
        }
        override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray) =
            generate(handle, roles, texts, maxTokens)
        override fun allocations(handle: Long) = longArrayOf(1,2,3)
        override fun unload(handle: Long) {}
        override fun destroy(handle: Long) { destroys++ }
    }
    @Test fun optInSyntheticFlowUsesSameEngineAndDoesNotReleaseResidentLease() = runBlocking {
        val n = Native(); val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic-nonprivate-path", config)
            val outcome = b.generateOwnedStream(request, 512, authority(), q)
            assertEquals(StreamKind.COMPLETED, outcome.terminal)
            assertEquals(StreamResourceState.CONTENT_DONE, outcome.resourceState)
            assertTrue(q.jniInvocationFinished)
            assertNull(ProcessResources.coordinator.snapshot().heavy)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            assertEquals(listOf(StreamKind.ACK, StreamKind.TOKEN, StreamKind.COMPLETED), generateSequence { q.poll() }.map { it.kind }.toList())
            assertEquals(1, n.creates); assertEquals(1, n.loads)
            b.unload(); assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { b.close() }
    }
    @Test fun warmAndLegacyPathsReuseSameWorkerWithoutSilentlyEnablingStreaming() = runBlocking {
        val n = Native(); val b = LlamaBackend(n)
        try {
            for (generation in 1L..2L) {
                b.prepareOperation(); b.load("synthetic", config)
                b.generateOwnedStream(request, 512, authority(generation), OwnedStreamBuffer())
            }
            assertEquals("SYNTHETIC complete legacy", b.generate(request))
            assertEquals(2, n.streamCalls); assertEquals(1, n.legacyCalls)
            assertEquals(1, n.loads); assertEquals(1, n.creates); assertEquals(1, n.threads.distinct().size)
        } finally { b.close() }
    }
    @Test fun absentQualifiedAuthorityRefusesBeforeStreamingJni() = runBlocking {
        val n = Native(); val b = LlamaBackend(n)
        try {
            b.prepareOperation(); b.load("synthetic", config)
            val deny = object : StreamOwnerAuthority { override fun authorize(p: StreamPrepared, r: StreamResident): StreamGrant? = null }
            assertTrue(runCatching { b.generateOwnedStream(request, 512, deny, OwnedStreamBuffer()) }.isFailure)
            assertEquals(0, n.streamCalls); assertNotNull(ProcessResources.coordinator.snapshot().model)
            assertNull(ProcessResources.coordinator.snapshot().heavy)
        } finally { b.close() }
    }
    @Test fun exactPreparedMismatchProducesOnlyErrorAndNoSyntheticSuccess() = runBlocking {
        val n = Native().apply { corruptPrepared = true }; val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic", config)
            val outcome = b.generateOwnedStream(request, 512, authority(), q)
            assertEquals(StreamKind.ERROR, outcome.terminal)
            assertEquals(StreamKind.ERROR, q.poll()!!.kind); assertNull(q.poll())
            assertEquals(StreamResourceState.UNKNOWN, outcome.resourceState)
        } finally { b.close() }
    }
    @Test fun stopAndConcurrentRefusalRetainLeasesUntilActualInjectedReturn() = runBlocking {
        val n = Native().apply { block = true }; val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic", config)
            val result = async(Dispatchers.Default) { b.generateOwnedStream(request, 512, authority(), q) }
            try {
                assertTrue(n.entered.await(3, TimeUnit.SECONDS))
                assertTrue(runCatching { b.generateOwnedStream(request, 512, authority(2), OwnedStreamBuffer()) }.isFailure)
                q.stop(); b.requestStop(); b.requestStop()
                assertFalse(q.jniInvocationFinished)
                assertNotNull(ProcessResources.coordinator.snapshot().heavy)
                assertNotNull(ProcessResources.coordinator.snapshot().model)
            } finally { n.release.countDown() }
            assertEquals(StreamKind.CANCELLED, result.await().terminal)
            assertTrue(q.jniInvocationFinished); assertEquals(2, n.cancels)
            assertNull(ProcessResources.coordinator.snapshot().heavy)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            assertEquals(StreamKind.CANCELLED, q.poll()!!.kind); assertNull(q.poll())
        } finally { n.release.countDown(); b.close() }
    }
    @Test fun nativeOutOfMemoryPropagatesWithoutCompletionOrReleaseClaim() = runBlocking {
        val n = Native().apply { oom = true }; val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic", config)
            val error = runCatching { b.generateOwnedStream(request, 512, authority(), q) }.exceptionOrNull()
            assertTrue(error is OutOfMemoryError)
            assertTrue(q.jniInvocationFinished); assertNull(q.poll())
            assertEquals(StreamResourceState.UNKNOWN, q.resourceState)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
        } finally { b.close() }
    }
    @Test fun returnedCompletedWithoutAcceptedCallbackCannotProduceSuccess() = runBlocking {
        val n = Native().apply { skipCallbacks = true }; val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic", config)
            assertTrue(runCatching { b.generateOwnedStream(request, 512, authority(), q) }.isFailure)
            assertNull(q.acceptedTerminal()); assertNull(q.poll())
            assertEquals(StreamResourceState.UNKNOWN, q.resourceState)
        } finally { b.close() }
    }
    @Test fun nativeStatusMustMatchTheExactAcceptedTerminal() = runBlocking {
        val n = Native().apply { mismatchedTerminal = true }; val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic", config)
            assertTrue(runCatching { b.generateOwnedStream(request, 512, authority(), q) }.isFailure)
            assertNull(q.acceptedTerminal()); assertNull(q.poll()); assertTrue(q.isUnknown())
        } finally { b.close() }
    }
    @Test fun callbackCannotEmitLateTextAfterActualInvocationFinishes() = runBlocking {
        val n = Native(); val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic", config)
            b.generateOwnedStream(request, 512, authority(), q)
            val before = q.pendingEvents()
            assertTrue(runCatching { n.retainedCallback!!.onNativeEvent(n.retainedBinding!!, 1, 3,
                "SYNTHETIC late".toByteArray(), 2, 100, 1) }.isFailure)
            assertEquals(before, q.pendingEvents()); assertFalse(q.isUnknown())
            assertEquals(StreamKind.COMPLETED, q.acceptedTerminal())
        } finally { b.close() }
    }
    @Test fun callbackExceptionClearsUnrenderedTextAndPermanentlyMarksUnknown() = runBlocking {
        val n = Native().apply { callbackFaultAfterChunk = true }; val b = LlamaBackend(n); val q = OwnedStreamBuffer()
        try {
            b.prepareOperation(); b.load("synthetic", config)
            assertTrue(runCatching { b.generateOwnedStream(request, 512, authority(), q) }.isFailure)
            assertTrue(q.isUnknown()); assertNull(q.poll()); assertNull(q.acceptedTerminal())
            assertEquals(StreamResourceState.UNKNOWN, q.resourceState); assertTrue(q.jniInvocationFinished)
            assertTrue(runCatching { n.retainedCallback!!.onNativeEvent(n.retainedBinding!!, 1, 2,
                "SYNTHETIC late".toByteArray(), 2, 100, 1) }.isFailure)
            assertNull(q.poll()); assertNotNull(ProcessResources.coordinator.snapshot().model)
        } finally { b.close() }
    }
    @Test fun reusedOwnerSequenceAndOldPreparedGrantNeverReachSecondJniCall() = runBlocking {
        val n = Native(); val b = LlamaBackend(n)
        try {
            b.prepareOperation(); b.load("synthetic", config)
            var old: StreamGrant? = null
            val first = object : StreamOwnerAuthority {
                override fun authorize(p: StreamPrepared, r: StreamResident): StreamGrant = authority(2).authorize(p,r)!!.also { old = it }
            }
            b.generateOwnedStream(request, 512, first, OwnedStreamBuffer())
            assertTrue(runCatching { b.generateOwnedStream(request, 512, authority(1), OwnedStreamBuffer()) }.isFailure)
            val replay = object : StreamOwnerAuthority { override fun authorize(p: StreamPrepared, r: StreamResident) = old }
            assertTrue(runCatching { b.generateOwnedStream(request, 512, replay, OwnedStreamBuffer()) }.isFailure)
            assertEquals(1, n.streamCalls)
        } finally { b.close() }
    }
}
