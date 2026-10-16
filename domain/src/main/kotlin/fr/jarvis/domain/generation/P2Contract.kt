package fr.jarvis.domain.generation

import fr.jarvis.domain.*
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.UUID

const val P2_VERSION = "jarvis.llm.p2/1.0"
enum class P2Error {
    UNSUPPORTED_VERSION, UNKNOWN_FIELD, DUPLICATE_FIELD, INVALID_INPUT, INVALID_ROLE, INVALID_UTF8,
    INPUT_TOO_LARGE, INVALID_LIMIT, CONTEXT_EXCEEDED, CONTEXT_NOT_REPRESENTABLE, TOKENIZER_UNAVAILABLE, NATIVE_P2_UNAVAILABLE,
    UNAUTHORIZED, MODEL_MISMATCH, INSUFFICIENT_RESOURCES, BUSY, CANCELLED, EXPIRED,
    STALE_OPERATION, NATIVE_FAILURE, CLEANUP_FAILED, UNKNOWN_STATE
}
class P2Exception(val code: P2Error) : IllegalArgumentException(code.name)
internal fun checkP2(condition: Boolean, code: P2Error) { if (!condition) throw P2Exception(code) }
internal fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
internal fun utf8Size(text: String): Int = try {
    Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text)).remaining()
} catch (_: java.nio.charset.CharacterCodingException) { throw P2Exception(P2Error.INVALID_UTF8) }
internal fun uuid(value: String) {
    checkP2(value.length == 36 && runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false), P2Error.INVALID_INPUT)
}
internal fun sha(value: String) { checkP2(value.matches(Regex("[a-f0-9]{64}")), P2Error.INVALID_INPUT) }
internal fun boundedText(text: String, maxBytes: Int, blankAllowed: Boolean = false, allowNoThink: Boolean = false) {
    checkP2(blankAllowed || text.isNotBlank(), P2Error.INVALID_INPUT)
    checkP2(text.length <= maxBytes, P2Error.INPUT_TOO_LARGE)
    checkP2(utf8Size(text) <= maxBytes, P2Error.INPUT_TOO_LARGE)
    checkP2(text.none { it == '\u0000' || (it.code < 32 && it !in "\n\r\t") }, P2Error.INVALID_INPUT)
    checkP2(listOf("<|", "<think>", "</think>", "/think", "/no_think", "[INST]", "[/INST]", "<<SYS>>").filterNot { allowNoThink && it == "/no_think" }.none { text.contains(it, ignoreCase = true) }, P2Error.INVALID_INPUT)
}
/** References identify an operation; they do NOT authenticate CORE or grant a permission. */
data class OperationKey(val epoch: String, val operationId: String, val sessionId: String, val correlationId: String,
                        val cancellationId: String, val authorizationRef: String, val leaseId: String, val generation: Long) {
    init { listOf(epoch, operationId, sessionId, correlationId, cancellationId, authorizationRef, leaseId).forEach(::uuid)
        checkP2(generation in 1..9_007_199_254_740_991L, P2Error.INVALID_INPUT) }
    override fun toString() = "OperationKey(generation=$generation, references=redacted)"
}
enum class HistoryRole { USER, ASSISTANT }
class HistoryTurn(val role: HistoryRole, val text: String) {
    init { boundedText(text, 4096) }
    override fun toString() = "HistoryTurn(${role.name}, redacted)"
}
class DocumentExcerpt(val reference: String, val revision: Int, val text: String) {
    init { uuid(reference); checkP2(revision > 0, P2Error.INVALID_INPUT); boundedText(text, 4096) }
    override fun toString() = "DocumentExcerpt(redacted)"
}
data class P2Limits(val contextTokens: Int, val outputTokens: Int, val inputBytes: Int, val remainingMillis: Int) {
    init {
        checkP2(contextTokens in 512..2048 && contextTokens % 256 == 0 && outputTokens in 1..256 && inputBytes in 1..65536 && remainingMillis in 1..90000, P2Error.INVALID_LIMIT)
    }
}
/** Explicit field list preserves duplicate detection; NOT a JSON parser or an IPC format. */
class Fields(fields: List<Pair<String, Any?>>) {
    internal val values = frozen(fields)
    override fun toString() = "Fields(redacted)"
}
class P2Request private constructor(val key: OperationKey, val system: String, history: List<HistoryTurn>,
    documents: List<DocumentExcerpt>, val question: String, val model: ModelKind, val modelSha256: String, val limits: P2Limits) {
    val version = P2_VERSION
    val history = frozen(history)
    val documents = frozen(documents)
    override fun toString() = "P2Request(version=$P2_VERSION, content=redacted)"
    companion object {
        fun create(version: String, key: OperationKey, system: String, history: List<HistoryTurn>,
                   documents: List<DocumentExcerpt>, question: String, model: ModelKind, modelSha256: String, limits: P2Limits): P2Request {
            checkP2(version == P2_VERSION, P2Error.UNSUPPORTED_VERSION)
            boundedText(system, 8192, blankAllowed = true, allowNoThink = true); boundedText(question, 8192); sha(modelSha256)
            checkP2(history.size <= 6 && history.size % 2 == 0 && documents.size <= 4, P2Error.INVALID_INPUT)
            history.forEachIndexed { index, turn -> checkP2(turn.role == (if (index % 2 == 0) HistoryRole.USER else HistoryRole.ASSISTANT), P2Error.INVALID_ROLE) }
            checkP2(documents.map { it.reference }.distinct().size == documents.size, P2Error.INVALID_INPUT)
            val bytes = utf8Size(system).toLong() + utf8Size(question) + history.sumOf { utf8Size(it.text).toLong() } + documents.sumOf { utf8Size(it.text).toLong() }
            checkP2(bytes <= limits.inputBytes, P2Error.INPUT_TOO_LARGE)
            return P2Request(key, system, history, documents, question, model, modelSha256, limits)
        }
        /** Closed local DTO decoder. A future wire decoder must also bound bytes/depth BEFORE constructing Fields. */
        fun decode(input: Fields): P2Request {
            val f = obj(input, setOf("version", "key", "system", "history", "documents", "question", "model", "modelSha256", "limits"))
            checkP2(str(f, "version") == P2_VERSION, P2Error.UNSUPPORTED_VERSION)
            val k = obj(f["key"], setOf("epoch", "operationId", "sessionId", "correlationId", "cancellationId", "authorizationRef", "leaseId", "generation"))
            val l = obj(f["limits"], setOf("contextTokens", "outputTokens", "inputBytes", "remainingMillis"))
            val key = OperationKey(str(k,"epoch"), str(k,"operationId"), str(k,"sessionId"), str(k,"correlationId"), str(k,"cancellationId"), str(k,"authorizationRef"), str(k,"leaseId"), k["generation"] as? Long ?: throw P2Exception(P2Error.INVALID_INPUT))
            val history = list(f["history"], 6).map { raw ->
                val h = obj(raw, setOf("role", "text"))
                val role = when(str(h,"role")) { "user" -> HistoryRole.USER; "assistant" -> HistoryRole.ASSISTANT; else -> throw P2Exception(P2Error.INVALID_ROLE) }
                HistoryTurn(role, str(h,"text"))
            }
            val docs = list(f["documents"], 4).map { raw -> val d = obj(raw, setOf("reference", "revision", "text")); DocumentExcerpt(str(d,"reference"), int(d,"revision"), str(d,"text")) }
            val model = when(str(f,"model")) { "SMALL" -> ModelKind.SMALL; "MAIN" -> ModelKind.MAIN; else -> throw P2Exception(P2Error.MODEL_MISMATCH) }
            return create(str(f,"version"), key, str(f,"system"), history, docs, str(f,"question"), model, str(f,"modelSha256"), P2Limits(int(l,"contextTokens"), int(l,"outputTokens"), int(l,"inputBytes"), int(l,"remainingMillis")))
        }
        private fun obj(value: Any?, keys: Set<String>): Map<String, Any?> {
            val fields = (value as? Fields)?.values ?: throw P2Exception(P2Error.INVALID_INPUT)
            checkP2(fields.size <= keys.size, if (fields.map { it.first }.distinct().size != fields.size) P2Error.DUPLICATE_FIELD else P2Error.UNKNOWN_FIELD)
            checkP2(fields.map { it.first }.distinct().size == fields.size, P2Error.DUPLICATE_FIELD)
            checkP2(fields.all { it.first in keys }, P2Error.UNKNOWN_FIELD)
            checkP2(fields.map { it.first }.toSet() == keys, P2Error.INVALID_INPUT)
            return fields.toMap()
        }
        private fun str(f: Map<String, Any?>, k: String) = f[k] as? String ?: throw P2Exception(P2Error.INVALID_INPUT)
        private fun int(f: Map<String, Any?>, k: String) = f[k] as? Int ?: throw P2Exception(P2Error.INVALID_INPUT)
        private fun list(value: Any?, max: Int): List<*> {
            val result = value as? List<*> ?: throw P2Exception(P2Error.INVALID_INPUT)
            checkP2(result.size <= max, P2Error.INVALID_INPUT); return result
        }
    }
}
