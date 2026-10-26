package fr.jarvis.domain.client.qualification

import fr.jarvis.domain.*
import fr.jarvis.domain.client.*
import fr.jarvis.domain.client.Error as ClientFailure
import fr.jarvis.domain.generation.*
import fr.jarvis.domain.runtime.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** TEST-ONLY software qualification. No JNI, model, native receipt or production authority. */
class ClientQualificationTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val other = "00000000-0000-0000-0000-000000000002"
    private fun key() = OperationKey(id,id,id,id,id,id,id,1)
    private fun request(k: OperationKey = key(), question: String = "Texte synthétique de test", millis: Int = 1000) =
        GenerateRequest.create(prompt=P2Request.create(P2_VERSION,k,"Instruction de test",emptyList(),emptyList(),question,
            ModelKind.SMALL,"a".repeat(64),P2Limits(512,64,4096,millis)),priority=Priority.INTERACTIVE,
            profile=Profile.TABLET_ECO,target=ExecutionTarget.ANDROID_LOCAL)
    private fun ack(r: GenerateRequest, origin: EventOrigin = EventOrigin.FIXTURE) = Ack(r.reference,origin)
    private fun chunk(r: GenerateRequest, text: String = "Synthétique", n: Int = 1) = StreamChunk(r.reference,n,text,EventOrigin.FIXTURE)
    private fun done(r: GenerateRequest) = Completed(r.reference,2,EventOrigin.FIXTURE)
    private fun fixture(r: GenerateRequest) = ClientStreamMonitor.fixture(r,0)
    private fun closed(m: ClientStreamMonitor) {
        assertFalse(m.nativeResources.mayAdmitAnotherEngine)
        assertEquals(NativeProofStatus.UNATTESTED,m.nativeResources.unload)
        assertEquals(NativeProofStatus.UNATTESTED,m.nativeResources.termination)
    }

    @Test fun doubleAckIsIdempotentWithoutDuplicatingTextOrGrantingResources() {
        val r=request();val m=fixture(r)
        assertEquals(EventDisposition.ACCEPTED,m.accept(ack(r),0))
        assertEquals(EventDisposition.DUPLICATE,m.accept(ack(r),1))
        assertEquals(ClientPhase.STREAMING,m.phase);assertEquals("",m.text());closed(m)
        m.accept(chunk(r),2);assertEquals(EventDisposition.DUPLICATE,m.accept(ack(r),3))
        assertEquals("Synthétique",m.text());closed(m)
    }
    @Test fun expiredDuplicateAckCannotExtendDeadline() {
        val r=request();val m=fixture(r);m.accept(ack(r),0)
        assertEquals(EventDisposition.DUPLICATE,m.accept(ack(r),999))
        assertEquals(EventDisposition.REJECTED,m.accept(ack(r),1000))
        assertEquals(ClientPhase.UNKNOWN,m.phase);closed(m)
    }
    @Test fun allCorrelationComponentsRejectStaleEventsAndCancellation() {
        val k=key();val r=request(k)
        val changes=listOf(k.copy(epoch=other),k.copy(operationId=other),k.copy(sessionId=other),k.copy(correlationId=other),
            k.copy(cancellationId=other),k.copy(authorizationRef=other),k.copy(leaseId=other),k.copy(generation=2))
        for(changed in changes) {
            val m=fixture(r);val old=request(changed)
            assertEquals(EventDisposition.STALE,m.accept(ack(old),0))
            assertEquals(EventDisposition.STALE,m.cancel(Cancel(old.reference)))
            assertEquals(ClientPhase.WAITING,m.phase);assertEquals("",m.text());closed(m)
        }
    }
    @Test fun staleEventDoesNotUpdateClockOrCurrentOutput() {
        val r=request();val m=fixture(r);m.accept(ack(r),0);m.accept(chunk(r),10)
        val stale=request(key().copy(epoch=other))
        assertEquals(EventDisposition.STALE,m.accept(ack(stale),Long.MAX_VALUE))
        assertEquals(EventDisposition.ACCEPTED,m.accept(done(r),11));assertEquals("Synthétique",m.text());closed(m)
    }
    @Test fun sameIdentifiersWithAlteredDigestCannotCancelCurrentRequest() {
        val r=request();val m=fixture(r);m.accept(ack(r),0)
        assertEquals(EventDisposition.STALE,m.cancel(Cancel(request(question="Modification synthétique").reference)))
        assertEquals(ClientPhase.STREAMING,m.phase);closed(m)
    }
    @Test fun unknownRemainsStickyAfterLateAckChunkCompletionAndCancel() {
        val r=request();val m=fixture(r);assertTrue(m.expire(1000))
        for(event in listOf(ack(r),chunk(r),done(r),ClientFailure(r.reference,0,ClientError.UNKNOWN_STATE,EventOrigin.FIXTURE))) {
            assertEquals(EventDisposition.TERMINAL,m.accept(event,1001));assertEquals(ClientPhase.UNKNOWN,m.phase);closed(m)
        }
        assertEquals(EventDisposition.TERMINAL,m.cancel(Cancel(r.reference)));assertEquals("",m.text())
    }
    @Test fun processRestartDoesNotCreateNativeUnloadEvidence() {
        val r=request();val old=fixture(r);old.accept(ack(r),0);old.cancel(Cancel(r.reference))
        val replacement=ClientStreamMonitor.live(request(key().copy(epoch=other)),0)
        assertEquals(EventDisposition.STALE,replacement.accept(done(r),0))
        assertNull(replacement.nativeResources.observation);closed(old);closed(replacement)
    }
    @Test fun cancelledOutputAndLateErrorStayNonNative() {
        val r=request();val m=fixture(r);m.accept(ack(r),0);m.accept(chunk(r),0);m.cancel(Cancel(r.reference))
        assertEquals(EventDisposition.TERMINAL,m.accept(ClientFailure(r.reference,2,ClientError.UNKNOWN_STATE,EventOrigin.FIXTURE),1))
        assertEquals(ClientPhase.STOP_REQUESTED,m.phase);assertEquals("",m.text());closed(m)
    }
    @Test fun permissionRevocationRejectsEvenAnIdenticalRequestOnSecondSubmission() {
        var permitted=true
        val client=UnavailableGenerationClient(ClientAuthorizationGate { permitted })
        val r=request();assertEquals(ClientError.NATIVE_UNQUALIFIED,(client.submit(r) as ClientFailure).code)
        permitted=false;assertEquals(ClientError.UNAUTHORIZED,(client.submit(r) as ClientFailure).code)
        assertEquals(TokenCountStatus.UNSUPPORTED,client.capabilities.exactTokenCount);assertFalse(client.capabilities.nativeReady)
    }
    @Test fun fixtureWithoutModelOrPermissionCannotEmitAckOrText() {
        val r=request()
        for(model in listOf(false,true)) for(permission in listOf(false,true)) {
            val rig=TestOnlyRig(model,permission);val e=rig.submit(r)
            if(model && permission) { assertTrue(e is Ack);assertEquals(EventOrigin.FIXTURE,e.origin) }
            else { assertTrue(e is ClientFailure);assertEquals(if(permission) ClientError.NATIVE_UNQUALIFIED else ClientError.UNAUTHORIZED,(e as ClientFailure).code) }
            assertFalse(rig.capabilities.nativeReady);assertEquals(TokenCountStatus.UNSUPPORTED,rig.capabilities.exactTokenCount)
        }
    }
    @Test fun liveMonitorCannotConsumeAQualifiedLookingFixture() {
        val r=request();val rig=TestOnlyRig(true,true);val m=ClientStreamMonitor.live(r,0)
        assertEquals(EventDisposition.REJECTED,m.accept(rig.submit(r),0));assertEquals(ClientPhase.UNKNOWN,m.phase);closed(m)
    }
    @Test fun multibyteOutputBoundaryCountsUtf8RatherThanCharacters() {
        val r=request();val m=fixture(r);m.accept(ack(r),0)
        repeat(16) { assertEquals(EventDisposition.ACCEPTED,m.accept(chunk(r,"é".repeat(2048),it+1),0)) }
        assertEquals(EventDisposition.REJECTED,m.accept(chunk(r,"é",17),0));assertEquals("",m.text());closed(m)
    }
    @Test fun validSequenceAndQuotaCannotHideWrongOrigin() {
        val r=request();val m=fixture(r);m.accept(ack(r),0)
        val wrong=StreamChunk(r.reference,1,"Synthétique",EventOrigin.UNAVAILABLE)
        assertEquals(EventDisposition.REJECTED,m.accept(wrong,0));assertEquals(ClientPhase.UNKNOWN,m.phase);closed(m)
    }
    @Test fun lateSuccessCannotReverseAnExplicitRefusal() {
        val r=request();val m=fixture(r)
        m.accept(ClientFailure(r.reference,0,ClientError.UNAUTHORIZED,EventOrigin.FIXTURE),0)
        assertEquals(EventDisposition.TERMINAL,m.accept(ack(r),1));assertEquals(EventDisposition.TERMINAL,m.accept(done(r),2))
        assertEquals(ClientPhase.REFUSED,m.phase);closed(m)
    }
    @Test fun concurrentCancellationIsIdempotentAndCannotReleaseAnything() {
        val r=request();val m=fixture(r);m.accept(ack(r),0)
        val executor=Executors.newFixedThreadPool(4);val start=CountDownLatch(1)
        try {
            val futures=(1..4).map { executor.submit<EventDisposition> { assertTrue(start.await(5,TimeUnit.SECONDS));m.cancel(Cancel(r.reference)) } }
            start.countDown();val results=futures.map { it.get(5,TimeUnit.SECONDS) }
            assertEquals(1,results.count { it==EventDisposition.ACCEPTED });assertEquals(3,results.count { it==EventDisposition.DUPLICATE })
            assertEquals(ClientPhase.STOP_REQUESTED,m.phase);closed(m)
        } finally { executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)) }
    }
    @Test fun barrierProvesChunkAfterStopCannotReappear() {
        val r=request();val m=fixture(r);m.accept(ack(r),0)
        val stopped=CountDownLatch(1);val executor=Executors.newFixedThreadPool(2)
        try {
            val stop=executor.submit<EventDisposition> { val d=m.cancel(Cancel(r.reference));stopped.countDown();d }
            val late=executor.submit<EventDisposition> { assertTrue(stopped.await(5,TimeUnit.SECONDS));m.accept(chunk(r),1) }
            assertEquals(EventDisposition.ACCEPTED,stop.get(5,TimeUnit.SECONDS));assertEquals(EventDisposition.TERMINAL,late.get(5,TimeUnit.SECONDS))
            assertEquals("",m.text());closed(m)
        } finally { executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)) }
    }
    @Test fun simultaneousDuplicateChunksAppearOnlyOnce() {
        val r=request();val m=fixture(r);m.accept(ack(r),0)
        val start=CountDownLatch(1);val executor=Executors.newFixedThreadPool(2)
        try {
            val futures=(1..2).map { executor.submit<EventDisposition> { assertTrue(start.await(5,TimeUnit.SECONDS));m.accept(chunk(r),1) } }
            start.countDown();val results=futures.map { it.get(5,TimeUnit.SECONDS) }
            assertEquals(1,results.count { it==EventDisposition.ACCEPTED });assertEquals(1,results.count { it==EventDisposition.DUPLICATE })
            assertEquals("Synthétique",m.text());closed(m)
        } finally { executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)) }
    }
    @Test fun boundedAdversarialTraceExplorationNeverInventsNativeEvidence() {
        val r=request();val stale=request(key().copy(generation=2));val reached=mutableSetOf<ClientPhase>();var count=0
        // All 8^4 short traces; this is a deterministic invariant test, NOT a performance benchmark.
        for(code in 0 until 4096) {
            val m=fixture(r);var digits=code
            repeat(4) {
                val before=m.phase
                when(digits%8) {
                    0 -> m.accept(ack(r),0)
                    1 -> m.accept(chunk(r),0)
                    2 -> m.accept(done(r),0)
                    3 -> m.cancel(Cancel(r.reference))
                    4 -> m.expire(1000)
                    5 -> { assertEquals(EventDisposition.STALE,m.accept(ack(stale),0));assertEquals(before,m.phase) }
                    6 -> m.accept(ClientFailure(r.reference,0,ClientError.UNKNOWN_STATE,EventOrigin.FIXTURE),0)
                    7 -> m.accept(ack(request(question="Digest différent")),0)
                }
                digits/=8;closed(m);reached.add(m.phase)
                if(m.phase !in setOf(ClientPhase.STREAMING,ClientPhase.CONTENT_DONE)) assertEquals("",m.text())
                if(before in setOf(ClientPhase.UNKNOWN,ClientPhase.REFUSED,ClientPhase.CONTENT_DONE)) assertEquals(before,m.phase)
                if(before==ClientPhase.STOP_REQUESTED) assertTrue(m.phase in setOf(ClientPhase.STOP_REQUESTED,ClientPhase.UNKNOWN))
            };count++
        }
        assertEquals(ClientPhase.entries.toSet(),reached)
        val output=File("build/fusion-02/client-traces.json");output.parentFile.mkdirs()
        output.writeText("{\"format\":\"jarvis.llm.client-fixture-traces/1\",\"origin\":\"TEST_ONLY\",\"traces\":$count,\"steps_per_trace\":4,\"native_ready\":false,\"physical_proof\":false,\"states_reached\":["+
            reached.sortedBy { it.name }.joinToString(",") { "\"${it.name}\"" }+"]}\n")
    }
}

/** No backend, model file, permissions authority or native proof. TEST source set only. */
private class TestOnlyRig(private val fixtureModelPresent: Boolean, private val fixturePermission: Boolean) : GenerationClientPort {
    override val capabilities = ClientCapabilities()
    override fun submit(request: GenerateRequest): ClientEvent = when {
        !fixturePermission -> ClientFailure(request.reference,0,ClientError.UNAUTHORIZED,EventOrigin.FIXTURE)
        !fixtureModelPresent -> ClientFailure(request.reference,0,ClientError.NATIVE_UNQUALIFIED,EventOrigin.FIXTURE)
        else -> Ack(request.reference,EventOrigin.FIXTURE)
    }
    override fun cancel(request: Cancel) = NativeResourceState(null)
}
