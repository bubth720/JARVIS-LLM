package fr.jarvis.llm

import fr.jarvis.domain.*
import fr.jarvis.llm.engine.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the real worker/backend and real process coordinator, with deterministic native fault injection.
 * These are JVM tests, not evidence of JNI execution or Android hardware behavior. */
class NativeBackendLifecycleTest {
    private val config = RuntimeConfig(ModelKind.SMALL,512,64,1)
    private val request = GenerationRequest(listOf(Message("user", "Question")), config, "Système CORE")
    private class Api : NativeApi {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        @Volatile var failLoad = false; @Volatile var failCleanup = false
        @Volatile var stopped = false
        var custom: String? = null; var destroyed = false
        override fun create() = 1L
        override fun begin(handle: Long) { stopped = false }
        override fun cancel(handle: Long) { stopped = true }
        override fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean) {
            if (failLoad) { failCleanup = true; error("injected load failure") }
        }
        override fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int): ByteArray {
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); check(!stopped); return "Réponse".toByteArray()
        }
        override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray {
            custom = system.toString(Charsets.UTF_8); return generate(handle,roles,texts,maxTokens)
        }
        override fun allocations(handle: Long) = longArrayOf(1,2,3)
        override fun unload(handle: Long) { check(!failCleanup) { "injected cleanup failure" } }
        override fun destroy(handle: Long) { check(!failCleanup); destroyed = true }
    }
    @Test fun concurrentGenerationRefusedAndCustomSystemPassedToNativeApi() = runBlocking {
        val api = Api(); val backend = LlamaBackend(api)
        try {
            backend.prepareOperation(); backend.load("synthetic",config)
            val first = async(Dispatchers.Default) { backend.generate(request) }
            try {
                assertTrue(api.entered.await(2,TimeUnit.SECONDS))
                try { backend.generate(request); fail("concurrent generation accepted") } catch (_: IllegalStateException) { }
                assertEquals("Système CORE",api.custom)
            } finally { api.release.countDown() }
            assertEquals("Réponse",first.await())
        } finally { api.release.countDown(); backend.close() }
        assertTrue(api.destroyed)
        assertNull(ProcessResources.coordinator.snapshot().model)
    }
    @Test fun stopKeepsLeasesUntilNativeReturnAndModelUntilUnload() = runBlocking {
        val api = Api(); val backend = LlamaBackend(api)
        try {
            backend.prepareOperation(); backend.load("synthetic",config)
            val first = async(Dispatchers.Default) { runCatching { backend.generate(request) } }
            try {
                assertTrue(api.entered.await(2,TimeUnit.SECONDS))
                backend.requestStop(); backend.requestStop()
                assertNotNull(ProcessResources.coordinator.snapshot().heavy)
                assertNotNull(ProcessResources.coordinator.snapshot().model)
                try { backend.prepareOperation(); fail("stop rearmed during active native call") } catch (_: IllegalStateException) { }
            } finally { api.release.countDown() }
            assertTrue(first.await().isFailure)
            assertNull(ProcessResources.coordinator.snapshot().heavy)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            backend.unload()
            assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.release.countDown(); backend.close() }
    }
    @Test fun failedCleanupKeepsModelLeaseAndBlocksNextOperationUntilConfirmedUnload() = runBlocking {
        val api = Api(); val backend = LlamaBackend(api)
        try {
            api.failLoad = true
            try { backend.load("synthetic",config); fail() } catch (_: IllegalStateException) { }
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            try { backend.prepareOperation(); fail() } catch (_: IllegalStateException) { }
            try { backend.load("synthetic",config); fail() } catch (_: IllegalStateException) { }
            try { backend.generate(request); fail() } catch (_: IllegalStateException) { }
            api.failLoad = false; api.failCleanup = false
            backend.unload(); assertNull(ProcessResources.coordinator.snapshot().model)
            backend.prepareOperation(); backend.load("synthetic",config)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.failCleanup = false; backend.close() }
    }
    @Test fun unloadAndCloseFailuresRetainLeaseAndBlockRearming() = runBlocking {
        val api = Api(); val backend = LlamaBackend(api)
        try {
            backend.prepareOperation(); backend.load("synthetic", config)
            api.failCleanup = true
            try { backend.unload(); fail() } catch (_: IllegalStateException) { }
            try { backend.close(); fail() } catch (_: IllegalStateException) { }
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            assertFalse(api.destroyed)
            try { backend.prepareOperation(); fail() } catch (_: IllegalStateException) { }
            api.failCleanup = false; backend.unload()
            assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.failCleanup = false; backend.close() }
    }

}
