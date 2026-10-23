package fr.jarvis.llm.model

import android.net.Uri
import fr.jarvis.domain.ModelKind
import fr.jarvis.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class VerifiedModelCatalogTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun descriptor(bytes: ByteArray, license: String = "synthetic test only") = ModelDescriptor(
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) },
        bytes.size.toLong(), ModelKind.SMALL, "qwen3", "Q4_K_M", license, "public synthetic fixture", 0)
    private fun bytes(seed: Int) = ByteArray(2048) { (it + seed).toByte() }.apply { "GGUF".toByteArray().copyInto(this) }
    @Test fun twoModelsSelectionPersistDeduplicateAndDeselect() {
        val name = "catalog-selection.db"; context.deleteDatabase(name)
        val a = descriptor(bytes(1)); val b = descriptor(bytes(2))
        VerifiedModelCatalog(context, name).use { c -> c.record(a); c.record(b); c.record(a.copy(license = "must not overwrite")); c.select(a.sha256) }
        VerifiedModelCatalog(context, name).use { c ->
            assertEquals(2, c.list().size); assertEquals(a.sha256, c.selected())
            assertEquals(a.license, c.list().first { it.sha256 == a.sha256 }.license)
            c.select(b.sha256); assertEquals(b.sha256, c.selected())
            c.select(null); assertNull(c.selected())
            assertThrows(IllegalArgumentException::class.java) { c.select("missing") }
            c.select(a.sha256); c.removeReference(a.sha256); assertNull(c.selected()); assertEquals(listOf(b), c.list())
        }
    }
    @Test fun compatibilityDoesNotClaimNativeQualification() {
        val m = descriptor(bytes(3))
        assertEquals(ModelQualificationState.EXECUTABLE_UNQUALIFIED, ModelCompatibilityChecker.check(m, true).state)
        assertEquals(ModelQualificationState.INCOMPATIBLE, ModelCompatibilityChecker.check(m, false).state)
        assertEquals(ModelQualificationState.INCOMPATIBLE, ModelCompatibilityChecker.check(m.copy(architecture = "unknown"), true).state)
        assertThrows(IllegalArgumentException::class.java) { m.copy(sha256 = "../escape") }
    }
    @Test fun corruptIncompleteAndCancelledImportsPreservePreviousAndOriginal() {
        val name = "catalog-import.db"; context.deleteDatabase(name)
        VerifiedModelCatalog(context, name).use { c ->
            val importer = ModelImportCoordinator(context, c)
            org.robolectric.shadows.ShadowStatFs.registerStats(java.io.File(context.filesDir, "models/catalog-v1").path, 4096, 4000000, 3000000)
            val good = bytes(10); val a = descriptor(good); val uri = Uri.parse("content://synthetic/model")
            importer.file(a.sha256).delete()
            org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(good))
            importer.import(uri, a) { false }; c.select(a.sha256)
            assertArrayEquals(good, importer.file(a.sha256).readBytes())
            val b = descriptor(bytes(11)); importer.file(b.sha256).delete()
            org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(good))
            assertThrows(IllegalArgumentException::class.java) { importer.import(uri, b) { false } }
            org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes(11).copyOf(1000)))
            assertThrows(IllegalArgumentException::class.java) { importer.import(uri, b) { false } }
            org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes(11)))
            assertThrows(java.io.InterruptedIOException::class.java) { importer.import(uri, b) { true } }
            assertFalse(importer.file(b.sha256).exists()); assertEquals(a.sha256, c.selected()); assertEquals(1, c.list().size)
            assertArrayEquals(good, importer.file(a.sha256).readBytes())
            assertFalse(java.io.File(importer.file(b.sha256).path + ".part").exists())
            importer.deleteCopy(a.sha256); assertNull(c.selected()); assertTrue(c.list().isEmpty())
        }
    }
}
