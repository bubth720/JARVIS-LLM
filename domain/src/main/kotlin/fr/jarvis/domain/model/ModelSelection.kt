package fr.jarvis.domain.model

import fr.jarvis.domain.ModelKind

/** Metadata is declarative; a verified hash does not prove architecture or Android inference. */
data class ModelDescriptor(
    val sha256: String, val sizeBytes: Long, val kind: ModelKind,
    val architecture: String, val quantization: String, val license: String,
    val source: String, val verifiedAtMillis: Long,
    val capabilities: Set<String> = setOf("text-generation")
) {
    init {
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(sizeBytes in 1024..kind.maxFileBytes && verifiedAtMillis >= 0)
        listOf(architecture, quantization, license, source).forEach {
            require(it.isNotBlank() && it.length <= 1024 && '\u0000' !in it)
        }
        require(capabilities.size <= 16 && capabilities.all { it.matches(Regex("[a-z-]{1,64}")) })
    }
    val opaqueUri: String get() = "jarvis-model:sha256:$sha256"
    override fun toString() = "ModelDescriptor(sha256=$sha256, qualification=UNQUALIFIED)"
}
enum class ModelQualificationState { IMPORTABLE, STATIC_COMPATIBLE, EXECUTABLE_UNQUALIFIED, INCOMPATIBLE }
data class CompatibilityResult(val state: ModelQualificationState, val reason: String)
object ModelCompatibilityChecker {
    fun check(model: ModelDescriptor, filePresent: Boolean): CompatibilityResult = when {
        !filePresent -> CompatibilityResult(ModelQualificationState.INCOMPATIBLE, "Fichier local absent ; réimporter une copie vérifiée")
        model.architecture != "qwen3" || model.quantization != "Q4_K_M" ->
            CompatibilityResult(ModelQualificationState.INCOMPATIBLE, "Architecture/quantification non admise par le pilote ; métadonnées déclaratives")
        else -> CompatibilityResult(ModelQualificationState.EXECUTABLE_UNQUALIFIED,
            "Empreinte vérifiée à l'import ; métadonnées déclaratives, inférence Android non qualifiée. Aucun chargement automatique")
    }
}
