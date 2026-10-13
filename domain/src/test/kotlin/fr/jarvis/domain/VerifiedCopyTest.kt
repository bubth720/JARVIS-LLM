package fr.jarvis.domain

import java.io.File
import java.io.InterruptedIOException
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class VerifiedCopyTest {
    private val data = ByteArray(2048) { 42 }.also { "GGUF".toByteArray().copyInto(it) }
    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun test(body: (File) -> Unit) {
        val root = Files.createTempDirectory("jarvis-import").toFile()
        try { body(File(root, "model.gguf")) } finally { root.deleteRecursively() }
    }
    private fun install(file: File, bytes: ByteArray = data, digest: String = hash(bytes), max: Long = 4096,
                        space: () -> Long = { 5L * RuntimeConfig.GIB }, stop: () -> Boolean = { false }) {
        bytes.inputStream().use { VerifiedCopy.install(it, file, digest, max, space, stop) }
    }
    @Test fun correctImportAtomicallyReplacesOldModel() = test { f ->
        f.writeText("old"); install(f); assertArrayEquals(data, f.readBytes()); assertFalse(File(f.path + ".part").exists())
    }
    @Test fun wrongHashPreservesOldModelAndRemovesPartial() = test { f ->
        f.writeText("old")
        assertThrows(IllegalArgumentException::class.java) { install(f, digest = "0".repeat(64)) }
        assertEquals("old", f.readText()); assertFalse(File(f.path + ".part").exists())
    }
    @Test fun malformedGgufRejectedEvenWithMatchingHash() = test { f ->
        assertThrows(IllegalArgumentException::class.java) { install(f, bytes = ByteArray(2048)) }; assertFalse(f.exists())
    }
    @Test fun interruptedImportCleansPartial() = test { f ->
        assertThrows(InterruptedIOException::class.java) { install(f, stop = { true }) }; assertFalse(f.exists()); assertFalse(File(f.path + ".part").exists())
    }
    @Test fun oversizedAndInsufficientSpaceRejected() = test { f ->
        assertThrows(IllegalArgumentException::class.java) { install(f, max = 1024) }
        assertThrows(IllegalArgumentException::class.java) { install(f, space = { 100 }) }
        assertFalse(f.exists())
    }
    @Test fun storagePressureDuringCopyPreservesExistingModel() = test { f ->
        f.writeText("old"); var reads = 0
        assertThrows(IllegalArgumentException::class.java) { install(f, space = { if (reads++ == 0) 5L * RuntimeConfig.GIB else 0 }) }
        assertEquals("old", f.readText()); assertFalse(File(f.path + ".part").exists())
    }
    @Test fun cancellationImmediatelyBeforeActivationPreservesModel() = test { f ->
        f.writeText("old"); var calls = 0
        assertThrows(InterruptedIOException::class.java) { install(f, stop = { ++calls >= 3 }) }
        assertEquals("old", f.readText())
    }
    @Test fun invalidReferenceNeverCreatesFile() = test { f ->
        assertThrows(IllegalArgumentException::class.java) { install(f, digest = "unknown") }; assertFalse(f.exists())
    }
}
