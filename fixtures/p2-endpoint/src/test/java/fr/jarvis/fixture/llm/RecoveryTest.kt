package fr.jarvis.fixture.llm

import fr.jarvis.fixture.p2.*
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Host files + Robolectric codec; injected peer epochs/inventory are NOT cross-APK/native proofs. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class RecoveryTest {
    @get:Rule val temp=TemporaryFolder()
    private val hash="a".repeat(64)
    private val pilot=uuid()
    private val peer=uuid() to uuid()
    private val nonce=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32))
    private var now=100L
    private val sync=object: DirectorySync { override fun sync(directory: File) { FileChannel.open(directory.toPath(),StandardOpenOption.READ).use { it.force(true) } } }
    private fun directory()=File(temp.newFolder(),"journal")
    private fun journal(d: File=directory())=RecoveryJournal(d,sync)
    private fun initialized(d: File=directory())=journal(d).also { it.initialize(pilot,hash,uuid()) }
    private val pins get()=RecoveryPins(pilot,hash,"123","124",hash,hash,hash)
    private val channel get()=RecoveryChannel("123",hash,"test-live-binding")
    private fun ProcessRecoveryAuthority.exchange(f: Map<String,String>,s: String)=handle(f,s,channel)
    private fun discover()=mapOf("version" to RecoveryProtocol.VERSION,"contract" to RecoveryProtocol.contract,"kind" to "DISCOVER","core_process_epoch" to peer.first,"broker_epoch" to peer.second,"issued_elapsed_ms" to "100","expires_elapsed_ms" to "5000","pilot_id" to pilot,"session_id" to uuid(),"round_id" to uuid(),"nonce" to nonce)
    private fun inventory(busy: Boolean=false,backends: String="0")=object: RecoveryInventory {
        override fun snapshot()=mapOf("backend_registry_state" to "COMPLETE","queue_state" to if(busy) "BUSY" else "EMPTY","native_state" to "NOT_STARTED","residency_state" to "NO_RESIDENCY_CONFIRMED","lease_state" to "NONE","backend_count" to backends,"active_native_calls" to "0","queued_operations" to "0","active_imports" to "0","resident_models" to "0","heavy_leases" to "0")
    }
    private fun authority(j: RecoveryJournal,busy: Boolean=false)=ProcessRecoveryAuthority(pins,j,{now},inventory(busy),true)
    private fun request(a: ProcessRecoveryAuthority,service: String,j: RecoveryJournal,kind: String="CHALLENGE"): Map<String,String> {
        val d=discover(); val n=a.exchange(d,service)
        val identity=n.filterKeys { it.endsWith("_epoch") }+mapOf("participant_set_digest" to hash,"core_uid" to "123","llm_uid" to "124","core_cert_sha256" to hash,"llm_cert_sha256" to hash)
        val common=d+identity+mapOf("kind" to kind,"recovery_id" to uuid(),"prior_key_state" to "NONE")
        if(kind in setOf("INSPECT","CLEANUP_REQUEST")) return common
        val inspection=a.exchange(common+mapOf("kind" to "INSPECT"),service)
        val r=j.snapshot()
        val coreJournal=mapOf("core_journal_id" to uuid(),"core_journal_seq" to "1","core_journal_state" to "INITIAL","core_journal_sha256" to hash)
        val local=mapOf("llm_journal_id" to r.id,"llm_journal_seq" to r.sequence.toString(),"llm_journal_state" to r.phase,"llm_journal_sha256" to r.sha256,"generation" to r.generation.toString(),"owner" to r.payload.getValue("owner"))
        val core=(inspection-setOf("llm_journal_id","llm_journal_seq","llm_journal_state","llm_journal_sha256"))+coreJournal+mapOf("kind" to "CORE_PREPARE","participant" to "core","backend_count" to "0","backend_registry_state" to "COMPLETE","queue_state" to "EMPTY","native_state" to "NOT_STARTED","lease_state" to "NONE","residency_state" to "NO_RESIDENCY_CONFIRMED","active_native_calls" to "0","queued_operations" to "0","active_imports" to "0","resident_models" to "0","heavy_leases" to "0","resolution_kind" to "INITIAL","prepared_journal_seq" to "2","prepared_journal_sha256" to "b".repeat(64),"predecessor_journal_sha256" to hash)
        return common+coreJournal+local+mapOf("core_prepare_b64" to RecoveryCommit.b64(core),"core_prepare_sha256" to RecoveryCommit.hash("core_prepare",core))
    }
    private fun commit(q: Map<String,String>,attest: Map<String,String>): Map<String,String> {
        val c=q.getValue("core_prepare_sha256"); val l=RecoveryCommit.hash("llm_attest",attest)
        val f=q+mapOf("kind" to "COMMIT","llm_attest_b64" to RecoveryCommit.b64(attest),"llm_attest_sha256" to l,"decision_id" to uuid(),"prepare_set_sha256" to RecoveryCommit.hash("prepare_set",mapOf("core_prepare_sha256" to c,"llm_attest_sha256" to l)))
        return f+mapOf("decision_sha256" to RecoveryCommit.hash("decision",f))
    }
    private fun denied(block: () -> Unit) { try { block(); fail("accepted") } catch(_: Refusal) {} }
    private fun assertCode(code: String,m: Map<String,String>) { assertEquals("REFUSED",m["kind"]); assertEquals(code,m["code"]); RecoveryProtocol.encode(m) }
    @Test fun b01ProductionDiscoveryRequiresAuthenticatedCoreEpochSource() {
        val j=initialized(); val a=authority(j); val service=a.attachService()
        assertCode("UNAUTHENTICATED",a.handle(discover(),service,null))
        val d=discover(); val negotiated=a.exchange(d,service); assertEquals("NEGOTIATED",negotiated["kind"]); assertEquals(peer.first,negotiated["core_process_epoch"])
        denied { a.requireNativeAdmission() }; assertEquals(1L,j.snapshot().sequence)
    }
    @Test fun closedCodecRejectsMixedPairsDuplicatesUnknownFieldsAndInvalidNonce() {
        val d=discover(); assertEquals(d,RecoveryProtocol.envelope(RecoveryProtocol.encode(d)))
        for(f in listOf(d+mapOf("version" to Wire.VERSION),d+mapOf("contract" to Wire.contract),d+mapOf("extra" to "x"),d+mapOf("nonce" to "short"))) denied { RecoveryProtocol.envelope(Wire.encode(f)) }
        denied { RecoveryProtocol.envelope((Wire.encode(d).toString(Charsets.UTF_8).dropLast(1)+",\"nonce\":\"duplicate\"}").toByteArray()) }
        denied { RecoveryProtocol.envelope(byteArrayOf(0xc0.toByte(),0x80.toByte())) }
        denied { RecoveryProtocol.envelope(ByteArray(24577)) }
    }
    @Test fun b02CleanRestartPreservesGenerationSeven() {
        val dir=directory(); val j=initialized(dir); j.append("COMMITTED",7,mapOf("owner" to "NONE","prior_key_state" to "NONE"))
        val reopened=journal(dir); assertNull(reopened.failure); assertEquals(7L,reopened.snapshot().generation)
        val a=authority(reopened); val s=a.attachService(); val inspect=request(a,s,reopened,"INSPECT")
        val snap=a.exchange(inspect,s); assertEquals("SNAPSHOT",snap["kind"]); assertTrue(snap.keys.none { it.startsWith("core_journal_") }); RecoveryProtocol.encode(snap); assertEquals(7L,reopened.snapshot().generation)
    }
    @Test fun b03UnfinishedOldOperationNeverAttests() {
        val j=initialized(); j.append("ADMITTED",1,mapOf("owner" to "llm","prior_key_state" to "NONE"))
        val a=authority(j); val s=a.attachService(); assertCode("NOT_QUIESCENT",a.exchange(request(a,s,j),s))
    }
    @Test fun b05CorruptionAndSidecarsArePreservedAndBlocked() {
        for(name in listOf("record-000001.json","journal.head","enrolment.marker","enrolment.intent","journal.head.new","write.pending","journal.head.bak")) {
            val dir=directory(); initialized(dir); val f=File(dir,name); f.writeText("broken")
            val bad=journal(dir); assertEquals("JOURNAL_BLOCKED",bad.failure); assertTrue(f.exists()); denied { bad.snapshot() }; denied { bad.initialize(pilot,hash,uuid()) }
        }
    }
    @Test fun b05DirectorySyncFailureBlocksWithoutErasingEvidence() {
        val dir=directory(); initialized(dir)
        val bad=RecoveryJournal(dir,object: DirectorySync { override fun sync(directory: File) { throw java.io.IOException("injected") } })
        denied { bad.append("UNKNOWN",0,mapOf("owner" to "NONE")) }; assertNotNull(bad.failure)
        assertTrue(File(dir,"record-000001.json").exists()); assertTrue(File(dir,"write.pending").exists()); assertEquals("JOURNAL_BLOCKED",journal(dir).failure)
    }
    @Test fun b06FreshnessAndEpochChangesInvalidatePrepared() {
        val j=initialized(); val a=authority(j); val s=a.attachService(); val q=request(a,s,j)
        val attest=a.exchange(q,s); assertEquals("ATTEST",attest["kind"]); RecoveryProtocol.encode(attest)
        now=5000; assertCode("EXPIRED",a.exchange(q,s)); assertEquals("RECOVERY_BLOCKED",a.inspectState())
        now=100; val next=a.attachService(); assertNotEquals(s,next); assertCode("STALE_INCARNATION",a.exchange(q,s))
    }
    @Test fun b09BusyInventoryNeverProducesPositiveProof() {
        val j=initialized(); val a=authority(j,busy=true); val s=a.attachService(); assertCode("NOT_QUIESCENT",a.exchange(request(a,s,j),s))
    }
    @Test fun b10ServiceRecreationPreservesProcessAndSingleAuthority() {
        val j=initialized(); val a=authority(j); val epoch=a.processEpoch; val first=a.attachService(); a.detachService(first)
        assertCode("STALE_INCARNATION",a.exchange(discover(),first))
        val second=a.attachService(); assertNotEquals(first,second); assertEquals(epoch,a.processEpoch)
        assertNotEquals(epoch,authority(j).processEpoch)
    }
    @Test fun b11ExactCommitRetryReturnsDurableAckAndNeverGrantsLoad() {
        val j=initialized(); val a=authority(j); val s=a.attachService(); val q=request(a,s,j); val attest=a.exchange(q,s)
        val commit=commit(q,attest)
        val ack=a.exchange(commit,s); assertEquals("COMMIT_ACK",ack["kind"]); RecoveryProtocol.encode(ack)
        assertEquals(ack,a.exchange(commit,s)); assertEquals(5L,j.snapshot().sequence); denied { a.requireNativeAdmission() }
        assertCode("DECISION_MISMATCH",a.exchange(commit+mapOf("decision_id" to uuid()),s))
    }
    @Test fun b12CleanupUnavailableDoesNotCallWorkerUnderGate() {
        val j=initialized(); val a=authority(j); val s=a.attachService(); val q=request(a,s,j,"CLEANUP_REQUEST")+mapOf("cleanup_command_id" to uuid())
        assertCode("CLEANUP_UNAVAILABLE",a.exchange(q,s)); assertEquals(1L,j.snapshot().sequence)
    }
    @Test fun b14PreparedGateRejectsAnotherChallengeAndAllNativeAdmission() {
        val j=initialized(); val a=authority(j); val s=a.attachService(); val q=request(a,s,j)
        assertEquals("ATTEST",a.exchange(q,s)["kind"]); assertCode("BUSY",a.exchange(q,s)); denied { a.requireNativeAdmission() }
    }
    @Test fun b16MissingOrRolledBackHeadNeverInitializesHealthy() {
        val dir=directory(); val j=initialized(dir); val old=File(dir,"journal.head").readBytes()
        j.append("COMMITTED",7,mapOf("owner" to "NONE","prior_key_state" to "NONE")); File(dir,"journal.head").writeBytes(old)
        assertEquals("JOURNAL_BLOCKED",journal(dir).failure)
        File(dir,"enrolment.marker").delete(); assertEquals("JOURNAL_BLOCKED",journal(dir).failure)
    }
    @Test fun b17BirthRegistryCannotInferNeverStartedAfterNativeAttempt() {
        val registry=BootstrapRegistry(); val owner=Any(); assertFalse(registry.provesNeverStarted(owner))
        registry.register(owner); registry.seal(); assertTrue(registry.provesNeverStarted(owner))
        registry.beforeNativeOrImport(owner); assertFalse(registry.provesNeverStarted(owner))
        try { registry.register(Any()); fail() } catch(_: IllegalStateException) {}
    }
    @Test fun numericAndWindowLimitsAreCanonical() {
        for(value in listOf("01","-1","9007199254740992","UNKNOWN")) denied { RecoveryProtocol.number(value) }
        assertEquals(9007199254740991L,RecoveryProtocol.number("9007199254740991"))
        denied { RecoveryProtocol.fresh(mapOf("issued_elapsed_ms" to "100","expires_elapsed_ms" to "5101"),100) }
    }
    @Test fun journalRejectsPrivatePayloadAndBackwardsGeneration() {
        val j=initialized(); denied { j.append("ADMITTED",0,mapOf("question" to "secret")) }
        j.append("COMMITTED",7,mapOf("owner" to "NONE","prior_key_state" to "NONE"))
        denied { j.append("COMMITTED",6,mapOf("owner" to "NONE")) }; assertEquals(7L,j.snapshot().generation)
    }
    @Test fun b08TerminalHashAloneIsNotNativeEndEvidence() {
        val j=initialized(); j.append("TERMINAL",1,mapOf("owner" to "NONE","prior_key_state" to "NONE","terminal_receipt_sha256" to hash))
        val a=authority(j); val service=a.attachService()
        assertCode("NOT_QUIESCENT",a.exchange(request(a,service,j),service))
    }
    @Test fun b15RestartCannotAcceptAnOldPreparedIncarnation() {
        val j=initialized(); val first=authority(j); val old=first.attachService(); val q=request(first,old,j)
        assertEquals("ATTEST",first.exchange(q,old)["kind"])
        val next=authority(j); val current=next.attachService()
        assertEquals("NEGOTIATED",next.exchange(discover()+mapOf("nonce" to java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { -1 })),current)["kind"])
        assertCode("STALE_INCARNATION",next.exchange(q,current)); assertEquals("ATTEST_OUTBOX",j.snapshot().phase)
    }
    @Test fun b18ReplayBudgetDoesNotEvictOlderPreparedRounds() {
        val j=initialized()
        val seed=authority(j); val seedService=seed.attachService(); val q=request(seed,seedService,j)
        repeat(128) { n ->
            val unique=q+mapOf("round_id" to uuid(),"nonce" to java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { n.toByte() }))
            j.append("PREPARED",0,mapOf("owner" to "NONE","prior_key_state" to "NONE","challenge_b64" to RecoveryCommit.b64(unique,18000)))
        }
        val a=authority(j); val service=a.attachService()
        assertCode("QUEUE_FULL",a.exchange(discover()+mapOf("nonce" to java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { -1 })),service)); assertEquals(129L,j.snapshot().sequence)
    }
    @Test fun b13CrossLanguageGoldenVectorMatchesActualCodecAndBundle() {
        val bytes=javaClass.getResourceAsStream("/discover-003f.json")!!.readBytes()
        val expected=javaClass.getResourceAsStream("/discover-003f.sha256")!!.bufferedReader().readText().trim()
        val message=RecoveryProtocol.envelope(bytes)
        assertEquals(RecoveryProtocol.contract,message["contract"]); assertEquals(expected,Wire.digest(message))
        assertEquals(message,RecoveryProtocol.envelope(RecoveryProtocol.encode(message)))
    }
    @Test fun b11EveryDirectorySyncBoundaryFailureRemainsBlocked() {
        for(boundary in 1..4) {
            val dir=directory(); initialized(dir); var count=0
            val failing=RecoveryJournal(dir,object: DirectorySync { override fun sync(directory: File) {
                count++; if(count==boundary) throw java.io.IOException("injected boundary") else sync.sync(directory)
            } })
            denied { failing.append("COMMITTED",0,mapOf("owner" to "NONE","prior_key_state" to "NONE")) }
            assertEquals("JOURNAL_BLOCKED",failing.failure); assertTrue(File(dir,"record-000001.json").exists())
            assertEquals("JOURNAL_BLOCKED",journal(dir).failure)
        }
    }
    @Test fun b09UnknownOrMultipleBackendsCannotAttestQuiescence() {
        for(count in listOf("UNKNOWN","2")) {
            val j=initialized(); val a=ProcessRecoveryAuthority(pins,j,{now},inventory(backends=count),true)
            val service=a.attachService(); assertCode("NOT_QUIESCENT",a.exchange(request(a,service,j),service))
            assertEquals("INITIAL",j.snapshot().phase)
        }
    }
    @Test fun b03ContradictoryOwnerCannotBecomeCleanRestart() {
        val j=initialized(); j.append("COMMITTED",7,mapOf("owner" to "llm","prior_key_state" to "NONE"))
        val a=authority(j); val service=a.attachService(); assertCode("NOT_QUIESCENT",a.exchange(request(a,service,j),service))
    }
    companion object { private fun uuid()=UUID.randomUUID().toString() }
}
