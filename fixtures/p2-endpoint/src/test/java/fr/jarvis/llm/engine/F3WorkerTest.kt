package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.domain.resources.Module
import fr.jarvis.llm.ProcessResources
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** REAL unmodified LlamaBackend worker + FAKE NativeApi. No JNI, model or F3 receipt.
 * Latches establish ordering; deadlines only bound test hangs, never prove native drain.
 */
class F3WorkerTest {
    private val config=RuntimeConfig(ModelKind.SMALL,512,64,1)
    private val request=GenerationRequest(listOf(Message("user","Texte synthétique")),config,"SYSTEM synthétique")
    private class Api:NativeApi {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val workerThreads=java.util.Collections.synchronizedSet(mutableSetOf<Thread>())
        @Volatile var failCreate=false;@Volatile var failLoad=false;@Volatile var failUnload=false
        @Volatile var failDestroy=false;@Volatile var failSensor=false;@Volatile var panic=false
        @Volatile var stop=false;@Volatile var blockUnload=false
        @Volatile var unloaded=false;@Volatile var destroyed=false
        override fun create():Long { check(!failCreate);return 11L }
        override fun begin(handle:Long) { stop=false }
        override fun cancel(handle:Long) { stop=true }
        override fun load(handle:Long,path:ByteArray,context:Int,threads:Int,small:Boolean) {
            workerThreads+=Thread.currentThread();check(!failLoad)
        }
        override fun generate(handle:Long,roles:Array<ByteArray>,texts:Array<ByteArray>,maxTokens:Int):ByteArray {
            workerThreads+=Thread.currentThread();entered.countDown();check(release.await(5,TimeUnit.SECONDS))
            if(panic) throw AssertionError("fake native panic returning to JVM")
            check(!stop);return "synthétique".toByteArray()
        }
        override fun generateP2(handle:Long,roles:Array<ByteArray>,texts:Array<ByteArray>,maxTokens:Int,system:ByteArray)=
            generate(handle,roles,texts,maxTokens)
        override fun allocations(handle:Long):LongArray { check(!failSensor);return longArrayOf(1,2,3) }
        override fun unload(handle:Long) {
            workerThreads+=Thread.currentThread()
            if(blockUnload) { entered.countDown();check(release.await(5,TimeUnit.SECONDS)) }
            check(!failUnload);unloaded=true
        }
        override fun destroy(handle:Long) { workerThreads+=Thread.currentThread();check(!failDestroy);destroyed=true }
    }
    private fun await(l:CountDownLatch) { assertTrue("fake call did not enter",l.await(3,TimeUnit.SECONDS)) }
    @Test fun createFailureCannotBePromotedToNativeReleaseEvidence() {
        val api=Api().apply { failCreate=true }
        try { LlamaBackend(api);fail() } catch(_:IllegalStateException){}
        assertNull(ProcessResources.coordinator.snapshot().model)
        assertFalse(api.destroyed) // no actual cleanup path was observed
    }
    @Test fun loadAndSensorFailuresCleanUpOnlyAfterReturnedCalls() = runBlocking {
        for(sensor in listOf(false,true)) {
            val api=Api().apply { failLoad=!sensor;failSensor=sensor };val b=LlamaBackend(api)
            try {
                assertTrue(runCatching { b.load("synthetic",config) }.isFailure)
                assertTrue(api.unloaded);assertNull(ProcessResources.coordinator.snapshot().model)
                assertNull(ProcessResources.coordinator.snapshot().heavy)
            } finally { api.failSensor=false;withContext(NonCancellable) { b.close() } }
        }
    }
    @Test fun returnedPanicKeepsResidentLeaseUntilExplicitUnload() = runBlocking {
        val api=Api().apply { panic=true };val b=LlamaBackend(api)
        try {
            b.load("synthetic",config)
            val generation=async(Dispatchers.Default) { runCatching { b.generate(request) } }
            await(api.entered);assertNotNull(ProcessResources.coordinator.snapshot().heavy)
            api.release.countDown();assertTrue(generation.await().exceptionOrNull() is AssertionError)
            assertNull(ProcessResources.coordinator.snapshot().heavy)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            b.unload();assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.release.countDown();withContext(NonCancellable) { b.close() } }
    }
    @Test fun coroutineCancellationAndRepeatedStopCannotReleaseRunningNativeLease() = runBlocking {
        val api=Api();val b=LlamaBackend(api)
        try {
            b.load("synthetic",config)
            val generation=async(Dispatchers.Default) { runCatching { b.generate(request) } }
            await(api.entered);generation.cancel();b.requestStop();b.requestStop()
            assertFalse(generation.isCompleted)
            assertNotNull(ProcessResources.coordinator.snapshot().heavy)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            api.release.countDown();generation.join()
            assertNull(ProcessResources.coordinator.snapshot().heavy)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            b.unload();assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.release.countDown();withContext(NonCancellable) { b.close() } }
    }
    @Test fun concurrentImportAndGenerationRefusedAndUnloadQueuedOnSameWorker() = runBlocking {
        val api=Api();val b=LlamaBackend(api)
        try {
            b.load("synthetic",config)
            val generation=async(Dispatchers.Default) { runCatching { b.generate(request) } }
            await(api.entered)
            assertTrue(runCatching { b.generate(request) }.isFailure)
            assertTrue(runCatching { ProcessResources.requireHeavy("fake-import",Module.MODEL_IMPORT,"synthetic") }.isFailure)
            val cleanup=async(Dispatchers.Default) { b.unload() }
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            api.release.countDown();assertTrue(generation.await().isSuccess);cleanup.await()
            assertNull(ProcessResources.coordinator.snapshot().model)
            assertEquals(1,api.workerThreads.size)
        } finally { api.release.countDown();withContext(NonCancellable) { b.close() } }
    }
    @Test fun failedUnloadAndCloseKeepLeaseUntilExplicitRetryOnSameBackend() = runBlocking {
        val api=Api();val b=LlamaBackend(api)
        try {
            b.load("synthetic",config);api.failUnload=true;api.failDestroy=true
            assertTrue(runCatching { b.unload() }.isFailure);assertTrue(runCatching { withContext(NonCancellable) { b.close() } }.isFailure)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            assertTrue(runCatching { b.prepareOperation() }.isFailure)
            assertTrue(runCatching { b.load("synthetic",config) }.isFailure)
            api.failUnload=false;api.failDestroy=false
            // Nothing auto-retried when flags changed.
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            b.unload();assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.failUnload=false;api.failDestroy=false;withContext(NonCancellable) { b.close() } }
    }
    @Test fun pendingUnloadKeepsModelLeaseUntilRealWorkerCallReturns() = runBlocking {
        val api=Api();val b=LlamaBackend(api)
        try {
            b.load("synthetic",config);api.failSensor=true;api.blockUnload=true
            val unload=async(Dispatchers.Default) { b.unload() };await(api.entered)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            api.release.countDown();unload.await();assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.release.countDown();api.blockUnload=false;withContext(NonCancellable) { b.close() } }
    }
    @Test fun sensorFailureAfterGenerationBecomesUnavailableRatherThanZero() = runBlocking {
        val api=Api();val b=LlamaBackend(api)
        try {
            b.load("synthetic",config);api.failSensor=true;api.release.countDown()
            b.generate(request);assertNull(b.allocations)
            assertEquals("Buffers natifs indisponibles",b.allocationDiagnostic)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
        } finally { withContext(NonCancellable) { b.close() } }
    }
}
