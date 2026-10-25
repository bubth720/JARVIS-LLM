package fr.jarvis.domain.client

import fr.jarvis.domain.Profile
import fr.jarvis.domain.generation.*
import fr.jarvis.domain.runtime.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

const val CLIENT_VERSION = "jarvis.llm.client/1"
enum class Priority { INTERACTIVE, BACKGROUND }
enum class ExecutionTarget { ANDROID_LOCAL, LINUX_LOCAL }
enum class TokenCountStatus { UNSUPPORTED }
enum class ClientError { UNAUTHORIZED, NATIVE_UNQUALIFIED, TOKENIZER_UNAVAILABLE, UNSUPPORTED_PROFILE, UNKNOWN_STATE }

/** A local API, not a wire schema, authorization grant or new admission policy. */
class GenerateRequest private constructor(val prompt: P2Request, val priority: Priority,
    val profile: Profile, val target: ExecutionTarget) {
    val version = CLIENT_VERSION
    val reference = RequestReference(prompt.key, fingerprint())
    // Length framing prevents concatenation ambiguity. All authorization-relevant fields are bound.
    private fun fingerprint(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun text(s: String) { val b = s.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b) }
            text(version); text(prompt.version); text(priority.name); text(profile.name); text(target.name)
            with(prompt.key) {
                listOf(epoch, operationId, sessionId, correlationId, cancellationId, authorizationRef, leaseId).forEach(::text)
                out.writeLong(generation)
            }
            text(prompt.system)
            out.writeInt(prompt.history.size); prompt.history.forEach { text(it.role.name); text(it.text) }
            out.writeInt(prompt.documents.size); prompt.documents.forEach { text(it.reference); out.writeInt(it.revision); text(it.text) }
            text(prompt.question); text(prompt.model.name); text(prompt.modelSha256)
            with(prompt.limits) { out.writeInt(contextTokens); out.writeInt(outputTokens); out.writeInt(inputBytes); out.writeInt(remainingMillis) }
        }
        return digest(bytes.toByteArray())
    }
    override fun toString() = "GenerateRequest(version=$version,content=redacted)"
    companion object {
        fun create(version: String = CLIENT_VERSION, prompt: P2Request, priority: Priority,
                   profile: Profile, target: ExecutionTarget): GenerateRequest {
            checkP2(version == CLIENT_VERSION, P2Error.UNSUPPORTED_VERSION)
            return GenerateRequest(prompt, priority, profile, target)
        }
        /** Closed in-process DTO. Any future transport must bound bytes/depth before decoding. */
        fun decode(input: Fields): GenerateRequest {
            val fields = input.values
            checkP2(fields.size <= 6, P2Error.INPUT_TOO_LARGE)
            checkP2(fields.map { it.first }.distinct().size == fields.size, P2Error.DUPLICATE_FIELD)
            val keys = setOf("version", "prompt", "priority", "profile", "target")
            checkP2(fields.all { it.first in keys }, P2Error.UNKNOWN_FIELD)
            checkP2(fields.map { it.first }.toSet() == keys, P2Error.INVALID_INPUT)
            val f = fields.toMap()
            fun string(k: String) = f[k] as? String ?: throw P2Exception(P2Error.INVALID_INPUT)
            checkP2(string("version") == CLIENT_VERSION, P2Error.UNSUPPORTED_VERSION)
            fun <T> choice(k: String, values: Array<T>): T where T : Enum<T> =
                values.firstOrNull { it.name == string(k) } ?: throw P2Exception(P2Error.INVALID_INPUT)
            return create(string("version"), P2Request.decode(f["prompt"] as? Fields ?: throw P2Exception(P2Error.INVALID_INPUT)),
                choice("priority", Priority.entries.toTypedArray()), choice("profile", Profile.entries.toTypedArray()),
                choice("target", ExecutionTarget.entries.toTypedArray()))
        }
    }
}
internal fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
data class RequestReference(val key: OperationKey, val requestSha256: String) {
    init { sha(requestSha256) }
    override fun toString() = "RequestReference(redacted)"
}
class Cancel(val reference: RequestReference) {
    val version = CLIENT_VERSION
    override fun toString() = "Cancel(version=$version,reference=redacted)"
}
/** No bool capable of inventing native termination or unload exists in this contract revision. */
data class NativeResourceState(val observation: RuntimeObservation?) {
    val termination = NativeProofStatus.UNATTESTED
    val unload = NativeProofStatus.UNATTESTED
    val mayAdmitAnotherEngine: Boolean get() = false
}
data class ClientCapabilities(val version: String = CLIENT_VERSION) {
    val nativeReady: Boolean get() = false
    val exactTokenCount: TokenCountStatus get() = TokenCountStatus.UNSUPPORTED
    val globalArbiter: Boolean get() = false
    val qualifiedFullPower: Boolean get() = false
}
enum class EventOrigin { FIXTURE, UNAVAILABLE }
sealed class ClientEvent(val reference: RequestReference, val sequence: Int, val origin: EventOrigin) {
    val version = CLIENT_VERSION
    init { checkP2(sequence in 0..257, P2Error.INVALID_LIMIT) }
    override fun toString() = "ClientEvent(version=$version,origin=$origin,content=redacted)"
}
class Ack(reference: RequestReference, origin: EventOrigin) : ClientEvent(reference, 0, origin)
class StreamChunk(reference: RequestReference, sequence: Int, val text: String, origin: EventOrigin) : ClientEvent(reference, sequence, origin) {
    init {
        checkP2(sequence > 0, P2Error.INVALID_LIMIT)
        checkP2(text.isNotEmpty() && text.length <= 4096 && utf8Size(text) <= 4096, P2Error.INPUT_TOO_LARGE)
        checkP2(text.none { it == '\u0000' || (it.code < 32 && it !in "\n\r\t") }, P2Error.INVALID_INPUT)
    }
}
/** Content completion is NOT JNI completion or resource release. */
class Completed(reference: RequestReference, sequence: Int, origin: EventOrigin) : ClientEvent(reference, sequence, origin)
class Error(reference: RequestReference, sequence: Int, val code: ClientError, origin: EventOrigin = EventOrigin.UNAVAILABLE) : ClientEvent(reference, sequence, origin)
fun interface ClientAuthorizationGate { fun permits(request: GenerateRequest): Boolean }
/** A portable local port only: it provides no Binder, socket, runtime composition or native worker. */
interface GenerationClientPort {
    val capabilities: ClientCapabilities
    fun submit(request: GenerateRequest): ClientEvent
    fun cancel(request: Cancel): NativeResourceState
}
class UnavailableGenerationClient(private val authorization: ClientAuthorizationGate = ClientAuthorizationGate { false }) : GenerationClientPort {
    override val capabilities = ClientCapabilities()
    override fun submit(request: GenerateRequest): ClientEvent {
        val allowed = runCatching { authorization.permits(request) }.getOrDefault(false)
        val code = when {
            !allowed -> ClientError.UNAUTHORIZED
            request.target == ExecutionTarget.ANDROID_LOCAL && request.profile == Profile.FULL_POWER -> ClientError.UNSUPPORTED_PROFILE
            else -> ClientError.NATIVE_UNQUALIFIED
        }
        return Error(request.reference, 0, code)
    }
    override fun cancel(request: Cancel) = NativeResourceState(null)
}
