package fr.jarvis.domain
import org.junit.Assert.*
import org.junit.Test
class MemoryTelemetryTest {
    @Test fun recordsSampledPeakAndMinimumAcrossPhases() {
        val t = MemoryTelemetry()
        t.sample(100, 1000); t.begin()
        t.phase(MemoryPhase.LOADING); t.sample(400, 700)
        t.phase(MemoryPhase.GENERATING); t.sample(300, 800)
        t.phase(MemoryPhase.UNLOADING); t.sample(120, 950)
        assertEquals(400L, t.peakPss); assertEquals(100L, t.baselinePss)
        assertEquals(700L, t.minimumAvailable); assertEquals(3, t.samples)
        assertTrue(t.summary().contains("pic échantillonné"))
    }
    @Test fun newOperationResetsPeakWithoutInventingSamples() {
        val t = MemoryTelemetry(); t.sample(400, 1000); t.sample(100, 1200); t.begin()
        assertEquals(100L, t.peakPss); assertEquals(0, t.samples); assertNull(t.minimumAvailable)
    }
    @Test fun invalidMeasurementIsIgnored() {
        val t = MemoryTelemetry(); t.sample(-1, 100); t.sample(100, -1)
        assertEquals(0, t.samples)
    }
}
