package fr.jarvis.perf

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StreamingCandidateTest {
    private fun key() = TraceKey(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
        UUID.randomUUID().toString(), "b".repeat(64))
    private fun event(k: TraceKey, n: Long, s: String = "fixture") = NativeTokenEvent(k, n, EventType.TOKEN, s.toByteArray())
    @Test fun ackIsNotModelText() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); assertEquals(Offer.ACCEPTED, q.offer(NativeTokenEvent(k, 0, EventType.ACK)))
        assertEquals(0, q.poll()!!.byteCount)
    }
    @Test fun missingAckFailsClosed() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); assertEquals(Offer.REJECTED, q.offer(event(k, 1)))
        assertTrue(q.isUnknown()); assertNull(q.poll())
    }
    @Test fun backpressureNeverDropsAcceptedText() {
        val k = key(); val q = ReviewStreamQueue.fixture(k, slots = 1)
        q.offer(NativeTokenEvent(k, 0, EventType.ACK)); assertEquals(Offer.BACKPRESSURE, q.offer(event(k, 1)))
        q.poll(); assertEquals(Offer.ACCEPTED, q.offer(event(k, 1))); assertEquals("fixture", q.poll()!!.copyBytes().toString(Charsets.UTF_8))
    }
    @Test fun byteBackpressureCanResumeSameSequence() {
        val k = key(); val q = ReviewStreamQueue.fixture(k, pendingBytes = 4)
        q.offer(NativeTokenEvent(k, 0, EventType.ACK)); q.poll(); q.offer(event(k, 1, "abcd"))
        assertEquals(Offer.BACKPRESSURE, q.offer(event(k, 2, "e"))); q.poll()
        assertEquals(Offer.ACCEPTED, q.offer(event(k, 2, "e")))
    }
    @Test fun cumulativeMemoryLimitFailsClosed() {
        val k = key(); val q = ReviewStreamQueue.fixture(k, totalBytes = 4)
        q.offer(NativeTokenEvent(k, 0, EventType.ACK)); q.poll(); q.offer(event(k, 1, "abcd")); q.poll()
        assertEquals(Offer.REJECTED, q.offer(event(k, 2, "e"))); assertFalse(q.mayAdmitAnotherEngine)
    }
    @Test fun duplicateIsIdempotentAndConflictingEmitRejected() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); q.offer(NativeTokenEvent(k, 0, EventType.ACK)); q.poll()
        assertEquals(Offer.ACCEPTED, q.offer(event(k, 1))); assertEquals(Offer.DUPLICATE, q.offer(event(k, 1)))
        assertEquals(Offer.REJECTED, q.offer(event(k, 1, "different")))
    }
    @Test fun gapIsUnknownNotReordered() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); q.offer(NativeTokenEvent(k, 0, EventType.ACK))
        assertEquals(Offer.REJECTED, q.offer(event(k, 2)))
    }
    @Test fun oldIdentityCannotPoisonCurrentOperation() {
        val q = ReviewStreamQueue.fixture(key()); assertEquals(Offer.STALE, q.offer(event(key(), 1))); assertFalse(q.isUnknown())
    }
    @Test fun stopClearsPendingTextWithoutInventingDrain() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); q.offer(NativeTokenEvent(k, 0, EventType.ACK)); q.offer(event(k, 1))
        assertTrue(q.stop(k)); assertFalse(q.stop(k)); assertEquals(0, q.pendingBytes()); assertNull(q.poll())
        assertEquals(Offer.TERMINAL, q.offer(event(k, 2))); assertEquals(Status.UNKNOWN, q.nativeDrain); assertEquals(Status.UNKNOWN, q.nativeUnload)
    }
    @Test fun terminalDisallowsLateTokens() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); q.offer(NativeTokenEvent(k, 0, EventType.ACK))
        q.offer(NativeTokenEvent(k, 1, EventType.COMPLETED)); assertEquals(Offer.TERMINAL, q.offer(event(k, 2)))
    }
    @Test fun stopAfterProducerTerminalStillDiscardsUnrenderedText() {
        for (type in listOf(EventType.COMPLETED, EventType.CANCELLED, EventType.ERROR)) {
            val k = key(); val q = ReviewStreamQueue.fixture(k)
            q.offer(NativeTokenEvent(k, 0, EventType.ACK)); q.offer(event(k, 1))
            assertEquals(Offer.ACCEPTED, q.offer(NativeTokenEvent(k, 2, type)))
            assertTrue(q.stop(k)); assertNull(q.poll()); assertEquals(0, q.pendingBytes())
            assertEquals(0, q.pendingCount()); assertEquals(Offer.TERMINAL, q.offer(event(k, 3)))
            assertEquals(Status.UNKNOWN, q.nativeDrain); assertEquals(Status.UNKNOWN, q.nativeUnload)
            assertFalse(q.mayAdmitAnotherEngine)
        }
    }
    @Test fun processDeathIsUnknown() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); q.processDied()
        assertTrue(q.isUnknown()); assertEquals(Offer.TERMINAL, q.offer(NativeTokenEvent(k, 0, EventType.ACK)))
    }
    @Test fun concurrentDoubleEmitIsBounded() {
        val k = key(); val q = ReviewStreamQueue.fixture(k); q.offer(NativeTokenEvent(k, 0, EventType.ACK)); q.poll()
        val pool = Executors.newFixedThreadPool(4); val results = (1..16).map { pool.submit<Offer> { q.offer(event(k, 1)) } }
        assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) == Offer.ACCEPTED }); pool.shutdownNow()
        assertEquals(1, q.pendingCount())
    }
    @Test fun payloadIsImmutableAndRedacted() {
        val bytes = "SECRET_FIXTURE".toByteArray(); val e = NativeTokenEvent(key(), 1, EventType.TOKEN, bytes); bytes.fill(0)
        e.copyBytes().fill(0); assertEquals("SECRET_FIXTURE", e.copyBytes().toString(Charsets.UTF_8)); assertFalse(e.toString().contains("SECRET_FIXTURE"))
    }
    @Test fun malformedEmptyOversizedAndControlsRejected() {
        val k = key()
        assertThrows(IllegalArgumentException::class.java) { NativeTokenEvent(k, 1, EventType.TOKEN) }
        assertThrows(IllegalArgumentException::class.java) { event(k, 1, "x".repeat(4097)) }
        assertThrows(IllegalArgumentException::class.java) { event(k, 1, "\u0000") }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { NativeTokenEvent(k, 1, EventType.TOKEN, byteArrayOf(0xc0.toByte(), 0x80.toByte())) }
    }
    @Test fun splitUtf8DoesNotInventAFirstTextToken() {
        val carry = Utf8Carry(); val bytes = "🖋".toByteArray()
        assertTrue(carry.push(bytes.copyOfRange(0, 1)).isEmpty()); assertEquals(1, carry.retainedBytes())
        assertTrue(carry.push(bytes.copyOfRange(1, 3)).isEmpty()); assertEquals(3, carry.retainedBytes())
        assertArrayEquals(bytes, carry.push(bytes.copyOfRange(3, 4))); carry.finish()
    }
    @Test fun truncatedUtf8AtTerminalIsAnError() {
        val carry = Utf8Carry(); carry.push(byteArrayOf(0xf0.toByte()))
        assertThrows(IllegalArgumentException::class.java) { carry.finish() }
    }
    @Test fun cancelDiscardsIncompleteUtf8AndPreventsRestart() {
        val carry = Utf8Carry(); carry.push(byteArrayOf(0xf0.toByte())); carry.cancel(); assertEquals(0, carry.retainedBytes())
        assertThrows(IllegalStateException::class.java) { carry.push(byteArrayOf(0x41)) }
    }
}
