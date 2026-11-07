package fr.jarvis.llm.engine

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.UUID

class LabAuthoritySecurityTest {
    @Test fun validSignatureAdmitsOneBoundAttemptOnly() {
        val f=LabFixtures();val signed=f.sign();val a=f.authority.redeem(signed,f.owner,f.request)
        try { assertTrue(a.isLive());assertEquals(LabPhase.AUTHORIZED,a.phase)
            assertTrue(runCatching { f.authority.redeem(signed,f.owner,f.request) }.isFailure)
        }finally { a.close() };assertFalse(a.isLive())
    }
    @Test fun absentSignatureFailsClosed() {
        val f=LabFixtures();assertTrue(runCatching { f.authority.redeem(LabSignedPermit(f.spec(),byteArrayOf()),f.owner,f.request) }.isFailure)
    }
    @Test fun wrongKeyFailsWithoutGrant() {
        val f=LabFixtures();val other=LabFixtures();val auth=LabCloudAuthority(other.trust,f.execution,f.clock)
        assertTrue(runCatching { auth.redeem(f.sign(),f.owner,f.request) }.isFailure)
    }
    @Test fun unconfiguredRootCannotBeReplacedByCallerSelfSignedKey() {
        val f=LabFixtures();val e=runCatching { LabOperatorTrust.fromApprovedKey(f.keys.public) }.exceptionOrNull()
        assertEquals("LAB_TRUST_ROOT_NOT_CONFIGURED",e!!.message)
    }
    @Test fun expiredPermitRejected() {
        val f=LabFixtures();val s=f.sign(f.spec(issued=f.clock.wall-1000,expiry=f.clock.wall))
        assertTrue(runCatching { f.authority.redeem(s,f.owner,f.request) }.isFailure)
    }
    @Test fun futurePermitRejected() {
        val f=LabFixtures();val s=f.sign(f.spec(issued=f.clock.wall+1000,expiry=f.clock.wall+2000))
        assertTrue(runCatching { f.authority.redeem(s,f.owner,f.request) }.isFailure)
    }
    @Test fun revokedBeforeAdmissionAndClonedSignatureRemainRejected() {
        val f=LabFixtures();val spec=f.spec();f.authority.revoke(f.sign(spec))
        assertTrue(runCatching { f.authority.redeem(f.sign(spec),f.owner,f.request) }.isFailure)
    }
    @Test fun revokeActivePermitCancelsAndInvokesStop() {
        val f=LabFixtures();val s=f.sign();val a=f.authority.redeem(s,f.owner,f.request);var stops=0
        try { a.attachStop(monitor={}) { stops++ };f.authority.revoke(s);assertEquals(1,stops);assertFalse(a.isLive()) }finally { a.close() }
    }
    @Test fun forgedRevocationCannotStopValidAttempt() {
        val f=LabFixtures();val signed=f.sign();val a=f.authority.redeem(signed,f.owner,f.request)
        try { assertTrue(runCatching { f.authority.revoke(LabSignedPermit(signed.spec,ByteArray(64))) }.isFailure);assertTrue(a.isLive()) }finally { a.close() }
    }
    @Test fun failingStopIsNotRetriedByRepeatedRevocation() {
        val f=LabFixtures();val signed=f.sign();val a=f.authority.redeem(signed,f.owner,f.request);var stops=0
        try { a.attachStop(monitor={}) { stops++;error("UNIT_STOP_FAILED") }
            assertTrue(runCatching { f.authority.revoke(signed) }.isFailure)
            repeat(3) { f.authority.revoke(signed) }
            assertEquals(1,stops);assertFalse(a.isLive())
        }finally { a.close() }
    }
    @Test fun expiredMonotonicDeadlineCannotBeExtendedByWallClockRollback() {
        val f=LabFixtures();val a=f.attempt()
        try { f.clock.wall=0;f.clock.mono+=60_000_000_000;assertFalse(a.isLive());assertTrue(runCatching { a.requireLive() }.isFailure) }finally { a.close() }
    }
    @Test fun scheduledControlWatchdogInvokesOnlyStop() {
        val f=LabFixtures();val s=f.sign(f.spec(expiry=f.clock.wall+100));val a=f.authority.redeem(s,f.owner,f.request);val stopped=CountDownLatch(1)
        try { a.attachStop(monitor={}) { stopped.countDown() };assertTrue(stopped.await(3,TimeUnit.SECONDS));assertFalse(a.isLive()) }finally { a.close() }
    }
    @Test fun closedAttemptRemovesRevocationCallback() {
        val f=LabFixtures();val s=f.sign();val a=f.authority.redeem(s,f.owner,f.request);var stops=0
        a.attachStop(monitor={}) { stops++ };a.close();f.authority.revoke(s);assertEquals(0,stops)
    }
    @Test fun revokeCallbackRunsOutsideReplayLedgerMonitor() {
        val f=LabFixtures();val s=f.sign();val a=f.authority.redeem(s,f.owner,f.request);val pool=Executors.newSingleThreadExecutor()
        try { a.attachStop(monitor={}) { assertFalse(pool.submit<Boolean> { a.isLive() }.get(2,TimeUnit.SECONDS)) };f.authority.revoke(s) }
        finally { a.close();pool.shutdownNow() }
    }
    @Test fun replayRejectedAcrossAuthorityInstances() {
        val f=LabFixtures();val spec=f.spec();f.authority.redeem(f.sign(spec),f.owner,f.request).close()
        val auth=LabCloudAuthority(f.trust,f.execution,f.clock)
        assertTrue(runCatching { auth.redeem(f.sign(spec),f.owner,f.request) }.isFailure)
    }
    @Test fun wrongRunCommitAttemptOrIncarnationRejected() {
        val f=LabFixtures()
        for(e in listOf(LabExecution("90000000","1",f.execution.commit),LabExecution(f.execution.run,"2",f.execution.commit),LabExecution(f.execution.run,"1","b".repeat(40)))) {
            assertTrue(runCatching { f.authority.redeem(f.sign(f.spec(execution=e)),f.owner,f.request) }.isFailure)
        }
        assertTrue(runCatching { f.authority.redeem(f.sign(),LabOwnerChallenge(f.owner.owner,UUID.randomUUID().toString()),f.request) }.isFailure)
    }
    @Test fun alteredSessionCannotReuseSignature() {
        val f=LabFixtures();val s=f.sign();val altered=f.spec(nonce=s.spec.nonce,session=UUID.randomUUID().toString())
        // Deliberately no valid signature for the changed session.
        assertTrue(runCatching { f.authority.redeem(LabSignedPermit(altered,java.security.Signature.getInstance("Ed25519").run { initSign(f.keys.private);update(s.spec.canonical());sign() }),f.owner,f.request) }.isFailure)
    }
    @Test fun wrongRequestShaAndConfigurationRejected() {
        val f=LabFixtures();assertTrue(runCatching { f.authority.redeem(f.sign(f.spec(requestSha="e".repeat(64))),f.owner,f.request) }.isFailure)
        assertTrue(runCatching { f.spec(config=f.config.copy(threads=2)) }.isFailure)
        assertTrue(runCatching { f.spec(config=f.config.copy(maxTokens=64)) }.isFailure)
    }
    @Test fun anotherModelOrHashCannotEnterPermit() {
        val f=LabFixtures();assertTrue(runCatching { f.spec(modelSha="b".repeat(64)) }.isFailure)
        assertTrue(runCatching { f.spec(config=f.config.copy(model=fr.jarvis.domain.ModelKind.MAIN)) }.isFailure)
    }
    @Test fun ttlIsFiniteAndNeverImplicit() {
        val f=LabFixtures();assertTrue(runCatching { f.spec(expiry=f.clock.wall+300001) }.isFailure)
        assertTrue(runCatching { f.spec(expiry=f.clock.wall) }.isFailure)
    }
    @Test fun malformedUnicodeAndOversizedRequestCannotBeSignedAsPrepared() {
        val f=LabFixtures()
        assertTrue(runCatching {
            val bad=fr.jarvis.domain.GenerationRequest(listOf(fr.jarvis.domain.Message("user","\uD800")),f.config,"unit")
            labRequestHash(bad)
        }.isFailure)
        assertTrue(runCatching {
            val huge=fr.jarvis.domain.GenerationRequest(listOf(fr.jarvis.domain.Message("user","a".repeat(128*1024+1))),f.config,"unit")
            labRequestHash(huge)
        }.isFailure)
    }
    @Test fun signedSnapshotDoesNotFollowMutableCallerHistory() {
        val f=LabFixtures();val mutable=f.request.messages.toMutableList()
        val incoming=fr.jarvis.domain.GenerationRequest(mutable,f.config,f.request.systemPrompt)
        val frozen=labFreezeRequest(incoming);val expected=labRequestHash(frozen)
        mutable[0]=fr.jarvis.domain.Message("user","SYNTHETIC altered after authorization")
        assertEquals(expected,labRequestHash(frozen));assertNotEquals(expected,labRequestHash(incoming))
        f.authority.redeem(f.sign(f.spec(requestSha=expected)),f.owner,frozen).close()
    }
    @Test fun requestAndCredentialsHaveNoImplicitTextLog() {
        val f=LabFixtures();val s=f.sign();val a=f.authority.redeem(s,f.owner,f.request)
        try { for(value in listOf(s,s.spec,a,f.trust,f.owner,f.execution)) {
            val text=value.toString();assertFalse(text.contains(f.request.messages.single().text));assertFalse(text.contains(f.owner.worker));assertFalse(text.contains(s.spec.nonce))
        } }finally { a.close() }
        assertFalse(s.spec.canonical().toString(Charsets.UTF_8).contains(f.request.messages.single().text))
    }
}
