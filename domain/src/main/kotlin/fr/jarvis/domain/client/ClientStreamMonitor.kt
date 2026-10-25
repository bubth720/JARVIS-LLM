package fr.jarvis.domain.client

import fr.jarvis.domain.generation.utf8Size

enum class ClientPhase { WAITING, STREAMING, CONTENT_DONE, STOP_REQUESTED, REFUSED, UNKNOWN }
enum class EventDisposition { ACCEPTED, DUPLICATE, STALE, TERMINAL, REJECTED }
/** Single request, bounded RAM. Thread-safe projection; NEVER owns or releases a native lease.
 * No reset/re-admission method: UNKNOWN requires a separate real qualification/recovery path. */
class ClientStreamMonitor private constructor(private val request: GenerateRequest, private val startMillis: Long,
                                              private val fixtureOnly: Boolean) {
    init { require(startMillis >= 0) }
    private var lastMillis = startMillis
    private val seen = ArrayList<String>()
    private val content = StringBuilder()
    private var outputBytes = 0
    @Volatile var phase = ClientPhase.WAITING; private set
    val sourceLabel: String get() = if (fixtureOnly) "FIXTURE — sans moteur LLM" else "NON CONNECTÉ"
    val nativeResources: NativeResourceState get() = NativeResourceState(null)
    @Synchronized fun text(): String = content.toString()
    @Synchronized fun cancel(cancel: Cancel): EventDisposition {
        if (cancel.reference != request.reference) return EventDisposition.STALE
        if (phase == ClientPhase.STOP_REQUESTED) return EventDisposition.DUPLICATE
        if (phase in setOf(ClientPhase.CONTENT_DONE, ClientPhase.REFUSED, ClientPhase.UNKNOWN)) return EventDisposition.TERMINAL
        phase = ClientPhase.STOP_REQUESTED; content.clear(); return EventDisposition.ACCEPTED
    }
    @Synchronized fun expire(nowMillis: Long): Boolean {
        if (phase in setOf(ClientPhase.CONTENT_DONE, ClientPhase.REFUSED, ClientPhase.UNKNOWN)) return false
        if (nowMillis < lastMillis || nowMillis < startMillis || nowMillis - startMillis >= request.prompt.limits.remainingMillis) {
            unknown(); return true
        }
        lastMillis = nowMillis; return false
    }
    @Synchronized fun accept(event: ClientEvent, nowMillis: Long): EventDisposition {
        // Old epoch/bail/session events cannot disturb the current request.
        if (event.reference.key != request.reference.key) return EventDisposition.STALE
        if (expire(nowMillis)) return EventDisposition.REJECTED
        if (phase in setOf(ClientPhase.CONTENT_DONE, ClientPhase.STOP_REQUESTED, ClientPhase.REFUSED, ClientPhase.UNKNOWN)) return EventDisposition.TERMINAL
        if (event.reference != request.reference) return reject()
        if (event.origin != (if (fixtureOnly) EventOrigin.FIXTURE else EventOrigin.UNAVAILABLE)) return reject()
        // Live path can only report an explicit refusal, never fixture text or a false ACK.
        if (!fixtureOnly && event !is Error) return reject()
        val payload = when (event) { is StreamChunk -> event.text; is Error -> event.code.name; else -> "" }
        val hash = digest((event.javaClass.simpleName + ":" + event.origin.name + ":" + payload).toByteArray(Charsets.UTF_8))
        if (event.sequence < seen.size) return if (seen[event.sequence] == hash) EventDisposition.DUPLICATE else reject()
        if (event.sequence != seen.size || seen.size >= 258) return reject()
        when (event) {
            is Ack -> { if (phase != ClientPhase.WAITING) return reject(); phase = ClientPhase.STREAMING }
            is StreamChunk -> {
                if (phase != ClientPhase.STREAMING || outputBytes.toLong() + utf8Size(event.text) > 65536) return reject()
                outputBytes += utf8Size(event.text); content.append(event.text)
            }
            is Completed -> { if (phase != ClientPhase.STREAMING) return reject(); phase = ClientPhase.CONTENT_DONE }
            is Error -> { content.clear(); phase = ClientPhase.REFUSED }
        }
        seen.add(hash); return EventDisposition.ACCEPTED
    }
    private fun unknown() { phase = ClientPhase.UNKNOWN; content.clear() }
    private fun reject(): EventDisposition { unknown(); return EventDisposition.REJECTED }
    companion object {
        fun live(request: GenerateRequest, startMillis: Long) = ClientStreamMonitor(request, startMillis, false)
        /** Explicit isolated test/demo opt-in. This cannot enable GenerationClientPort or an engine. */
        fun fixture(request: GenerateRequest, startMillis: Long) = ClientStreamMonitor(request, startMillis, true)
    }
}
