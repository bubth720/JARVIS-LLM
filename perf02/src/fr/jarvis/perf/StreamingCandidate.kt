package fr.jarvis.perf

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class EventType { ACK, TOKEN, COMPLETED, CANCELLED, ERROR }
enum class Offer { ACCEPTED, BACKPRESSURE, STALE, DUPLICATE, REJECTED, TERMINAL }

/** Candidate local DTO only. No JNI symbol, wire contract, actual token producer or live-client permission. */
class NativeTokenEvent(val key: TraceKey, val sequence: Long, val type: EventType, bytes: ByteArray = byteArrayOf()) {
    private val payload = bytes.copyOf()
    val byteCount get() = payload.size
    init {
        require(sequence >= 0 && sequence <= 257)
        require(bytes.size <= 4096)
        require((type == EventType.TOKEN) == bytes.isNotEmpty())
        if (bytes.isNotEmpty()) {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            require(text.none { it == '\u0000' || it.code == 127 || it.code < 32 && it !in "\n\r\t" })
        }
    }
    internal fun same(other: NativeTokenEvent) = key == other.key && sequence == other.sequence && type == other.type && payload.contentEquals(other.payload)
    fun copyBytes() = payload.copyOf()
    override fun toString() = "NativeTokenEvent(type=$type,sequence=$sequence,bytes=${payload.size},text=redacted)"
}

/** REVIEW FIXTURE ONLY. Existing CORE authorization/lease owner is deliberately outside this queue.
 * Fixed at <=16 slots, 16 KiB pending and 64 KiB cumulative; does not own a worker or touch a model.
 * Retries must reuse the exact sequence after BACKPRESSURE. No accepted item is silently dropped.
 */
class ReviewStreamQueue private constructor(private val expected: TraceKey, private val maxSlots: Int,
    private val maxPendingBytes: Int, private val maxTotalBytes: Int) {
    init { require(maxSlots in 1..16); require(maxPendingBytes in 1..16384); require(maxTotalBytes in 1..65536) }
    private val queue = java.util.ArrayDeque<NativeTokenEvent>()
    private var next = 0L
    private var pending = 0
    private var total = 0
    private var last: NativeTokenEvent? = null
    private var terminal = false
    private var stopped = false
    private var unknown = false
    val mayAdmitAnotherEngine: Boolean get() = false
    val nativeDrain: Status get() = Status.UNKNOWN
    val nativeUnload: Status get() = Status.UNKNOWN
    @Synchronized fun pendingBytes() = pending
    @Synchronized fun pendingCount() = queue.size
    @Synchronized fun isUnknown() = unknown
    @Synchronized fun offer(event: NativeTokenEvent): Offer {
        if (event.key != expected) return Offer.STALE
        if (unknown) return Offer.TERMINAL
        if (last?.sequence == event.sequence) {
            if (last!!.same(event)) return Offer.DUPLICATE
            return reject()
        }
        if (terminal || stopped) return Offer.TERMINAL
        if (event.sequence != next || (next == 0L) != (event.type == EventType.ACK)) return reject()
        if (total.toLong() + event.byteCount > maxTotalBytes) return reject()
        if (queue.size >= maxSlots || pending.toLong() + event.byteCount > maxPendingBytes) return Offer.BACKPRESSURE
        queue.addLast(event); pending += event.byteCount; total += event.byteCount
        last = event; next++
        terminal = event.type in setOf(EventType.COMPLETED, EventType.CANCELLED, EventType.ERROR)
        return Offer.ACCEPTED
    }
    /** Stop invalidates queued display content, not native work, cleanup, ownership or a lease. */
    @Synchronized fun stop(key: TraceKey): Boolean {
        if (key != expected || stopped || terminal || unknown) return false
        stopped = true; queue.clear(); pending = 0; last = null; return true
    }
    @Synchronized fun processDied() { unknown = true; queue.clear(); pending = 0; last = null }
    @Synchronized fun poll(): NativeTokenEvent? {
        if (stopped || unknown) return null
        val event = queue.pollFirst() ?: return null
        pending -= event.byteCount; return event
    }
    private fun reject(): Offer { unknown = true; queue.clear(); pending = 0; last = null; return Offer.REJECTED }
    companion object {
        fun fixture(key: TraceKey, slots: Int = 16, pendingBytes: Int = 16384, totalBytes: Int = 65536) =
            ReviewStreamQueue(key, slots, pendingBytes, totalBytes)
    }
}

/** Review-only incremental UTF-8 assembly; byte-fallback tokens may split a codepoint.
 * Empty output is NEVER a display token. At most three undecoded bytes survive a push.
 */
class Utf8Carry {
    private var carry = byteArrayOf()
    private var closed = false
    @Synchronized fun push(piece: ByteArray): ByteArray {
        check(!closed); require(piece.size <= 4096)
        val combined = carry + piece
        val input = ByteBuffer.wrap(combined)
        val output = java.nio.CharBuffer.allocate(combined.size)
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(input, output, false)
        if (result.isError) { closed = true; carry = byteArrayOf(); result.throwException() }
        check(!result.isOverflow)
        val complete = combined.copyOfRange(0, input.position())
        carry = combined.copyOfRange(input.position(), combined.size)
        check(carry.size <= 3)
        return complete
    }
    @Synchronized fun finish() { closed = true; val truncated = carry.isNotEmpty(); carry = byteArrayOf(); require(!truncated) }
    @Synchronized fun cancel() { closed = true; carry = byteArrayOf() }
    @Synchronized fun retainedBytes() = carry.size
}
