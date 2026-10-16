package fr.jarvis.domain.generation

import fr.jarvis.domain.*

internal const val ID = "00000000-0000-0000-0000-000000000001"
internal const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
internal fun key(generation: Long = 1) = OperationKey(ID, "00000000-0000-0000-0000-" + generation.toString().padStart(12,'0'), ID, ID, ID, ID, ID, generation)
internal fun request(key: OperationKey = key(), system: String = "Instructions CORE synthétiques.",
                     question: String = "Question synthétique finale.", history: List<HistoryTurn> = emptyList(),
                     documents: List<DocumentExcerpt> = emptyList(), limits: P2Limits = P2Limits(512,64,65536,1000),
                     version: String = P2_VERSION) = P2Request.create(version,key,system,history,documents,question,ModelKind.SMALL,SHA,limits)
internal fun prepared(r: P2Request = request(), tokens: Int = 100, fixtureOnly: Boolean = true): PreparedP2 = P2Planner.prepare(r,
    SafetyDecision(RuntimeConfig(ModelKind.SMALL,512,64,1), "synthetic admission, NOT a device measurement"), SHA,
    TemplateTokenizer { _, model -> TokenMeasurement(tokens,model,SHA,Verification.FIXTURE) },fixtureOnly, CoreAuthorizationGate { true })
internal fun fields(role: String = "user"): List<Pair<String,Any?>> {
    val k = Fields(listOf("epoch" to ID,"operationId" to ID,"sessionId" to ID,"correlationId" to ID,"cancellationId" to ID,"authorizationRef" to ID,"leaseId" to ID,"generation" to 1L))
    val l = Fields(listOf("contextTokens" to 512,"outputTokens" to 64,"inputBytes" to 65536,"remainingMillis" to 1000))
    return listOf("version" to P2_VERSION,"key" to k,"system" to "CORE synthetic", "history" to listOf(Fields(listOf("role" to role,"text" to "ancienne question")),Fields(listOf("role" to "assistant","text" to "ancienne réponse"))),"documents" to emptyList<Fields>(),"question" to "question finale","model" to "SMALL","modelSha256" to SHA,"limits" to l)
}
