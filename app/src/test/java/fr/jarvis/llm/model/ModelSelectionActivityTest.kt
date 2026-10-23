package fr.jarvis.llm.model

import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29,35])
class ModelSelectionActivityTest {
    @Test fun emptyCatalogueOpensWithoutNativeLibraryOrModelAndIsNotExported() {
        Robolectric.buildActivity(ModelSelectionActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val info = activity.packageManager.getActivityInfo(android.content.ComponentName(activity, ModelSelectionActivity::class.java), 0)
            assertFalse(info.exported)
            fun text(v: android.view.View): String = if (v is android.view.ViewGroup)
                (0 until v.childCount).joinToString { text(v.getChildAt(it)) } else (v as? TextView)?.text?.toString() ?: ""
            assertTrue(text(activity.window.decorView).contains("Aucun chargement automatique"))
        }
    }
}
