package fr.jarvis.llm.perf03b.lab

import fr.jarvis.domain.GenerationRequest
import fr.jarvis.domain.LanguageModel
import fr.jarvis.domain.RuntimeConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Injected unit tests do not attest JNI execution, a loaded model or Android rendering. */
class LabLifecycleControllerTest {
    private class Backend : LanguageModel {
        var prepare = 0
        var stop = 0
        var close = 0
        var failPrepare = false
        var failStop = false
        var failClose = false
        var closeEntered: CompletableDeferred<Unit>? = null
        var closeRelease: CompletableDeferred<Unit>? = null
        override fun prepareOperation() { prepare++; if (failPrepare) error("injected") }
        override fun requestStop() { stop++; if (failStop) error("injected") }
        override suspend fun close() {
            close++
            closeEntered?.complete(Unit)
            closeRelease?.await()
            if (failClose) error("injected")
        }
        override suspend fun load(path: String, config: RuntimeConfig) { error("Model loading is forbidden in this lab") }
        override suspend fun generate(request: GenerationRequest): String = error("Generation is forbidden in this lab")
        override suspend fun unload() { error("No model is loaded in this lab") }
    }

    @Test fun lifecycleNeverLoadsOrGeneratesAndClosesExactlyOnce() = runBlocking {
        val backend = Backend()
        var factories = 0
        val controller = LabLifecycleController { factories++; backend }
        assertEquals(LabLifecycleController.Result.PASS, controller.exercise())
        assertEquals(1, factories)
        assertEquals(1, backend.prepare); assertEquals(1, backend.stop); assertEquals(1, backend.close)
        assertFalse(controller.hasActiveBackend()); assertFalse(controller.isQuarantined())
    }

    @Test fun anExplicitNewTestCanCreateOnlyAfterConfirmedPriorClose() = runBlocking {
        val backends = mutableListOf<Backend>()
        val controller = LabLifecycleController { Backend().also { backends += it } }
        repeat(2) { assertEquals(LabLifecycleController.Result.PASS, controller.exercise()) }
        assertEquals(2, backends.size); assertTrue(backends.all { it.close == 1 })
    }

    @Test fun prepareFailureStillClosesThenPermanentlyBlocksReplacement() = runBlocking {
        val backend = Backend().apply { failPrepare = true }
        var factories = 0
        val controller = LabLifecycleController { factories++; backend }
        assertEquals(LabLifecycleController.Result.ERROR, controller.exercise())
        assertEquals(1, backend.close); assertFalse(controller.hasActiveBackend())
        assertEquals(LabLifecycleController.Result.QUARANTINED, controller.exercise())
        assertEquals(1, factories)
    }

    @Test fun stopFailureStillClosesAndDoesNotClaimSuccess() = runBlocking {
        val backend = Backend().apply { failStop = true }
        val controller = LabLifecycleController { backend }
        assertEquals(LabLifecycleController.Result.ERROR, controller.exercise())
        assertEquals(1, backend.close); assertTrue(controller.isQuarantined())
    }

    @Test fun failedCloseRetainsBackendAndNeverRetriesCleanupOrFactory() = runBlocking {
        val backend = Backend().apply { failClose = true }
        var factories = 0
        val controller = LabLifecycleController { factories++; backend }
        assertEquals(LabLifecycleController.Result.CLEANUP_FAILED, controller.exercise())
        assertTrue(controller.hasActiveBackend()); assertTrue(controller.isQuarantined())
        assertEquals(LabLifecycleController.Result.QUARANTINED, controller.exercise())
        assertEquals(1, backend.close); assertEquals(1, factories)
    }

    @Test fun concurrentExerciseCannotCreateAnotherEngineWhileCloseIsPending() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val backend = Backend().apply { closeEntered = entered; closeRelease = release }
        var factories = 0
        val controller = LabLifecycleController { factories++; backend }
        val first = async(start = CoroutineStart.UNDISPATCHED) { controller.exercise() }
        entered.await()
        assertTrue(controller.hasActiveBackend())
        assertEquals(LabLifecycleController.Result.BUSY, controller.exercise())
        assertEquals(1, factories); assertEquals(1, backend.close)
        release.complete(Unit)
        assertEquals(LabLifecycleController.Result.PASS, first.await())
        assertFalse(controller.hasActiveBackend())
    }

    @Test fun ownerCancellationDoesNotSubstituteForActualCloseReturn() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val backend = Backend().apply { closeEntered = entered; closeRelease = release }
        val controller = LabLifecycleController { backend }
        val first = launch(start = CoroutineStart.UNDISPATCHED) { controller.exercise() }
        entered.await(); first.cancel()
        assertTrue(controller.hasActiveBackend()); assertFalse(first.isCompleted)
        release.complete(Unit); first.join()
        assertFalse(controller.hasActiveBackend()); assertTrue(controller.isQuarantined())
        assertEquals(1, backend.close)
    }

    @Test fun factoryFailureNeverStartsAnOperationOrCreatesReplacement() = runBlocking {
        var attempts = 0
        val controller = LabLifecycleController { attempts++; error("injected") }
        assertEquals(LabLifecycleController.Result.ERROR, controller.exercise())
        assertEquals(LabLifecycleController.Result.QUARANTINED, controller.exercise())
        assertEquals(1, attempts); assertFalse(controller.hasActiveBackend())
    }
}
