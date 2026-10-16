package fr.jarvis.llm

import fr.jarvis.domain.*
import fr.jarvis.llm.diagnostic.DiagnosticJson
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Entirely synthetic input. No fixture value is an Android hardware observation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticExportTest {
    private val refs = mapOf(ModelKind.SMALL to ("a".repeat(64) to 1234L))
    private val config = RuntimeConfig(ModelKind.SMALL, 512, 64, 1)
    private fun frame(elapsed: Long) = DiagnosticFrame(elapsed, MemoryPhase.LOADING,
        100, 200, 300, 400, 1000, 50, false, null, null, null, true,
        20, 500, 0, 520, -120, config, true, Profile.TABLET_ECO,
        NativeAllocations(30, 40, 50), "synthetic owner-free resource summary")
    private fun export(c: DiagnosticCapture, current: Map<ModelKind, Pair<String?, Long>> = refs) =
        JSONObject(DiagnosticJson.export(c, Profile.TABLET_ECO, current, "synthetic", "test-commit", false, refs))
    @Test fun unitsNullsPolicyArithmeticAndNativeBuffersStaySeparate() {
        val c = DiagnosticCapture(); c.start(1000); c.add(1010, frame(10))
        c.event(1020, DiagnosticEventType.MODEL_LOADED); c.event(1030, DiagnosticEventType.GENERATION_SUCCEEDED)
        c.event(1040, DiagnosticEventType.STOP_REQUESTED); c.event(1050, DiagnosticEventType.MODEL_UNLOADED)
        c.stop("synthetic test")
        val report = export(c); val f = report.getJSONArray("frames").getJSONObject(0)
        assertEquals(100, report.getLong("pssPeakSampledBytes"))
        assertEquals(200, f.getLong("rssBytes")); assertEquals(300, f.getLong("rssLifetimeHighWaterBytes"))
        assertEquals(30, f.getJSONObject("nativeBuffersLastObserved").getLong("modelBytes"))
        assertTrue(f.isNull("batteryPercent")); assertTrue(f.isNull("thermalStatus")); assertTrue(f.isNull("thermalHeadroom"))
        assertEquals(f.getLong("availableBytes") - f.getLong("requiredAvailableBytes"), f.getLong("admissionMarginBytes"))
        assertFalse(report.getBoolean("policyChanged")); assertTrue(report.getBoolean("requiresHumanReviewBeforeNewBudget"))
        assertEquals(50, report.getJSONArray("events").getJSONObject(4).getLong("elapsedMs"))
        assertFalse(report.has("messages")); assertFalse(report.has("prompt")); assertFalse(report.has("serial"))
    }
    @Test fun missingPssIsNullNotZeroAndChangedModelCannotLookLikeStableEvidence() {
        val c = DiagnosticCapture(); c.start(0); c.add(10, frame(10).copy(pssBytes=null,rssBytes=null,rssHighWaterBytes=null,nativeAllocations=null))
        val r = export(c, mapOf(ModelKind.SMALL to ("b".repeat(64) to 1234L)))
        assertTrue(r.isNull("pssPeakSampledBytes"))
        val f = r.getJSONArray("frames").getJSONObject(0)
        assertTrue(f.isNull("rssBytes")); assertTrue(f.isNull("nativeBuffersLastObserved"))
        val warnings = r.getJSONArray("evidenceWarnings").toString()
        assertTrue(warnings.contains("changed")); assertTrue(warnings.contains("unavailable"))
        assertEquals("a".repeat(64), r.getJSONArray("modelsAtCaptureStart").getJSONObject(0).getString("expectedSha256"))
    }
    @Test fun truncatedCaptureAndMixedProfilesAreExplicitlyFlagged() {
        val c = DiagnosticCapture(maxFrames=8); c.start(0)
        repeat(9) { n -> c.add(n.toLong(), frame(n.toLong()).copy(profile=if(n==0) Profile.TABLET_ECO else Profile.ECO)) }
        c.add(10, frame(10))
        val r = export(c)
        assertEquals(2, r.getInt("droppedFrames"))
        val warnings = r.getJSONArray("evidenceWarnings").toString()
        assertTrue(warnings.contains("evicted")); assertTrue(warnings.contains("Mixed profiles"))
    }
}
