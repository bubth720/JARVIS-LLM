package fr.jarvis.domain

enum class Profile { ECO, BALANCED, AUTO, FULL_POWER }
enum class ModelKind(val fileName: String, val maxFileBytes: Long) {
    SMALL("qwen3-0.6b.gguf", 800L * 1024 * 1024),
    MAIN("qwen3-1.7b.gguf", 1600L * 1024 * 1024)
}
data class RuntimeConfig(val model: ModelKind, val context: Int, val maxTokens: Int, val threads: Int) {
    // Conservative prototype admission budgets; these are not measured peaks.
    val memoryBudget: Long get() = if (model == ModelKind.SMALL) 2L * GIB else 4L * GIB
    val batch: Int get() = if (model == ModelKind.SMALL) 32 else 64
    val microBatch: Int get() = if (model == ModelKind.SMALL) 8 else 32
    val idleMillis: Long get() = if (model == ModelKind.SMALL) 30_000 else 120_000
    companion object { const val GIB = 1024L * 1024 * 1024 }
}
data class DeviceSnapshot(
    val availableBytes: Long, val totalBytes: Long, val lowMemory: Boolean,
    val batteryPercent: Int?, val powerSave: Boolean, val thermalStatus: Int?,
    val thermalHeadroom: Float?, val foreground: Boolean,
    val abiSupported: Boolean, val cores: Int,
    val pssBytes: Long = 0, val androidLowMemoryThreshold: Long = 0
)
data class Residency(val model: ModelKind, val observedPssIncrease: Long)
data class MemoryAssessment(val reserve: Long, val estimatedBudget: Long, val measuredCredit: Long,
                            val available: Long) {
    val requiredAvailable: Long get() = reserve + estimatedBudget - measuredCredit
    val margin: Long get() = available - requiredAvailable
    val fits: Boolean get() = margin >= 0
}
data class SafetyDecision(val config: RuntimeConfig?, val reason: String, val memory: MemoryAssessment? = null)

class SafetyPolicy {
    fun memory(config: RuntimeConfig, s: DeviceSnapshot, resident: Residency? = null): MemoryAssessment {
        val reserve = maxOf(RuntimeConfig.GIB, s.totalBytes.coerceAtLeast(0) / 5,
            s.androidLowMemoryThreshold.coerceIn(0, s.totalBytes.coerceAtLeast(0)) + 256L * 1024 * 1024)
        val credit = if (resident?.model == config.model) resident.observedPssIncrease.coerceIn(0, minOf(config.memoryBudget, s.pssBytes.coerceAtLeast(0))) else 0
        return MemoryAssessment(reserve, config.memoryBudget, credit, s.availableBytes)
    }
    fun decide(profile: Profile, s: DeviceSnapshot, models: Set<ModelKind>, resident: Residency? = null): SafetyDecision {
        fun refuse(message: String) = SafetyDecision(null, message)
        if (!s.foreground) return refuse("Application en arrière-plan")
        if (!s.abiSupported) return refuse("ABI ARM64 requise")
        if (profile == Profile.FULL_POWER) return refuse("PLEINE PUISSANCE non qualifiée sur ce prototype mobile")
        if (s.thermalStatus == null || s.thermalStatus !in 0..6 || s.batteryPercent == null || s.batteryPercent !in 0..100) return refuse("Capteurs de sécurité indisponibles")
        if (s.thermalStatus >= 3) return refuse("État thermique sévère : calcul arrêté")
        if (s.batteryPercent < 10) return refuse("Batterie inférieure à 10 %")
        if (s.lowMemory || s.availableBytes <= 0 || s.totalBytes <= 0) return refuse("Pression mémoire")
        val headroom = s.thermalHeadroom?.takeIf { it.isFinite() && it >= 0 }
        if (headroom != null && headroom >= 1f) return refuse("Limite thermique prévue : calcul arrêté")
        val eco = profile == Profile.ECO || s.thermalStatus >= 1 || s.batteryPercent < 20 || s.powerSave || (headroom != null && headroom >= 0.8f)
        val small = RuntimeConfig(ModelKind.SMALL, 768, 96, 1)
        val main = RuntimeConfig(ModelKind.MAIN, 2048, 256, s.cores.coerceIn(1, 4))
        fun fits(config: RuntimeConfig): Boolean {
            return memory(config, s, resident).fits
        }
        val selected = if (!eco && ModelKind.MAIN in models && fits(main)) main
            else if (ModelKind.SMALL in models && fits(small)) small else null
        if (selected != null) return SafetyDecision(selected, if (selected.model == ModelKind.SMALL) "ÉCO renforcé / repli 0.6B" else "ÉQUILIBRÉ 1.7B", memory(selected, s, resident))
        val candidate = if (ModelKind.SMALL in models) small else if (!eco && ModelKind.MAIN in models) main else null
        if (candidate == null) return refuse("Modèle vérifié adapté au profil absent")
        val m = memory(candidate, s, resident)
        fun gib(bytes: Long) = "%.2f".format(java.util.Locale.FRANCE, bytes / RuntimeConfig.GIB.toDouble())
        return SafetyDecision(null, "RAM insuffisante : ${gib(m.available)} Gio disponibles ; ${gib(m.requiredAvailable)} Gio requis (budget estimé ${gib(m.estimatedBudget)}, réserve ${gib(m.reserve)}, crédit PSS mesuré ${gib(m.measuredCredit)}).", m)
    }
}
