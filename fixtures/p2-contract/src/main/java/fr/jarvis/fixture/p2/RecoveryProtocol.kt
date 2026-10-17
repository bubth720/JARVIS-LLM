package fr.jarvis.fixture.p2
import java.util.Base64
import java.util.UUID

/** /1 Wire codec stays frozen. Only this /2 codec accepts the appended control transaction. */
object RecoveryProtocol {
    const val VERSION="jarvis.fixture.p2/2"
    val contract get()=BuildConfig.CONTRACT_SHA
    fun envelope(bytes: ByteArray,expectedContract: String=contract): Map<String,String> {
        // Wire.decode requires an exact closed field set. Read the discriminator with a bounded strict scan,
        // preserving duplicates and types, before selecting the closed kind table.
        demand(bytes.size <= 24576,"INPUT_TOO_LARGE")
        val candidates=RecoveryTables.fields.values.flatten().toSet()+RecoveryTables.prior
        val fields=RecoveryScan.read(bytes,candidates)
        demand(fields["version"] == VERSION && fields["contract"] == expectedContract,"INCOMPATIBLE")
        val kind=fields["kind"] ?: throw Refusal("MALFORMED")
        var expected=RecoveryTables.fields[kind] ?: throw Refusal("INCOMPATIBLE")
        if ("prior_key_state" in expected) {
            demand(fields["prior_key_state"] in setOf("NONE","PRESENT"),"INVALID_FIELD")
            if (fields["prior_key_state"] == "PRESENT") expected=expected+RecoveryTables.prior
        }
        demand(fields.keys == expected,"INVALID_FIELD")
        for ((k,v) in fields) {
            when {
                k == "nonce" -> nonce(v)
                k.endsWith("_id") || k.endsWith("_epoch") || k == "prior_epoch" || k == "prior_authorization_ref" -> uuid(v)
                k.endsWith("_sha256") || k.endsWith("_digest") -> demand(v == "NONE" && k == "terminal_receipt_sha256" || v.matches(Regex("[a-f0-9]{64}")),"INVALID_FIELD")
                k.endsWith("_uid") || k.endsWith("_seq") || k.endsWith("_generation") || k == "generation" || k.endsWith("_ms") || k in setOf("backend_count","active_native_calls","queued_operations","active_imports","resident_models","heavy_leases") -> if(v=="UNKNOWN") demand(kind=="SNAPSHOT" && k in setOf("backend_count","active_native_calls","queued_operations","active_imports","resident_models","heavy_leases"),"INVALID_NUMBER") else number(v)
            }
            RecoveryTables.enums[k]?.let { demand(v in it,"INVALID_FIELD") }
            if (k == "code") demand(v in RecoveryTables.codes,"INVALID_FIELD")
            if (k.endsWith("_journal_state")) demand(v in journalStates,"INVALID_FIELD")
        }
        return fields.toMap()
    }
    internal val technicalNames=RecoveryTables.fields.values.flatten().toSet()+RecoveryTables.prior+setOf("epoch","operation_id","correlation_id","cancellation_id","authorization_ref","lease_id","request_digest","native_ever_started","effective_end","model_unloaded","release_confirmed","phase","enrolment_id","pins_digest","process_epoch","service_epoch","state","attestation_sha256","commit_message_sha256","ack_payload","prepare_payload","terminal_payload","prior_payload","participant_set_digest","intent_sha256","challenge_b64","attest_payload","commit_payload","ack_b64","prepared_ref","attest_outbox_ref","committed_ref","core_proof_digest","observation_digest")
    fun technical(bytes: ByteArray): Map<String,String> { demand(bytes.size<=24576,"INPUT_TOO_LARGE"); return RecoveryScan.read(bytes,technicalNames) }
    val journalStates get()=RecoveryTables.journalStates
    fun uuid(s: String) { demand(runCatching { UUID.fromString(s).toString()==s }.getOrDefault(false),"INVALID_FIELD") }
    fun nonce(s: String) { demand(s.length==43 && s.matches(Regex("[A-Za-z0-9_-]{43}")),"INVALID_NONCE"); val b=runCatching { Base64.getUrlDecoder().decode(s) }.getOrElse { throw Refusal("INVALID_NONCE") }; demand(b.size==32 && Base64.getUrlEncoder().withoutPadding().encodeToString(b)==s,"INVALID_NONCE") }
    fun number(s: String): Long { demand(s.matches(Regex("0|[1-9][0-9]{0,15}")),"INVALID_NUMBER"); val n=s.toLongOrNull() ?: throw Refusal("INVALID_NUMBER"); demand(n<=9007199254740991L,"INVALID_NUMBER"); return n }
    fun fresh(f: Map<String,String>,now: Long) { val a=number(f.getValue("issued_elapsed_ms")); val b=number(f.getValue("expires_elapsed_ms")); demand(b>a && b-a<=5000 && now>=a && now<b,"EXPIRED") }
    fun encode(f: Map<String,String>): ByteArray { val b=Wire.encode(f); envelope(b); return b }
    fun refused(f: Map<String,String>,code: String)=mapOf("version" to VERSION,"contract" to contract,"kind" to "REFUSED","round_id" to f.getValue("round_id"),"nonce" to f.getValue("nonce"),"code" to if(code in RecoveryTables.codes) code else "UNKNOWN")
}
internal object RecoveryScan {
    fun read(bytes: ByteArray,names: Set<String>): Map<String,String> {
        val text=try { Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (_: Exception) { throw Refusal("MALFORMED") }
        try {
            val f=linkedMapOf<String,String>()
            android.util.JsonReader(java.io.StringReader(text)).use { r ->
                r.isLenient=false; r.beginObject()
                while(r.hasNext()) { val k=r.nextName(); demand(k in names && k !in f && r.peek()==android.util.JsonToken.STRING,"INVALID_FIELD"); val v=r.nextString(); Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(v)); f[k]=v }
                r.endObject(); demand(r.peek()==android.util.JsonToken.END_DOCUMENT,"MALFORMED")
            }; return f
        } catch(e: Refusal) { throw e } catch (_: Exception) { throw Refusal("MALFORMED") }
    }
}
