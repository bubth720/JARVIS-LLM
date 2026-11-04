package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.perf.*

/** Test-only decorator, same delegate/worker/handle. No factory, lease, new native entry point or scheduler. */
internal class MeasuredNativeApi(private val delegate: NativeApi, private val currentTrace: () -> LatencyTrace?) : NativeApi by delegate {
    constructor(delegate: NativeApi, trace: LatencyTrace) : this(delegate, { trace })
    override fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean) {
        val trace = currentTrace()
        trace?.record(trace.key, Stage.MODEL_LOAD_ENTER)
        try { delegate.load(handle, path, context, threads, small) }
        finally { trace?.record(trace.key, Stage.MODEL_LOAD_RETURN) }
    }
    private inline fun generated(call: () -> ByteArray): ByteArray {
        val trace = currentTrace() // one immutable binding until this actual JNI call returns
        trace?.record(trace.key, Stage.NATIVE_ENTER)
        // A returned ByteArray is NOT a native-first-token callback. Never split it to fake streaming.
        try { return call() } finally { trace?.record(trace.key, Stage.NATIVE_RETURN) }
    }
    override fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int) =
        generated { delegate.generate(handle, roles, texts, maxTokens) }
    override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray) =
        generated { delegate.generateP2(handle, roles, texts, maxTokens, system) }
    override fun generateFixtureP2(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int) =
        generated { delegate.generateFixtureP2(handle, system, question, maxTokens, contextLimit) }
}

internal class MeasuredBackend(private val delegate: LanguageModel, private val trace: LatencyTrace) : LanguageModel by delegate {
    override suspend fun load(path: String, config: RuntimeConfig) {
        trace.record(trace.key, Stage.LOAD_REQUEST)
        var failure: Failure? = null
        try { delegate.load(path, config) }
        catch (error: Exception) { failure = Failure.BACKEND_ERROR; throw error }
        finally { trace.record(trace.key, Stage.LOAD_RETURN); failure?.let { trace.failed(trace.key, it) } }
    }
    override suspend fun generate(request: GenerationRequest): String {
        trace.record(trace.key, Stage.ENGINE_ENTER)
        var failure: Failure? = null
        try { return delegate.generate(request) }
        catch (cancel: kotlinx.coroutines.CancellationException) { failure = Failure.CANCELLED; throw cancel }
        catch (error: Exception) { failure = Failure.BACKEND_ERROR; throw error }
        finally { trace.record(trace.key, Stage.BACKEND_RETURN); failure?.let { trace.failed(trace.key, it) } }
    }
    override fun requestStop() { trace.record(trace.key, Stage.STOP_REQUESTED); delegate.requestStop() }
}

/** Android clock is a distinct scope. Never subtract Linux/JVM nanoTime from Android timestamps. */
internal object AndroidMonotonicClock : MonotonicClock {
    override fun nanos() = android.os.SystemClock.elapsedRealtimeNanos()
}

fun interface FrameCommitPort { fun nextCommit(callback: () -> Unit): Boolean }
/** Explicit test harness hook: caller supplies ONLY the already-generated nonempty model text's presence.
 * This never marks a state-flow update or a catalogue/ACK as the first model frame.
 */
internal class FirstRenderProbe(private val trace: LatencyTrace, private val frames: FrameCommitPort) {
    private var armed = false
    @Synchronized fun textSet(key: TraceKey, nonemptyRealOutput: Boolean): Disposition {
        if (key != trace.key) return Disposition.STALE
        if (!nonemptyRealOutput) return Disposition.REJECTED
        if (armed) return Disposition.DUPLICATE
        val accepted = trace.record(key, Stage.UI_TEXT_SET)
        if (accepted != Disposition.ACCEPTED) return accepted
        armed = true
        return if (frames.nextCommit { trace.record(key, Stage.UI_FIRST_RENDER) }) accepted else Disposition.UNSUPPORTED
    }
}

/** API29+ swapchain commit observation, not a photon/display sensor. Unsupported software views stay missing. */
internal class AndroidFrameCommit(private val view: android.view.View) : FrameCommitPort {
    override fun nextCommit(callback: () -> Unit): Boolean {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return false
        if (!view.isAttachedToWindow || !view.isShown || !view.isHardwareAccelerated) return false
        view.viewTreeObserver.registerFrameCommitCallback(callback)
        view.postInvalidateOnAnimation()
        return true
    }
}
