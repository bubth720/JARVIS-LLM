package fr.jarvis.llm.engine

import fr.jarvis.domain.resources.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Independent component audit only. No JNI, model, installed trust root or productive permits. */
class AuthorityAuditTest {
    private class PairFixture : AutoCloseable {
        val broker=ResourceCoordinator()
        val owner="public-audit-fixture"
        val heavy=(broker.acquire(ResourceRequest(owner,Module.LLM,ResourceClass.HEAVY,"audit fixture")) as ResourceGrant.Granted).lease
        val model=(broker.acquire(ResourceRequest(owner,Module.LLM,ResourceClass.MODEL,"audit fixture",modelKey="SMALL")) as ResourceGrant.Granted).lease
        fun observe()=requireNotNull(broker.observeOwnedPair(owner,heavy,model))
        override fun close() { model.close();heavy.close() }
    }
    @Test fun configuredFactoryRemainsClosed() {
        val key=java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public
        val e=runCatching { LabOperatorTrust.fromApprovedKey(key) }.exceptionOrNull()
        assertEquals("LAB_TRUST_ROOT_NOT_CONFIGURED",e!!.message)
    }
    @Test fun environmentOnlyExecutionCannotAttest_A06() {
        val failure=runCatching { LabExecution.observe() }.exceptionOrNull()
        assertEquals("LAB_EXECUTION_ATTESTATION_NOT_CONFIGURED",failure?.message)
    }
    @Test fun arbitraryExecutionConstructorIsNotHostObservation() {
        // This is not a credential: it demonstrates why approved bootstrap composition is mandatory.
        val claimed=LabExecution("71000001","1","1".repeat(40))
        assertEquals("71000001",claimed.run)
        assertEquals("1".repeat(40),claimed.commit)
    }
    @Test fun failingWatcherDoesNotSkipLaterNotification_FIXED()=PairFixture().use { f ->
        val first=f.observe();val second=f.observe();var calls=0
        try {
            first.onRevoked { calls++;error("PUBLIC_UNIT_WATCHER_FAILURE") }
            second.onRevoked { calls++ }
            val error=runCatching { f.heavy.close() }.exceptionOrNull()
            assertEquals("PUBLIC_UNIT_WATCHER_FAILURE",error!!.message)
            assertEquals(2,calls)
            assertFalse(first.isLive());assertFalse(second.isLive())
        }finally { first.close();second.close() }
    }
    @Test fun revocationCallbackIsOutsideBrokerAndLeaseMonitor_FIXED()=PairFixture().use { f ->
        f.observe().use { proof ->
            var brokerHeld=true;var leaseHeld=false
            proof.onRevoked { brokerHeld=Thread.holdsLock(f.broker);leaseHeld=Thread.holdsLock(f.heavy) }
            f.heavy.close();assertFalse(brokerHeld);assertFalse(leaseHeld)
        }
    }
    @Test fun concurrentCloseAndReentrantStopDoNotDeadlock_A08()=PairFixture().use { f ->
        f.observe().use { proof ->
            val entered=CountDownLatch(1)
            val finish=CountDownLatch(1)
            var notifications=0
            proof.onRevoked {
                notifications++
                entered.countDown()
                // Reenter the same lease from a different thread; the original
                // close must not hold a monitor that blocks this completion.
                val worker=Thread { try { f.heavy.close() } finally { finish.countDown() } }
                worker.isDaemon=true
                worker.start()
                assertTrue("Lease monitor held across callback",finish.await(2,TimeUnit.SECONDS))
            }
            val closer=Thread { f.heavy.close() }
            closer.start()
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            closer.join(2000)
            assertFalse("Revocation callback deadlocked",closer.isAlive)
            assertEquals(1,notifications)
            assertFalse(proof.isLive())
        }
    }
    @Test fun revokedPinnedSlotsAreNotReissued()=PairFixture().use { f ->
        f.observe().use { proof ->
            f.model.close();assertFalse(proof.isLive());assertNotNull(f.broker.snapshot().model)
            assertTrue(f.broker.acquire(ResourceRequest("other",Module.LLM,ResourceClass.HEAVY,"audit fixture")) is ResourceGrant.Refused)
        };assertNull(f.broker.snapshot().model)
    }
    @Test fun closedObservationDropsItsWatcher()=PairFixture().use { f ->
        var calls=0;val proof=f.observe();proof.onRevoked { calls++ };proof.close();f.model.close();assertEquals(0,calls)
    }
    @Test fun currentFileModeDoesNotRevokePreopenedWritableDescriptor_REPRODUCED() {
        val root=Files.createTempDirectory("jarvis-public-audit-");val path=Files.write(root.resolve("public-fixture.txt"),"AAAA".toByteArray())
        try {
            FileChannel.open(path,StandardOpenOption.WRITE).use { writer ->
                Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("r--------"))
                writer.position(0);writer.write(ByteBuffer.wrap("BBBB".toByteArray()));writer.force(true)
                assertEquals("BBBB",Files.readString(path))
                assertEquals(PosixFilePermissions.fromString("r--------"),Files.getPosixFilePermissions(path))
            }
        }finally { Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));Files.delete(path);Files.delete(root) }
    }
    @Test fun metadataOnlyStabilityCheckMissesRestoredMtimeMutation_REPRODUCED() {
        val root=Files.createTempDirectory("jarvis-public-audit-");val path=root.resolve(LabAuthorizedModel.fileName)
        try {
            java.io.RandomAccessFile(path.toFile(),"rw").use { it.setLength(LabAuthorizedModel.size) }
            val before=Files.readAttributes(path,BasicFileAttributes::class.java)
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("r--------"))
            // Explicit unit-only construction: no valid model SHA, residence, permit or inference is claimed.
            val ctor=LabFileProof::class.java.getDeclaredConstructor(Path::class.java,Any::class.java,java.lang.Long.TYPE).apply { isAccessible=true }
            val proof=ctor.newInstance(path,before.fileKey(),before.lastModifiedTime().toMillis())
            proof.requireStable()
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"))
            FileChannel.open(path,StandardOpenOption.WRITE).use { writer -> writer.write(ByteBuffer.wrap("SYNTHETIC MUTATION".toByteArray()));writer.force(true) }
            Files.setLastModifiedTime(path,before.lastModifiedTime())
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("r--------"))
            proof.requireStable() // The pinned code checks inode/size/mtime/links/mode, not content here.
            assertEquals("SYNTHETIC MUTATION",Files.newInputStream(path).use { String(it.readNBytes(18)) })
            assertEquals(before.fileKey(),Files.readAttributes(path,BasicFileAttributes::class.java).fileKey())
        }finally { Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));Files.deleteIfExists(path);Files.delete(root) }
    }
    @Test fun filesystemExceptionRedactsPrivatePath_FIXED() {
        val root=Files.createTempDirectory("jarvis-public-audit-").resolve("private-fixture-missing")
        try {
            val e=runCatching { LabFileProof.verify(root,root.resolve(LabAuthorizedModel.fileName)) {} }.exceptionOrNull()
            assertNotNull(e);assertEquals("LAB_FILE_IO_REFUSED",e!!.message);assertFalse(e.message.orEmpty().contains("private-fixture-missing"))
        }finally { Files.delete(root.parent) }
    }
    @Test fun deadlineIndependentOfBlockingMonitor_FIXED() {
        val fixture=LabFixtures();val signed=fixture.sign(fixture.spec(expiry=fixture.clock.wall+300));val a=fixture.authority.redeem(signed,fixture.owner,fixture.request)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val cancelled=CountDownLatch(1)
        try {
            a.attachStop(monitor={entered.countDown();check(release.await(5,TimeUnit.SECONDS))}) { cancelled.countDown() }
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            // The periodic monitor owns the only control thread beyond the real scheduled deadline.
            assertTrue("Deadline must not wait for the blocking monitor",cancelled.await(1500,TimeUnit.MILLISECONDS))
            release.countDown();assertTrue(cancelled.await(2,TimeUnit.SECONDS))
        }finally { release.countDown();a.close() }
    }
    @Test fun terminalPhaseCannotBeOverwritten_FIXED() {
        val f=LabFixtures();val a=f.attempt()
        try { a.terminal(LabPhase.CLEANUP_FAILED);val failure=runCatching { a.terminal(LabPhase.CONTENT_DONE) }.exceptionOrNull();assertEquals("LAB_TERMINAL_ALREADY_SEALED",failure!!.message);assertEquals(LabPhase.CLEANUP_FAILED,a.phase) }finally { a.close() }
    }
    @Test fun revocationIsOneShotAfterFailingStop() {
        val f=LabFixtures();val signed=f.sign();val a=f.authority.redeem(signed,f.owner,f.request);var calls=0
        try {
            a.attachStop(monitor={}) { calls++;error("PUBLIC_UNIT_STOP_FAILURE") }
            assertTrue(runCatching { f.authority.revoke(signed) }.isFailure)
            f.authority.revoke(signed);assertEquals(1,calls);assertFalse(a.isLive())
        }finally { a.close() }
    }
}
