package fr.jarvis.llm.engine

import fr.jarvis.domain.resources.*
import fr.jarvis.llm.ProcessResources
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Injected native and host-memory fixtures, never a real load/token or a substitute for native qualification. */
class LabBackendRefusalTest {
    private class EmptyNative : NativeApi {
        var loads=0;var creates=0;var generated=0;var cancels=0;var unloads=0;var failUnload=false
        override fun create(): Long { creates++;return 1 }
        override fun begin(handle: Long) {}
        override fun cancel(handle: Long) { cancels++ }
        override fun load(handle: Long,path: ByteArray,context: Int,threads: Int,small: Boolean) { loads++ }
        override fun generate(handle: Long,roles: Array<ByteArray>,texts: Array<ByteArray>,maxTokens: Int): ByteArray { generated++;return byteArrayOf() }
        override fun generateP2(handle: Long,roles: Array<ByteArray>,texts: Array<ByteArray>,maxTokens: Int,system: ByteArray)=generate(handle,roles,texts,maxTokens)
        override fun allocations(handle: Long)=longArrayOf(0,0,0)
        override fun unload(handle: Long) { unloads++;if(failUnload)error("SYNTHETIC cleanup failure") }
        override fun destroy(handle: Long) {}
    }
    @Test fun missingSignatureNeverLoadsOrGeneratesAndUsesOnlyExistingEngine() = runBlocking {
        val f=LabFixtures();val n=EmptyNative();val b=LlamaBackend(n,labHostGuard={})
        try { val spec=f.spec(challenge=b.labOwnerChallenge());assertTrue(runCatching {
            b.qualifyLab(f.authority,LabSignedPermit(spec,byteArrayOf()),java.nio.file.Path.of("/unit"),java.nio.file.Path.of("/unit/model"),f.request,OwnedStreamBuffer())
        }.isFailure);assertEquals(1,n.creates);assertEquals(0,n.loads);assertEquals(0,n.generated);assertNull(ProcessResources.coordinator.snapshot().model) }
        finally { b.close() }
    }
    @Test fun nonexistentFileConsumesPermitAndReleasesActualHeavyLeaseAfterCleanup() = runBlocking {
        val f=LabFixtures();val n=EmptyNative();val b=LlamaBackend(n,labHostGuard={});val s=f.sign(f.spec(challenge=b.labOwnerChallenge()))
        try { repeat(2) { assertTrue(runCatching { b.qualifyLab(f.authority,s,java.nio.file.Path.of("/unit-missing"),java.nio.file.Path.of("/unit-missing/model"),f.request,OwnedStreamBuffer()) }.isFailure) }
            assertEquals(0,n.loads);assertEquals(0,n.generated);assertNull(ProcessResources.coordinator.snapshot().heavy)
        } finally { b.close() }
    }
    @Test fun failedNativeCleanupRetainsModelAndRequiresExplicitRecovery() = runBlocking {
        val f=LabFixtures();val n=EmptyNative();val b=LlamaBackend(n,labHostGuard={})
        try {
            // Historical injected loader, not a verified laboratory residence or real GGUF.
            b.load("SYNTHETIC unit-only path",f.config);n.failUnload=true
            assertTrue(runCatching { b.unload() }.isFailure)
            assertNotNull(ProcessResources.coordinator.snapshot().model)
            val count=n.unloads;assertTrue(runCatching { b.generate(f.request) }.isFailure);assertEquals(count,n.unloads)
            n.failUnload=false;b.unload();assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { n.failUnload=false;b.close() }
    }
    @Test fun resourceRefusalDoesNotUnloadOrTouchOtherOwner() = runBlocking {
        val f=LabFixtures();val n=EmptyNative();val b=LlamaBackend(n,labHostGuard={})
        val heavy=(ProcessResources.coordinator.acquire(ResourceRequest("other",Module.LLM,ResourceClass.HEAVY,"unit")) as ResourceGrant.Granted).lease
        try { val s=f.sign(f.spec(challenge=b.labOwnerChallenge()));assertTrue(runCatching {
            b.qualifyLab(f.authority,s,java.nio.file.Path.of("/unit"),java.nio.file.Path.of("/unit/model"),f.request,OwnedStreamBuffer())
        }.isFailure);assertEquals(0,n.loads);assertEquals(0,n.unloads);assertNotNull(ProcessResources.coordinator.snapshot().heavy)
        } finally { heavy.close();b.close() }
    }
    @Test fun labScopeDoesNotFallBackToHistoricalPathsAfterFailure() = runBlocking {
        val f=LabFixtures();val n=EmptyNative();val b=LlamaBackend(n,labHostGuard={})
        try { val s=f.sign(f.spec(challenge=b.labOwnerChallenge()));runCatching {
            b.qualifyLab(f.authority,s,java.nio.file.Path.of("/unit"),java.nio.file.Path.of("/unit/model"),f.request,OwnedStreamBuffer()) }
            assertTrue(runCatching { b.prepareOperation() }.isFailure);assertTrue(runCatching { b.generate(f.request) }.isFailure)
            assertTrue(runCatching { b.load("SYNTHETIC",f.config) }.isFailure);assertEquals(0,n.loads)
        } finally { b.close() }
    }
}
