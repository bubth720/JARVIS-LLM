package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.llm.ProcessResources
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic event sources and FAKE NativeApi only. Proposed backend/JNI patch NOT installed. */
class F3CandidateTest {
    private fun trace()=F3Trace(UUID.randomUUID().toString(),"a".repeat(64)).apply { bindOwner("synthetic-owner") }
    private fun returned(t:F3Trace,kind:F3Trace.Call,flags:LongArray?=null,success:Boolean=true) {
        t.returned(t.entered(kind),success,flags)
    }
    private fun born():F3Trace=trace().apply {
        returned(this,F3Trace.Call.CREATE)
        val task=queued(F3Trace.Task.LOAD);started(task);taskReturned(task)
        returned(this,F3Trace.Call.PROBE,longArrayOf(1,1,1,1))
    }
    private fun state(t:F3Trace,model:Int?=0,heavy:Int?=0,coherent:Boolean=true)=F3Trace.classify(t.snapshot(),coherent,model,heavy)
    @Test fun emptyNewProcessNeverProvesOldOperationReleased() {
        assertEquals(F3Trace.State.UNKNOWN,state(trace()))
    }
    @Test fun stopOnlyReturnsIntentWhileCallRemainsActive() {
        val t=born();val c=t.entered(F3Trace.Call.GENERATE);returned(t,F3Trace.Call.CANCEL)
        assertEquals(1,t.snapshot().calls);assertEquals(F3Trace.State.STOP_REQUESTED,state(t))
        t.returned(c,false);assertEquals(F3Trace.State.UNKNOWN,state(t,1))
        returned(t,F3Trace.Call.PROBE,longArrayOf(1,1,1,1));assertEquals(F3Trace.State.DRAINED,state(t,1))
    }
    @Test fun coroutineAbandonmentDoesNotRemoveQueuedWorkOrProveDrain() {
        val t=born();val q=t.queued(F3Trace.Task.GENERATE);t.dispatchAbandoned(q)
        assertEquals(1,t.snapshot().queued);assertEquals(F3Trace.State.UNKNOWN,state(t))
    }
    @Test fun importsAndTaskQueueAreSeparateFromNativeCalls() {
        val t=born();val i=t.importEntered();val q=t.queued(F3Trace.Task.UNLOAD)
        assertEquals(1,t.snapshot().imports);assertEquals(1,t.snapshot().queued);assertEquals(0,t.snapshot().calls)
        assertEquals(F3Trace.State.RUNNING,state(t));t.started(q);t.importReturned(i);t.taskReturned(q)
        assertEquals(F3Trace.State.UNKNOWN,state(t))
        returned(t,F3Trace.Call.PROBE,longArrayOf(1,1,1,1));assertEquals(F3Trace.State.DRAINED,state(t))
    }
    @Test fun successfulUnloadNeedsPointerProbeAndNoLeases() {
        val t=born();returned(t,F3Trace.Call.UNLOAD)
        assertEquals(F3Trace.State.UNKNOWN,state(t))
        returned(t,F3Trace.Call.PROBE,longArrayOf(1,1,0,0))
        assertEquals(F3Trace.State.DRAINED,state(t,1));assertEquals(F3Trace.State.DRAINED,state(t,0,1))
        assertEquals(F3Trace.State.UNLOADED,state(t))
    }
    @Test fun failedOrMalformedProbeCannotReusePreviousEmptyPointers() {
        for(flags in listOf<LongArray?>(null,longArrayOf(),longArrayOf(2,1,0,0),longArrayOf(1,0,0,0),longArrayOf(1,1,-1,0))) {
            val t=born();returned(t,F3Trace.Call.UNLOAD);returned(t,F3Trace.Call.PROBE,longArrayOf(1,1,0,0))
            val reader=F3NativeApi(object:NativeApi by FakeApi() {
                override fun create():Long = error("probe must not create")
            },t)
            if (flags==null) assertTrue(runCatching { reader.probeExisting(7) { error("synthetic sensor failure") } }.isFailure)
            else reader.probeExisting(7) { handle -> assertEquals(7L,handle);flags }
            assertNull(t.snapshot().pointerFlags);assertEquals(F3Trace.State.UNKNOWN,state(t))
        }
    }
    @Test fun modelAndContextResidencyAreIndependent() {
        for(flags in listOf(longArrayOf(1,1,1,0),longArrayOf(1,1,0,1),longArrayOf(1,1,1,1))) {
            val t=born();returned(t,F3Trace.Call.UNLOAD);returned(t,F3Trace.Call.PROBE,flags)
            assertEquals(F3Trace.State.DRAINED,state(t))
        }
    }
    @Test fun failedCleanupRemainsBlockedUntilExplicitReturnedRetryAndProbe() {
        val t=born();returned(t,F3Trace.Call.UNLOAD,success=false)
        returned(t,F3Trace.Call.PROBE,longArrayOf(1,1,0,0))
        assertEquals(F3Trace.State.CLEANUP_FAILED,state(t))
        returned(t,F3Trace.Call.UNLOAD);assertEquals(F3Trace.State.UNKNOWN,state(t))
        returned(t,F3Trace.Call.PROBE,longArrayOf(1,1,0,0));assertEquals(F3Trace.State.UNLOADED,state(t))
    }
    @Test fun missingLeaseSourceOrIncoherentReadRemainsUnknown() {
        val t=born();returned(t,F3Trace.Call.DESTROY)
        assertEquals(F3Trace.State.UNKNOWN,state(t,model=null));assertEquals(F3Trace.State.UNKNOWN,state(t,heavy=null))
        assertEquals(F3Trace.State.UNKNOWN,state(t,coherent=false))
        assertEquals(F3Trace.State.UNLOADED,state(t)) // synthetic destroy return, NOT a JNI proof
    }
    @Test fun staleKeyAndForeignTokenCannotCloseAnotherCall() {
        val t=born();val c=t.entered(F3Trace.Call.GENERATE)
        try { t.requireKey("b".repeat(64));fail() } catch(_:IllegalStateException){}
        val foreign=born().entered(F3Trace.Call.GENERATE)
        try { t.returned(foreign,true);fail() } catch(_:IllegalStateException){}
        assertEquals(1,t.snapshot().calls);t.returned(c,true)
    }
    @Test fun unexpectedWorkerThreadPoisonsFutureObservation() {
        val t=born();val q=t.queued(F3Trace.Task.LOAD)
        val thread=Thread { t.started(q);t.taskReturned(q) };thread.start();thread.join(3000)
        assertFalse(thread.isAlive);assertEquals(F3Trace.State.UNKNOWN,state(t))
    }
    @Test fun inspectorTicketDoesNotHideRealPendingCleanup() {
        val t=born();val inspect=t.queued(F3Trace.Task.INSPECT);t.started(inspect)
        val cleanup=t.queued(F3Trace.Task.CLOSE)
        assertEquals(1,t.snapshot().queued);assertEquals(F3Trace.State.RUNNING,state(t))
        t.started(cleanup);t.taskReturned(cleanup);t.taskReturned(inspect)
    }
    @Test fun recordingFailureCannotTurnSuccessfulDestroyIntoCleanupFailure() {
        val t=born();var destroys=0
        val api=object:NativeApi by FakeApi() {
            override fun destroy(handle:Long) {
                destroys++
                val field=F3Trace::class.java.getDeclaredField("calls");field.isAccessible=true
                @Suppress("UNCHECKED_CAST")
                (field.get(t) as MutableMap<Any,Any>).clear() // injected observer corruption only
            }
        }
        F3NativeApi(api,t).destroy(7)
        assertEquals(1,destroys);assertTrue(t.snapshot().uncertain)
        assertEquals(F3Trace.State.UNKNOWN,state(t));assertFalse(t.snapshot().destroyed)
    }
    @Test fun recordingFailureNeverMasksOriginalNativeException() {
        val t=born();val original=IllegalArgumentException("synthetic original")
        val api=object:NativeApi by FakeApi() {
            override fun unload(handle:Long) {
                val field=F3Trace::class.java.getDeclaredField("calls");field.isAccessible=true
                @Suppress("UNCHECKED_CAST")
                (field.get(t) as MutableMap<Any,Any>).clear()
                throw original
            }
        }
        val result=runCatching { F3NativeApi(api,t).unload(7) }
        assertSame(original,result.exceptionOrNull());assertTrue(t.snapshot().uncertain)
    }
    @Test fun zeroHandleCreateCannotBecomeAValidBirthObservation() {
        val t=trace();val api=object:NativeApi by FakeApi() { override fun create()=0L }
        assertTrue(runCatching { F3NativeApi(api,t).create() }.isFailure)
        assertEquals(0,t.snapshot().calls);assertEquals(F3Trace.State.UNKNOWN,state(t))
    }
    private class FakeApi:NativeApi {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        @Volatile var failUnload=false
        override fun create()=7L
        override fun begin(handle:Long){}
        override fun cancel(handle:Long){}
        override fun load(handle:Long,path:ByteArray,context:Int,threads:Int,small:Boolean){}
        override fun generate(handle:Long,roles:Array<ByteArray>,texts:Array<ByteArray>,maxTokens:Int):ByteArray {
            entered.countDown();check(release.await(5,TimeUnit.SECONDS));return "synthetic".toByteArray()
        }
        override fun generateP2(handle:Long,roles:Array<ByteArray>,texts:Array<ByteArray>,maxTokens:Int,system:ByteArray)=generate(handle,roles,texts,maxTokens)
        override fun allocations(handle:Long)=longArrayOf(1,2,3)
        override fun unload(handle:Long) { check(!failUnload) }
        override fun destroy(handle:Long){}
    }
    @Test fun decoratorCountsActualSynchronousReturnOnUnmodifiedRealWorkerFakeJni()=runBlocking {
        val t=trace();val api=FakeApi();val b=LlamaBackend(F3NativeApi(api,t))
        val config=RuntimeConfig(ModelKind.SMALL,512,64,1)
        try {
            b.load("synthetic",config)
            val generation=async(Dispatchers.Default) { runCatching { b.generate(GenerationRequest(listOf(Message("user","synthetic")),config)) } }
            assertTrue(api.entered.await(3,TimeUnit.SECONDS));val before=t.snapshot()
            assertEquals(1,before.calls);b.requestStop();assertEquals(1,t.snapshot().calls)
            assertNotNull(ProcessResources.coordinator.snapshot().heavy)
            api.release.countDown();assertTrue(generation.await().isSuccess)
            assertEquals(0,t.snapshot().calls);assertTrue(t.snapshot().callEndSequence>before.callEndSequence)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            // Worker task instrumentation is deliberately absent in this unmodified backend.
            assertEquals(F3Trace.State.UNKNOWN,state(t,1))
        } finally { api.release.countDown();withContext(NonCancellable) { b.close() } }
    }
    @Test fun decoratorCleanupFailureRetainsRealLeaseUntilExplicitRetryFakeJni()=runBlocking {
        val t=trace();val api=FakeApi();val b=LlamaBackend(F3NativeApi(api,t))
        try {
            b.load("synthetic",RuntimeConfig(ModelKind.SMALL,512,64,1));api.failUnload=true
            assertTrue(runCatching { b.unload() }.isFailure);assertTrue(t.snapshot().cleanupFailed)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            api.failUnload=false;assertNotNull(ProcessResources.coordinator.snapshot().model)
            b.unload();assertFalse(t.snapshot().cleanupFailed);assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { api.failUnload=false;withContext(NonCancellable) { b.close() } }
    }
}
