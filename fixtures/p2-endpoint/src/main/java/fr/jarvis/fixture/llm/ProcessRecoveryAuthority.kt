package fr.jarvis.fixture.llm

import fr.jarvis.fixture.p2.*
import java.util.UUID

/** Instrumented process registry, not another owner of ProcessResources. */
interface RecoveryInventory {
    fun snapshot(): Map<String,String>
}
data class RecoveryPins(val pilot: String,val participants: String,val coreUid: String,val llmUid: String,
    val coreCert: String,val llmCert: String,val digest: String)

/** One instance per Application, never per Service. All transitions/snapshots share this gate. */
class ProcessRecoveryAuthority(private val pins: RecoveryPins?,private val journal: RecoveryJournal,
    private val clock: () -> Long,private val inventory: RecoveryInventory,
    private val trustedPeerEpochs: () -> Pair<String,String>? = { null },
    private val enrolledInThisProcess: Boolean = false) {
    val processEpoch=UUID.randomUUID().toString()
    private var serviceEpoch=""
    private val barrierId=UUID.randomUUID().toString()
    private var observation=0L
    private var state="RECOVERY_BLOCKED"
    private var prepared: Map<String,String>?=null
    private var challenge: Map<String,String>?=null
    private val rounds=mutableMapOf<String,String>()
    init {
        if(journal.enrolled()) journal.history().filter { it.phase=="PREPARED" }.forEach { r ->
            val round=r.payload["round_id"]; val nonce=r.payload["nonce"]
            if(round!=null && nonce!=null) rounds[round]=nonce
        }
    }
    @Synchronized fun attachService(): String { serviceEpoch=UUID.randomUUID().toString(); invalidate(); return serviceEpoch }
    @Synchronized fun detachService(epoch: String) { if(serviceEpoch==epoch) { invalidate(); serviceEpoch="" } }
    @Synchronized fun invalidate() { state="RECOVERY_BLOCKED"; prepared=null; challenge=null }
    /** Kept closed even after COMMIT: a NEW genuine /2 operation mandate is still required. */
    @Synchronized fun requireNativeAdmission(): Unit = throw Refusal("RECOVERY_REQUIRED")
    @Synchronized fun inspectState()=state
    private fun check(f: Map<String,String>,service: String) {
        demand(serviceEpoch==service && service.isNotEmpty(),"STALE_INCARNATION")
        val p=pins ?: throw Refusal("ENROLMENT_REQUIRED")
        demand(journal.enrolled(),if(journal.failure!=null) "JOURNAL_BLOCKED" else "JOURNAL_MISSING")
        val marker=journal.marker()
        demand(marker["pilot_id"]==p.pilot && marker["pins_digest"]==p.digest && f["pilot_id"]==p.pilot,"ENROLMENT_CONFLICT")
        if(f["kind"]!="DISCOVER") {
            RecoveryProtocol.fresh(f,clock())
            val peer=trustedPeerEpochs() ?: throw Refusal("PEER_INCARNATION_REQUIRED")
            val expected=mapOf("participant_set_digest" to p.participants,"core_uid" to p.coreUid,"llm_uid" to p.llmUid,"core_cert_sha256" to p.coreCert,"llm_cert_sha256" to p.llmCert,
                "core_process_epoch" to peer.first,"broker_epoch" to peer.second,"llm_process_epoch" to processEpoch,"llm_service_epoch" to serviceEpoch,"endpoint_epoch" to serviceEpoch)
            demand(expected.all { f[it.key]==it.value },"STALE_INCARNATION")
        }
    }
    private fun observation(): Map<String,String> {
        val snap=inventory.snapshot()
        val names=setOf("backend_registry_state","queue_state","native_state","residency_state","lease_state","backend_count","active_native_calls","queued_operations","active_imports","resident_models","heavy_leases")
        demand(snap.keys==names,"UNKNOWN")
        return snap+mapOf("participant" to "llm","barrier_id" to barrierId,"observation_seq" to (++observation).toString(),
            "resolution_kind" to resolution(),"terminal_receipt_sha256" to (journal.snapshot().payload["terminal_receipt_sha256"] ?: "NONE"))
    }
    private fun resolution(): String {
        val r=journal.snapshot()
        return when {
            r.phase=="INITIAL" && r.generation==0L && r.payload["owner"]=="NONE" && r.payload["prior_key_state"]=="NONE" -> if(enrolledInThisProcess) "INITIAL" else "CLEAN_RESTART"
            r.phase in setOf("COMMITTED","PREPARED") && r.payload["prior_key_state"]=="NONE" && r.payload["owner"]=="NONE" -> "CLEAN_RESTART"
            // Real terminal-receipt/worker-drain observation is not wired in this blocked revision.
            else -> "UNKNOWN"
        }
    }
    private fun quiescent(o: Map<String,String>) = o["backend_registry_state"]=="COMPLETE" && o["backend_count"] in setOf("0","1") && o["queue_state"]=="EMPTY" && o["native_state"] in setOf("NOT_STARTED","NATIVE_RETURNED") && o["residency_state"]=="NO_RESIDENCY_CONFIRMED" && o["lease_state"]=="NONE" && listOf("active_native_calls","queued_operations","active_imports","resident_models","heavy_leases").all { o[it]=="0" }
    private fun journalFields(f: Map<String,String>): Map<String,String> {
        val r=journal.snapshot()
        val local=mapOf("llm_journal_id" to r.id,"llm_journal_seq" to r.sequence.toString(),"llm_journal_state" to r.phase,"llm_journal_sha256" to r.sha256,"generation" to r.generation.toString(),"owner" to (r.payload["owner"] ?: throw Refusal("UNKNOWN")))
        return f.filterKeys { it.startsWith("core_journal_") }+local
    }
    private fun prior(f: Map<String,String>) {
        val p=journal.snapshot().payload
        val expected=p.filterKeys { it.startsWith("prior_") }
        demand(f.filterKeys { it.startsWith("prior_") } == expected,"PRECONDITION_CHANGED")
    }
    private fun precondition(f: Map<String,String>) {
        demand(journalFields(f).all { f[it.key]==it.value },"PRECONDITION_CHANGED"); prior(f)
    }
    private fun reserveRound(f: Map<String,String>) {
        val key=f.getValue("round_id")
        demand(key !in rounds && rounds.values.none { it==f["nonce"] },"REPLAY_CONFLICT")
        demand(rounds.size<128,"QUEUE_FULL"); rounds[key]=f.getValue("nonce")
        // The durable PREPARED record binds nonce and round; old records are never evicted.
        demand(journal.snapshot().sequence<500,"QUEUE_FULL")
    }
    @Synchronized fun handle(f: Map<String,String>,service: String): Map<String,String> {
        try {
            check(f,service)
            return when(f.getValue("kind")) {
                "DISCOVER" -> {
                    val peer=trustedPeerEpochs() ?: throw Refusal("PEER_INCARNATION_REQUIRED")
                    demand(rounds.size<128,"QUEUE_FULL")
                    f+mapOf("kind" to "NEGOTIATED","code" to "NONE","endpoint_epoch" to serviceEpoch,"llm_process_epoch" to processEpoch,"llm_service_epoch" to serviceEpoch,"core_process_epoch" to peer.first,"broker_epoch" to peer.second)
                }
                "INSPECT" -> {
                    prior(f)
                    // D4-02D SNAPSHOT requires CORE J, absent from INSPECT. Never fabricate it.
                    throw Refusal("PEER_JOURNAL_REQUIRED")
                }
                "CHALLENGE" -> {
                    demand(state!="QUIESCENT_PREPARED","BUSY")
                    precondition(f)
                    val o=observation()
                    demand(quiescent(o) && o["resolution_kind"]!="UNKNOWN","NOT_QUIESCENT")
                    reserveRound(f)
                    val predecessor=journal.snapshot()
                    val p=journal.append("PREPARED",predecessor.generation,f+mapOf("process_epoch" to processEpoch,"service_epoch" to serviceEpoch))
                    val attest=f+o+mapOf("kind" to "ATTEST","prepared_journal_seq" to p.sequence.toString(),"prepared_journal_sha256" to p.sha256,"predecessor_journal_sha256" to predecessor.sha256)
                    RecoveryProtocol.encode(attest)
                    challenge=f.toMap(); prepared=attest; state="QUIESCENT_PREPARED"; attest
                }
                "COMMIT" -> commit(f)
                "CLEANUP_REQUEST" -> {
                    prior(f); demand(f["prior_key_state"]=="PRESENT","CLEANUP_UNAVAILABLE")
                    // Never retry based on process emptiness or an opaque boolean. Inventory requires same owner/call drain.
                    // The real worker observation port is not complete: do not wait for it under this gate.
                    throw Refusal("CLEANUP_UNAVAILABLE")

                }
                else -> throw Refusal("INCOMPATIBLE")
            }
        } catch(e: Refusal) {
            if(e.code in setOf("EXPIRED","STALE_INCARNATION","JOURNAL_BLOCKED","PRECONDITION_CHANGED")) invalidate()
            return RecoveryProtocol.refused(f,e.code)
        } catch (_: Throwable) { invalidate(); return RecoveryProtocol.refused(f,"UNKNOWN") }
    }
    private fun acknowledge(f: Map<String,String>,record: RecoveryJournal.Record)=f+mapOf("kind" to "COMMIT_ACK","committed_journal_seq" to record.sequence.toString(),"committed_journal_sha256" to record.sha256,"llm_recovery_state" to "RECOVERY_COMMITTED")
    private fun commit(f: Map<String,String>): Map<String,String> {
        val last=journal.snapshot()
        if(last.phase=="COMMITTED") {
            demand(last.payload["commit_message_sha256"]==Wire.digest(f),"REPLAY_CONFLICT")
            demand(last.payload["process_epoch"]==processEpoch && last.payload["service_epoch"]==serviceEpoch,"STALE_INCARNATION")
            val saved=last.payload["ack_payload"]
            val ack=if(saved!=null) RecoveryProtocol.envelope(saved.toByteArray(Charsets.UTF_8)) else {
                val response=acknowledge(f,last)
                journal.append("COMMITTED",last.generation,last.payload+mapOf("ack_payload" to RecoveryProtocol.encode(response).toString(Charsets.UTF_8)))
                response
            }
            state="RECOVERY_COMMITTED"; return ack
        }
        val c=challenge ?: throw Refusal("RECOVERY_REQUIRED")
        val a=prepared ?: throw Refusal("RECOVERY_REQUIRED")
        demand(state=="QUIESCENT_PREPARED" && (f-setOf("kind","decision_id","decision_sha256","prepare_set_sha256"))==(c-"kind"),"PRECONDITION_CHANGED")
        demand(quiescent(observation()),"NOT_QUIESCENT")
        demand(last.phase=="PREPARED" && last.sha256==a["prepared_journal_sha256"],"PRECONDITION_CHANGED")
        // Decision is bound to our exact prepared proof. The CORE proof-set commitment remains CORE's authority.
        val expected=Wire.digest((f-"decision_sha256")+mapOf("attestation_sha256" to Wire.digest(a)))
        demand(f["decision_sha256"]==expected,"DECISION_MISMATCH")
        val payload=f+mapOf("commit_message_sha256" to Wire.digest(f),"attestation_sha256" to Wire.digest(a),"process_epoch" to processEpoch,"service_epoch" to serviceEpoch)
        val record=journal.append("COMMITTED",last.generation,payload)
        val ack=acknowledge(f,record)
        // Persist ACK in its own append-only record to avoid a self-referential hash.
        journal.append("COMMITTED",record.generation,payload+mapOf("ack_payload" to RecoveryProtocol.encode(ack).toString(Charsets.UTF_8)))
        state="RECOVERY_COMMITTED"; return ack
    }
}
