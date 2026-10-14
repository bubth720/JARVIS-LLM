package fr.jarvis.llm

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import fr.jarvis.domain.*
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** Simulated lifecycle only: no JNI, GGUF, device measurement or native timing claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ResourceLifecycleTest {
    private class FakeBackend : LanguageModel {
        val release = CompletableDeferred<Unit>()
        var unloads = 0
        var loads = 0
        var generations = 0
        var stops = 0
        override fun prepareOperation() {}
        override suspend fun load(path: String, config: RuntimeConfig) { loads++ }
        override suspend fun generate(request: GenerationRequest): String { generations++; return "fake" }
        override fun requestStop() { stops++ }
        override suspend fun unload() { unloads++; release.await() }
        override suspend fun close() {}
    }
    @Test fun captureIsPassiveStopsInBackgroundAndExportsNoConversation() {
        val backend = FakeBackend()
        val vm = ChatViewModel(RuntimeEnvironment.getApplication(), backend)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            vm.foreground(true)
            vm.startCapture()
            assertTrue(vm.ui.value.capturing)
            val report = JSONObject(vm.diagnosticJson())
            assertFalse(report.getBoolean("policyChanged"))
            assertTrue(report.getBoolean("requiresHumanReviewBeforeNewBudget"))
            assertTrue(report.getJSONArray("frames").length() > 0)
            assertFalse(report.has("messages"))
            assertFalse(report.has("prompt"))
            vm.foreground(false)
            assertFalse(vm.ui.value.capturing)
            assertEquals("arrière-plan", JSONObject(vm.diagnosticJson()).getString("stopReason"))
            assertEquals(0, backend.loads)
            assertEquals(0, backend.generations)
        } finally { backend.release.complete(Unit); store.clear(); shadowOf(Looper.getMainLooper()).idle() }
    }
    @Test fun queuedUnloadKeepsUiBusyAndRejectsNewWorkUntilActualCompletion() {
        val backend = FakeBackend()
        val vm = ChatViewModel(RuntimeEnvironment.getApplication(), backend)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            vm.foreground(true)
            vm.foreground(false)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, backend.unloads)
            vm.foreground(true)
            assertTrue(vm.ui.value.busy)
            vm.send("Explique les aurores boréales")
            assertTrue(vm.ui.value.messages.isEmpty())
            vm.startCapture()
            assertFalse(vm.ui.value.capturing)
            backend.release.complete(Unit)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(vm.ui.value.busy)
            assertEquals(0, backend.loads)
        } finally { backend.release.complete(Unit); store.clear(); shadowOf(Looper.getMainLooper()).idle() }
    }
    @Test fun backgroundWithoutResidentModelHasNoPeriodicPolling() {
        val backend = FakeBackend().apply { release.complete(Unit) }
        val vm = ChatViewModel(RuntimeEnvironment.getApplication(), backend)
        val store = ViewModelStore().apply { put("vm", vm) }
        try {
            vm.foreground(false)
            shadowOf(Looper.getMainLooper()).idle()
            val observations = vm.periodicObservationCount
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45))
            assertEquals(observations, vm.periodicObservationCount)
            assertFalse(vm.ui.value.busy)
            assertEquals(0, backend.generations)
        } finally { store.clear(); shadowOf(Looper.getMainLooper()).idle() }
    }
}
