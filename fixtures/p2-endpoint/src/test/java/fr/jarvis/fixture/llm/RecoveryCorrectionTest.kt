package fr.jarvis.fixture.llm

import fr.jarvis.fixture.p2.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.UUID

/** Pure software inventory + authenticated-channel fixture injection, NOT Android Binder/native evidence. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=android.app.Application::class)
class RecoveryCorrectionTest {
    @get:Rule val temp=TemporaryFolder()
    private val hash="a".repeat(64)
    private fun uuid()=UUID.randomUUID().toString()
    private val sync=object: DirectorySync { override fun sync(directory: File) { FileChannel.open(directory.toPath(),StandardOpenOption.READ).use { it.force(true) } } }
    private fun map(o: JSONObject)=o.keys().asSequence().associateWith { o.getString(it) }
    private fun resource(name: String)=JSONObject(javaClass.getResourceAsStream(name)!!.bufferedReader().readText())
    private fun denied(block: ()->Unit): String { try { block(); fail("accepted") } catch(e: Refusal) { return e.code }; error("unreachable") }
    private fun refused(m: Map<String,String>,vararg codes: String) { assertEquals("REFUSED",m["kind"]); if(codes.isNotEmpty()) assertTrue(m.toString(),m["code"] in codes) }
    private fun inventory()=mapOf("backend_registry_state" to "COMPLETE","queue_state" to "EMPTY","native_state" to "NOT_STARTED","residency_state" to "NO_RESIDENCY_CONFIRMED","lease_state" to "NONE","backend_count" to "0","active_native_calls" to "0","queued_operations" to "0","active_imports" to "0","resident_models" to "0","heavy_leases" to "0")
    private inner class Lab(val checkpoint: (String)->Unit={}) {
        val directory=File(temp.newFolder(),"journal")
        val pilot=uuid(); val channel=RecoveryChannel("123",hash,uuid())
        val journal=RecoveryJournal(directory,sync,checkpoint).also { it.initialize(pilot,hash,uuid()) }
        var now=100L
        var observed=inventory()
        val authority=ProcessRecoveryAuthority(RecoveryPins(pilot,hash,"123","124",hash,hash,hash),journal,{now},object: RecoveryInventory { override fun snapshot()=observed },true)
        val service=authority.attachService()
        val discover=mapOf("version" to RecoveryProtocol.VERSION,"contract" to RecoveryProtocol.contract,"kind" to "DISCOVER","pilot_id" to pilot,"session_id" to uuid(),"round_id" to uuid(),"core_process_epoch" to uuid(),"broker_epoch" to uuid(),"nonce" to Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }),"issued_elapsed_ms" to "100","expires_elapsed_ms" to "5100")
        fun send(f: Map<String,String>,c: RecoveryChannel=channel)=authority.handle(f,service,c)
        fun negotiate(): Map<String,String> { val n=send(discover); assertEquals("NEGOTIATED",n["kind"]); return n }
        fun inspection(): Map<String,String> {
            val n=negotiate()
            return discover+n.filterKeys { it.endsWith("_epoch") }+mapOf("kind" to "INSPECT","recovery_id" to uuid(),"participant_set_digest" to hash,"core_uid" to "123","llm_uid" to "124","core_cert_sha256" to hash,"llm_cert_sha256" to hash,"prior_key_state" to "NONE")
        }
        fun challenge(): Map<String,String> {
            val i=inspection(); val snap=send(i); assertEquals("SNAPSHOT",snap["kind"])
            val j=mapOf("core_journal_id" to uuid(),"core_journal_seq" to "1","core_journal_state" to "INITIAL","core_journal_sha256" to hash)
            val core=(snap-setOf("llm_journal_id","llm_journal_seq","llm_journal_state","llm_journal_sha256"))+j+inventory()+mapOf("kind" to "CORE_PREPARE","participant" to "core","prepared_journal_seq" to "2","prepared_journal_sha256" to "b".repeat(64),"predecessor_journal_sha256" to hash,"code" to "NONE")
            return i+j+snap.filterKeys { it.startsWith("llm_journal_") || it in setOf("generation","owner") }+mapOf("kind" to "CHALLENGE","core_prepare_b64" to RecoveryCommit.b64(core),"core_prepare_sha256" to RecoveryCommit.hash("core_prepare",core))
        }
        fun commitment(q: Map<String,String>,a: Map<String,String>): Map<String,String> {
            val c=q.getValue("core_prepare_sha256");val l=RecoveryCommit.hash("llm_attest",a)
            val f=q+mapOf("kind" to "COMMIT","llm_attest_b64" to RecoveryCommit.b64(a),"llm_attest_sha256" to l,"decision_id" to uuid(),"prepare_set_sha256" to RecoveryCommit.hash("prepare_set",mapOf("core_prepare_sha256" to c,"llm_attest_sha256" to l)))
            return f+mapOf("decision_sha256" to RecoveryCommit.hash("decision",f))
        }
    }
    private fun checkVectors(name: String) {
        val v=resource(name);val c=map(v.getJSONObject("core_prepare"));val a=map(v.getJSONObject("llm_attest"));val m=map(v.getJSONObject("commit"));val expected=v.getJSONObject("expected")
        RecoveryCommit.verify(m,c,a,m.getValue("contract"))
        for((domain,key,fields) in listOf(Triple("core_prepare","core_prepare_sha256",c),Triple("llm_attest","llm_attest_sha256",a),Triple("prepare_set","prepare_set_sha256",mapOf("core_prepare_sha256" to m.getValue("core_prepare_sha256"),"llm_attest_sha256" to m.getValue("llm_attest_sha256"))),Triple("decision","decision_sha256",m-"decision_sha256"))) assertEquals(expected.getString(key),RecoveryCommit.hash(domain,fields))
        val primitives=v.getJSONArray("primitive_vectors")
        for(n in 0 until primitives.length()) { val p=primitives.getJSONObject(n);val b=RecoveryCommit.canonical(map(p.getJSONObject("fields")));assertEquals(p.getString("canonical_hex"),b.joinToString("") { "%02x".format(it.toInt() and 255) });assertEquals(p.getString("sha256"),Wire.hash(b)) }
        val messages=v.getJSONArray("message_vectors")
        for(n in 0 until messages.length()) { val item=messages.getJSONObject(n);val fields=map(item.getJSONObject("fields"));assertEquals(fields,RecoveryProtocol.envelope(RecoveryCommit.json(fields),m.getValue("contract")));assertEquals(item.getString("sha256"),RecoveryCommit.hash("message",fields));assertEquals(item.getInt("canonical_bytes"),RecoveryCommit.canonical(fields).size) }
    }
    @Test fun approvedSentinelVectorsMatchKotlinExactly()=checkVectors("/d4e-reference.json")
    @Test fun realBundleVectorsMatchKotlinAndPythonExactly()=checkVectors("/d4e-actual.json")
    @Test fun sentinelContractCannotEnterProductionAuthority() { val l=Lab();refused(l.send(l.discover+mapOf("contract" to "0".repeat(64))),"INCOMPATIBLE") }
    @Test fun discoverBindsChannelAndEchoesCoreEpochsWithoutNativeAdmission() {
        val l=Lab();val n=l.negotiate();assertTrue((l.discover-"kind").all { n[it.key]==it.value });assertEquals(n,l.negotiate());assertEquals(1L,l.journal.snapshot().sequence);assertEquals("RECOVERY_REQUIRED",denied { l.authority.requireNativeAdmission() })
    }
    @Test fun everyDiscoverTupleMutationAndBindingChangeIsRefused() {
        for(key in listOf("core_process_epoch","broker_epoch","session_id","nonce","issued_elapsed_ms","expires_elapsed_ms")) {
            val l=Lab();l.negotiate();l.now=200;val replacement=when(key) { "nonce"->Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 8 });"issued_elapsed_ms"->"101";"expires_elapsed_ms"->"5000";else->uuid() }
            refused(l.send(l.discover+mapOf(key to replacement)),"REPLAY_CONFLICT","STALE_INCARNATION")
        }
        val l=Lab();l.negotiate();refused(l.send(l.discover,l.channel.copy(bindingId=uuid())),"STALE_INCARNATION")
    }
    @Test fun unknownUidAndCertificateAreNotTrustedFromJson() {
        val l=Lab();refused(l.send(l.discover,l.channel.copy(uid="999")),"UNAUTHENTICATED");refused(l.send(l.discover,l.channel.copy(certificate="f".repeat(64))),"UNAUTHENTICATED");assertEquals(1L,l.journal.snapshot().sequence)
    }
    @Test fun expireOrDeadServiceDoesNotReturnNegotiationOrOldAck() {
        val l=Lab();l.negotiate();l.now=5100;refused(l.send(l.discover),"EXPIRED")
        l.now=100;l.authority.detachService(l.service);refused(l.send(l.discover),"STALE_INCARNATION")
    }
    @Test fun snapshotHasOnlyLlmJournalAndForeignOwnershipIsRejected() {
        val l=Lab();val i=l.inspection();val s=l.send(i);assertEquals("SNAPSHOT",s["kind"]);assertFalse(s.keys.any { it.startsWith("core_journal_") });assertEquals(s,l.send(i));assertEquals("INVALID_FIELD",denied { RecoveryProtocol.encode(s+mapOf("core_journal_id" to uuid())) })
    }
    @Test fun corePrepareIsNotAnRpcAndRolesAreEnforced() {
        val l=Lab();val q=l.challenge();val c=RecoveryCommit.unb64(q.getValue("core_prepare_b64"));refused(l.send(c),"INCOMPATIBLE")
        assertEquals("INVALID_PROOF",denied { RecoveryCommit.proof(RecoveryCommit.b64(c+mapOf("participant" to "llm")),"CORE_PREPARE") })
    }
    @Test fun snapshotHeadOrInventoryMutationPreventsPreparedWrite() {
        for(changeInventory in listOf(true,false)) {
            val l=Lab();val q=l.challenge()
            if(changeInventory) l.observed=l.observed+mapOf("queued_operations" to "1") else l.journal.append("UNKNOWN",0,mapOf("owner" to "NONE","prior_key_state" to "NONE"))
            val seq=l.journal.snapshot().sequence;refused(l.send(q),"PRECONDITION_CHANGED");assertEquals(seq,l.journal.snapshot().sequence)
        }
    }
    @Test fun coreProofMismatchRejectsBeforeAnyPreparedRecord() {
        val l=Lab();val q=l.challenge();val c=RecoveryCommit.unb64(q.getValue("core_prepare_b64"))+mapOf("generation" to "1")
        refused(l.send(q+mapOf("core_prepare_b64" to RecoveryCommit.b64(c),"core_prepare_sha256" to RecoveryCommit.hash("core_prepare",c))),"PROOF_SET_MISMATCH");assertEquals(1L,l.journal.snapshot().sequence)
    }
    @Test fun proofBoundsMalformedBase64DuplicateJsonAndNoncanonicalEncodingAreRejected() {
        val v=resource("/d4e-actual.json");val c=map(v.getJSONObject("core_prepare"));val canonical=RecoveryCommit.json(c)
        for(raw in listOf(" "+canonical.toString(Charsets.UTF_8),canonical.toString(Charsets.UTF_8).dropLast(1)+",\"kind\":\"CORE_PREPARE\"}","{\"kind\":2}")) denied { RecoveryCommit.proof(Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray()),"CORE_PREPARE") }
        for(s in listOf("!","A=", "A".repeat(8193))) denied { RecoveryCommit.proof(s,"CORE_PREPARE") }
        assertEquals("INPUT_TOO_LARGE",denied { RecoveryCommit.b64(mapOf("owner" to "x".repeat(6144))) })
        assertTrue(RecoveryCommit.json(mapOf("owner" to "é\u2028")).toString(Charsets.UTF_8).contains("é\u2028"))
        denied { RecoveryCommit.json(mapOf("owner" to "bad\n")) }
    }
    @Test fun fullCommitHasExactMembersAndAckReferencesCommittedNotOutbox() {
        val l=Lab();val q=l.challenge();val a=l.send(q);assertEquals("ATTEST",a["kind"]);assertEquals("ATTEST_OUTBOX",l.journal.snapshot().phase)
        val m=l.commitment(q,a);val ack=l.send(m);assertEquals("COMMIT_ACK",ack["kind"]);assertEquals("4",ack["committed_journal_seq"]);assertEquals("ACK_OUTBOX",l.journal.snapshot().phase);assertEquals(5L,l.journal.snapshot().sequence)
        assertNotEquals(l.journal.snapshot().sha256,ack["committed_journal_sha256"]);assertEquals(ack,l.send(m));assertEquals(5L,l.journal.snapshot().sequence);denied { l.authority.requireNativeAdmission() }
    }
    @Test fun substitutedProofWithAllHashesRecomputedStillFailsMembership() {
        val l=Lab();val q=l.challenge();val a=l.send(q);val forged=a+mapOf("observation_seq" to "999")
        refused(l.send(l.commitment(q,forged)),"PROOF_SET_MISMATCH");assertEquals("ATTEST_OUTBOX",l.journal.snapshot().phase)
    }
    @Test fun swappedOrMissingMemberAndArbitrarySetNeverCommit() {
        val l=Lab();val q=l.challenge();val a=l.send(q);val m=l.commitment(q,a)
        refused(l.send(m+mapOf("core_prepare_b64" to m.getValue("llm_attest_b64"))),"INVALID_PROOF")
        refused(l.send(m-"llm_attest_b64"),"INVALID_FIELD")
        refused(l.send(m+mapOf("prepare_set_sha256" to "f".repeat(64))),"PROOF_SET_MISMATCH")
        assertEquals("ATTEST_OUTBOX",l.journal.snapshot().phase)
    }
    @Test fun outboxCorruptionCannotBeReconstructedFromClientProof() {
        val l=Lab();val q=l.challenge();val a=l.send(q);File(l.directory,"record-000003.json").writeText("broken")
        refused(l.send(l.commitment(q,a)),"JOURNAL_BLOCKED");assertTrue(File(l.directory,"record-000003.json").readText()=="broken")
    }
    @Test fun interruptionAfterCommittedCanPersistTheSameAckBeforeReply() {
        var armed=false
        val l=Lab { point -> if(armed && point=="durable:COMMITTED") { armed=false; throw IllegalStateException("lost thread after durable commit") } }
        val q=l.challenge();val a=l.send(q);val m=l.commitment(q,a);armed=true
        refused(l.send(m),"UNKNOWN");assertEquals("COMMITTED",l.journal.snapshot().phase);l.negotiate()
        val ack=l.send(m);assertEquals("COMMIT_ACK",ack["kind"]);assertEquals("4",ack["committed_journal_seq"]);assertEquals(5L,l.journal.snapshot().sequence);assertEquals(ack,l.send(m))
    }
    @Test fun lostAckAndExpiredAckDoNotCauseAnotherResolutionOrGrant() {
        val l=Lab();val q=l.challenge();val a=l.send(q);val m=l.commitment(q,a);val ack=l.send(m)
        l.authority.invalidate();l.negotiate();assertEquals(ack,l.send(m));assertEquals(5L,l.journal.snapshot().sequence)
        l.now=5100;refused(l.send(m),"EXPIRED");assertEquals(5L,l.journal.snapshot().sequence);denied { l.authority.requireNativeAdmission() }
    }
    @Test fun everyFsyncAndPublicationBoundaryFailsClosedWithoutNativeRelease() {
        for(boundary in 1..44) {
            var armed=false;var n=0
            val l=Lab { if(armed && ++n==boundary) throw java.io.IOException("injected boundary $boundary") }
            val q=l.challenge();armed=true;val a=l.send(q)
            val result=if(a["kind"]=="ATTEST") l.send(l.commitment(q,a)) else a
            refused(result);assertTrue(File(l.directory,"record-000001.json").exists());denied { l.authority.requireNativeAdmission() }
            val disk=RecoveryJournal(l.directory,sync)
            if(boundary%11==0) assertNull(disk.failure) else assertEquals("JOURNAL_BLOCKED",disk.failure)
        }
    }
    @Test fun fourRecordsMustFitBeforeAnyPrepareWrite() {
        val l=Lab()
        repeat(508) { l.journal.append("COMMITTED",0,mapOf("owner" to "NONE","prior_key_state" to "NONE")) }
        val q=l.challenge();val seq=l.journal.snapshot().sequence
        refused(l.send(q),"QUEUE_FULL");assertEquals(seq,l.journal.snapshot().sequence)
    }
    @Test fun recordReferencesUseCanonicalLabelNotFileHashOrSelfReference() {
        val l=Lab();val file=File(l.directory,"record-000001.json");val fields=map(JSONObject(file.readText()))
        assertEquals(setOf("journal_id","seq","prev_record_sha256","phase","payload_b64"),fields.keys)
        assertEquals(l.journal.snapshot().sha256,RecoveryCommit.hash("record",fields));assertNotEquals(l.journal.snapshot().sha256,Wire.hash(file.readBytes()))
        assertEquals("NONE",fields["prev_record_sha256"]);assertEquals(l.journal.snapshot().sha256,RecoveryJournal(l.directory,sync).snapshot().sha256)
    }
    @Test fun expiryDuringDurableAckWriteCannotEmitPositiveAck() {
        var afterAck=false
        lateinit var l: Lab
        l=Lab { point -> if(afterAck && point=="durable:ACK_OUTBOX") l.now=5100 }
        val q=l.challenge();val a=l.send(q);afterAck=true
        refused(l.send(l.commitment(q,a)),"EXPIRED");assertEquals("ACK_OUTBOX",l.journal.snapshot().phase);denied { l.authority.requireNativeAdmission() }
    }
    @Test fun anotherFullyHashedDecisionCannotResolveSamePreparedRoundTwice() {
        val l=Lab();val q=l.challenge();val a=l.send(q);val first=l.commitment(q,a)
        assertEquals("COMMIT_ACK",l.send(first)["kind"])
        val changed=(first-"decision_sha256")+mapOf("decision_id" to uuid())
        refused(l.send(changed+mapOf("decision_sha256" to RecoveryCommit.hash("decision",changed))),"REPLAY_CONFLICT")
        assertEquals(5L,l.journal.snapshot().sequence)
    }
    @Test fun aRetiredBrokerOrSupersededRoundCannotBeRevived() {
        val l=Lab();l.negotiate()
        val newer=l.discover+mapOf("broker_epoch" to uuid(),"round_id" to uuid(),"nonce" to Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 12 }))
        refused(l.send(newer),"STALE_INCARNATION")
        assertEquals("NEGOTIATED",l.send(newer)["kind"])
        refused(l.send(l.discover),"STALE_INCARNATION")
        refused(l.send(l.discover+mapOf("round_id" to uuid())),"STALE_INCARNATION")
        assertEquals(1L,l.journal.snapshot().sequence);denied { l.authority.requireNativeAdmission() }
    }
    @Test fun commitChecksLlmOwnedJournalFieldsEvenWithoutTheirPresenceInCoreProof() {
        val v=resource("/d4e-actual.json");val c=map(v.getJSONObject("core_prepare"));val a=map(v.getJSONObject("llm_attest"));val m=map(v.getJSONObject("commit"))
        val forged=a+mapOf("llm_journal_sha256" to "e".repeat(64));val l=RecoveryCommit.hash("llm_attest",forged)
        val changed=(m-"decision_sha256")+mapOf("llm_attest_b64" to RecoveryCommit.b64(forged),"llm_attest_sha256" to l,"prepare_set_sha256" to RecoveryCommit.hash("prepare_set",mapOf("core_prepare_sha256" to m.getValue("core_prepare_sha256"),"llm_attest_sha256" to l)))
        assertEquals("PROOF_SET_MISMATCH",denied { RecoveryCommit.verify(changed+mapOf("decision_sha256" to RecoveryCommit.hash("decision",changed)),c,forged) })
    }
}
