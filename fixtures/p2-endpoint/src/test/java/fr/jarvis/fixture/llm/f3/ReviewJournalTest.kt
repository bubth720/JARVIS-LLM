package fr.jarvis.fixture.llm.f3

import fr.jarvis.fixture.llm.DirectorySync
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Real host files/fsync; directory fsync is a TEST double, not Android durability proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ReviewJournalTest {
    private val sync=object:DirectorySync { override fun sync(directory:File){} }
    private fun id()=UUID.randomUUID().toString()
    private fun expected()=Expectation(id(),id(),id(),OperationKey(id(),id(),id(),id(),1,id(),1,id(),id(),id(),"a".repeat(64)),1,1,id(),1,1)
    private fun dir()=File(Files.createTempDirectory("f3-review-test").toFile(),"review")
    private fun refused(block:()->Unit) { try { block();fail("accepted") } catch(_:RuntimeException){} }
    @Test fun absentJournalAndReenrolmentCannotManufactureHealthyState() {
        val d=dir();try {
            val j=ReviewJournal(d,sync);refused { j.read() };refused { j.initializeLabOnly() }
            assertFalse(d.exists())
        } finally { d.parentFile!!.deleteRecursively() }
    }
    @Test fun roundTripReviewsAreExplicitlyUnknownAndNeverTerminalReceipts() {
        val d=dir();try {
            val j=ReviewJournal(d,sync);j.initializeLabOnly();val e=expected();val sha=j.appendReview(e,null)
            val rows=ReviewJournal(d,sync).read();assertEquals(1,rows.size)
            assertEquals("UNKNOWN",rows.single()["state"]);assertEquals("f3a-review-only/0",rows.single()["schema"])
            assertEquals(64,sha.length);assertFalse(rows.single().containsKey("terminal_record_ref"))
            refused { j.initializeLabOnly() }
        } finally { d.parentFile!!.deleteRecursively() }
    }
    @Test fun corruptionMissingRecordAndUnexpectedSidecarsBlockWithoutRepair() {
        for(mutate in listOf<(File)->Unit>(
            { File(it,"review-001.json").writeText("{}") },
            { File(it,"marker").delete() },
            { File(it,"head").delete() },
            { File(it,"head").writeText("{}") },
            { File(it,"review-001.json").delete() },
            { File(it,"review-002.json").delete() },
            { File(it,"unexpected").writeText("x") })) {
            val d=dir();try {
                val j=ReviewJournal(d,sync);j.initializeLabOnly();j.appendReview(expected(),null);j.appendReview(expected(),null)
                mutate(d);val names=d.list()!!.toSet()
                refused { ReviewJournal(d,sync).read() };assertEquals(names,d.list()!!.toSet())
            } finally { d.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun interruptionAtEachBoundaryEitherBlocksOrPreservesDurableReviewOnly() {
        val points=mutableListOf<String>();val baseline=dir()
        try { val j=ReviewJournal(baseline,sync) { points+=it };j.initializeLabOnly();points.clear();j.appendReview(expected(),null) }
        finally { baseline.parentFile!!.deleteRecursively() }
        assertEquals(10,points.size)
        for(point in points) {
            val d=dir();try {
                ReviewJournal(d,sync).initializeLabOnly()
                val j=ReviewJournal(d,sync) { if(it==point) throw IllegalStateException("injected interruption") }
                refused { j.appendReview(expected(),null) };refused { j.read() }
                val result=runCatching { ReviewJournal(d,sync).read() }
                if(point in setOf("pending.removed","complete.directory")) {
                    assertTrue(result.isSuccess);assertEquals("UNKNOWN",result.getOrThrow().single()["state"])
                } else assertTrue(point,result.isFailure)
            } finally { d.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun ioFailurePreservesPendingAndPreventsFurtherWrites() {
        val d=dir();try {
            ReviewJournal(d,sync).initializeLabOnly()
            val broken=object:DirectorySync { override fun sync(directory:File) { error("fsync injected") } }
            val j=ReviewJournal(d,broken);refused { j.appendReview(expected(),null) }
            assertTrue(File(d,"write.pending").exists());refused { j.appendReview(expected(),null) }
            refused { ReviewJournal(d,sync).read() }
        } finally { d.parentFile!!.deleteRecursively() }
    }
    @Test fun boundedCapacityNeverEvictsOrResetsReviews() {
        val d=dir();try {
            val j=ReviewJournal(d,sync);j.initializeLabOnly();repeat(64) { j.appendReview(expected(),null) }
            refused { j.appendReview(expected(),null) };assertEquals(64,j.read().size)
        } finally { d.parentFile!!.deleteRecursively() }
    }
}
