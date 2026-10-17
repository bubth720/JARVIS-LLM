package fr.jarvis.fixture.llm.f3

import fr.jarvis.fixture.llm.DirectorySync
import fr.jarvis.fixture.llm.AndroidDirectorySync
import fr.jarvis.fixture.p2.RecoveryCommit
import fr.jarvis.fixture.p2.Wire
import java.io.File
import java.io.FileOutputStream

/** Isolated bounded dry-run review log, NOT a /2 terminal receipt.
 * Never read by recovery, never grants native admission. No prompts, paths or exception text.
 * Missing directory is UNKNOWN, explicit lab initialization only; no migration/repair/reset.
 * Future real terminal receipt requires a separately reviewed owner/worker instrumentation.
 */
internal class ReviewJournal(private val dir: File, private val sync: DirectorySync = AndroidDirectorySync(),
                             private val checkpoint: (String) -> Unit = {}) {
    private val fields = setOf("schema", "sequence", "previous", "process", "key_sha256", "state", "reason")
    private var blocked = false
    private fun hash(m: Map<String, String>) = Wire.hash("jarvis.f3a.review-only\u0000".toByteArray() + RecoveryCommit.canonical(m))
    private fun write(file: File, bytes: ByteArray) {
        check(file.createNewFile()) { "JOURNAL_BLOCKED" }
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
        checkpoint("file:${file.name}")
    }
    private fun checkDirectory() = check(!blocked && dir.isDirectory) { "JOURNAL_UNKNOWN" }
    @Synchronized fun initializeLabOnly() {
        check(!blocked && !dir.exists()) { "ENROLMENT_CONFLICT" }
        try {
            check(dir.mkdir()); sync.sync(dir.parentFile!!)
            write(File(dir, "marker"), "F3A_REVIEW_ONLY_NOT_NATIVE_EVIDENCE".toByteArray())
            write(File(dir, "head"), RecoveryCommit.json(mapOf("sequence" to "0", "sha256" to "NONE")))
            sync.sync(dir); checkpoint("initialized")
        } catch (failure: Throwable) { blocked = true; throw IllegalStateException("JOURNAL_BLOCKED", failure) }
    }
    @Synchronized fun read(): List<Map<String, String>> {
        try {
            checkDirectory()
            val files = dir.listFiles() ?: error("JOURNAL_UNKNOWN")
            check(File(dir, "marker").length() in 1..128)
            check(File(dir, "marker").readText() == "F3A_REVIEW_ONLY_NOT_NATIVE_EVIDENCE")
            val records = files.filter { it.name.matches(Regex("review-[0-9]{3}\\.json")) }.sortedBy { it.name }
            check(records.size <= 64 && files.size == records.size + 2) { "JOURNAL_BLOCKED" }
            var previous = "NONE"
            val result = records.mapIndexed { index, file ->
                check(file.name == "review-%03d.json".format(index + 1) && file.length() in 1..2048)
                val raw = file.readBytes(); val m = Wire.decode(raw, fields)
                check(RecoveryCommit.json(m).contentEquals(raw) && m["schema"] == "f3a-review-only/0" &&
                    m["sequence"] == (index + 1).toString() && m["previous"] == previous)
                requireUuid(m.getValue("process")); check(m.getValue("key_sha256").matches(Regex("[a-f0-9]{64}")))
                check(m["state"] in Completion.entries.map { it.name })
                check(m.getValue("reason").matches(Regex("[A-Z_]{1,64}")))
                previous = hash(m); m
            }
            val head = File(dir, "head"); check(head.length() in 1..256)
            check(Wire.decode(head.readBytes(), setOf("sequence", "sha256")) ==
                mapOf("sequence" to result.size.toString(), "sha256" to previous))
            return result
        } catch (failure: Throwable) { blocked = true; throw IllegalStateException("JOURNAL_BLOCKED", failure) }
    }
    @Synchronized fun appendReview(expected: Expectation, observation: Observation?): String {
        val records = read(); check(records.size < 64) { "JOURNAL_FULL" }
        requireUuid(expected.process)
        val result = ObservationReview.review(expected, observation)
        val k = expected.key
        val keyHash = Wire.hash(RecoveryCommit.canonical(mapOf("pilot" to k.pilot, "session" to k.session,
            "operation" to k.operation, "correlation" to k.correlation, "generation" to k.generation.toString(),
            "lease" to k.lease, "lease_generation" to k.leaseGeneration.toString(),
            "epoch" to k.epoch, "cancellation" to k.cancellation, "authorization" to k.authorization, "request_digest" to k.requestDigest)))
        val m = mapOf("schema" to "f3a-review-only/0", "sequence" to (records.size + 1).toString(),
            "previous" to (records.lastOrNull()?.let { hash(it) } ?: "NONE"), "process" to expected.process,
            "key_sha256" to keyHash, "state" to result.state.name, "reason" to result.reason)
        try {
            write(File(dir, "write.pending"), hash(m).toByteArray()); sync.sync(dir); checkpoint("pending.directory")
            val temp = File(dir, "review.new"); write(temp, RecoveryCommit.json(m))
            val dest = File(dir, "review-%03d.json".format(records.size + 1))
            check(!dest.exists() && temp.renameTo(dest)); checkpoint("published")
            sync.sync(dir); checkpoint("record.directory")
            val head = File(dir, "head.new")
            write(head, RecoveryCommit.json(mapOf("sequence" to (records.size + 1).toString(), "sha256" to hash(m))))
            check(head.renameTo(File(dir, "head"))); checkpoint("head.published")
            sync.sync(dir); checkpoint("head.directory")
            check(File(dir, "write.pending").delete()); checkpoint("pending.removed")
            sync.sync(dir); checkpoint("complete.directory")
        } catch (failure: Throwable) { blocked = true; throw IllegalStateException("JOURNAL_BLOCKED", failure) }
        return hash(m) // hash of a REVIEW, never a terminal_record_ref
    }
    private fun requireUuid(value: String) { require(java.util.UUID.fromString(value).toString() == value) }
}
