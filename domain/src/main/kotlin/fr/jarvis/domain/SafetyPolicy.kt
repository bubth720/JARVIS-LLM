package fr.jarvis.domain

enum class Profile { ECO, BALANCED, AUTO, FULL_POWER }
enum class ModelKind(val fileName: String, val maxFileBytes: Long) {
    SMALL("qwen3-0.6b.gguf", 800L * 1024 * 1024),
    MAIN("qwen3-1.7b.gguf", 1600L * 1024 * 1024)
}
data class RuntimeConfig(val model: ModelKind, val context: Int, val maxTokens: Int, val threads: Int) {
    // Conservative prototype admission budgets; these are not measured peaks.
    val memoryBudget: Long get() = if (model == ModelKind.SMALL) 2L * GIB else 4L * GIB
    val idleMillis: Long get() = if (model == ModelKind.SMALL) 30_000 else 120_000
    companion object { const val GIB = 1024L * 1024 * 1024 }
}
data class DeviceSnapshot(
    val availableBytes: Long, val totalBytes: Long, val lowMemory: Boolean,
    val batteryPercent: Int?, val powerSave: Boolean, val thermalStatus: Int?,
    val thermalHeadroom: Float?, val foreground: Boolean,
    val abiSupported: Boolean, val cores: Int
)
data class SafetyDecision(val config: RuntimeConfig?, val reason: String)

class SafetyPolicy {
    fun decide(profile: Profile, s: DeviceSnapshot, models: Set<ModelKind>, residentBudget: Long = 0): SafetyDecision {
        fun refuse(message: String) = SafetyDecision(null, message)
        if (!s.foreground) return refuse("Application en arrière-plan")
        if (!s.abiSupported) return refuse("ABI ARM64 requise")
        if (profile == Profile.FULL_POWER) return refuse("PLEINE PUISSANCE non qualifiée sur ce prototype mobile")
        if (s.thermalStatus == null || s.thermalStatus !in 0..6 || s.batteryPercent == null || s.batteryPercent !in 0..100) return refuse("Capteurs de sécurité indisponibles")
        if (s.thermalStatus >= 3) return refuse("État thermique sévère : calcul arrêté")
        if (s.batteryPercent < 10) return refuse("Batterie inférieure à 10 %")
        if (s.lowMemory || s.availableBytes <= 0 || s.totalBytes <= 0) return refuse("Pression mémoire")
        val reserve = maxOf(RuntimeConfig.GIB, s.totalBytes / 5)
        val headroom = s.thermalHeadroom?.takeIf { it.isFinite() && it >= 0 }
        if (headroom != null && headroom >= 1f) return refuse("Limite thermique prévue : calcul arrêté")
        val eco = profile == Profile.ECO || s.thermalStatus >= 1 || s.batteryPercent < 20 || s.powerSave || (headroom != null && headroom >= 0.8f)
        val small = RuntimeConfig(ModelKind.SMALL, 1024, 128, s.cores.coerceIn(1, 2))
        val main = RuntimeConfig(ModelKind.MAIN, 2048, 256, s.cores.coerceIn(1, 4))
        fun fits(config: RuntimeConfig): Boolean {
            // Credit residency only for the same model budget; never fund a larger model from an estimate.
            val credit = if (residentBudget == config.memoryBudget) residentBudget else 0
            return s.availableBytes + credit - reserve >= config.memoryBudget
        }
        val selected = if (!eco && ModelKind.MAIN in models && fits(main)) main
            else if (ModelKind.SMALL in models && fits(small)) small else null
        return if (selected == null) refuse("Modèle vérifié absent ou marge mémoire insuffisante")
            else SafetyDecision(selected, if (selected.model == ModelKind.SMALL) "ÉCO / repli 0.6B" else "ÉQUILIBRÉ 1.7B")
    }
}
