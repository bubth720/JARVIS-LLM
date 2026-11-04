package fr.jarvis.perf

enum class BenchProfile { MOBILE_ECO, TABLET_ECO, EQUILIBRE }
class BenchCase(val environmentSha256: String, val configSha256: String, val corpusSha256: String,
    val profile: BenchProfile, val outputCap: Int, val codeCommit: String) {
    init {
        listOf(environmentSha256, configSha256, corpusSha256).forEach { require(it.matches(Regex("[a-f0-9]{64}"))) }
        require(codeCommit.matches(Regex("[a-f0-9]{40}"))); require(outputCap in setOf(8, 24))
    }
}
/** Metadata-only JSONL. All strings are enums, validated UUID/digests or constants, never text/paths. */
object TraceExport {
    fun line(trace: TraceSnapshot, case: BenchCase): String = buildString {
        append("{\"schema\":\"jarvis.perf02.trace/1\",\"origin\":\"").append(trace.origin.name)
        append("\",\"clock\":\"").append(trace.clock.name).append("\",\"clock_scope\":\"").append(trace.scope)
        append("\",\"residency\":\"").append(trace.residency.name).append("\",\"state\":\"").append(trace.state.name)
        append("\",\"native_first_token_status\":\"").append(trace.nativeFirstToken.name)
        append("\",\"core_accept_status\":\"").append(trace.coreAcceptance.name)
        append("\",\"native_drain_status\":\"").append(trace.nativeDrain.name)
        append("\",\"native_unload_status\":\"").append(trace.nativeUnload.name)
        append("\",\"failure\":").append(trace.failure?.let { "\"${it.name}\"" } ?: "null")
        with(trace.key) {
            append(",\"key\":{\"request\":\"").append(request).append("\",\"session\":\"").append(session)
            append("\",\"epoch\":\"").append(epoch).append("\",\"lease\":\"").append(lease)
            append("\",\"generation\":").append(generation).append(",\"model_sha256\":\"").append(modelSha256).append("\"}")
        }
        append(",\"case\":{\"environment_sha256\":\"").append(case.environmentSha256)
        append("\",\"config_sha256\":\"").append(case.configSha256).append("\",\"corpus_sha256\":\"").append(case.corpusSha256)
        append("\",\"profile\":\"").append(case.profile.name).append("\",\"output_cap\":").append(case.outputCap)
        append(",\"code_commit\":\"").append(case.codeCommit).append("\"}")
        append(",\"events\":{")
        append(trace.events.entries.sortedBy { it.key.ordinal }.joinToString(",") { "\"${it.key.name}\":${it.value}" })
        append("},\"input_tokens\":null,\"output_tokens\":null,\"tokenizer_status\":\"UNKNOWN\"")
        append(",\"sensors\":{\"rss_bytes\":null,\"pss_bytes\":null,\"thermal_status\":null,\"battery_percent\":null,\"energy_uj\":null}}")
    }
}
