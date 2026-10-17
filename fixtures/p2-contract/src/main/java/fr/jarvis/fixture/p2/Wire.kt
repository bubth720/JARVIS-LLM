package fr.jarvis.fixture.p2

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import fr.jarvis.domain.*
import fr.jarvis.domain.generation.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

class Refusal(val code: String) : IllegalArgumentException(code)
fun demand(ok: Boolean, code: String) { if (!ok) throw Refusal(code) }
object Wire {
    const val VERSION = "jarvis.fixture.p2/1"
    const val CLIENT = "fr.jarvis.fixture.core"
    const val ENDPOINT = "fr.jarvis.fixture.llm"
    const val PERMISSION = "fr.jarvis.fixture.llm.permission.BIND_P2"
    val contract: String get() = BuildConfig.LEGACY_CONTRACT_SHA
    val keyNames = setOf("version","contract","epoch","session_id","operation_id","correlation_id","cancellation_id","authorization_ref","lease_id","generation")
    val requestNames = keyNames + setOf("system","question","model","model_sha256","profile","context_tokens","output_tokens","remaining_ms","synthetic")
    fun decode(bytes: ByteArray, names: Set<String>): Map<String,String> {
        demand(bytes.size <= 24576,"INPUT_TOO_LARGE")
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() } catch (_: Exception) { throw Refusal("INVALID_UTF8") }
        try {
            val fields = linkedMapOf<String,String>()
            JsonReader(StringReader(text)).use { r ->
                r.isLenient = false; r.beginObject()
                while (r.hasNext()) {
                    val name = r.nextName()
                    demand(name in names && name !in fields,"UNKNOWN_OR_DUPLICATE_FIELD")
                    demand(r.peek() == JsonToken.STRING,"INVALID_TYPE")
                    fields[name] = r.nextString()
                }
                r.endObject(); demand(r.peek() == JsonToken.END_DOCUMENT,"TRAILING_DATA")
            }
            demand(fields.keys == names,"MISSING_FIELD")
            return fields.toMap()
        } catch (e: Refusal) { throw e } catch (_: Exception) { throw Refusal("MALFORMED") }
    }
    fun encode(fields: Map<String,String>): ByteArray {
        val s = StringWriter()
        JsonWriter(s).use { w -> w.beginObject(); fields.toSortedMap().forEach { (k,v) -> w.name(k).value(v) }; w.endObject() }
        val bytes = s.toString().toByteArray(Charsets.UTF_8); demand(bytes.size <= 24576,"OUTPUT_TOO_LARGE"); return bytes
    }
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun digest(fields: Map<String,String>): String {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(fields.size)
            fields.toSortedMap().forEach { (k,v) -> for (s in listOf(k,v)) { val b=s.toByteArray(Charsets.UTF_8); d.writeInt(b.size); d.write(b) } }
        }
        return hash(out.toByteArray())
    }
    fun version(f: Map<String,String>) { demand(f["version"] == VERSION && f["contract"] == contract,"INCOMPATIBLE") }
    fun number(s: String, max: Long): Long { demand(s.matches(Regex("[1-9][0-9]{0,15}")),"INVALID_NUMBER"); val n=s.toLongOrNull() ?: throw Refusal("INVALID_NUMBER"); demand(n in 1..max,"INVALID_LIMIT"); return n }
    fun key(f: Map<String,String>): OperationKey {
        version(f)
        return try { OperationKey(f.getValue("epoch"),f.getValue("operation_id"),f.getValue("session_id"),f.getValue("correlation_id"),f.getValue("cancellation_id"),f.getValue("authorization_ref"),f.getValue("lease_id"),number(f.getValue("generation"),9007199254740991)) }
        catch (e: Refusal) { throw e } catch (_: Exception) { throw Refusal("INVALID_KEY") }
    }
    fun request(bytes: ByteArray): Request {
        val f=decode(bytes,requestNames); val key=key(f)
        val ctx=number(f.getValue("context_tokens"),2048).toInt(); val output=number(f.getValue("output_tokens"),256).toInt()
        val duration=number(f.getValue("remaining_ms"),90000).toInt()
        demand(f["synthetic"] == "true","SYNTHETIC_ONLY")
        // Validate general P2 bounds BEFORE checking the deliberately finite pilot corpus.
        val p2 = try { P2Request.create(P2_VERSION,key,f.getValue("system"),emptyList(),emptyList(),f.getValue("question"),ModelKind.valueOf(f.getValue("model")),f.getValue("model_sha256"),P2Limits(ctx,output,16384,duration)) }
        catch (_: Exception) { throw Refusal("INVALID_P2") }
        demand(p2.question in setOf("Dis bonjour en français.","Combien font deux plus deux ?"),"SYNTHETIC_ONLY")
        demand(p2.system in setOf("","Réponds brièvement en français. /no_think","Réponds en une phrase en français. /no_think"),"SYNTHETIC_ONLY")
        val profile=try { Profile.valueOf(f.getValue("profile")) } catch (_: Exception) { throw Refusal("INVALID_PROFILE") }
        demand(profile != Profile.FULL_POWER,"INVALID_PROFILE")
        demand(p2.model != ModelKind.MAIN || ctx >= 768,"INVALID_LIMIT")
        return Request(f.toMap(),p2,profile,digest(f))
    }
}
class Request(val fields: Map<String,String>,val p2: P2Request,val profile: Profile,val digest: String) {
    val key get() = p2.key
    val keyFields get() = fields.filterKeys { it in Wire.keyNames }
    override fun toString() = "Request(redacted)"
}

/** Only call after a real authenticated broker callback. Pure validation never manufactures an authority. */
object AuthorityReceipts {
    fun verify(query: Map<String,String>,response: Map<String,String>,now: Long,deadline: Long): Long {
        demand(response.keys == query.keys + setOf("approved","expires_elapsed_ms"),"UNVERIFIED_LEASE")
        demand(query.all { response[it.key] == it.value } && response["approved"] == "true","UNVERIFIED_LEASE")
        val expires=Wire.number(response.getValue("expires_elapsed_ms"),Long.MAX_VALUE)
        demand(expires > now && expires <= deadline,"EXPIRED_LEASE")
        return expires
    }
}
