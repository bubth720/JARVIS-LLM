package fr.jarvis.llm.diagnostic

import android.os.Build
import fr.jarvis.domain.*
import org.json.JSONArray
import org.json.JSONObject

object DiagnosticJson {
    fun export(capture: DiagnosticCapture, profile: Profile, models: Map<ModelKind, Pair<String?, Long>>,
               appVersion: String, sourceCommit: String, startedWithResident: Boolean, modelsAtStart: Map<ModelKind, Pair<String?, Long>>): String {
        val frames = capture.frames()
        val events = capture.events()
        return JSONObject().put("formatVersion", 1).put("appVersion", appVersion).put("sourceCommit", sourceCommit)
            .put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("android", Build.VERSION.RELEASE).put("api", Build.VERSION.SDK_INT).put("firmware", Build.DISPLAY)
                .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())))
            .put("profileAtExport", profile.name).put("startedWithResidentModel", startedWithResident)
            .put("stopReason", capture.stopReason).put("droppedFrames", capture.dropped)
            .put("sampling", "500 ms foreground capture target; phase boundaries; sampled PSS, NOT continuous peak; scheduling may delay samples")
            .put("policyChanged", false).put("requiresHumanReviewBeforeNewBudget", true)
            .put("modelsAtCaptureStart", modelJson(modelsAtStart))
            .put("modelsAtExport", modelJson(models))
            .put("events", JSONArray(events.map { JSONObject().put("elapsedMs", it.elapsedMillis).put("type", it.type.name) }))
            .put("frames", JSONArray(frames.map { f ->
                JSONObject().put("elapsedMs", f.elapsedMillis).put("phase", f.phase.name)
                    .put("pssBytes", f.pssBytes ?: JSONObject.NULL).put("rssBytes", f.rssBytes ?: JSONObject.NULL)
                    .put("rssLifetimeHighWaterBytes", f.rssHighWaterBytes ?: JSONObject.NULL)
                    .put("availableBytes", f.availableBytes).put("totalBytes", f.totalBytes)
                    .put("androidLowMemoryThreshold", f.lowMemoryThreshold).put("lowMemory", f.lowMemory)
                    .put("batteryPercent", f.batteryPercent ?: JSONObject.NULL).put("thermalStatus", f.thermalStatus ?: JSONObject.NULL)
                    .put("thermalHeadroom", f.thermalHeadroom ?: JSONObject.NULL).put("foreground", f.foreground)
                    .put("systemReserveBytes", f.reserveBytes).put("admissionBudgetBytes", f.budgetBytes)
                    .put("observedResidencyCreditBytes", f.measuredCredit).put("requiredAvailableBytes", f.requiredAvailableBytes)
                    .put("admissionMarginBytes", f.admissionMargin)
                    .put("configurationIsActual", f.configurationIsActual).put("profile", f.profile.name)
                    .put("configuration", JSONObject().put("model", f.configuration.model.name).put("context", f.configuration.context)
                        .put("maxOutputTokens", f.configuration.maxTokens).put("batch", f.configuration.batch)
                        .put("microBatch", f.configuration.microBatch).put("threads", f.configuration.threads))
                    .put("nativeBuffersLastObserved", f.nativeAllocations?.let { JSONObject().put("modelBytes", it.model)
                        .put("contextBytes", it.context).put("computeBytes", it.compute) } ?: JSONObject.NULL)
                    .put("resources", f.resources)
            }))
            .put("pssPeakSampledBytes", frames.mapNotNull { it.pssBytes }.maxOrNull() ?: JSONObject.NULL)
            .put("minimumAvailableBytes", frames.minOfOrNull { it.availableBytes } ?: JSONObject.NULL)
            .put("evidenceWarnings", JSONArray(buildList {
                add("PSS, RSS and native buffers overlap; never add them together.")
                add("RSS high water is since process creation, including phases outside this capture.")
                add("Expected GGUF digest is import metadata; hash is reverified only before an actual cold load.")
                add("No automatic recommendation or admission override is produced.")
                if (frames.any { it.pssBytes == null }) add("Some PSS samples are unavailable.")
                if (capture.dropped > 0) add("Frames were evicted: this capture is incomplete.")
                if (events.none { it.type == DiagnosticEventType.GENERATION_SUCCEEDED }) add("No successful generation was recorded.")
                if (events.none { it.type == DiagnosticEventType.MODEL_UNLOADED }) add("Native unload completion was not recorded.")
                if (modelsAtStart != models) add("Model import metadata changed during this session; do not qualify a budget from it.")
                if (frames.map { it.profile }.distinct().size > 1 || frames.filter { it.configurationIsActual }.map { it.configuration }.distinct().size > 1)
                    add("Mixed profiles or actual configurations; do not qualify a single budget from this session.")
                if (startedWithResident) add("This is not a cold baseline.")
            })).toString(2)
    }
    private fun modelJson(models: Map<ModelKind, Pair<String?, Long>>) = JSONArray(models.map { (kind, data) ->
        JSONObject().put("kind", kind.name).put("expectedSha256", data.first ?: JSONObject.NULL).put("fileBytes", data.second)
    })
}
