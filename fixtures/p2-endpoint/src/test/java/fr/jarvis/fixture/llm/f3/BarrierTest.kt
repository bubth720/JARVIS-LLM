package fr.jarvis.fixture.llm.f3

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BarrierTest {
    private fun id()=UUID.randomUUID().toString()
    private fun key()=OperationKey(id(),id(),id(),id(),1,id(),1,id(),id(),id(),"a".repeat(64))
    private fun gate()=AdmissionBarrier(id(),setOf("runtime","imports")).apply {
        register("runtime");register("imports");seal()
    }
    private fun refused(block:()->Unit) { try { block();fail("accepted") } catch(_:IllegalStateException){} }
    @Test fun incompleteRegistryNeverFreezesOrAdmits() {
        val g=AdmissionBarrier(id(),setOf("runtime","imports"));g.register("runtime")
        refused { g.seal() };refused { g.admit("runtime",key(),AdmissionBarrier.Work.LOAD) }
        refused { g.freeze(id(),id()) }
    }
    @Test fun freezeBlocksAllWorkKindsAndNewParticipants() {
        val g=gate();g.freeze(id(),id())
        AdmissionBarrier.Work.entries.forEach { refused { g.admit("runtime",key(),it) } }
        refused { g.register("other") }
    }
    @Test fun outstandingImportsAndQueuedCleanupStayVisibleUntilRealReturn() {
        val g=gate();val i=g.admit("imports",key(),AdmissionBarrier.Work.IMPORT)
        val u=g.admit("runtime",key(),AdmissionBarrier.Work.UNLOAD)
        g.start(i);val f=g.freeze(id(),id());val before=g.inspect(f)
        assertEquals(1,before.imports);assertEquals(1,before.queued);assertEquals(1,before.active)
        refused { g.returned(u) };g.returned(i);g.start(u)
        assertEquals(1,g.inspect(f).active);g.returned(u)
        assertEquals(0,g.inspect(f).active);assertEquals(0,g.inspect(f).queued)
        assertTrue(g.inspect(f).revision>before.revision)
        refused { g.admit("runtime",key(),AdmissionBarrier.Work.LOAD) }
    }
    @Test fun repeatedFreezeIdempotentAndStaleTicketsAndRoundRefused() {
        val g=gate();val t=g.admit("runtime",key(),AdmissionBarrier.Work.GENERATE);g.start(t)
        val round=id();val barrier=id();val f=g.freeze(round,barrier)
        assertSame(f,g.freeze(round,barrier));refused { g.freeze(id(),barrier) }
        refused { g.returned(t.copy()) };g.returned(t);refused { g.returned(t) }
        refused { g.inspect(f.copy()) }
    }
    @Test fun recreationAndQuarantineNeverClearOldWorkOrTransferTokens() {
        val a=gate();val t=a.admit("runtime",key(),AdmissionBarrier.Work.LOAD);a.start(t)
        val f=a.freeze(id(),id());val b=gate()
        refused { b.inspect(f) };refused { b.returned(t) };assertEquals(1,a.inspect(f).active)
        a.quarantine();refused { a.inspect(f) };refused { a.start(t) }
        refused { a.freeze(id(),id()) }
    }
    @Test fun freezeAndAdmissionRaceHasNoInvisibleTicket() {
        repeat(24) {
            val g=gate();val go=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
            try {
                val admitted=pool.submit<AdmissionBarrier.Ticket?> { go.await();runCatching { g.admit("imports",key(),AdmissionBarrier.Work.IMPORT) }.getOrNull() }
                val frozen=pool.submit<AdmissionBarrier.Frozen> { go.await();g.freeze(id(),id()) }
                go.countDown();val t=admitted.get(3,TimeUnit.SECONDS);val f=frozen.get(3,TimeUnit.SECONDS)
                assertEquals(if(t==null)0 else 1,g.inspect(f).queued)
                refused { g.admit("runtime",key(),AdmissionBarrier.Work.GENERATE) }
            } finally { pool.shutdownNow() }
        }
    }
    @Test fun boundedLedgerNeverEvictsOutstandingWork() {
        val g=gate();repeat(128) { g.admit("runtime",key(),AdmissionBarrier.Work.LOAD) }
        refused { g.admit("runtime",key(),AdmissionBarrier.Work.LOAD) }
        assertEquals(128,g.inspect(g.freeze(id(),id())).queued)
    }
}
