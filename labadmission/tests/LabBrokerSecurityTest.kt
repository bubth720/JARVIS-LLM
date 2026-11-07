package fr.jarvis.llm.engine

import fr.jarvis.domain.resources.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LabBrokerSecurityTest {
    private class PairFixture(val broker: ResourceCoordinator=ResourceCoordinator()) : AutoCloseable {
        val owner="unit-owner"
        val heavy=(broker.acquire(ResourceRequest(owner,Module.LLM,ResourceClass.HEAVY,"unit")) as ResourceGrant.Granted).lease
        val model=(broker.acquire(ResourceRequest(owner,Module.LLM,ResourceClass.MODEL,"unit",modelKey="SMALL")) as ResourceGrant.Granted).lease
        fun proof()=requireNotNull(broker.observeOwnedPair(owner,heavy,model))
        override fun close() { model.close();heavy.close() }
    }
    @Test fun actualPairHasMintedIdentityAndIncarnation() = PairFixture().use { f ->
        f.proof().use { p -> assertTrue(p.isLive());assertTrue(p.heavyIdentity > 0);assertTrue(p.modelIdentity > p.heavyIdentity);assertTrue(p.revision > 0);assertNotNull(p.brokerIncarnation) }
    }
    @Test fun foreignBrokerEvenWithMatchingNumericIdsIsRejected() = PairFixture().use { a -> PairFixture().use { b ->
        assertNull(a.broker.observeOwnedPair(a.owner,b.heavy,b.model));assertNull(a.broker.observeOwnedPair(a.owner,a.heavy,b.model))
        a.proof().use { x -> b.proof().use { y -> assertNotEquals(x.brokerIncarnation,y.brokerIncarnation) } }
    } }
    @Test fun ownerAndClassMismatchFailClosed() = PairFixture().use { f ->
        assertNull(f.broker.observeOwnedPair("wrong-owner",f.heavy,f.model));assertNull(f.broker.observeOwnedPair(f.owner,f.model,f.heavy))
    }
    @Test fun closedLeaseCannotBeAttested() = PairFixture().use { f ->
        f.model.close();assertNull(f.broker.observeOwnedPair(f.owner,f.heavy,f.model))
    }
    @Test fun closeDuringPinRevokesImmediatelyAndRetainsSlots() = PairFixture().use { f ->
        val p=f.proof();f.close();assertFalse(p.isLive());assertNotNull(f.broker.snapshot().heavy);assertNotNull(f.broker.snapshot().model)
        assertTrue(f.broker.acquire(ResourceRequest("other",Module.LLM,ResourceClass.HEAVY,"unit")) is ResourceGrant.Refused)
        p.close();p.close();assertNull(f.broker.snapshot().heavy);assertNull(f.broker.snapshot().model)
    }
    @Test fun oldObservationNeverRevalidatesAfterNewLease() {
        val b=ResourceCoordinator();val old=PairFixture(b);val p=old.proof();val hid=p.heavyIdentity
        old.close();p.close();PairFixture(b).use { next ->
            next.proof().use { current -> assertTrue(current.heavyIdentity > hid);assertFalse(p.isLive());assertTrue(current.isLive()) }
            old.close();assertNotNull(b.snapshot().model)
        };assertNull(b.snapshot().model)
    }
    @Test fun bothPinsMustFinishBeforeClosedSlotIsReleased() = PairFixture().use { f ->
        val p=f.proof();val q=f.proof();f.close();p.close();assertNotNull(f.broker.snapshot().model);q.close();assertNull(f.broker.snapshot().model)
    }
    @Test fun brokerRevocationSignalsStopOutsideMonitor() = PairFixture().use { f ->
        val pool=Executors.newSingleThreadExecutor();val p=f.proof();var stops=0
        try { p.onRevoked { pool.submit { f.broker.snapshot() }.get(2,TimeUnit.SECONDS);stops++ };f.heavy.close();assertEquals(1,stops);assertFalse(p.isLive()) }
        finally { p.close();pool.shutdownNow() }
    }
    @Test fun lateWatchOfRevokedObservationImmediatelySignalsStop() = PairFixture().use { f ->
        val p=f.proof();var stops=0;f.model.close();p.onRevoked { stops++ };assertEquals(1,stops);p.close()
    }
    @Test fun closingObservationDetachesWatchAndRetainsLiveLease() = PairFixture().use { f ->
        val p=f.proof();var stops=0;p.onRevoked { stops++ };p.close();f.model.close();assertEquals(0,stops)
    }
    @Test fun snapshotsDoNotExposeLeaseCredentials() = PairFixture().use { f ->
        f.proof().use { p -> assertEquals("ResourceObservation(redacted)",p.toString());assertEquals("ResourceLease(redacted)",f.model.toString()) }
    }
}
