package fr.jarvis.llm.model

import android.content.Context
import android.net.Uri
import android.os.StatFs
import fr.jarvis.domain.VerifiedCopy
import fr.jarvis.domain.model.ModelDescriptor
import fr.jarvis.domain.resources.Module
import fr.jarvis.llm.ProcessResources
import java.io.File

/** Uses the existing broker and verified-copy algorithm; owns no engine, native worker or model handle. */
class ModelImportCoordinator(private val context: Context, private val catalog: VerifiedModelCatalog) {
    // The existing store remains the owner of its historical filenames; catalogue copies use a separate subtree.
    private val root = File(context.filesDir, "models/catalog-v1").apply { mkdirs() }
    fun file(sha: String): File {
        require(sha.matches(Regex("[a-f0-9]{64}")))
        return File(root, "$sha.gguf")
    }
    fun import(uri: Uri, descriptor: ModelDescriptor, cancelled: () -> Boolean) = synchronized(lock) {
        ProcessResources.requireHeavy("model-catalog-import", Module.MODEL_IMPORT, "import catalogue GGUF", 128L * 1024 * 1024).use {
            val target = file(descriptor.sha256)
            val previous = catalog.list().firstOrNull { it.sha256 == descriptor.sha256 }
            if (previous != null && target.isFile) {
                require(previous.sizeBytes == descriptor.sizeBytes) { "Taille incohérente pour cette identité" }
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                target.inputStream().buffered().use { input ->
                    val block = ByteArray(1024 * 1024)
                    while (true) {
                        if (cancelled()) throw java.io.InterruptedIOException("Vérification annulée")
                        val n = input.read(block); if (n < 0) break
                        digest.update(block, 0, n)
                    }
                }
                require(target.length() == descriptor.sizeBytes && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == descriptor.sha256) { "Copie modifiée ; conservation pour inspection" }
                return@synchronized
            }
            require(!target.exists()) { "Copie orpheline présente ; aucun écrasement automatique" }
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Document local inaccessible" }
                // Reject declared-length mismatch before installing, without trusting SAF metadata.
                val counted = object : java.io.FilterInputStream(input) {
                    var count = 0L
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0) { count += n; require(count <= descriptor.sizeBytes) { "Taille réelle supérieure à la déclaration" } }
                        if (n < 0) require(count == descriptor.sizeBytes) { "Copie incomplète" }
                        return n
                    }
                }
                VerifiedCopy.install(counted, target, descriptor.sha256, descriptor.kind.maxFileBytes,
                    { StatFs(root.path).availableBytes }, cancelled)
            }
            // If SQLite fails, keep the verified orphan for inspection; never delete an original.
            catalog.record(descriptor)
        }
    }
    /** Explicit user deletion of this catalogue copy only; historical ModelStore files are never removed. */
    fun deleteCopy(sha: String) = synchronized(lock) {
        ProcessResources.requireHeavy("model-catalog-delete", Module.MODEL_IMPORT, "suppression volontaire catalogue").use {
            val target = file(sha)
            check(!target.exists() || target.delete()) { "Suppression impossible ; référence conservée" }
            catalog.removeReference(sha)
        }
    }
    companion object { private val lock = Any() }
}
