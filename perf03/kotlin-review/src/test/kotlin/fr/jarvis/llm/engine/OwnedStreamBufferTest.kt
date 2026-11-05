package fr.jarvis.llm.engine

import fr.jarvis.domain.ModelKind
import fr.jarvis.domain.RuntimeConfig
import fr.jarvis.domain.resources.Module
import fr.jarvis.domain.resources.ResourceClass
import fr.jarvis.domain.resources.ResourceCoordinator
import fr.jarvis.domain.resources.ResourceGrant
import fr.jarvis.domain.resources.ResourceLease
import fr.jarvis.domain.resources.ResourceRequest
import org.junit.Assert.*
import org.junit.Test

/** Nonproductive fixtures. No native calls, authority implementation, Android or model. */
class OwnedStreamBufferTest {
    private class Fixture : AutoCloseable {
        private val coordinator = ResourceCoordinator()
        private val heavy = (coordinator.acquire(ResourceRequest("review-owner", Module.LLM,
            ResourceClass.HEAVY, "test-only")) as ResourceGrant.Granted).lease
        private val model: ResourceLease = (coordinator.acquire(ResourceRequest("review-owner", Module.LLM,
            ResourceClass.MODEL, "test-only", modelKey = "review-fixture")) as ResourceGrant.Granted).lease
        val config = RuntimeConfig(ModelKind.SMALL, 512, 8, 1)
        val rendered = "test-only-rendered".toByteArray()
        val template = "test-only-template".toByteArray()
        private val prepared = StreamPrepared(streamHash(rendered), streamHash(template), "3".repeat(64), 7, 512, config)
        private val resident = StreamResident(model, "review-owner", "review-worker", "review-incarnation", config)
        val grant = StreamGrant("00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000002", "00000000-0000-0000-0000-000000000003",
            1, "00000000-0000-0000-0000-000000000004", "a".repeat(64), "b".repeat(64), prepared, resident, StreamRevocation())
        val buffer = OwnedStreamBuffer().also { it.bind(grant) }
        val callback = OwnedStreamCallback(grant, buffer)
        fun event(sequence: Int, kind: StreamKind = StreamKind.TOKEN, bytes: ByteArray = "x".toByteArray(),
                  sampled: Int = if (kind == StreamKind.ACK) 0 else 1,
                  first: Long = if (kind == StreamKind.ACK) -1 else 123,
                  clock: NativeStreamClock = NativeStreamClock.HOST_MONOTONIC) =
            OwnedStreamEvent(grant, sequence, kind, bytes, sampled, first, clock)
        fun ack() = assertEquals(StreamOffer.ACCEPTED, buffer.offer(event(0, StreamKind.ACK, byteArrayOf())))
        fun prepare() = assertTrue(callback.onNativePrepared(rendered, template, 7))
        fun callbackEvent(sequence: Int, kind: StreamKind, bytes: ByteArray = byteArrayOf(),
                          sampled: Int = if (kind == StreamKind.ACK) 0 else 1,
                          first: Long = if (kind == StreamKind.ACK) -1 else 123) =
            callback.onNativeEvent(grant.bindingSha256.toByteArray(Charsets.US_ASCII), kind.ordinal,
                sequence, bytes, sampled, first, NativeStreamClock.HOST_MONOTONIC.ordinal)
        override fun close() { model.close(); heavy.close() }
    }

    @Test fun sixteenthSlotIsReservedForTerminal() = Fixture().use { f ->
        f.ack()
        for (sequence in 1..14) assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(sequence)))
        assertEquals(15, f.buffer.pendingEvents())
        assertEquals(StreamOffer.RETRY, f.buffer.offer(f.event(15)))
        assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(15, StreamKind.COMPLETED, byteArrayOf())))
        assertEquals(16, f.buffer.pendingEvents())
        assertEquals(StreamResourceState.CONTENT_DONE, f.buffer.resourceState)
    }

    @Test fun pendingBytesBoundAndRetryPreserveSequence() = Fixture().use { f ->
        f.ack()
        for (sequence in 1..4) assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(sequence, bytes = ByteArray(4096) { 120 })))
        assertEquals(16384, f.buffer.pendingBytes())
        assertEquals(StreamOffer.RETRY, f.buffer.offer(f.event(5)))
        assertNotNull(f.buffer.poll()); assertNotNull(f.buffer.poll())
        assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(5)))
        assertEquals(12289, f.buffer.pendingBytes())
    }

    @Test fun cumulativeTextRemainsBoundedAfterConsumption() = Fixture().use { f ->
        f.ack(); f.buffer.poll()
        for (sequence in 1..16) {
            assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(sequence, bytes = ByteArray(4096) { 120 })))
            assertNotNull(f.buffer.poll())
        }
        assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(17)))
        assertTrue(f.buffer.isUnknown()); assertEquals(0, f.buffer.pendingBytes())
    }

    @Test fun chunksAreSizeBoundedAndCopiedImmutably() = Fixture().use { f ->
        f.ack(); val source = ByteArray(4096) { 120 }; val event = f.event(1, bytes = source)
        source[0] = 121
        assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(event))
        val copy = event.utf8Copy(); copy[0] = 122
        assertEquals(120.toByte(), event.utf8Copy()[0])
        assertThrows(IllegalArgumentException::class.java) { f.event(2, bytes = ByteArray(4097) { 120 }) }
        Unit
    }

    @Test fun ackAndStrictOrderAreRequired() {
        Fixture().use { f ->
            assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1))); assertTrue(f.buffer.isUnknown())
        }
        Fixture().use { f ->
            f.ack(); assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(2))); assertTrue(f.buffer.isUnknown())
        }
    }

    @Test fun exactDuplicateIsIdempotentAndContradictionQuarantines() = Fixture().use { f ->
        f.ack(); assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(1)))
        assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(1)))
        assertEquals(2, f.buffer.pendingEvents()); assertEquals(1, f.buffer.pendingBytes())
        assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1, bytes = "different".toByteArray())))
        assertTrue(f.buffer.isUnknown()); assertEquals(0, f.buffer.pendingEvents())
        assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(2)))
    }

    @Test fun clockDomainAndFirstTokenTimestampMustRemainCoherent() {
        Fixture().use { f ->
            f.ack(); assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1, clock = NativeStreamClock.ANDROID_BOOTTIME)))
        }
        Fixture().use { f ->
            f.ack(); f.buffer.offer(f.event(1))
            assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(2, first = 124)))
        }
    }

    @Test fun stopDiscardsUnrenderedTextEvenAfterProducerTerminal() {
        for (kind in listOf(StreamKind.COMPLETED, StreamKind.CANCELLED, StreamKind.ERROR)) Fixture().use { f ->
            f.ack(); f.buffer.offer(f.event(1))
            assertEquals(StreamOffer.ACCEPTED, f.buffer.offer(f.event(2, kind, byteArrayOf())))
            f.buffer.stop(); f.buffer.stop()
            assertEquals(0, f.buffer.pendingEvents()); assertEquals(0, f.buffer.pendingBytes()); assertNull(f.buffer.poll())
            assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(3)))
        }
    }

    @Test fun foreignGrantIsRejectedEvenWithMatchingDeclarativeBinding() = Fixture().use { f ->
        Fixture().use { foreign ->
            assertEquals(f.grant.bindingSha256, foreign.grant.bindingSha256)
            f.ack(); assertEquals(StreamOffer.ERROR, f.buffer.offer(foreign.event(1)))
            assertTrue(f.buffer.isUnknown()); assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1)))
        }
    }

    @Test fun revocationCancelsCallbackWithoutPublishingMoreText() = Fixture().use { f ->
        f.prepare(); assertEquals(0, f.callbackEvent(0, StreamKind.ACK))
        f.grant.revocation.revoke()
        assertEquals(StreamOffer.CANCEL.wire, f.callbackEvent(1, StreamKind.TOKEN, "x".toByteArray()))
        assertEquals(0, f.buffer.pendingBytes())
        assertEquals(StreamOffer.ACCEPTED.wire, f.callbackEvent(1, StreamKind.CANCELLED))
    }

    @Test fun wrongPreparedHashCannotBeRepairedWithinTheSameCallback() = Fixture().use { f ->
        assertFalse(f.callback.onNativePrepared("wrong".toByteArray(), f.template, 7))
        assertThrows(IllegalStateException::class.java) { f.callback.onNativePrepared(f.rendered, f.template, 7) }
        assertThrows(IllegalStateException::class.java) { f.callbackEvent(0, StreamKind.ACK) }
        assertEquals(0, f.buffer.pendingEvents())
    }

    @Test fun invalidUtf8AndUnsupportedControlsAreRejectedBeforeCopy() = Fixture().use { f ->
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { f.event(1, bytes = byteArrayOf(0xC0.toByte(), 0xAF.toByte())) }
        for (value in listOf(0, 1, 11, 31, 127))
            assertThrows(IllegalArgumentException::class.java) { f.event(1, bytes = byteArrayOf(value.toByte())) }
        assertEquals("\t\n\r", f.event(1, bytes = "\t\n\r".toByteArray()).utf8Copy().toString(Charsets.UTF_8))
    }

    @Test fun bindingHashMismatchIsRejectedBeforePublication() = Fixture().use { f ->
        f.prepare()
        assertThrows(IllegalStateException::class.java) {
            f.callback.onNativeEvent("0".repeat(64).toByteArray(), StreamKind.ACK.ordinal,
                0, byteArrayOf(), 0, -1, NativeStreamClock.HOST_MONOTONIC.ordinal)
        }
        assertEquals(0, f.buffer.pendingEvents())
    }

    @Test fun staleReplayAndProcessDeathRemainUnknown() {
        Fixture().use { f ->
            f.ack(); f.buffer.offer(f.event(1)); f.buffer.offer(f.event(2))
            assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1))); assertTrue(f.buffer.isUnknown())
        }
        Fixture().use { f ->
            f.ack(); f.buffer.processDied(); f.buffer.nativeInvocationFinished()
            assertTrue(f.buffer.jniInvocationFinished); assertTrue(f.buffer.isUnknown())
            assertEquals(StreamResourceState.UNKNOWN, f.buffer.resourceState)
            assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1)))
        }
    }

    @Test fun sampledTokenMetadataCannotGoBackwardsOrInventATokenAtAck() {
        Fixture().use { f ->
            assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(0, StreamKind.ACK, byteArrayOf(), sampled = 1, first = 123)))
        }
        Fixture().use { f ->
            f.ack(); f.buffer.offer(f.event(1, sampled = 2))
            assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(2, sampled = 1)))
        }
        Fixture().use { f ->
            f.ack(); assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1, sampled = 0, first = -1)))
        }
        Fixture().use { f ->
            f.ack(); assertEquals(StreamOffer.ERROR, f.buffer.offer(f.event(1, sampled = 9)))
        }
    }

    @Test fun nativeReturnDoesNotProveDrainOrUnload() = Fixture().use { f ->
        f.ack(); f.buffer.offer(f.event(1)); f.buffer.offer(f.event(2, StreamKind.COMPLETED, byteArrayOf()))
        f.buffer.nativeInvocationFinished()
        assertTrue(f.buffer.jniInvocationFinished)
        assertEquals(StreamResourceState.CONTENT_DONE, f.buffer.resourceState)
        assertNotEquals(StreamResourceState.DRAINED, f.buffer.resourceState)
        assertNotEquals(StreamResourceState.UNLOADED, f.buffer.resourceState)
    }
}
