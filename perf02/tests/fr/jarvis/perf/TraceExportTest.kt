package fr.jarvis.perf

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TraceExportTest {
    @Test fun metadataOnlyNoInventedTokenSensorOrCore() {
        val k = TraceKey(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
            UUID.randomUUID().toString(), "a".repeat(64))
        val t = LatencyTrace(k, MonotonicClock { 1 }, ClockKind.SYNTHETIC, Origin.SYNTHETIC, Residency.UNKNOWN)
        t.record(k, Stage.UI_SEND)
        val line = TraceExport.line(t.snapshot(), BenchCase("b".repeat(64), "c".repeat(64), "d".repeat(64), BenchProfile.MOBILE_ECO, 8, "e".repeat(40)))
        assertTrue(line.contains("\"native_first_token_status\":\"UNSUPPORTED\""))
        assertTrue(line.contains("\"core_accept_status\":\"UNSUPPORTED\""))
        assertTrue(line.contains("\"input_tokens\":null")); assertTrue(line.contains("\"pss_bytes\":null"))
        assertFalse(line.contains("prompt")); assertFalse(line.contains("path")); assertFalse(line.contains("text\":"))
    }
    @Test fun callerCannotSmuggleTextInMetadata() {
        assertThrows(IllegalArgumentException::class.java) { BenchCase("private/path", "c".repeat(64), "d".repeat(64), BenchProfile.EQUILIBRE, 8, "e".repeat(40)) }
        assertThrows(IllegalArgumentException::class.java) { BenchCase("b".repeat(64), "c".repeat(64), "d".repeat(64), BenchProfile.EQUILIBRE, 256, "e".repeat(40)) }
    }
}
