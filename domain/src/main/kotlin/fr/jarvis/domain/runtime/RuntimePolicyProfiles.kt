package fr.jarvis.domain.runtime

import fr.jarvis.domain.*
import fr.jarvis.domain.resources.*

enum class RuntimePolicyProfile(val legacy: Profile) {
    TABLET_ECO(Profile.TABLET_ECO), MOBILE_ECO(Profile.ECO), EQUILIBRE(Profile.BALANCED),
    DEDICATED_FULL_POWER(Profile.FULL_POWER)
}
/** Named views of the ONE existing policy, never a second admission authority. */
object RuntimePolicyProfiles {
    fun androidDecision(profile: RuntimePolicyProfile, device: DeviceSnapshot, models: Set<ModelKind>, resident: Residency? = null): SafetyDecision =
        SafetyPolicy().decide(profile.legacy, device, models, resident)
    /** Future CENTRAL entry is a description only, with no selectable backend or increased budget. */
    val centralStatus: String get() = "UNQUALIFIED_NOT_ACTIVATABLE"
}

data class ReportedHardwareCapabilities(val arm64Reported: Boolean, val coresReported: Int,
                                        val totalMemoryReported: Long) { val androidQualified: Boolean get() = false }
object HardwareCapabilityDetector {
    /** Snapshot reports do not qualify an Android device, GPU, NPU, or concurrent residency. */
    fun reported(snapshot: DeviceSnapshot) = ReportedHardwareCapabilities(snapshot.abiSupported,
        snapshot.cores.coerceAtLeast(0), snapshot.totalBytes.coerceAtLeast(0))
}

enum class DeclaredBrokerActivity { EMPTY_UNATTESTED, HEAVY_DECLARED, MODEL_DECLARED, HEAVY_AND_MODEL_DECLARED }
enum class NativeProofStatus { UNATTESTED }
data class RuntimeObservation(val activity: DeclaredBrokerActivity, val heavyModule: Module?, val modelModule: Module?,
                              val nativeTermination: NativeProofStatus = NativeProofStatus.UNATTESTED,
                              val nativeUnload: NativeProofStatus = NativeProofStatus.UNATTESTED)
/** Passive projection only. Empty broker, requestStop, coroutine completion or a PID prove no native cleanup. */
object RuntimeResourceProjection {
    fun project(snapshot: ResourceState): RuntimeObservation = RuntimeObservation(when {
        snapshot.heavy != null && snapshot.model != null -> DeclaredBrokerActivity.HEAVY_AND_MODEL_DECLARED
        snapshot.heavy != null -> DeclaredBrokerActivity.HEAVY_DECLARED
        snapshot.model != null -> DeclaredBrokerActivity.MODEL_DECLARED
        else -> DeclaredBrokerActivity.EMPTY_UNATTESTED
    }, snapshot.heavy?.module, snapshot.model?.module)
}
/** No methods for allocation, admission, stop, reset, cleanup, generation, or confirmation. */
fun interface RuntimeBackendPort { fun observe(): RuntimeObservation }
