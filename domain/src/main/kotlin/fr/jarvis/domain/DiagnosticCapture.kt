package fr.jarvis.domain

/** Optional /proc/self/status values: RSS/HWM are process lifetime values, not PSS peaks. */
data class ProcessMemory(val rssBytes: Long?, val rssHighWaterBytes: Long?) {
    companion object {
        fun parse(status: String): ProcessMemory {
            fun value(key: String): Long? {
                val line = status.lineSequence().firstOrNull { it.startsWith("$key:") } ?: return null
                val fields = line.substringAfter(':').trim().split(Regex("\\s+"))
                if (fields.size != 2 || fields[1] != "kB") return null
                val kib = fields[0].toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 1024 } ?: return null
                return kib * 1024
            }
            return ProcessMemory(value("VmRSS"), value("VmHWM"))
        }
    }
}
data class NativeAllocations(val model: Long, val context: Long, val compute: Long)
data class DiagnosticFrame(val elapsedMillis: Long, val phase: MemoryPhase, val pssBytes: Long?,
    val rssBytes: Long?, val rssHighWaterBytes: Long?, val availableBytes: Long, val totalBytes: Long,
    val lowMemoryThreshold: Long, val lowMemory: Boolean, val batteryPercent: Int?, val thermalStatus: Int?,
    val thermalHeadroom: Float?, val foreground: Boolean, val reserveBytes: Long, val budgetBytes: Long,
    val measuredCredit: Long, val requiredAvailableBytes: Long, val admissionMargin: Long,
    val configuration: RuntimeConfig, val configurationIsActual: Boolean, val profile: Profile, val nativeAllocations: NativeAllocations?, val resources: String)
enum class DiagnosticEventType { START, STOP_REQUESTED, ADMISSION_REFUSED, MODEL_LOADED, GENERATION_SUCCEEDED, OPERATION_ERROR, MODEL_UNLOADED, BACKGROUND, CAPTURE_STOPPED }
data class DiagnosticEvent(val elapsedMillis: Long, val type: DiagnosticEventType)
/** A manually armed bounded observation, never an inference or permission to lower admission. */
class DiagnosticCapture(private val maxFrames: Int = 256, val maxMillis: Long = 90_000) {
    init { require(maxFrames in 8..1024 && maxMillis in 1000..120_000) }
    var active = false; private set
    var stopReason = "non démarrée"; private set
    var dropped = 0; private set
    private var started = 0L
    private var hasSession = false
    private val events = java.util.ArrayDeque<DiagnosticEvent>()
    private val frames = java.util.ArrayDeque<DiagnosticFrame>()
    @Synchronized fun start(now: Long) { require(now >= 0); started = now; frames.clear(); events.clear(); dropped = 0; active = true; hasSession = true; stopReason = "en cours"; event(now, DiagnosticEventType.START) }
    @Synchronized fun add(now: Long, frame: DiagnosticFrame) {
        if (!active) return
        require(now >= started && frame.elapsedMillis == now - started)
        if (now - started > maxMillis) { stop("limite de durée atteinte"); return }
        if (frames.size == maxFrames) { dropped++; frames.removeFirst() }
        frames.addLast(frame)
    }
    @Synchronized fun event(now: Long, type: DiagnosticEventType) {
        // Completion may arrive after a foreground capture stopped; label its real relative time.
        if (!hasSession || now < started || now - started > 120_000) return
        if (events.size == 128) events.removeFirst()
        events.addLast(DiagnosticEvent(now - started, type))
    }
    @Synchronized fun events(): List<DiagnosticEvent> = events.toList()
    @Synchronized fun elapsed(now: Long) = (now - started).coerceAtLeast(0)
    @Synchronized fun expired(now: Long) = active && now - started >= maxMillis
    @Synchronized fun stop(reason: String) { active = false; stopReason = reason.take(160) }
    @Synchronized fun frames(): List<DiagnosticFrame> = frames.toList()
    @Synchronized fun summary(): String = "Capture courte : ${if (active) "ACTIVE (90 s max)" else stopReason} ; ${frames.size} relevés, $dropped évincés. Aucun budget modifié."
}
