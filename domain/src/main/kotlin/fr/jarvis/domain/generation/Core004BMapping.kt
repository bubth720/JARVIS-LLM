package fr.jarvis.domain.generation

import fr.jarvis.domain.ModelKind

/** Local view of CORE 004B 07cd282 fields, NOT an imported CORE class or a shared/wire schema. */
class Core004BRequestView(val contractVersion: Int, val sessionId: String, val requestId: String,
    val systemPrompt: String, val question: String, val context: String,
    val hasSources: Boolean, val contextSize: Int, val maxTokens: Int) {
    override fun toString() = "Core004BRequestView(content=redacted)"
}
object Core004BMapping {
    /** Synthetic text-only subset. Context/sources cannot be reconstructed faithfully from CORE's flat context string. */
    fun textOnly(input: Core004BRequestView, binding: OperationKey, model: ModelKind,
                 verifiedModelSha: String, remainingMillis: Int, maxTextBytes: Int): P2Request {
        checkP2(input.contractVersion == 1, P2Error.UNSUPPORTED_VERSION)
        checkP2(input.sessionId == binding.sessionId && input.requestId == binding.operationId, P2Error.STALE_OPERATION)
        checkP2(input.context.isEmpty() && !input.hasSources, P2Error.CONTEXT_NOT_REPRESENTABLE)
        return P2Request.create(P2_VERSION, binding, input.systemPrompt, emptyList(), emptyList(), input.question,
            model, verifiedModelSha, P2Limits(input.contextSize,input.maxTokens,maxTextBytes,remainingMillis))
    }
}
