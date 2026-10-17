package fr.jarvis.fixture.p2

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

/** D4-02E.1 canonical commitments. No transport identity, native observation or grant is inferred here. */
object RecoveryCommit {
    fun canonical(fields: Map<String,String>): ByteArray {
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(fields.size)
            fields.toSortedMap().forEach { (k,v) ->
                demand(k.all { it.code in 32..126 },"INVALID_FIELD")
                for(s in listOf(k,v)) {
                    val b=Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(s))
                    val bytes=ByteArray(b.remaining()); b.get(bytes); d.writeInt(bytes.size); d.write(bytes)
                }
            }
        }
        return out.toByteArray()
    }
    fun hash(domain: String,fields: Map<String,String>)=Wire.hash(("jarvis.fixture.p2/2.$domain.D4E1\u0000").toByteArray(Charsets.US_ASCII)+canonical(fields))
    /** Restricted canonical JSON: sorted ASCII names, no controls; JsonWriter's HTML/U+2028 escaping is not used. */
    fun json(fields: Map<String,String>): ByteArray {
        fun quote(s: String): String {
            demand(s.none { it.code<32 },"INVALID_PROOF")
            Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(s))
            return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""
        }
        demand(fields.keys.all { k -> k.all { it.code in 32..126 } },"INVALID_PROOF")
        return fields.toSortedMap().entries.joinToString(",","{","}") { quote(it.key)+":"+quote(it.value) }.toByteArray(Charsets.UTF_8)
    }
    fun b64(fields: Map<String,String>,max: Int=6144): String {
        val b=json(fields); demand(b.size<=max,"INPUT_TOO_LARGE")
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }
    fun unb64(value: String,max: Int=6144): Map<String,String> {
        demand(value.length<=((max+2)/3)*4,"INPUT_TOO_LARGE")
        demand(value.matches(Regex("[A-Za-z0-9_-]+")),"INVALID_PROOF")
        val bytes=try { Base64.getUrlDecoder().decode(value) } catch(_: Exception) { throw Refusal("INVALID_PROOF") }
        demand(bytes.size<=max,"INPUT_TOO_LARGE")
        demand(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)==value,"INVALID_PROOF")
        // Parse a flat map with duplicate/type/UTF-8 checks; exact allowed names are checked by the proof codec.
        val fields=RecoveryScan.read(bytes,RecoveryProtocol.technicalNames)
        demand(json(fields).contentEquals(bytes),"INVALID_PROOF")
        return fields
    }
    fun proof(value: String,kind: String,contract: String=RecoveryProtocol.contract): Map<String,String> {
        val p=try { RecoveryProtocol.envelope(json(unb64(value)),contract) }
        catch(e: Refusal) { if(e.code=="INPUT_TOO_LARGE") throw e else throw Refusal("INVALID_PROOF") }
        demand(p["kind"]==kind && p["participant"]==if(kind=="CORE_PREPARE") "core" else "llm","INVALID_PROOF")
        return p
    }
    fun verify(commit: Map<String,String>,localCore: Map<String,String>,localLlm: Map<String,String>,contract: String=RecoveryProtocol.contract) {
        RecoveryProtocol.envelope(json(commit),contract)
        val core=proof(commit.getValue("core_prepare_b64"),"CORE_PREPARE",contract)
        val llm=proof(commit.getValue("llm_attest_b64"),"ATTEST",contract)
        demand(core==localCore && llm==localLlm,"PROOF_SET_MISMATCH")
        val c=hash("core_prepare",core); val l=hash("llm_attest",llm)
        demand(llm["core_prepare_sha256"]==c && commit["core_prepare_sha256"]==c && commit["llm_attest_sha256"]==l,"PROOF_SET_MISMATCH")
        for(member in listOf(core,llm)) {
            val shared=member.keys.intersect(commit.keys)-setOf("kind","prepared_journal_seq","prepared_journal_sha256","predecessor_journal_sha256")
            demand(shared.all { member[it]==commit[it] },"PROOF_SET_MISMATCH")
        }
        demand(commit["prepare_set_sha256"]==hash("prepare_set",mapOf("core_prepare_sha256" to c,"llm_attest_sha256" to l)),"PROOF_SET_MISMATCH")
        demand(commit["decision_sha256"]==hash("decision",commit-"decision_sha256"),"DECISION_MISMATCH")
    }
}
