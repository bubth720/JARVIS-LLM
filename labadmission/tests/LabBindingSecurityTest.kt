package fr.jarvis.llm.engine

import fr.jarvis.domain.resources.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Staged unit fixtures simulate residence/preparation only; no native/model admission is asserted. */
class LabBindingSecurityTest {
    private class Staged : AutoCloseable {
        val f=LabFixtures();val a=f.attempt();val broker=ResourceCoordinator()
        val h=(broker.acquire(ResourceRequest(f.owner.owner,Module.LLM,ResourceClass.HEAVY,"unit")) as ResourceGrant.Granted).lease
        val m=(broker.acquire(ResourceRequest(f.owner.owner,Module.LLM,ResourceClass.MODEL,"unit",modelKey="SMALL")) as ResourceGrant.Granted).lease
        val proof=requireNotNull(broker.observeOwnedPair(f.owner.owner,h,m))
        val r=StreamResident(m,f.owner.owner,f.owner.worker,UUID.randomUUID().toString(),f.config)
        init { a.advance(LabPhase.AUTHORIZED,LabPhase.FILE_VERIFIED);a.advance(LabPhase.FILE_VERIFIED,LabPhase.RESIDENT) }
        fun prepared(origin: String=a.spec.requestSha256, template: String=streamHash(f.template)) =
            StreamPrepared(streamHash(f.rendered),template,"c".repeat(64),10,512,f.config,origin)
        var residence=true
        fun bind()=LabStreamBinding.admit(a,proof,prepared(),r) { residence }
        override fun close() { a.close();proof.close();m.close();h.close() }
    }
    @Test fun scopeHasNoCoreMandateFieldAndBindingIsDifferent() = Staged().use { s ->
        val b=s.bind();assertFalse(b.javaClass.declaredFields.any { it.name.contains("coreMandate",true) });assertTrue(b.isLive());assertEquals(LabPhase.PREPARED,s.a.phase)
        assertTrue(b.toString().contains("LAB_CLOUD"));assertFalse(b.toString().contains(s.r.incarnation))
    }
    @Test fun lostNativeResidenceIncarnationRevokesBinding() = Staged().use { s ->
        val binding=s.bind();s.residence=false;assertFalse(binding.isLive())
    }
    @Test fun missingResidenceAttestationCannotAdmit() = Staged().use { s ->
        s.residence=false;assertTrue(runCatching { s.bind() }.isFailure)
    }
    @Test fun preparationConsumedOnce() = Staged().use { s ->
        s.bind();assertTrue(runCatching { s.bind() }.isFailure)
    }
    @Test fun wrongTemplateOrRequestOriginRejected() = Staged().use { s ->
        assertTrue(runCatching { LabStreamBinding.admit(s.a,s.proof,s.prepared(template="d".repeat(64)),s.r) { s.residence } }.isFailure)
        assertTrue(runCatching { LabStreamBinding.admit(s.a,s.proof,s.prepared(origin="e".repeat(64)),s.r) { s.residence } }.isFailure)
    }
    @Test fun revokedLeaseRefusesPreparation() = Staged().use { s ->
        s.h.close();assertTrue(runCatching { s.bind() }.isFailure)
    }
    @Test fun callbackValidatesExactNativeRenderedBytesAndCount() = Staged().use { s ->
        val b=s.bind();val q=OwnedStreamBuffer();q.bind(b);val cb=OwnedStreamCallback(b,q)
        assertFalse(cb.onNativePrepared(s.f.rendered,s.f.template,11));assertFalse(cb.hasPrepared());cb.close()
    }
    @Test fun callbackRejectsTemplateMismatchBeforeAnyToken() = Staged().use { s ->
        val b=s.bind();val q=OwnedStreamBuffer();q.bind(b);val cb=OwnedStreamCallback(b,q)
        assertFalse(cb.onNativePrepared(s.f.rendered,"SYNTHETIC changed".toByteArray(),10));assertFalse(cb.hasPrepared());cb.close()
    }
    @Test fun labRevocationRejectsLaterTextAndAllowsCancellationTerminal() = Staged().use { s ->
        val b=s.bind();val q=OwnedStreamBuffer();q.bind(b);val cb=OwnedStreamCallback(b,q);val id=b.bindingSha256.toByteArray(Charsets.US_ASCII)
        assertTrue(cb.onNativePrepared(s.f.rendered,s.f.template,10));assertEquals(0,cb.onNativeEvent(id,0,0,byteArrayOf(),0,-1,1))
        s.a.revoke();assertEquals(2,cb.onNativeEvent(id,1,1,"SYNTHETIC late".toByteArray(),1,100,1))
        assertEquals(0,cb.onNativeEvent(id,3,1,byteArrayOf(),0,-1,1));assertEquals(StreamKind.CANCELLED,q.acceptedTerminal());cb.close()
    }
    @Test fun actualBrokerRevocationAlsoMakesCallbackCancel() = Staged().use { s ->
        val b=s.bind();val q=OwnedStreamBuffer();q.bind(b);val cb=OwnedStreamCallback(b,q);val id=b.bindingSha256.toByteArray(Charsets.US_ASCII)
        assertTrue(cb.onNativePrepared(s.f.rendered,s.f.template,10));s.m.close()
        assertEquals(2,cb.onNativeEvent(id,0,0,byteArrayOf(),0,-1,1));cb.close()
    }
    @Test fun backpressureRetriesWithoutChangingSequenceInUnitBuffer() = Staged().use { s ->
        val b=s.bind();val q=OwnedStreamBuffer();q.bind(b)
        assertEquals(StreamOffer.ACCEPTED,q.offer(OwnedStreamEvent(b,0,StreamKind.ACK,byteArrayOf(),0,-1,NativeStreamClock.HOST_MONOTONIC)))
        for(i in 1..4) assertEquals(StreamOffer.ACCEPTED,q.offer(OwnedStreamEvent(b,i,StreamKind.TOKEN,ByteArray(4096){65},i,100,NativeStreamClock.HOST_MONOTONIC)))
        val next=OwnedStreamEvent(b,5,StreamKind.TOKEN,byteArrayOf(66),5,100,NativeStreamClock.HOST_MONOTONIC)
        assertEquals(StreamOffer.RETRY,q.offer(next));assertEquals(16384,q.pendingBytes());q.poll();q.poll();assertEquals(StreamOffer.ACCEPTED,q.offer(next))
        assertEquals(StreamOffer.ACCEPTED,q.offer(next));assertEquals(12289,q.pendingBytes())
    }
    @Test fun terminalAndClosedCallbackRejectLateData() = Staged().use { s ->
        val b=s.bind();val q=OwnedStreamBuffer();q.bind(b);val cb=OwnedStreamCallback(b,q);cb.close()
        assertTrue(runCatching { cb.onNativePrepared(s.f.rendered,s.f.template,10) }.isFailure)
        s.a.terminal(LabPhase.UNKNOWN);assertEquals(LabPhase.UNKNOWN,s.a.phase)
        s.a.terminal(LabPhase.CLEANUP_FAILED);assertEquals(LabPhase.CLEANUP_FAILED,s.a.phase)
    }
}
