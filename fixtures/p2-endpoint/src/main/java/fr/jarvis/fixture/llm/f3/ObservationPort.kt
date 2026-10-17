package fr.jarvis.fixture.llm.f3

/** INTERNAL DESIGN CANDIDATE. Not wired to AndroidRuntime, recovery or /2.
 * No constructor creates a backend. Unknown fields are nullable, never assumed zero.
 * A real adapter must sample the EXISTING owner on its worker under a frozen admission gate.
 */
internal data class OperationKey(
    val pilot: String, val session: String, val operation: String, val correlation: String,
    val generation: Long, val lease: String, val leaseGeneration: Long,
    val epoch: String, val cancellation: String, val authorization: String, val requestDigest: String
) {
    init {
        listOf(pilot, session, operation, correlation, lease, epoch, cancellation, authorization).forEach {
            require(java.util.UUID.fromString(it).toString() == it)
        }
        require(generation in 1..9007199254740991L && leaseGeneration in 1..9007199254740991L)
        require(requestDigest.matches(Regex("[a-f0-9]{64}")))
    }
}
internal enum class Ownership { EMPTY, OWNED, UNKNOWN }
internal enum class Completion { RUNNING, STOP_REQUESTED, DRAINED, UNLOADED, CLEANUP_FAILED, UNKNOWN }
internal data class Observation(
    val process: String?, val worker: String?, val owner: String?, val key: OperationKey?,
    val registryGeneration: Long?, val admissionRevision: Long?, val barrier: String?,
    val queue: Int?, val imports: Int?, val calls: Int?, val modelLeases: Int?, val heavyLeases: Int?,
    val handle: Ownership, val model: Ownership, val context: Ownership,
    val callEndSequence: Long?, val unloadSequence: Long?, val completion: Completion
)
internal data class Expectation(
    val process: String, val worker: String, val owner: String, val key: OperationKey,
    val registryGeneration: Long, val admissionRevision: Long, val barrier: String,
    val minimumCallEnd: Long, val minimumUnload: Long
)
/** Proposed read port only. Stop/drain/unload commands need separate approval and exact-key checks. */
internal fun interface ObservationPort { suspend fun observe(expected: Expectation): Observation? }
internal object UnavailableObservationPort : ObservationPort {
    override suspend fun observe(expected: Expectation): Observation? = null
}
internal data class ReviewResult(val state: Completion, val reason: String)
internal object ObservationReview {
    /** Pure consistency check, NOT an authenticated receipt or native release proof. */
    fun review(expected: Expectation, actual: Observation?): ReviewResult {
        fun unknown(reason: String) = ReviewResult(Completion.UNKNOWN, reason)
        if (actual == null) return unknown("PORT_UNAVAILABLE")
        if (expected.registryGeneration <= 0 || expected.admissionRevision <= 0 ||
            expected.minimumCallEnd <= 0 || expected.minimumUnload <= 0 ||
            listOf(expected.process, expected.worker, expected.owner, expected.barrier).any { it.isBlank() })
            return unknown("INVALID_EXPECTATION")
        if (actual.process != expected.process || actual.worker != expected.worker || actual.owner != expected.owner ||
            actual.key != expected.key || actual.registryGeneration != expected.registryGeneration ||
            actual.admissionRevision != expected.admissionRevision || actual.barrier != expected.barrier)
            return unknown("PROVENANCE_MISMATCH")
        if (actual.completion == Completion.CLEANUP_FAILED)
            return ReviewResult(Completion.CLEANUP_FAILED, "CLEANUP_FAILED")
        val counts = listOf(actual.queue, actual.imports, actual.calls, actual.modelLeases, actual.heavyLeases)
        if (counts.any { it == null || it < 0 } || listOf(actual.handle, actual.model, actual.context).any { it == Ownership.UNKNOWN })
            return unknown("INCOMPLETE_INVENTORY")
        if (actual.completion == Completion.UNKNOWN) return unknown("SOURCE_UNKNOWN")
        // Stop is intent only; outstanding queues/imports/calls cannot be a drain.
        if (actual.completion == Completion.STOP_REQUESTED || actual.completion == Completion.RUNNING)
            return ReviewResult(actual.completion, "NOT_DRAINED")
        if (actual.queue != 0 || actual.imports != 0 || actual.calls != 0 ||
            actual.callEndSequence == null || actual.callEndSequence < expected.minimumCallEnd)
            return unknown("DRAIN_UNCONFIRMED")
        if (actual.completion == Completion.DRAINED) return ReviewResult(Completion.DRAINED, "UNLOAD_NOT_CONFIRMED")
        if (actual.modelLeases != 0 || actual.heavyLeases != 0 || actual.model != Ownership.EMPTY ||
            actual.context != Ownership.EMPTY || actual.unloadSequence == null || actual.unloadSequence < expected.minimumUnload)
            return unknown("UNLOAD_UNCONFIRMED")
        // An empty model/context may coexist with an allocated library handle; no RSS=0 claim.
        return ReviewResult(Completion.UNLOADED, "CONSISTENT_CANDIDATE_ONLY")
    }
}
