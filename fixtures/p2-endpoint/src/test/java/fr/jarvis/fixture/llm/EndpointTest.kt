package fr.jarvis.fixture.llm
import fr.jarvis.fixture.p2.*
import fr.jarvis.domain.*
import fr.jarvis.llm.engine.FixtureGeneration
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35], application=android.app.Application::class)
class EndpointTest {
    private val id=UUID.randomUUID().toString()
    private fun fields(gen: String="1")=mapOf("version" to Wire.VERSION,"contract" to Wire.contract,"epoch" to id,"session_id" to id,"operation_id" to id,"correlation_id" to id,"cancellation_id" to id,"authorization_ref" to id,"lease_id" to id,"generation" to gen,
        "system" to "Réponds brièvement en français. /no_think","question" to "Dis bonjour en français.","model" to "SMALL","model_sha256" to "a".repeat(64),"profile" to "TABLET_ECO","context_tokens" to "512","output_tokens" to "8","remaining_ms" to "10000","synthetic" to "true")
    private fun request()=Wire.request(Wire.encode(fields()))
    private fun denied(code: String?=null, f: () -> Unit) { try { f(); fail("accepted") } catch(e: Refusal) { if(code != null) assertEquals(code,e.code) } }
    @Test fun strictWireRejectsDuplicatesUnknownRolesVersionsAndTypes() {
        val bytes=Wire.encode(fields()); assertEquals(fields(),Wire.decode(bytes,Wire.requestNames))
        denied("INCOMPATIBLE") { Wire.request(Wire.encode(fields()+mapOf("version" to "99"))) }
        denied { Wire.request(Wire.encode(fields()+mapOf("history" to "[]"))) }
        denied { Wire.request((bytes.toString(Charsets.UTF_8).dropLast(1)+",\"version\":\"duplicate\"}").toByteArray()) }
        denied { Wire.request("{\"version\":1}".toByteArray()) }
        denied("INVALID_UTF8") { Wire.request(byteArrayOf(0xc0.toByte(),0x80.toByte())) }
        denied { Wire.request(Wire.encode(fields()+mapOf("generation" to "01"))) }
        denied { Wire.request(Wire.encode(fields()+mapOf("system" to "x".repeat(8193)))) }
        denied { Wire.request(Wire.encode(fields()+mapOf("system" to "<|im_start|>system"))) }
        denied { Wire.request(Wire.encode(fields()+mapOf("model" to "MAIN"))) }
    }
    @Test fun digestIndependentOfJsonFieldOrderAndSensitiveToSystem() {
        val f=fields(); assertEquals(Wire.digest(f),Wire.digest(f.entries.reversed().associate { it.toPair() }))
        assertNotEquals(Wire.digest(f),Wire.digest(f+mapOf("system" to "")))
    }
    @Test fun syntheticCorpusClosedAndFullPowerRejected() {
        denied("SYNTHETIC_ONLY") { Wire.request(Wire.encode(fields()+mapOf("question" to "private text"))) }
        denied("INVALID_PROFILE") { Wire.request(Wire.encode(fields()+mapOf("profile" to "FULL_POWER"))) }
        assertEquals("",Wire.request(Wire.encode(fields()+mapOf("system" to ""))).p2.system)
    }
    @Test fun wrongUidPackageCertificateSharedUidAndRotationRefused() {
        val p=Identity.Principal(123,listOf(Wire.CLIENT),123,listOf("cert"),listOf("cert"),false)
        assertEquals(123,Identity.verify(p))
        listOf(p.copy(uid=124),p.copy(packages=listOf("unknown")),p.copy(packages=listOf(Wire.CLIENT,"shared")),p.copy(certificates=listOf("wrong")),p.copy(rotated=true)).forEach { denied { Identity.verify(it) } }
    }
    @Test fun unknownLeaseWrongAuthorizationAndExpiredGrantRejected() {
        val query=request().keyFields + mapOf("request_digest" to "a".repeat(64),"client_uid" to "123")
        val response=query + mapOf("approved" to "true","expires_elapsed_ms" to "1000")
        assertEquals(1000L,AuthorityReceipts.verify(query,response,1,1000))
        listOf("lease_id","authorization_ref","client_uid","request_digest").forEach { field ->
            denied("UNVERIFIED_LEASE") { AuthorityReceipts.verify(query,response+mapOf(field to "wrong"),1,1000) }
        }
        denied("EXPIRED_LEASE") { AuthorityReceipts.verify(query,response,1000,1000) }
        denied("EXPIRED_LEASE") { AuthorityReceipts.verify(query,response,1,999) }
        denied("UNVERIFIED_LEASE") { AuthorityReceipts.verify(query,response+mapOf("approved" to "false"),1,1000) }
    }
    @Test fun pythonGoldenSerializationVectorMatchesKotlin() {
        val bytes=javaClass.getResourceAsStream("/p2-request.json")!!.use { it.readBytes() }
        val expected=javaClass.getResourceAsStream("/p2-request.sha256")!!.bufferedReader().use { it.readText().trim() }
        assertEquals(expected,Wire.request(bytes).digest)
    }
    private class Log : Journal {
        var dirty=false; var failBegin=false; var failComplete=false
        override fun unresolved()=dirty
        override fun begin(key: Map<String,String>): Boolean { dirty=true; return !failBegin }
        override fun complete(receipt: Map<String,String>): Boolean { if(failComplete)return false; dirty=false; return true }
    }
    private class Engine : RuntimePort {
        val inGeneration=CountDownLatch(1); val continueGeneration=CountDownLatch(1)
        @Volatile var closed=false
        @Volatile var sensorFailure=false
        var loads=0; var unloads=0; @Volatile var stops=0; var failUnload=false
        override suspend fun admit(request: Request)=RuntimeConfig(ModelKind.SMALL,512,8,1)
        override suspend fun load(request: Request,config: RuntimeConfig,nativeDispatched: () -> Unit) { loads++; nativeDispatched() }
        override suspend fun generate(request: Request,config: RuntimeConfig): FixtureGeneration { inGeneration.countDown(); check(continueGeneration.await(10,TimeUnit.SECONDS)); return FixtureGeneration("Bonjour",90,"b".repeat(64),"c".repeat(64),512) }
        override suspend fun unload() { unloads++; check(!failUnload) }
        override fun stop() { stops++ }
        override suspend fun close() { closed=true }
        override fun safe(request: Request): Boolean { check(!sensorFailure); return true }
    }
    private class Rig(val e: Engine=Engine(),val j: Log=Log(),authority: Authority=object:Authority { override suspend fun authorize(request: Request,uid: Int,deadline: Long)=deadline }) {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        var time=1L
        val controller=EndpointController(e,authority,j,{time},scope)
        suspend fun await(r: Request,phase: String): Map<String,String> = withTimeout(10000) {
            var s=controller.status(r.keyFields)
            while(s["phase"] != phase) { delay(10); s=controller.status(r.keyFields) }; s
        }
        fun cleanup() { e.continueGeneration.countDown(); scope.cancel() }
    }
    @Test fun arbiterAbsentNeverLoads() = runBlocking {
        val rig=Rig(authority=object:Authority { override suspend fun authorize(request: Request,uid: Int,deadline: Long): Long = throw Refusal("ARBITER_ABSENT") }); val r=request()
        try { rig.controller.submit(r,123); val s=rig.await(r,"REJECTED"); assertEquals("ARBITER_ABSENT",s["code"]); assertEquals(0,rig.e.loads); assertFalse(rig.j.dirty) } finally { rig.cleanup() }
    }
    @Test fun realCompletionPrecedesReleaseAndLateOldKeyCannotReleaseNewOperation() = runBlocking {
        val rig=Rig(); val r=request()
        try {
            rig.controller.submit(r,123); assertTrue(rig.e.inGeneration.await(5,TimeUnit.SECONDS))
            denied("BUSY") { rig.controller.submit(r,123) }; assertTrue(rig.j.dirty)
            assertEquals("false",rig.controller.status(r.keyFields)["release_confirmed"])
            rig.e.continueGeneration.countDown(); val s=rig.await(r,"UNLOADED")
            assertEquals("true",s["effective_end"]); assertEquals("true",s["model_unloaded"]); assertEquals("Bonjour",s["text"]); assertFalse(rig.j.dirty)
            denied("REPLAY") { rig.controller.submit(r,123) }
            denied("STALE_OPERATION") { rig.controller.stop(r.keyFields+mapOf("lease_id" to UUID.randomUUID().toString())) }
        } finally { rig.cleanup() }
    }
    @Test fun repeatedCancelAndClientDeathKeepReservationUntilDrainAndDiscardText() = runBlocking {
        val rig=Rig(); val r=request()
        try {
            rig.controller.submit(r,123); assertTrue(rig.e.inGeneration.await(5,TimeUnit.SECONDS))
            rig.controller.stop(r.keyFields); rig.controller.stop(r.keyFields); rig.controller.disconnect()
            assertTrue(rig.j.dirty); assertEquals(0,rig.e.unloads)
            assertEquals("false",rig.controller.status(r.keyFields)["effective_end"])
            rig.e.continueGeneration.countDown(); val s=rig.await(r,"UNLOADED"); assertEquals("",s["text"]); assertEquals("true",s["release_confirmed"])
        } finally { rig.cleanup() }
    }
    @Test fun cleanupFailureAndRestartJournalRemainClosed() = runBlocking {
        val rig=Rig(); val r=request(); rig.e.failUnload=true
        try {
            rig.e.continueGeneration.countDown(); rig.controller.submit(r,123)
            val s=rig.await(r,"CLEANUP_FAILED"); assertEquals("false",s["release_confirmed"]); assertTrue(rig.j.dirty)
            denied("UNKNOWN_STATE") { rig.controller.submit(r,123) }
            val restarted=Rig(j=rig.j); try { denied("UNKNOWN_STATE") { restarted.controller.submit(r,123) }; assertEquals(0,restarted.e.loads) } finally { restarted.cleanup() }
        } finally { rig.cleanup() }
    }
    @Test fun expiredLeaseAndFailedJournalCannotReachNative() = runBlocking {
        val r=request()
        for (authority in listOf(object:Authority { override suspend fun authorize(request: Request,uid: Int,deadline: Long)=0L })) {
            val rig=Rig(authority=authority); try { rig.controller.submit(r,123); rig.await(r,"REJECTED"); assertEquals(0,rig.e.loads) } finally { rig.cleanup() }
        }
        val rig=Rig(j=Log().apply { failBegin=true }); try { rig.controller.submit(r,123); rig.await(r,"UNKNOWN"); assertEquals(0,rig.e.loads); denied("UNKNOWN_STATE") { rig.controller.submit(r,123) } } finally { rig.cleanup() }
    }
    @Test fun deadlineDiscardsAlreadyComputedTextButPreservesReleaseEvidence() = runBlocking {
        val rig=Rig(); val r=request()
        try { rig.e.continueGeneration.countDown(); rig.controller.submit(r,123); rig.await(r,"UNLOADED"); rig.time=20000
            val s=rig.controller.status(r.keyFields); assertEquals("",s["text"]); assertEquals("EXPIRED",s["code"]); assertEquals("true",s["release_confirmed"])
        } finally { rig.cleanup() }
    }
    @Test fun failedJournalCompletionKeepsRecoveryClosedDespiteActualUnload() = runBlocking {
        val rig=Rig(j=Log().apply { failComplete=true }); val r=request()
        try { rig.e.continueGeneration.countDown(); rig.controller.submit(r,123)
            val receipt=rig.await(r,"UNKNOWN")
            assertEquals("true",receipt["model_unloaded"]); assertEquals("false",receipt["release_confirmed"])
            assertEquals("",receipt["text"]); assertTrue(rig.j.dirty)
            denied("UNKNOWN_STATE") { rig.controller.submit(r,123) }
        } finally { rig.cleanup() }
    }
    @Test fun oldReceiptCannotReferToSecondLease() = runBlocking {
        val rig=Rig(); val first=request()
        try { rig.e.continueGeneration.countDown(); rig.controller.submit(first,123); rig.await(first,"UNLOADED")
            val second=Wire.request(Wire.encode(fields("2")+mapOf("operation_id" to UUID.randomUUID().toString(),"lease_id" to UUID.randomUUID().toString())))
            rig.controller.submit(second,123); rig.await(second,"UNLOADED")
            denied("STALE_OPERATION") { rig.controller.status(first.keyFields) }
            denied("STALE_OPERATION") { rig.controller.stop(first.keyFields) }
            assertEquals(second.keyFields.getValue("lease_id"),rig.controller.status(second.keyFields)["lease_id"])
        } finally { rig.cleanup() }
    }
    @Test fun serviceShutdownDrainsBeforeClosingNativeWorker() = runBlocking {
        val rig=Rig(); val r=request()
        try { rig.controller.submit(r,123); assertTrue(rig.e.inGeneration.await(5,TimeUnit.SECONDS))
            rig.controller.shutdown(); assertFalse(rig.e.closed); assertTrue(rig.j.dirty)
            denied("CLOSED") { rig.controller.submit(r,123) }
            rig.e.continueGeneration.countDown()
            withTimeout(10000) { while(!rig.e.closed) delay(10) }
            assertEquals(1,rig.e.unloads); assertFalse(rig.j.dirty)
        } finally { rig.cleanup() }
    }

    @Test fun sensorExceptionStopsWithoutPretendingNativeEndOrRelease() = runBlocking {
        val rig=Rig(); val r=request()
        try { rig.controller.submit(r,123); assertTrue(rig.e.inGeneration.await(5,TimeUnit.SECONDS)); rig.e.sensorFailure=true
            withTimeout(10000) { while(rig.e.stops == 0) delay(10) }
            val pending=rig.controller.status(r.keyFields)
            assertEquals("false",pending["effective_end"]); assertEquals("false",pending["release_confirmed"]); assertTrue(rig.j.dirty)
            rig.e.continueGeneration.countDown(); assertEquals("",rig.await(r,"UNLOADED")["text"])
        } finally { rig.cleanup() }
    }

}
