package fr.jarvis.domain

import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest

/** The expected digest must be obtained independently from a trusted reference. */
object VerifiedCopy {
    fun install(input: InputStream, destination: File, expected: String, maxBytes: Long,
                usableBytes: () -> Long, cancelled: () -> Boolean) {
        require(expected.matches(Regex("[a-fA-F0-9]{64}"))) { "SHA-256 attendu requis" }
        val reserve = RuntimeConfig.GIB
        require(usableBytes() >= maxBytes + reserve) { "Espace libre insuffisant pour l'import" }
        val temp = File(destination.parentFile, destination.name + ".part")
        require(!temp.exists()) { "Un import est déjà en cours ou doit être nettoyé" }
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        val magic = ByteArray(4)
        try {
            temp.outputStream().buffered().use { out ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    if (cancelled()) throw InterruptedIOException("Import annulé")
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    for (i in 0 until minOf(n, (4 - count).coerceAtLeast(0).toInt())) magic[count.toInt() + i] = buffer[i]
                    count += n
                    require(count <= maxBytes) { "Modèle trop volumineux pour ce prototype" }
                    require(usableBytes() >= reserve + n) { "Réserve de stockage atteinte" }
                    digest.update(buffer, 0, n); out.write(buffer, 0, n)
                }
            }
            require(count >= 1024 && magic.contentEquals("GGUF".toByteArray())) { "Fichier GGUF invalide" }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(actual.equals(expected, true)) { "Empreinte SHA-256 incorrecte" }
            if (cancelled()) throw InterruptedIOException("Import annulé")
            java.nio.file.Files.move(temp.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
}
