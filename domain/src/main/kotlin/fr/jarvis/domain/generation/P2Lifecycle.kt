package fr.jarvis.domain.generation

import fr.jarvis.domain.*
import fr.jarvis.domain.resources.*

enum class P2State { IDLE, PREPARED, LOADING, READY, TOKENIZING, PROMPT_READY, RUNNING, STOP_REQUESTED, NATIVE_DONE, UNLOADING, UNLOADED, CLEANUP_FAILED, UNKNOWN, CLOSED }
data class P2Snapshot(val state: P2State, val key: OperationKey?, val stopRequested: Boolean,
    val nativeCallActive: Boolean, val nativeEverStarted: Boolean, val effectiveEnd: Boolean, val unloadConfirmed: Boolean,
    val heavyHeld: Boolean, val modelHeld: Boolean)
/** Opt-in contract model, NOT wired to JNI/UI. Callbacks below require real backend evidence in a future adapter. */
class P2Lifecycle(private val broker: ResourceCoordinator, private val owner: String, private val epoch: String, private val clockMillis: () -> Long) {
    init { uuid(epoch) }
    private var state = P2State.IDLE
    private var current: OperationKey? = null
    private var lastGeneration = 0L
    private val seenOperations = mutableSetOf<String>()
    private var activeRequest: P2Request? = null
    private var everStarted = false
    private var stopped = false
    private var stopCause: P2Error? = null
    private var deadline = 0L
    private var callActive = false
    private var end = false
    private var unloaded = false
    private var heavy: ResourceLease? = null
    private var model: ResourceLease? = null
    private var pending: P2Result? = null
    private var consumed = false
    private var config: RuntimeConfig? = null
    @Synchronized fun snapshot() = P2Snapshot(state, current, stopped, callActive, everStarted, end, unloaded, heavy != null, model != null)
    private fun matches(key: OperationKey) = current == key
    private fun requireKey(key: OperationKey) { checkP2(matches(key), P2Error.STALE_OPERATION) }
    private fun acquire(request: ResourceRequest) = when(val result = broker.acquire(request)) {
        is ResourceGrant.Granted -> result.lease
        is ResourceGrant.Refused -> throw P2Exception(P2Error.BUSY)
    }
    @Synchronized fun prepare(request: P2Request, admitted: SafetyDecision, authorization: CoreAuthorizationGate = CoreAuthorizationGate { false }) {
        val key = request.key
        checkP2(state in setOf(P2State.IDLE, P2State.UNLOADED), if(state == P2State.CLEANUP_FAILED) P2Error.CLEANUP_FAILED else P2Error.BUSY)
        checkP2(authorization.permits(request), P2Error.UNAUTHORIZED)
        val admittedConfig = admitted.config ?: throw P2Exception(P2Error.INSUFFICIENT_RESOURCES)
        checkP2(admittedConfig.model == request.model, P2Error.MODEL_MISMATCH)
        try { GenerationRequest(listOf(Message("user", request.question)), admittedConfig) }
        catch (_: IllegalArgumentException) { throw P2Exception(P2Error.INVALID_LIMIT) }
        checkP2(key.epoch == epoch && key.generation > lastGeneration && key.operationId !in seenOperations, P2Error.STALE_OPERATION)
        checkP2(seenOperations.size < 256, P2Error.BUSY)
        val now = clockMillis()
        checkP2(now >= 0 && now <= Long.MAX_VALUE - request.limits.remainingMillis, P2Error.INVALID_LIMIT)
        heavy = acquire(ResourceRequest(owner, Module.LLM, ResourceClass.HEAVY, "P2 contract operation", admittedConfig.memoryBudget))
        deadline = now + request.limits.remainingMillis
        current = key; lastGeneration = key.generation; config = admittedConfig
        activeRequest = request; seenOperations.add(key.operationId); everStarted = false
        stopped = false; stopCause = null; callActive = false; end = false; unloaded = false; pending = null; consumed = false
        state = P2State.PREPARED
    }
    @Synchronized fun beginLoad(key: OperationKey) {
        requireKey(key); checkP2(state == P2State.PREPARED && !stopped, P2Error.BUSY)
        checkDeadline(key)
        val c = checkNotNull(config)
        model = acquire(ResourceRequest(owner, Module.LLM, ResourceClass.MODEL, "P2 possible residency", c.memoryBudget, c.model.name))
        everStarted = true; callActive = true; state = P2State.LOADING
    }
    @Synchronized fun modelReady(key: OperationKey): Boolean {
        if (!matches(key)) return false
        checkP2(callActive && state in setOf(P2State.LOADING, P2State.STOP_REQUESTED), P2Error.BUSY)
        callActive = false
        if (stopped) { end = true; pending = P2Result.Failure(key, stopCause ?: P2Error.CANCELLED); state = P2State.NATIVE_DONE }
        else state = P2State.READY
        return true
    }
    @Synchronized fun beginPromptPreparation(key: OperationKey) {
        requireKey(key); checkP2(state == P2State.READY && !stopped, P2Error.BUSY)
        checkDeadline(key); callActive = true; state = P2State.TOKENIZING
    }
    @Synchronized fun promptPrepared(key: OperationKey, prepared: PreparedP2): Boolean {
        if (!matches(key)) return false
        checkP2(callActive && state in setOf(P2State.TOKENIZING, P2State.STOP_REQUESTED), P2Error.BUSY)
        val r = checkNotNull(activeRequest); val c = checkNotNull(config)
        checkP2(prepared.request === r && prepared.config == c.copy(maxTokens = minOf(c.maxTokens, r.limits.outputTokens)) &&
            prepared.effectiveContextLimit == minOf(c.context, r.limits.contextTokens), P2Error.STALE_OPERATION)
        callActive = false
        if (!stopped && clockMillis() >= deadline) { stopped = true; stopCause = P2Error.EXPIRED }
        if (stopped) { end = true; pending = P2Result.Failure(key, stopCause ?: P2Error.CANCELLED); state = P2State.NATIVE_DONE }
        else state = P2State.PROMPT_READY
        return true
    }
    @Synchronized fun beginGeneration(key: OperationKey) {
        requireKey(key); checkP2(state == P2State.PROMPT_READY && !stopped, P2Error.BUSY)
        checkDeadline(key); callActive = true; state = P2State.RUNNING
    }
    /** An actual call has not started (or previous load returned): reject without pretending active native work ended. */
    @Synchronized fun rejectBeforeNativeCall(key: OperationKey, code: P2Error) {
        requireKey(key); checkP2(!callActive && state in setOf(P2State.PREPARED, P2State.READY, P2State.PROMPT_READY), P2Error.BUSY)
        pending = P2Result.Failure(key, code); end = true; state = P2State.NATIVE_DONE
    }
    /** Return true only for the first stop that needs signalling an actual native call. Never releases a lease. */
    @Synchronized fun requestStop(key: OperationKey): Boolean = stopFor(key, P2Error.CANCELLED)
    @Synchronized fun pollDeadline(key: OperationKey): Boolean = if (matches(key) && clockMillis() >= deadline) stopFor(key, P2Error.EXPIRED) else false
    private fun checkDeadline(key: OperationKey) {
        if (clockMillis() >= deadline) { stopFor(key, P2Error.EXPIRED); throw P2Exception(P2Error.EXPIRED) }
    }
    private fun stopFor(key: OperationKey, cause: P2Error): Boolean {
        if (!matches(key) || stopped || state in setOf(P2State.UNLOADED, P2State.CLOSED, P2State.CLEANUP_FAILED)) return false
        stopped = true; stopCause = cause; pending = P2Result.Failure(key, cause)
        if (state == P2State.UNKNOWN) return callActive // retain UNKNOWN until evidence arrives
        if (callActive) { state = P2State.STOP_REQUESTED; return true }
        if (state !in setOf(P2State.UNLOADING, P2State.NATIVE_DONE)) { end = true; state = P2State.NATIVE_DONE }
        return false
    }
    /** A stale/late callback can never finish or free a newer operation. No result accepted after cancellation. */
    @Synchronized fun nativeReturned(key: OperationKey, text: String? = null, failure: P2Error? = null): Boolean {
        if (!matches(key) || !callActive) return false
        checkP2(state in setOf(P2State.LOADING, P2State.TOKENIZING, P2State.RUNNING, P2State.STOP_REQUESTED, P2State.UNKNOWN), P2Error.BUSY)
        checkP2(text == null || failure == null, P2Error.INVALID_INPUT)
        if (!stopped && clockMillis() >= deadline) { stopped = true; stopCause = P2Error.EXPIRED }
        val result = if(stopped) P2Result.Failure(key, stopCause ?: P2Error.CANCELLED)
            else if(failure != null) P2Result.Failure(key, failure)
            else if(text != null && state == P2State.RUNNING) try { P2Result.Text(key, text) } catch (e: P2Exception) { P2Result.Failure(key, e.code) }
            else P2Result.Failure(key, P2Error.NATIVE_FAILURE)
        callActive = false; end = true; pending = result; state = P2State.NATIVE_DONE
        return true
    }
    @Synchronized fun beginCleanup(key: OperationKey) {
        requireKey(key)
        checkP2(end && !callActive && state in setOf(P2State.NATIVE_DONE, P2State.CLEANUP_FAILED), P2Error.BUSY)
        state = P2State.UNLOADING
    }
    @Synchronized fun cleanupFailed(key: OperationKey) {
        requireKey(key); checkP2(state == P2State.UNLOADING, P2Error.BUSY)
        pending = P2Result.Failure(key, P2Error.CLEANUP_FAILED); state = P2State.CLEANUP_FAILED
    }
    /** Invoke only AFTER unload/destroy actually returned successfully, never at timeout or merely isLoaded=false. */
    @Synchronized fun confirmUnloaded(key: OperationKey) {
        requireKey(key); checkP2(state == P2State.UNLOADING && end && !callActive, P2Error.BUSY)
        model?.close(); model = null; heavy?.close(); heavy = null
        activeRequest = null; unloaded = true; state = P2State.UNLOADED
    }
    @Synchronized fun takeResult(key: OperationKey): P2Result {
        requireKey(key); checkP2(state == P2State.UNLOADED && unloaded && end && !consumed, P2Error.BUSY)
        val saved = pending
        val result = if (saved is P2Result.Failure && saved.code == P2Error.CLEANUP_FAILED) saved
            else if (clockMillis() >= deadline) P2Result.Failure(key, P2Error.EXPIRED)
            else pending ?: P2Result.Failure(key, if(stopped) (stopCause ?: P2Error.CANCELLED) else P2Error.NATIVE_FAILURE)
        pending = null; consumed = true; return result
    }
    @Synchronized fun markUnknown(key: OperationKey) {
        requireKey(key); checkP2(state !in setOf(P2State.UNLOADED, P2State.CLOSED, P2State.CLEANUP_FAILED), P2Error.BUSY)
        pending = P2Result.Failure(key, P2Error.UNKNOWN_STATE); state = P2State.UNKNOWN
    }
    @Synchronized fun close() {
        checkP2(state in setOf(P2State.IDLE, P2State.UNLOADED, P2State.CLOSED) && heavy == null && model == null, P2Error.BUSY)
        activeRequest = null; pending = null; consumed = true; state = P2State.CLOSED
    }
}
