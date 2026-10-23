package fr.jarvis.domain.prompt

import fr.jarvis.domain.SafetyDecision
import fr.jarvis.domain.generation.*

/** Host-injected, request-scoped exact tokenizer only. A future implementation must be bound by its
 * trusted backend to the SAME loaded model/context/template/worker/lease and reject changed epochs,
 * cancellation or expiry. No such binding or JNI implementation is provided here. The interface itself
 * is neither a reservation authority nor evidence that native measurement exists. */
fun interface NativeTokenCountPort : TemplateTokenizer
object UnavailableNativeTokenCountPort : NativeTokenCountPort {
    const val status = "UNSUPPORTED"
    override fun measure(prompt: PromptSpec, modelSha256: String): TokenMeasurement =
        throw P2Exception(P2Error.TOKENIZER_UNAVAILABLE)
}

/** Pins must originate in the trusted GGUF/qualification pipeline, never in an IPC request. */
class TemplatePolicy(val modelSha256: String, val templateSha256: String, val version: String = "qwen3-template/1") {
    init {
        require(version == "qwen3-template/1") { "Unsupported template policy; no fallback" }
        require(listOf(modelSha256, templateSha256).all { it.matches(Regex("[a-f0-9]{64}")) })
    }
    override fun toString() = "TemplatePolicy(version=$version)"
}
/** Rendering belongs to the native model's exact template, not to Kotlin string concatenation. */
class TemplateBoundTokenizer(private val policy: TemplatePolicy,
                             private val port: NativeTokenCountPort = UnavailableNativeTokenCountPort) : TemplateTokenizer {
    override fun measure(prompt: PromptSpec, modelSha256: String): TokenMeasurement {
        if (modelSha256 != policy.modelSha256) throw P2Exception(P2Error.MODEL_MISMATCH)
        val measured = port.measure(prompt, modelSha256)
        if (measured.modelSha256 != modelSha256 || measured.templateSha256 != policy.templateSha256)
            throw P2Exception(P2Error.MODEL_MISMATCH)
        return measured
    }
}

/** Immutable structured input; existing P2 validation keeps SYSTEM/history/documents/question separate. */
class PromptFrame(val request: P2Request) {
    override fun toString() = "PromptFrame(content=redacted)"
}
enum class HistoryReduction { REFUSE, DROP_OLDEST_COMPLETE_TURNS }
class ContextPlan(val prepared: PreparedP2, val droppedPairs: Int) {
    val inputTokens get() = prepared.measurement.inputTokens
    val outputReserve get() = prepared.config.maxTokens
    val marginReserve get() = P2Rules.GUARD_TOKENS
    val admittedCapacity get() = prepared.effectiveContextLimit
    val verification get() = prepared.measurement.verification
    override fun toString() = "ContextPlan(inputTokens=$inputTokens,droppedPairs=$droppedPairs,verification=$verification)"
}

/** Planning only: no backend call, no reservation acquisition/release and no modification of /1 or /2 contracts. */
object ContextPlanner {
    fun prepare(frame: PromptFrame, admitted: SafetyDecision, verifiedModelSha256: String,
                template: TemplatePolicy, native: NativeTokenCountPort = UnavailableNativeTokenCountPort,
                reduction: HistoryReduction = HistoryReduction.REFUSE,
                authorization: CoreAuthorizationGate = CoreAuthorizationGate { false },
                fixtureOnly: Boolean = false): ContextPlan {
        val original = frame.request
        if (!authorization.permits(original)) throw P2Exception(P2Error.UNAUTHORIZED)
        var candidate = original
        var dropped = 0
        while (true) {
            try {
                // Existing planner checks authorization of the EXACT derived request, model, native proof and budget.
                val prepared = P2Planner.prepare(candidate, admitted, verifiedModelSha256,
                    TemplateBoundTokenizer(template, native), fixtureOnly, authorization)
                return ContextPlan(prepared, dropped)
            } catch (e: P2Exception) {
                if (e.code != P2Error.CONTEXT_EXCEEDED || reduction != HistoryReduction.DROP_OLDEST_COMPLETE_TURNS ||
                    candidate.history.isEmpty()) throw e
                val retained = candidate.history.drop(2)
                candidate = P2Request.create(original.version, original.key, original.system, retained,
                    original.documents, original.question, original.model, original.modelSha256, original.limits)
                dropped++
            }
        }
    }
}
