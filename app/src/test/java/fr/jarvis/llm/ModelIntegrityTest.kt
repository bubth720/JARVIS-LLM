package fr.jarvis.llm

import fr.jarvis.domain.ModelKind
import fr.jarvis.llm.catalogue.CatalogueStore
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ModelIntegrityTest {
    @Test fun tamperedOrCancelledVerificationNeverChangesCatalogueAndCanRecover() {
        val context = RuntimeEnvironment.getApplication()
        val models = ModelStore(context)
        val file = models.file(ModelKind.SMALL)
        // Synthetic non-GGUF bytes: this exercises SHA verification, NOT native loading.
        val original = ByteArray(2048) { (it % 127).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it.toInt() and 255) }
        file.writeBytes(original)
        val refs = context.getSharedPreferences("model_hashes", 0)
        refs.edit().putString(ModelKind.SMALL.name, sha).commit()
        CatalogueStore(context, "integrity-003c.db").use { catalogue ->
            fun export() = ByteArrayOutputStream().also { catalogue.export(it) }.toByteArray()
            val before = export()
            try {
                assertEquals(file.absolutePath, models.verify(ModelKind.SMALL) { false })
                file.writeBytes(original.copyOf().apply { this[100] = (this[100] + 1).toByte() })
                assertThrows(IllegalArgumentException::class.java) { models.verify(ModelKind.SMALL) { false } }
                assertArrayEquals(before, export())
                file.writeBytes(original)
                assertThrows(InterruptedIOException::class.java) { models.verify(ModelKind.SMALL) { true } }
                assertEquals(file.absolutePath, models.verify(ModelKind.SMALL) { false })
                assertEquals(sha, models.expectedDigest(ModelKind.SMALL))
                assertArrayEquals(before, export())
            } finally { file.delete(); refs.edit().remove(ModelKind.SMALL.name).commit() }
        }
    }
}
