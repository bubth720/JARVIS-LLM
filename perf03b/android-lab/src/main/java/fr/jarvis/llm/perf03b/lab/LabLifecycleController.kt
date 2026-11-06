package fr.jarvis.llm.perf03b.lab

import fr.jarvis.domain.LanguageModel
import fr.jarvis.llm.engine.LlamaBackend
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Process-wide default: Activity recreation or another controller cannot replace an unresolved engine. */
internal class LabProcessFence {
    internal val running = AtomicBoolean(false)
    internal val quarantined = AtomicBoolean(false)
    @Volatile internal var active: LanguageModel? = null

    companion object {
        internal val process = LabProcessFence()
    }
}

/** Model-free lifecycle only. An explicit fixture fence is injectable solely for isolated unit tests. */
internal class LabLifecycleController(
    private val factory: () -> LanguageModel = { LlamaBackend() },
    private val fence: LabProcessFence = LabProcessFence.process
) {
    enum class Result { PASS, BUSY, ERROR, CLEANUP_FAILED, QUARANTINED }

    suspend fun exercise(): Result {
        if (fence.quarantined.get()) return Result.QUARANTINED
        if (!fence.running.compareAndSet(false, true)) return Result.BUSY
        var backend: LanguageModel? = null
        var operationFailed = false
        var cleanupFailed = false
        var cancellation: CancellationException? = null
        try {
            // Recheck after admission so a concurrent failed test cannot authorize a new factory.
            if (fence.quarantined.get()) return Result.QUARANTINED
            backend = factory().also { fence.active = it }
            backend.prepareOperation()
            backend.requestStop()
        } catch (cancelled: CancellationException) {
            cancellation = cancelled
            operationFailed = true
            fence.quarantined.set(true)
        } catch (_: Throwable) {
            operationFailed = true
            fence.quarantined.set(true)
        } finally {
            val held = backend
            if (held != null) {
                // Activity cancellation never substitutes for the actual close return.
                withContext(NonCancellable) {
                    try {
                        held.close()
                        check(fence.active === held) { "Laboratory process ownership changed" }
                        fence.active = null
                    } catch (_: Throwable) {
                        cleanupFailed = true
                        fence.quarantined.set(true)
                        // Keep the failed backend; never retry its cleanup or create a replacement.
                    }
                }
            }
            if (!currentCoroutineContext().isActive) fence.quarantined.set(true)
            fence.running.set(false)
        }
        cancellation?.let { throw it }
        if (!currentCoroutineContext().isActive) throw CancellationException("Laboratory owner cancelled")
        return when {
            cleanupFailed -> Result.CLEANUP_FAILED
            operationFailed || fence.quarantined.get() -> Result.ERROR
            else -> Result.PASS
        }
    }

    /** Reaches only the existing backend's atomic native Stop. No cleanup receipt is inferred. */
    fun requestStop() {
        try { fence.active?.requestStop() } catch (_: Throwable) { fence.quarantined.set(true) }
    }
    fun hasActiveBackend() = fence.active != null
    fun isQuarantined() = fence.quarantined.get()
}
