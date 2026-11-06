package fr.jarvis.llm.perf03b.lab

import fr.jarvis.domain.LanguageModel
import fr.jarvis.llm.engine.LlamaBackend
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Model-free lifecycle only. No authority/grant, model loading or generation exists in this app. */
internal class LabLifecycleController(private val factory: () -> LanguageModel = { LlamaBackend() }) {
    enum class Result { PASS, BUSY, ERROR, CLEANUP_FAILED, QUARANTINED }
    private val running = AtomicBoolean(false)
    private val quarantined = AtomicBoolean(false)
    @Volatile private var active: LanguageModel? = null

    suspend fun exercise(): Result {
        if (quarantined.get()) return Result.QUARANTINED
        if (!running.compareAndSet(false, true)) return Result.BUSY
        var backend: LanguageModel? = null
        var operationFailed = false
        var cleanupFailed = false
        var cancellation: CancellationException? = null
        try {
            // Recheck after admission so a concurrent failed test cannot authorize a new factory.
            if (quarantined.get()) return Result.QUARANTINED
            backend = factory().also { active = it }
            backend.prepareOperation()
            backend.requestStop()
        } catch (cancelled: CancellationException) {
            cancellation = cancelled
            operationFailed = true
            quarantined.set(true)
        } catch (_: Throwable) {
            operationFailed = true
            quarantined.set(true)
        } finally {
            val held = backend
            if (held != null) {
                // Activity cancellation never substitutes for the actual close return.
                withContext(NonCancellable) {
                    try {
                        held.close()
                        active = null
                    } catch (_: Throwable) {
                        cleanupFailed = true
                        quarantined.set(true)
                        // Keep the failed backend; never retry its cleanup or create a replacement.
                    }
                }
            }
            if (!currentCoroutineContext().isActive) quarantined.set(true)
            running.set(false)
        }
        cancellation?.let { throw it }
        if (!currentCoroutineContext().isActive) throw CancellationException("Laboratory owner cancelled")
        return when {
            cleanupFailed -> Result.CLEANUP_FAILED
            operationFailed || quarantined.get() -> Result.ERROR
            else -> Result.PASS
        }
    }

    /** Reaches only the existing backend's atomic native Stop. No cleanup receipt is inferred. */
    fun requestStop() {
        try { active?.requestStop() } catch (_: Throwable) { quarantined.set(true) }
    }
    fun hasActiveBackend() = active != null
    fun isQuarantined() = quarantined.get()
}
