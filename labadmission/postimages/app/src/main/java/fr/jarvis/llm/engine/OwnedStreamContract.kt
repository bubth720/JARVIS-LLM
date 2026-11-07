package fr.jarvis.llm.engine

import fr.jarvis.domain.RuntimeConfig
import fr.jarvis.domain.resources.ResourceLease
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID

/** Review candidate: CORE and LAB_CLOUD have disjoint credentials; no productive wiring. */
internal enum class StreamKind { ACK, TOKEN, COMPLETED, CANCELLED, ERROR }
internal enum class NativeStreamClock { ANDROID_BOOTTIME, HOST_MONOTONIC }
internal enum class StreamOffer(val wire: Int) { ACCEPTED(0), RETRY(1), CANCEL(2), ERROR(3) }
internal enum class StreamResourceState { CONTENT_DONE, DRAINED, UNLOADED, UNKNOWN, CLEANUP_FAILED }
internal fun streamHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) }
private fun validHash(value: String) = value.matches(Regex("[0-9a-f]{64}"))
private fun validId(value: String) = value.length == 36 && runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

/** Actual lease object and local incarnation, NOT a broker attestation or permission. Never close it here. */
internal class StreamResident(val lease: ResourceLease, val owner: String, val worker: String,
                              val incarnation: String, val config: RuntimeConfig) {
    override fun toString() = "StreamResident(redacted)"
}
/** Native canonical rendering, tokenizer and unchanged admitted output/config. No prompt is retained. */
internal class StreamPrepared(val renderedSha256: String, val templateSha256: String,
    val requestSha256: String, val inputTokens: Int, val contextLimit: Int, val config: RuntimeConfig,
    val originRequestSha256: String?) {
    constructor(renderedSha256: String, templateSha256: String, requestSha256: String, inputTokens: Int,
        contextLimit: Int, config: RuntimeConfig) : this(renderedSha256,templateSha256,requestSha256,inputTokens,contextLimit,config,null)
    init { require(originRequestSha256 == null || validHash(originRequestSha256))
        require(validHash(renderedSha256) && validHash(templateSha256) && validHash(requestSha256))
        require(inputTokens > 0 && inputTokens.toLong() + config.maxTokens + 8 <= contextLimit)
        require(contextLimit in 512..config.context && contextLimit % 256 == 0) }
    override fun toString() = "StreamPrepared(redacted)"
}
/** A LAB_CLOUD binding has no coreMandateSha256 and cannot implement StreamOwnerAuthority. */
internal sealed interface StreamBinding {
    val prepared: StreamPrepared
    val resident: StreamResident
    val revocation: StreamRevocation
    val bindingSha256: String
    fun isLive(): Boolean
}
/** All fields must be checked against CORE/broker by the explicitly installed authority. */
internal class StreamGrant(val request: String, val session: String, val epoch: String,
    val generation: Long, val leaseRef: String, val modelSha256: String, val coreMandateSha256: String,
    override val prepared: StreamPrepared, override val resident: StreamResident, override val revocation: StreamRevocation, val ownerSequence: Long = generation) : StreamBinding {
    init { require(listOf(request, session, epoch, leaseRef).all(::validId))
        require(generation in 1..9_007_199_254_740_991L && ownerSequence in 1..9_007_199_254_740_991L)
        require(validHash(modelSha256) && validHash(coreMandateSha256)) }
    override val bindingSha256: String = streamHash(listOf("jarvis.llm.stream/1", request, session, epoch,
        generation.toString(), ownerSequence.toString(), leaseRef, modelSha256, coreMandateSha256, prepared.requestSha256,
        prepared.renderedSha256, prepared.templateSha256, resident.worker, resident.incarnation,
        prepared.inputTokens.toString(), prepared.contextLimit.toString(), prepared.config.model.name,
        prepared.config.context.toString(), prepared.config.maxTokens.toString(),
        prepared.config.threads.toString()).joinToString("\n").toByteArray(Charsets.US_ASCII))
    override fun isLive() = revocation.isAllowed()
    override fun toString() = "StreamGrant(scope=CORE, redacted)"
}
internal class StreamRevocation {
    private val allowed = java.util.concurrent.atomic.AtomicBoolean(true)
    fun revoke() { allowed.set(false) }
    internal fun isAllowed() = allowed.get()
}

/** No implementation is supplied: CORE mandate, verified model and real lease proof remain prerequisites. */
internal interface StreamOwnerAuthority {
    fun authorize(prepared: StreamPrepared, resident: StreamResident): StreamGrant?
}

internal class OwnedStreamEvent(val grant: StreamBinding, val sequence: Int, val kind: StreamKind,
    bytes: ByteArray, val sampledTokens: Int, val nativeFirstTokenNanos: Long, val nativeClockDomain: NativeStreamClock) {
    private val payload: ByteArray
    init { require(sequence in 0..512 && sampledTokens in 0..256 && nativeFirstTokenNanos >= -1)
        require(bytes.size <= 4096 && (kind == StreamKind.TOKEN) == bytes.isNotEmpty())
        if (bytes.isNotEmpty()) {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
            require(bytes.none { it == 0.toByte() || it == 127.toByte() || (it.toInt() in 1..31 && it.toInt() !in listOf(9,10,13)) })
        }
        payload = bytes.copyOf()
    }
    val byteCount get() = payload.size
    fun utf8Copy() = payload.copyOf()
    internal fun same(other: OwnedStreamEvent) = grant === other.grant && sequence == other.sequence &&
        kind == other.kind && nativeClockDomain == other.nativeClockDomain && sampledTokens == other.sampledTokens && nativeFirstTokenNanos == other.nativeFirstTokenNanos && payload.contentEquals(other.payload)
    override fun toString() = "OwnedStreamEvent(kind=$kind, sequence=$sequence, bytes=${payload.size}, redacted)"
}

/** One producer (existing JNI worker), one logical Android consumer; no additional generation thread. */
internal class OwnedStreamBuffer {
    private val queue = ArrayDeque<OwnedStreamEvent>()
    private var grant: StreamBinding? = null
    private var next = 0
    private var pending = 0
    private var total = 0
    private var last: OwnedStreamEvent? = null
    private var stopped = false
    private var terminal = false
    private var terminalReceipt: StreamKind? = null
    private var invalid = false
    @Volatile var jniInvocationFinished = false; private set
    @Volatile var resourceState = StreamResourceState.UNKNOWN; private set
    @Synchronized fun bind(expected: StreamBinding) { check(grant == null && !stopped && !invalid); grant = expected }
    @Synchronized fun offer(event: OwnedStreamEvent): StreamOffer {
        if (jniInvocationFinished) return StreamOffer.ERROR
        if (invalid || event.grant !== grant) return fail()
        if (event.sampledTokens > event.grant.prepared.config.maxTokens) return fail()
        if (last?.sequence == event.sequence) return if (last!!.same(event)) StreamOffer.ACCEPTED else fail()
        if (terminal || event.sequence != next) return fail()
        if (event.kind == StreamKind.ACK && (event.sampledTokens != 0 || event.nativeFirstTokenNanos != -1L)) return fail()
        if (event.kind == StreamKind.TOKEN && (event.sampledTokens == 0 || event.nativeFirstTokenNanos < 0)) return fail()
        if (event.sampledTokens == 0 && event.nativeFirstTokenNanos != -1L || event.sampledTokens > 0 && event.nativeFirstTokenNanos < 0) return fail()
        if (last != null && (event.sampledTokens < last!!.sampledTokens || event.nativeClockDomain != last!!.nativeClockDomain ||
            last!!.nativeFirstTokenNanos >= 0 && event.nativeFirstTokenNanos != last!!.nativeFirstTokenNanos)) return fail()
        if (stopped && event.kind !in setOf(StreamKind.CANCELLED, StreamKind.ERROR)) return StreamOffer.CANCEL
        if (next == 0 && event.kind !in setOf(StreamKind.ACK, StreamKind.CANCELLED, StreamKind.ERROR) || next > 0 && event.kind == StreamKind.ACK) return fail()
        val end = event.kind in setOf(StreamKind.COMPLETED, StreamKind.CANCELLED, StreamKind.ERROR)
        if (end) {
            // Reserve the 16th slot for the terminal; content and sequence remain intact under load.
            if (queue.size >= 16) return fail()
        } else {
            if (total + event.byteCount > 64 * 1024) return fail()
            if (queue.size >= 15 || pending + event.byteCount > 16 * 1024) return StreamOffer.RETRY
        }
        queue.addLast(event); pending += event.byteCount; total += event.byteCount
        next++; last = event; terminal = end
        if (end) terminalReceipt = event.kind
        if (event.kind == StreamKind.COMPLETED) resourceState = StreamResourceState.CONTENT_DONE
        return StreamOffer.ACCEPTED
    }
    @Synchronized private fun fail(): StreamOffer { invalid = true; queue.clear(); pending = 0; terminalReceipt = null
        resourceState = StreamResourceState.UNKNOWN; return StreamOffer.ERROR }
    @Synchronized fun poll(): OwnedStreamEvent? = queue.pollFirst()?.also { pending -= it.byteCount }
    /** Stop discards queued, not yet displayed content; already displayed text remains caller-owned and labelled partial. */
    @Synchronized fun stop() { stopped = true; queue.clear(); pending = 0 }
    @Synchronized fun processDied() { fail() }
    @Synchronized fun invocationFailed() { fail() }
    @Synchronized fun acceptedTerminal(): StreamKind? = if (invalid) null else terminalReceipt
    @Synchronized fun nativeInvocationFinished() { jniInvocationFinished = true /* not entry, DRAINED or UNLOADED proof */ }
    @Synchronized fun pendingBytes() = pending
    @Synchronized fun pendingEvents() = queue.size
    @Synchronized fun isUnknown() = invalid
}

/** Only this final adapter is passed to JNI. It never calls UI or arbitrary consumer code. */
internal class OwnedStreamCallback(private val grant: StreamBinding, private val buffer: OwnedStreamBuffer) {
    private var prepared = false
    private var preparationAttempted = false
    private var closed = false
    /** Called with the actual formatter/tokenizer output, before any KV prefill or ACK. */
    @Synchronized fun onNativePrepared(rendered: ByteArray, template: ByteArray, inputTokens: Int): Boolean {
        check(!closed)
        try {
            check(!preparationAttempted && rendered.size <= 128 * 1024 && template.size <= 128 * 1024)
            preparationAttempted = true
            prepared = inputTokens == grant.prepared.inputTokens && streamHash(rendered) == grant.prepared.renderedSha256 &&
                streamHash(template) == grant.prepared.templateSha256 && grant.isLive()
            return prepared
        } catch (failure: Throwable) { buffer.invocationFailed(); throw failure }
    }
    /** Native clock domain is ANDROID_BOOTTIME; host CLOCK_MONOTONIC is separately labelled. */
    @Synchronized fun onNativeEvent(binding: ByteArray, kind: Int, sequence: Int, bytes: ByteArray,
                      sampledTokens: Int, nativeFirstTokenNanos: Long, nativeClockDomain: Int): Int {
        check(!closed) // a rejected late callback cannot rewrite a previously established result
        try {
            check(binding.contentEquals(grant.bindingSha256.toByteArray(Charsets.US_ASCII)))
            check(prepared || kind == StreamKind.CANCELLED.ordinal || kind == StreamKind.ERROR.ordinal)
            require(kind in 0..4 && nativeClockDomain in 0..1)
            if (!grant.isLive() && kind != StreamKind.CANCELLED.ordinal && kind != StreamKind.ERROR.ordinal)
                return StreamOffer.CANCEL.wire
            return buffer.offer(OwnedStreamEvent(grant, sequence, StreamKind.entries[kind], bytes, sampledTokens,
                nativeFirstTokenNanos, NativeStreamClock.entries[nativeClockDomain])).wire
        } catch (failure: Throwable) { buffer.invocationFailed(); throw failure }
    }
    @Synchronized fun hasPrepared() = prepared
    @Synchronized fun close() { closed = true }
}

internal class OwnedStreamOutcome(val terminal: StreamKind, val nativeReturned: Boolean) {
    val resourceState = if (terminal == StreamKind.COMPLETED) StreamResourceState.CONTENT_DONE else StreamResourceState.UNKNOWN
    override fun toString() = "OwnedStreamOutcome(terminal=$terminal, nativeReturned=$nativeReturned, noDrainOrUnloadProof)"
}
