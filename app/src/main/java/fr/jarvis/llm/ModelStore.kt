package fr.jarvis.llm

import android.content.Context
import android.net.Uri
import fr.jarvis.domain.*
import java.io.File
import java.io.InterruptedIOException
import java.security.MessageDigest

class ModelStore(private val context: Context) {
    private val root = File(context.filesDir, "models").apply { mkdirs() }
    private val refs = context.getSharedPreferences("model_hashes", Context.MODE_PRIVATE)
    init { root.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() } }
    fun file(kind: ModelKind) = File(root, kind.fileName)
    fun expectedDigest(kind: ModelKind): String? = refs.getString(kind.name, null)?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
    fun available(): Set<ModelKind> = ModelKind.entries.filter { file(it).isFile && refs.contains(it.name) }.toSet()
    fun import(uri: Uri, kind: ModelKind, digest: String, cancelled: () -> Boolean) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Document inaccessible")
        stream.use { VerifiedCopy.install(it, file(kind), digest.trim(), kind.maxFileBytes,
            { android.os.StatFs(root.path).availableBytes }, cancelled) }
        check(refs.edit().putString(kind.name, digest.trim().lowercase()).commit()) { "Référence d'intégrité non enregistrée" }
    }
    fun verify(kind: ModelKind, cancelled: () -> Boolean): String {
        val f = file(kind)
        require(f.length() in 1024..kind.maxFileBytes) { "Taille de modèle invalide" }
        val expected = refs.getString(kind.name, null) ?: error("Modèle non vérifié")
        val digest = MessageDigest.getInstance("SHA-256")
        f.inputStream().buffered().use { input ->
            val block = ByteArray(1024 * 1024)
            while (true) {
                if (cancelled()) throw InterruptedIOException("Vérification annulée")
                val n = input.read(block); if (n < 0) break
                digest.update(block, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(actual == expected) { "Le modèle a changé : réimporte une copie fiable" }
        return f.absolutePath
    }
}
