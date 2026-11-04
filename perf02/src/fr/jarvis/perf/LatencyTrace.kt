package fr.jarvis.perf

import java.util.UUID

/** TEST/REVIEW SOURCES ONLY. Never included in any main source set or authority. */
fun interface MonotonicClock { fun nanos(): Long }
enum class ClockKind { HOST_NANO_TIME, ANDROID_ELAPSED_REALTIME_NANOS, SYNTHETIC }
enum class Origin { REAL_MODEL, SYNTHETIC, DETERMINISTIC_LOCAL, UNAVAILABLE }
enum class Residency { COLD_PROCESS, COLD_MODEL, WARM_VERIFIED, UNKNOWN }
enum class Stage {
    UI_SEND, CORE_ACCEPT, ENGINE_ENTER, LOAD_REQUEST, LOAD_RETURN, MODEL_LOAD_ENTER, MODEL_LOAD_RETURN,
    NATIVE_ENTER, NATIVE_FIRST_TOKEN, NATIVE_RETURN, BACKEND_RETURN, UI_TEXT_SET, UI_FIRST_RENDER,
    COMPLETED, STOP_REQUESTED, DRAIN_RETURN
}
enum class TraceState { OPEN, STOP_REQUESTED, COMPLETED, UNKNOWN }
enum class Disposition { ACCEPTED, DUPLICATE, STALE, REJECTED, UNSUPPORTED, TERMINAL }
enum class Status { OBSERVED, UNSUPPORTED, UNKNOWN, NOT_RUN }
data class DurationValue(val status: Status, val nanos: Long? = null) {
    init { require((status == Status.OBSERVED) == (nanos != null)); require(nanos == null || nanos >= 0) }
}

/** Correlation only: neither UUIDs nor model digests grant CORE authorization or a lease. */
class TraceKey(val request: String, val session: String, val epoch: String, val generation: Long,
               val lease: String, val modelSha256: String) {
    init {
        listOf(request, session, epoch, lease).forEach { require(UUID.fromString(it).toString() == it) }
        require(generation > 0); require(modelSha256.matches(Regex("[a-f0-9]{64}")))
    }
    override fun equals(other: Any?) = other is TraceKey && request == other.request && session == other.session &&
        epoch == other.epoch && generation == other.generation && lease == other.lease && modelSha256 == other.modelSha256
    override fun hashCode() = listOf(request, session, epoch, generation, lease, modelSha256).hashCode()
    override fun toString() = "TraceKey(redacted)"
}

class TraceSnapshot internal constructor(val key: TraceKey, val clock: ClockKind, val scope: String,
    val origin: Origin, val residency: Residency, val state: TraceState, events: Map<Stage, Long>,
    val nativeFirstToken: Status, val coreAcceptance: Status, val failure: Failure?) {
    val events: Map<Stage, Long> = java.util.Collections.unmodifiableMap(HashMap(events))
    val exactInputTokens: Status get() = Status.UNKNOWN
    val exactOutputTokens: Status get() = Status.UNKNOWN
    val nativeDrain: Status get() = if (origin == Origin.SYNTHETIC && Stage.DRAIN_RETURN in events) Status.OBSERVED else Status.UNKNOWN
    val nativeUnload: Status get() = Status.UNKNOWN
    val mayAdmitAnotherEngine: Boolean get() = false
    fun duration(from: Stage, to: Stage): DurationValue {
        if (state == TraceState.UNKNOWN) return DurationValue(Status.UNKNOWN)
        if ((from == Stage.NATIVE_FIRST_TOKEN || to == Stage.NATIVE_FIRST_TOKEN) && nativeFirstToken == Status.UNSUPPORTED)
            return DurationValue(Status.UNSUPPORTED)
        if ((from == Stage.CORE_ACCEPT || to == Stage.CORE_ACCEPT) && coreAcceptance == Status.UNSUPPORTED)
            return DurationValue(Status.UNSUPPORTED)
        val a = events[from] ?: return DurationValue(Status.NOT_RUN)
        val b = events[to] ?: return DurationValue(Status.NOT_RUN)
        return try { DurationValue(Status.OBSERVED, Math.subtractExact(b, a).also { require(it >= 0) }) }
        catch (_: Exception) { DurationValue(Status.UNKNOWN) }
    }
    override fun toString() = "TraceSnapshot(origin=$origin,state=$state,content=absent)"
}
enum class Failure { CANCELLED, NATIVE_ERROR, BACKEND_ERROR, MEMORY_LIMIT, CLOCK_ERROR, INVALID_ORDER, CLOSED }

/** Bounded O(1) observer. Has no token text, paths, timer, worker, native handle, model factory or resource broker. */
class LatencyTrace(val key: TraceKey, private val clock: MonotonicClock,
    private val clockKind: ClockKind, private val origin: Origin, private val residency: Residency,
    private val fixtureStreaming: Boolean = false, private val clockScope: String = UUID.randomUUID().toString(),
    private val fixtureCore: Boolean = false) {
    init { require(!(fixtureStreaming || fixtureCore) || origin == Origin.SYNTHETIC); require(UUID.fromString(clockScope).toString() == clockScope) }
    private val events = java.util.EnumMap<Stage, Long>(Stage::class.java)
    private var last: Long? = null
    private var state = TraceState.OPEN
    private var failure: Failure? = null
    @Synchronized fun record(reference: TraceKey, stage: Stage): Disposition {
        if (reference != key) return Disposition.STALE
        if (state == TraceState.UNKNOWN) return Disposition.TERMINAL
        if (stage in events) return Disposition.DUPLICATE
        if (state == TraceState.COMPLETED) return Disposition.TERMINAL
        // The current JNI exposes neither first token nor a qualified drain receipt.
        if ((stage == Stage.NATIVE_FIRST_TOKEN || stage == Stage.DRAIN_RETURN) && !fixtureStreaming) return Disposition.UNSUPPORTED
        if (stage == Stage.CORE_ACCEPT && !fixtureCore) return Disposition.UNSUPPORTED
        if (stage == Stage.NATIVE_FIRST_TOKEN && Stage.NATIVE_RETURN in events ||
            stage == Stage.CORE_ACCEPT && Stage.ENGINE_ENTER in events ||
            stage == Stage.MODEL_LOAD_ENTER && Stage.LOAD_RETURN in events) return invalidate(Failure.INVALID_ORDER)
        if (state == TraceState.STOP_REQUESTED && stage !in setOf(Stage.NATIVE_RETURN, Stage.BACKEND_RETURN,
                Stage.MODEL_LOAD_RETURN, Stage.LOAD_RETURN, Stage.DRAIN_RETURN)) return Disposition.TERMINAL
        val parents = when (stage) {
            Stage.UI_SEND -> emptyList()
            Stage.CORE_ACCEPT -> listOf(Stage.UI_SEND)
            Stage.ENGINE_ENTER -> listOf(if (fixtureCore) Stage.CORE_ACCEPT else Stage.UI_SEND)
            Stage.LOAD_REQUEST -> listOf(Stage.UI_SEND)
            Stage.LOAD_RETURN -> listOf(Stage.LOAD_REQUEST)
            Stage.MODEL_LOAD_ENTER -> listOf(Stage.LOAD_REQUEST)
            Stage.MODEL_LOAD_RETURN -> listOf(Stage.MODEL_LOAD_ENTER)
            Stage.NATIVE_ENTER -> listOf(Stage.ENGINE_ENTER)
            Stage.NATIVE_FIRST_TOKEN, Stage.NATIVE_RETURN -> listOf(Stage.NATIVE_ENTER)
            Stage.BACKEND_RETURN -> listOf(Stage.ENGINE_ENTER)
            Stage.UI_TEXT_SET -> listOf(if (fixtureStreaming) Stage.NATIVE_FIRST_TOKEN else Stage.NATIVE_RETURN)
            Stage.UI_FIRST_RENDER -> listOf(Stage.UI_TEXT_SET)
            Stage.COMPLETED -> listOf(Stage.NATIVE_RETURN, Stage.BACKEND_RETURN, Stage.UI_FIRST_RENDER)
            Stage.STOP_REQUESTED -> listOf(Stage.UI_SEND)
            Stage.DRAIN_RETURN -> listOf(Stage.STOP_REQUESTED, Stage.NATIVE_RETURN)
        }
        if (parents.any { it !in events }) return invalidate(Failure.INVALID_ORDER)
        val now = try { clock.nanos() } catch (_: Exception) { return invalidate(Failure.CLOCK_ERROR) }
        val previous = last
        if (previous != null) {
            try { if (Math.subtractExact(now, previous) < 0) return invalidate(Failure.CLOCK_ERROR) }
            catch (_: ArithmeticException) { return invalidate(Failure.CLOCK_ERROR) }
        }
        last = now; events[stage] = now
        if (stage == Stage.STOP_REQUESTED) { state = TraceState.STOP_REQUESTED; failure = Failure.CANCELLED }
        if (stage == Stage.COMPLETED) state = TraceState.COMPLETED
        return Disposition.ACCEPTED
    }
    @Synchronized fun failed(reference: TraceKey, reason: Failure) {
        if (reference == key && state != TraceState.COMPLETED) { failure = reason; state = TraceState.UNKNOWN }
    }
    private fun invalidate(reason: Failure): Disposition { failure = reason; state = TraceState.UNKNOWN; return Disposition.REJECTED }
    @Synchronized fun snapshot() = TraceSnapshot(key, clockKind, clockScope, origin, residency, state, events,
        if (fixtureStreaming && Stage.NATIVE_FIRST_TOKEN in events) Status.OBSERVED else if (fixtureStreaming) Status.NOT_RUN else Status.UNSUPPORTED,
        if (fixtureCore && Stage.CORE_ACCEPT in events) Status.OBSERVED else if (fixtureCore) Status.NOT_RUN else Status.UNSUPPORTED, failure)
}

object Quantiles {
    /** Nearest rank, no interpolation; 20 or more samples for p95. Never returns a fake zero for absent data. */
    fun nearestRank(values: List<Long>, percentile: Int): Long? {
        require(percentile in 1..100); require(values.all { it >= 0 })
        if (values.isEmpty() || percentile == 95 && values.size < 20) return null
        val rank = (percentile.toLong() * values.size + 99) / 100
        return values.sorted()[(rank - 1).toInt()]
    }
}
