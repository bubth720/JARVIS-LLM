package fr.jarvis.domain

enum class MemoryPhase { PASSIVE, VERIFYING, LOADING, GENERATING, UNLOADING }
/** Sampled PSS is not a continuous peak and is not the GGUF file size or RSS high-water mark. */
class MemoryTelemetry {
    var phase = MemoryPhase.PASSIVE
        private set
    var currentPss = 0L
        private set
    var peakPss = 0L
        private set
    var minimumAvailable: Long? = null
        private set
    var baselinePss: Long? = null
        private set
    var samples = 0
        private set
    private val peaks = mutableMapOf<MemoryPhase, Long>()
    @Synchronized fun begin() {
        baselinePss = currentPss; peakPss = currentPss; minimumAvailable = null; samples = 0; peaks.clear()
    }
    @Synchronized fun phase(value: MemoryPhase) { phase = value }
    @Synchronized fun sample(pss: Long, available: Long) {
        if (pss < 0 || available < 0) return
        currentPss = pss; peakPss = maxOf(peakPss, pss)
        minimumAvailable = minOf(minimumAvailable ?: available, available)
        peaks[phase] = maxOf(peaks[phase] ?: 0, pss); samples++
    }
    @Synchronized fun summary(): String {
        fun mib(bytes: Long?) = bytes?.let { "%.1f".format(java.util.Locale.FRANCE, it / 1048576.0) } ?: "non mesuré"
        return "PSS actuel / pic échantillonné : ${mib(currentPss)} / ${mib(peakPss)} Mio\n" +
            "PSS début d'opération : ${mib(baselinePss)} Mio ; minimum RAM disponible : ${mib(minimumAvailable)} Mio\n" +
            "Phase : $phase ; échantillons : $samples (1 s actif / 2 s résident ; pics transitoires possibles)\n" +
            peaks.entries.joinToString(" ; ") { "${it.key}=${mib(it.value)} Mio" }
    }
}
