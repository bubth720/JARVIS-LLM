package fr.jarvis.domain.generation

import fr.jarvis.domain.resources.*
import fr.jarvis.domain.*
import org.junit.Assert.*
import org.junit.Test

class P2LifecycleTest {
    private val broker=ResourceCoordinator()
    private var now=0L
    private val life=P2Lifecycle(broker,"p2-fixture",ID) { now }
    private val requests=mutableMapOf<OperationKey,P2Request>()
    private fun arm(k:OperationKey=key()) { val r=request(key=k); requests[k]=r; life.prepare(r,SafetyDecision(RuntimeConfig(ModelKind.SMALL,512,64,1),"synthetic"),CoreAuthorizationGate { true }) }
    private fun running(k: OperationKey=key()) { arm(k);life.beginLoad(k);life.modelReady(k);life.beginGeneration(k,prepared(checkNotNull(requests[k]))) }
    private fun finish(k:OperationKey=key()) { life.beginCleanup(k);life.confirmUnloaded(k) }
    @Test fun stopIsIdempotentLateOutputIsDiscardedAndLeasesSurviveUntilUnload() {
        running(); assertTrue(life.requestStop(key()));assertFalse(life.requestStop(key()))
        assertEquals(P2State.STOP_REQUESTED,life.snapshot().state)
        assertFalse(life.snapshot().effectiveEnd); assertNotNull(broker.snapshot().model)
        assertThrows(P2Exception::class.java) { life.beginCleanup(key()) }
        life.nativeReturned(key(),"late fixture output")
        assertEquals(P2State.NATIVE_DONE,life.snapshot().state);assertNotNull(broker.snapshot().heavy)
        assertThrows(P2Exception::class.java) { life.takeResult(key()) }
        finish(); assertEquals(P2Error.CANCELLED,(life.takeResult(key()) as P2Result.Failure).code)
        assertNull(broker.snapshot().model);assertNull(broker.snapshot().heavy)
    }
    @Test fun cleanupFailureBlocksSecondOperationAndCloseUntilExplicitSuccessfulRetry() {
        running();life.nativeReturned(key(),"synthetic success");life.beginCleanup(key());life.cleanupFailed(key())
        assertEquals(P2State.CLEANUP_FAILED,life.snapshot().state)
        assertEquals(P2Error.CLEANUP_FAILED,assertThrows(P2Exception::class.java) { arm(key(2)) }.code)
        assertThrows(P2Exception::class.java) { life.close() };assertNotNull(broker.snapshot().model)
        finish();assertEquals(P2Error.CLEANUP_FAILED,(life.takeResult(key()) as P2Result.Failure).code)
        arm(key(2))
        assertEquals(P2State.PREPARED,life.snapshot().state)
    }
    @Test fun staleCallbacksAndOldLeaseGenerationCannotFreeOrCancelNewWork() {
        running();life.nativeReturned(key(),"result one");finish()
        val next=key(2);running(next)
        assertFalse(life.nativeReturned(key(),"old result"));assertFalse(life.requestStop(key()))
        assertThrows(P2Exception::class.java) { life.confirmUnloaded(key()) }
        assertEquals(next,life.snapshot().key);assertEquals(P2State.RUNNING,life.snapshot().state)
        life.requestStop(next);life.nativeReturned(next);finish(next)
        assertThrows(P2Exception::class.java) { arm() }
    }
    @Test fun timeoutDoesNotReleaseAndAnUnknownOperationNeverAdmitsFallback() {
        running();now=1000
        assertTrue(life.pollDeadline(key()));assertFalse(life.pollDeadline(key()))
        assertNotNull(broker.snapshot().heavy);assertFalse(life.snapshot().effectiveEnd)
        life.markUnknown(key());assertEquals(P2State.UNKNOWN,life.snapshot().state)
        assertThrows(P2Exception::class.java) { arm(key(2)) }
        life.nativeReturned(key(),"expired output");finish()
        assertEquals(P2Error.EXPIRED,(life.takeResult(key()) as P2Result.Failure).code)
    }
    @Test fun loadFailureDrainsBeforeRetryAndNeverPretendsModelFreed() {
        arm();life.beginLoad(key())
        life.nativeReturned(key(),failure=P2Error.NATIVE_FAILURE)
        assertNotNull(broker.snapshot().model)
        finish();assertEquals(P2Error.NATIVE_FAILURE,(life.takeResult(key()) as P2Result.Failure).code)
        running(key(2));assertNotNull(broker.snapshot().model)
    }
    @Test fun otherHeavyWorkOrModelCannotEnterWhileTheP2ContractIsResident() {
        running()
        val foreign=ResourceRequest("other",Module.CORE,ResourceClass.HEAVY,"fixture, not real CORE")
        assertTrue(broker.acquire(foreign) is ResourceGrant.Refused)
        assertTrue(broker.acquire(foreign.copy(kind=ResourceClass.MODEL,modelKey="MAIN")) is ResourceGrant.Refused)
        assertThrows(P2Exception::class.java) { arm(key(2)) }
        life.nativeReturned(key(),"text");finish()
        assertTrue(broker.acquire(foreign) is ResourceGrant.Granted)
    }
    @Test fun cancellationBeforeNativeStartAndExpiryBeforeDeliveryRemainDistinct() {
        arm();assertFalse(life.requestStop(key()))
        assertTrue(life.snapshot().effectiveEnd);finish()
        assertEquals(P2Error.CANCELLED,(life.takeResult(key()) as P2Result.Failure).code)
        running(key(2));life.nativeReturned(key(2),"text");finish(key(2));now=1000
        assertEquals(P2Error.EXPIRED,(life.takeResult(key(2)) as P2Result.Failure).code)
        assertThrows(P2Exception::class.java) { life.takeResult(key(2)) }
        life.close();assertEquals(P2State.CLOSED,life.snapshot().state)
    }
    @Test fun expiredBeforeLoadDoesNotEnterNativeAndCannotReleaseBeforeAcknowledgment() {
        arm(); now=1000
        assertEquals(P2Error.EXPIRED,assertThrows(P2Exception::class.java) { life.beginLoad(key()) }.code)
        assertFalse(life.snapshot().nativeEverStarted);assertNull(broker.snapshot().model)
        assertNotNull(broker.snapshot().heavy);finish()
        assertEquals(P2Error.EXPIRED,(life.takeResult(key()) as P2Result.Failure).code)
    }
    @Test fun preparedPromptMustBeForTheSameImmutableRequestAndOperationCannotBeReplayed() {
        arm();life.beginLoad(key());life.modelReady(key())
        assertThrows(P2Exception::class.java) { life.beginGeneration(key(),prepared(request())) }
        life.beginGeneration(key(),prepared(checkNotNull(requests[key()])))
        life.nativeReturned(key(),"result");finish()
        val replay=key().copy(generation=2)
        assertEquals(P2Error.STALE_OPERATION,assertThrows(P2Exception::class.java) { arm(replay) }.code)
    }

    @Test fun malformedOutputStillRecordsActualReturnAndCleanupErrorIsNotMaskedByTimeout() {
        running();life.nativeReturned(key(),"\uD800")
        assertTrue(life.snapshot().effectiveEnd);assertFalse(life.snapshot().nativeCallActive)
        finish();assertEquals(P2Error.INVALID_UTF8,(life.takeResult(key()) as P2Result.Failure).code)
        running(key(2));life.nativeReturned(key(2),"text");life.beginCleanup(key(2));life.cleanupFailed(key(2))
        now=2000;finish(key(2))
        assertEquals(P2Error.CLEANUP_FAILED,(life.takeResult(key(2)) as P2Result.Failure).code)
    }

    @Test fun unauthorizedRequestAndFullBoundedReplayLedgerNeverAcquireANewLease() {
        val admission=SafetyDecision(RuntimeConfig(ModelKind.SMALL,512,64,1),"synthetic")
        assertEquals(P2Error.UNAUTHORIZED,assertThrows(P2Exception::class.java) { life.prepare(request(),admission) }.code)
        assertNull(broker.snapshot().heavy)
        repeat(256) { n -> val k=key(n+1L);arm(k);life.requestStop(k);finish(k);life.takeResult(k) }
        assertEquals(P2Error.BUSY,assertThrows(P2Exception::class.java) { arm(key(257)) }.code)
        assertNull(broker.snapshot().model);assertNull(broker.snapshot().heavy)
        life.close()
    }

}
