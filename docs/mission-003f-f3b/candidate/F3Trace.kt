package fr.jarvis.llm.engine

/** PROPOSED INTERNAL SOURCE, compiled only by the isolated review tests in F3B.
 * Does not own/free resources. Default production backend has no tracer.
 * Events are not durable terminal receipts and cannot grant /2 admission.
 */
class F3Trace(val processEpoch: String, val operationKeyDigest: String) {
    enum class Call { CREATE, BEGIN, CANCEL, LOAD, GENERATE, MEASURE, ALLOCATIONS, PROBE, UNLOAD, DESTROY }
    enum class Task { LOAD, GENERATE, UNLOAD, CLOSE, INSPECT }
    enum class State { STOP_REQUESTED, RUNNING, DRAINED, UNLOADED, CLEANUP_FAILED, UNKNOWN }
    class Token internal constructor(val id: Long)
    data class Snapshot(val process: String, val keyDigest: String, val owner: String?, val worker: String,
        val revision: Long, val queued: Int, val tasks: Int, val imports: Int, val calls: Int,
        val callEndSequence: Long, val unloadSequence: Long, val destroyed: Boolean,
        val pointerFlags: List<Long>?, val cleanupFailed: Boolean, val uncertain: Boolean, val stop: Boolean)
    private val workerId = java.util.UUID.randomUUID().toString()
    private var owner: String? = null
    private var workerThread: Thread? = null
    private var next = 0L; private var revision = 0L; private var end = 0L; private var unload = 0L
    private var uncertain = false; private var cleanupFailed = false; private var stopped = false
    private var destroyed = false; private var created = false
    private var pointers: List<Long>? = null
    private val calls = mutableMapOf<Token, Call>()
    private val tasks = mutableMapOf<Token, Pair<Task, Boolean>>()
    private val imports = mutableSetOf<Token>()
    init {
        require(java.util.UUID.fromString(processEpoch).toString() == processEpoch)
        require(operationKeyDigest.matches(Regex("[a-f0-9]{64}")))
    }
    @Synchronized fun bindOwner(value: String) { check(owner == null && value.isNotBlank()); owner = value; revision++ }
    @Synchronized fun quarantine() { uncertain = true; revision++ }
    @Synchronized fun requireKey(value: String) { check(value == operationKeyDigest) { "STALE_OPERATION" } }
    @Synchronized fun queued(kind: Task): Token {
        check(tasks.size < 128); val t = Token(++next); tasks[t] = kind to false; revision++; return t
    }
    @Synchronized fun started(t: Token) {
        val entry = tasks[t] ?: error("STALE_TASK")
        check(!entry.second)
        val thread = Thread.currentThread()
        if (workerThread == null) workerThread = thread else if (workerThread !== thread) uncertain = true
        tasks[t] = entry.first to true; revision++
    }
    /** Called inside the synchronous worker body, never in the caller coroutine's finally. */
    @Synchronized fun taskReturned(t: Token) { check(tasks[t]?.second == true); tasks.remove(t); revision++ }
    @Synchronized fun dispatchAbandoned(t: Token) {
        // Cancel before worker entry cannot prove a task ran/returned. Retain ticket, quarantine.
        if (tasks.containsKey(t)) { uncertain = true; revision++ }
    }
    @Synchronized fun importEntered(): Token { check(imports.size < 128); val t=Token(++next); imports.add(t); pointers = null; revision++; return t }
    @Synchronized fun importReturned(t: Token) { check(imports.remove(t)); revision++ }
    @Synchronized fun entered(kind: Call): Token {
        check(calls.size < 128)
        val t=Token(++next); calls[t]=kind
        if (kind == Call.CANCEL) stopped = true
        // No mutation/probe may retain an old pointer observation across a failed call.
        pointers = null; revision++; return t
    }
    /** Only directly after synchronous NativeApi call returns or throws to Java. */
    @Synchronized fun returned(t: Token, success: Boolean, flags: LongArray? = null) {
        val kind=calls.remove(t) ?: error("STALE_NATIVE_CALL")
        end++; revision++
        if (!success) {
            pointers = null
            if (kind == Call.UNLOAD || kind == Call.DESTROY) cleanupFailed = true
            if (kind == Call.CREATE) uncertain = true
            return
        }
        when (kind) {
            Call.CREATE -> { created = true; destroyed = false }
            Call.BEGIN -> stopped = false
            Call.UNLOAD -> { unload++; cleanupFailed = false }
            Call.DESTROY -> { unload++; destroyed = true; cleanupFailed = false }
            Call.PROBE -> {
                if (flags == null || flags.size != 4 || flags[0] != 1L || flags[1] != 1L ||
                    flags[2] !in 0L..1L || flags[3] !in 0L..1L) pointers = null
                else pointers = flags.toList()
            }
            else -> Unit
        }
    }
    @Synchronized fun snapshot(): Snapshot {
        val work=tasks.values.filter { it.first != Task.INSPECT }
        return Snapshot(processEpoch, operationKeyDigest, owner, workerId, revision,
            work.count { !it.second }, work.count { it.second }, imports.size, calls.size, end, unload,
            destroyed, pointers?.toList(), cleanupFailed,
            uncertain || !created || workerThread == null || owner == null, stopped)
    }
    companion object {
        /** Consistency classifier only. Caller must additionally verify frozen census and exact lease identities.
         * A fresh empty tracer, PID, death, elapsed time or resource count alone never proves UNLOADED.
         */
        fun classify(s: Snapshot, coherent: Boolean, modelLeases: Int?, heavyLeases: Int?): State {
            if (!coherent || s.uncertain || modelLeases == null || heavyLeases == null || modelLeases < 0 || heavyLeases < 0)
                return State.UNKNOWN
            if (s.cleanupFailed) return State.CLEANUP_FAILED
            if (s.calls != 0 || s.tasks != 0 || s.imports != 0 || s.queued != 0)
                return if (s.stop) State.STOP_REQUESTED else State.RUNNING
            if (s.callEndSequence == 0L || (!s.destroyed && s.pointerFlags == null)) return State.UNKNOWN
            if (s.unloadSequence > 0 && modelLeases == 0 && heavyLeases == 0 &&
                (s.destroyed || s.pointerFlags?.let { it[2] == 0L && it[3] == 0L } == true)) return State.UNLOADED
            return State.DRAINED
        }
    }
}
