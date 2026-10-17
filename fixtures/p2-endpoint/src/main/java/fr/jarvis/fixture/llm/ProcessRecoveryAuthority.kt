package fr.jarvis.fixture.llm

import fr.jarvis.fixture.p2.*
import java.util.UUID

interface RecoveryInventory { fun snapshot(): Map<String,String> }
data class RecoveryPins(val pilot: String,val participants: String,val coreUid: String,val llmUid: String,
    val coreCert: String,val llmCert: String,val digest: String)
/** Captured from verified Binder/PackageManager and a specific live binding, never from JSON. */
data class RecoveryChannel(val uid: String,val certificate: String,val bindingId: String)

/** Application-owned barrier. Does not acquire any native lease or constitute an Android global arbiter. */
class ProcessRecoveryAuthority(private val pins: RecoveryPins?,private val journal: RecoveryJournal,
    private val clock: ()->Long,private val inventory: RecoveryInventory,
    private val enrolledInThisProcess: Boolean=false) {
    val processEpoch=UUID.randomUUID().toString()
    private var serviceEpoch=""
    private val barrierId=UUID.randomUUID().toString()
    private var observation=0L
    private var state="RECOVERY_BLOCKED"
    private var discovery: Map<String,String>?=null
    private var binding: RecoveryChannel?=null
    private var inspected: Pair<Map<String,String>,Map<String,String>>?=null
    private val rounds=mutableMapOf<String,Pair<Map<String,String>,RecoveryChannel>>()
    private val revokedRounds=mutableSetOf<String>()
    private val retiredPeers=mutableSetOf<Pair<String,String>>()
    private var lastPeer: Pair<String,String>?=null
    private val preparedRounds=mutableMapOf<String,String>()
    private var historyValid=true
    init {
        try { if(journal.enrolled()) journal.history().filter { it.phase=="PREPARED" }.forEach { r ->
            r.payload.getValue("challenge_b64").let { encoded ->
                val q=RecoveryProtocol.envelope(RecoveryCommit.json(RecoveryCommit.unb64(encoded,18000)))
                preparedRounds[q.getValue("round_id")]=q.getValue("nonce")
            }
        } } catch(_: Throwable) { historyValid=false }
    }
    @Synchronized fun attachService(): String { revokedRounds.addAll(rounds.keys); serviceEpoch=UUID.randomUUID().toString(); invalidate(); return serviceEpoch }
    @Synchronized fun detachService(epoch: String) { if(serviceEpoch==epoch) { revokedRounds.addAll(rounds.keys); invalidate(); serviceEpoch="" } }
    @Synchronized fun invalidate() { state="RECOVERY_BLOCKED"; discovery=null; binding=null; inspected=null }
    @Synchronized fun requireNativeAdmission(): Unit = throw Refusal("RECOVERY_REQUIRED")
    @Synchronized fun inspectState()=state
    private fun check(f: Map<String,String>,service: String,channel: RecoveryChannel?) {
        demand(serviceEpoch==service && service.isNotEmpty(),"STALE_INCARNATION")
        val p=pins ?: throw Refusal("ENROLMENT_REQUIRED")
        demand(channel!=null && channel.uid==p.coreUid && channel.certificate==p.coreCert && channel.bindingId.isNotEmpty(),"UNAUTHENTICATED")
        demand(journal.enrolled(),if(journal.failure!=null) "JOURNAL_BLOCKED" else "JOURNAL_MISSING")
        demand(historyValid,"JOURNAL_BLOCKED")
        journal.verifyDurable()
        val marker=journal.marker()
        demand(marker["pilot_id"]==p.pilot && marker["pins_digest"]==p.digest && f["pilot_id"]==p.pilot,"ENROLMENT_CONFLICT")
        RecoveryProtocol.fresh(f,clock())
        if(f["kind"]!="DISCOVER") {
            val d=discovery ?: throw Refusal("RECOVERY_REQUIRED")
            demand(binding==channel,"STALE_INCARNATION")
            demand((d-"kind").all { f[it.key]==it.value },"STALE_INCARNATION")
            val identities=mapOf("participant_set_digest" to p.participants,"core_uid" to p.coreUid,"llm_uid" to p.llmUid,
                "core_cert_sha256" to p.coreCert,"llm_cert_sha256" to p.llmCert,"llm_process_epoch" to processEpoch,
                "llm_service_epoch" to serviceEpoch,"endpoint_epoch" to serviceEpoch)
            demand(identities.all { f[it.key]==it.value },"STALE_INCARNATION")
        }
    }
    private fun inventoryNow(): Map<String,String> {
        val snap=inventory.snapshot()
        demand(snap.keys==setOf("backend_registry_state","queue_state","native_state","residency_state","lease_state","backend_count","active_native_calls","queued_operations","active_imports","resident_models","heavy_leases"),"BACKEND_OBSERVATION_UNAVAILABLE")
        return snap
    }
    private fun quiescent(o: Map<String,String>)=o["backend_registry_state"]=="COMPLETE" && o["backend_count"] in setOf("0","1") && o["queue_state"]=="EMPTY" && o["native_state"] in setOf("NOT_STARTED","NATIVE_RETURNED") && o["residency_state"]=="NO_RESIDENCY_CONFIRMED" && o["lease_state"]=="NONE" && listOf("active_native_calls","queued_operations","active_imports","resident_models","heavy_leases").all { o[it]=="0" }
    private fun resolution(): String {
        val r=journal.snapshot()
        return when {
            r.payload["prior_key_state"]!="NONE" || r.payload["owner"]!="NONE" -> "UNKNOWN"
            r.phase=="INITIAL" && r.generation==0L -> if(enrolledInThisProcess) "INITIAL" else "CLEAN_RESTART"
            r.phase in setOf("PREPARED","ATTEST_OUTBOX","COMMITTED","ACK_OUTBOX","FINALISED") -> "CLEAN_RESTART"
            else -> "UNKNOWN"
        }
    }
    private fun observation(snap: Map<String,String>)=snap+mapOf("participant" to "llm","barrier_id" to barrierId,
        "observation_seq" to (++observation).toString(),"resolution_kind" to resolution(),"terminal_receipt_sha256" to "NONE")
    private fun ownJournal(): Map<String,String> {
        val r=journal.snapshot()
        return mapOf("llm_journal_id" to r.id,"llm_journal_seq" to r.sequence.toString(),"llm_journal_state" to r.phase,
            "llm_journal_sha256" to r.sha256,"generation" to r.generation.toString(),"owner" to (r.payload["owner"] ?: throw Refusal("UNKNOWN")))
    }
    private fun prior(f: Map<String,String>) { demand(f.filterKeys { it.startsWith("prior_") }==journal.snapshot().payload.filterKeys { it.startsWith("prior_") },"PRECONDITION_CHANGED") }
    private fun metadata(f: Map<String,String>)=f.filterKeys { it.startsWith("prior_") || it in setOf("owner","pilot_id","generation") }+mapOf("process_epoch" to processEpoch,"service_epoch" to serviceEpoch)
    private fun discover(f: Map<String,String>,channel: RecoveryChannel): Map<String,String> {
        val peer=f.getValue("core_process_epoch") to f.getValue("broker_epoch")
        demand(peer !in retiredPeers && f["round_id"] !in revokedRounds,"STALE_INCARNATION")
        if(lastPeer!=null && lastPeer!=peer) {
            demand(retiredPeers.size<128,"QUEUE_FULL")
            retiredPeers.add(lastPeer!!); revokedRounds.addAll(rounds.keys); invalidate(); lastPeer=peer
            throw Refusal("STALE_INCARNATION")
        }
        lastPeer=peer
        val old=rounds[f.getValue("round_id")]
        if(old!=null) {
            demand(old.second==channel && old.first["core_process_epoch"]==f["core_process_epoch"] && old.first["broker_epoch"]==f["broker_epoch"],"STALE_INCARNATION")
            demand(old.first==f,"REPLAY_CONFLICT")
        }
        else {
            demand(state!="QUIESCENT_PREPARED","BUSY")
            demand(f["round_id"] !in preparedRounds && preparedRounds.values.none { it==f["nonce"] } && rounds.values.none { it.first["nonce"]==f["nonce"] },"REPLAY_CONFLICT")
            demand(rounds.size<128 && preparedRounds.size<128,"QUEUE_FULL")
            rounds[f.getValue("round_id")]=f.toMap() to channel
        }
        if(discovery!=null && discovery!=f) { revokedRounds.add(discovery!!.getValue("round_id")); invalidate() }
        discovery=f.toMap(); binding=channel
        return f+mapOf("kind" to "NEGOTIATED","code" to "NONE","llm_process_epoch" to processEpoch,"llm_service_epoch" to serviceEpoch,"endpoint_epoch" to serviceEpoch)
    }
    private fun inspect(f: Map<String,String>): Map<String,String> {
        demand(state!="QUIESCENT_PREPARED","BUSY"); prior(f)
        inspected?.let { if(it.first==f && ownJournal().all { v -> it.second[v.key]==v.value }) return it.second }
        val snap=f+ownJournal()+observation(inventoryNow())+mapOf("kind" to "SNAPSHOT","code" to "NONE")
        RecoveryProtocol.encode(snap); inspected=f.toMap() to snap
        return snap
    }
    private fun prepare(f: Map<String,String>): Map<String,String> {
        demand(state!="QUIESCENT_PREPARED","BUSY")
        val preview=inspected ?: throw Refusal("RECOVERY_REQUIRED")
        demand((preview.first-"kind").all { f[it.key]==it.value } && ownJournal().all { f[it.key]==it.value } && ownJournal().all { preview.second[it.key]==it.value },"PRECONDITION_CHANGED")
        prior(f)
        val core=RecoveryCommit.proof(f.getValue("core_prepare_b64"),"CORE_PREPARE")
        demand(f["core_prepare_sha256"]==RecoveryCommit.hash("core_prepare",core),"INVALID_PROOF")
        demand(core.filterKeys { it in f.keys && it!="kind" }.all { f[it.key]==it.value },"PROOF_SET_MISMATCH")
        demand(quiescent(core) && core["backend_count"]=="0" && core["code"]=="NONE" && core["resolution_kind"]!="UNKNOWN","NOT_QUIESCENT")
        val inventory=inventoryNow()
        demand(inventory.all { preview.second[it.key]==it.value },"PRECONDITION_CHANGED")
        val obs=observation(inventory)
        demand(quiescent(obs) && obs["resolution_kind"]!="UNKNOWN","NOT_QUIESCENT")
        demand(f["round_id"] !in preparedRounds && preparedRounds.values.none { it==f["nonce"] },"REPLAY_CONFLICT")
        demand(preparedRounds.size<128,"QUEUE_FULL"); journal.requireCapacity(4)
        val previous=journal.snapshot()
        preparedRounds[f.getValue("round_id")]=f.getValue("nonce") // reserve before a durable interruption can escape append
        val prepared=journal.append("PREPARED",previous.generation,metadata(f)+mapOf("challenge_b64" to RecoveryCommit.b64(f,18000)))
        val attest=(f-"core_prepare_b64")+obs+mapOf("kind" to "ATTEST","prepared_journal_seq" to prepared.sequence.toString(),"prepared_journal_sha256" to prepared.sha256,"predecessor_journal_sha256" to previous.sha256)
        RecoveryProtocol.encode(attest)
        journal.append("ATTEST_OUTBOX",previous.generation,metadata(f)+mapOf("llm_attest_b64" to RecoveryCommit.b64(attest),"prepared_ref" to prepared.sha256))
        state="QUIESCENT_PREPARED"
        return attest
    }
    private fun ack(commit: Map<String,String>,record: RecoveryJournal.Record)=(commit-setOf("core_prepare_b64","llm_attest_b64"))+mapOf("kind" to "COMMIT_ACK","committed_journal_seq" to record.sequence.toString(),"committed_journal_sha256" to record.sha256,"llm_recovery_state" to "RECOVERY_COMMITTED")
    private fun commit(f: Map<String,String>): Map<String,String> {
        val last=journal.snapshot(); val history=journal.history()
        demand(last.phase in setOf("ATTEST_OUTBOX","COMMITTED","ACK_OUTBOX"),"RECOVERY_REQUIRED")
        demand(last.payload["process_epoch"]==processEpoch && last.payload["service_epoch"]==serviceEpoch,"STALE_INCARNATION")
        val outbox=history.lastOrNull { it.phase=="ATTEST_OUTBOX" } ?: throw Refusal("JOURNAL_BLOCKED")
        val prepared=history.singleOrNull { it.sha256==outbox.payload["prepared_ref"] } ?: throw Refusal("JOURNAL_BLOCKED")
        val challenge=RecoveryCommit.unb64(prepared.payload.getValue("challenge_b64"),18000)
        val core=RecoveryCommit.proof(challenge.getValue("core_prepare_b64"),"CORE_PREPARE")
        val local=RecoveryCommit.proof(outbox.payload.getValue("llm_attest_b64"),"ATTEST")
        val predecessor=history.singleOrNull { it.sequence==prepared.sequence-1 } ?: throw Refusal("JOURNAL_BLOCKED")
        demand(prepared.phase=="PREPARED" && outbox.sequence==prepared.sequence+1 &&
            local["prepared_journal_seq"]==prepared.sequence.toString() && local["prepared_journal_sha256"]==prepared.sha256 &&
            local["predecessor_journal_sha256"]==predecessor.sha256,"JOURNAL_BLOCKED")
        demand((challenge-setOf("kind","core_prepare_b64")).all { local[it.key]==it.value },"PROOF_SET_MISMATCH")
        demand((challenge-setOf("kind","core_prepare_b64")).all { f[it.key]==it.value },"PRECONDITION_CHANGED")
        RecoveryCommit.verify(f,core,local)
        val inventory=inventoryNow()
        demand(quiescent(inventory) && inventory.all { local[it.key]==it.value },"NOT_QUIESCENT")
        val committed=when(last.phase) {
            "ATTEST_OUTBOX" -> {
                demand(last.sha256==outbox.sha256 && last.sequence==prepared.sequence+1,"PRECONDITION_CHANGED")
                journal.requireCapacity(2)
                journal.append("COMMITTED",last.generation,metadata(f)+mapOf("commit_payload" to RecoveryCommit.json(f-setOf("core_prepare_b64","llm_attest_b64")).toString(Charsets.UTF_8),"attest_outbox_ref" to outbox.sha256))
            }
            "COMMITTED" -> last
            else -> history.singleOrNull { it.sha256==last.payload["committed_ref"] } ?: throw Refusal("JOURNAL_BLOCKED")
        }
        demand(committed.phase=="COMMITTED" && committed.sequence==outbox.sequence+1 && committed.payload["attest_outbox_ref"]==outbox.sha256,"PRECONDITION_CHANGED")
        val saved=RecoveryProtocol.technical(committed.payload.getValue("commit_payload").toByteArray(Charsets.UTF_8))
        demand(saved==f-setOf("core_prepare_b64","llm_attest_b64"),"REPLAY_CONFLICT")
        val expected=ack(f,committed)
        RecoveryProtocol.encode(expected)
        if(last.phase=="ACK_OUTBOX") {
            demand(last.sequence==committed.sequence+1,"PRECONDITION_CHANGED")
            val actual=RecoveryCommit.unb64(last.payload.getValue("ack_b64"))
            demand(actual==expected,"JOURNAL_BLOCKED")
        } else journal.append("ACK_OUTBOX",committed.generation,metadata(f)+mapOf("ack_b64" to RecoveryCommit.b64(expected),"committed_ref" to committed.sha256))
        state="RECOVERY_COMMITTED"
        return expected
    }
    @Synchronized fun handle(f: Map<String,String>,service: String,channel: RecoveryChannel?=null): Map<String,String> {
        try {
            RecoveryProtocol.envelope(RecoveryCommit.json(f))
            check(f,service,channel)
            val result=when(f["kind"]) {
                "DISCOVER" -> discover(f,channel!!)
                "INSPECT" -> inspect(f)
                "CHALLENGE" -> prepare(f)
                "COMMIT" -> commit(f)
                "CLEANUP_REQUEST" -> { prior(f); throw Refusal("CLEANUP_UNAVAILABLE") }
                else -> throw Refusal("INCOMPATIBLE")
            }
            RecoveryProtocol.fresh(f,clock()) // durable I/O must not turn an expired response into a fresh proof
            return result
        } catch(e: Refusal) {
            if(e.code=="STALE_INCARNATION") discovery?.get("round_id")?.let { revokedRounds.add(it) }
            if(e.code in setOf("EXPIRED","STALE_INCARNATION","JOURNAL_BLOCKED","PRECONDITION_CHANGED","UNAUTHENTICATED")) invalidate()
            return RecoveryProtocol.refused(f,e.code)
        } catch(_: Throwable) { invalidate(); return RecoveryProtocol.refused(f,"UNKNOWN") }
    }
}
