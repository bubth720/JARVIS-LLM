package fr.jarvis.llm

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import android.os.Looper

/** Android JVM simulation only; never a hardware or native inference benchmark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class OfflineStartupTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun startupShowsDiagnosticWithoutLoadingNativeModel() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val texts = views(controller.get().window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.any { "JARVIS LLM" in it })
            assertTrue(texts.any { "aucun" in it && "Modèles importés" in it })
            assertTrue(texts.any { "GPU/NPU non qualifiés" in it })
            assertTrue(texts.any { "pic échantillonné" in it && "Budget estimé" in it && "Marge admission" in it })
            assertFalse(views(controller.get().window.decorView).filterIsInstance<Button>().single { it.text == "Arrêter" }.isEnabled)
            // No native library is available on this x86 JVM: startup passing also proves no eager JNI load.
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun sendWithoutModelIsRefusedAndLeavesUiAvailable() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val all = views(controller.get().window.decorView)
            all.filterIsInstance<EditText>().single().setText("Bonjour")
            val send = all.filterIsInstance<Button>().single { it.text == "Envoyer" }
            send.performClick(); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(send.isEnabled)
            assertFalse(all.filterIsInstance<Button>().single { it.text == "Arrêter" }.isEnabled)
            assertTrue(all.filterIsInstance<TextView>().any { "Modèles importés : aucun" in it.text.toString() })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun passiveDiagnosticSurvivesBackgroundWithoutImportOrNativeWork() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().start().resume().visible()
            shadowOf(Looper.getMainLooper()).idle()
            val all = views(controller.get().window.decorView)
            assertTrue(all.filterIsInstance<TextView>().any { "Modèles importés : aucun" in it.text.toString() })
            assertFalse(all.filterIsInstance<Button>().single { it.text == "Arrêter" }.isEnabled)
            val app = controller.get().application
            assertFalse(java.io.File(app.filesDir, "models").listFiles().orEmpty().any { it.extension == "gguf" })
        } finally { controller.pause().stop().destroy() }
    }
}
