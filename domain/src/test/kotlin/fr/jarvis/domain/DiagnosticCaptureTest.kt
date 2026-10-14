package fr.jarvis.domain

import org.junit.Assert.*
import org.junit.Test

class DiagnosticCaptureTest {
    private fun frame(ms:Long)=DiagnosticFrame(ms,MemoryPhase.PASSIVE,593L*1024*1024,650L*1024*1024,700L*1024*1024,
        3L*RuntimeConfig.GIB,8L*RuntimeConfig.GIB,256L*1024*1024,false,76,0,null,true,RuntimeConfig.GIB,2L*RuntimeConfig.GIB,0,
        3L*RuntimeConfig.GIB,0,RuntimeConfig(ModelKind.SMALL,512,64,1),false,Profile.TABLET_ECO,null,"aucun")
    @Test fun pssRssHighWaterAndBuffersRemainDistinct() {
        val p=ProcessMemory.parse("Name:\ttest\nVmRSS:\t665600 kB\nVmHWM:\t716800 kB\nVmSize:\t999999 kB")
        assertEquals(650L*1024*1024,p.rssBytes); assertEquals(700L*1024*1024,p.rssHighWaterBytes)
        assertNull(ProcessMemory.parse("VmRSS: wrong kB\nVmHWM: 8 bytes").rssBytes)
        assertNull(ProcessMemory.parse("VmRSS: 9223372036854775807 kB").rssBytes)
    }
    @Test fun captureIsBoundedAndDoesNotAutomaticallyChangePolicy() {
        val c=DiagnosticCapture(maxFrames=8,maxMillis=1000); c.start(100)
        repeat(20) { c.add(100L+it,frame(it.toLong())) }
        assertEquals(8,c.frames().size); assertEquals(12,c.dropped)
        assertEquals(2L*RuntimeConfig.GIB,c.frames().last().budgetBytes)
        c.add(1101,frame(1001)); assertFalse(c.active); assertEquals(8,c.frames().size)
    }
    @Test fun sessionResetDoesNotMixOldPeaksAndUnavailableMetricsAreNull() {
        val c=DiagnosticCapture(); c.start(0); c.add(1,frame(1))
        c.stop("manual"); c.add(2,frame(2)); assertEquals(1,c.frames().size)
        c.start(100); c.add(101,frame(1).copy(pssBytes=null,rssBytes=null))
        assertEquals(1,c.frames().size); assertNull(c.frames().single().pssBytes)
        assertEquals(listOf(DiagnosticEventType.START),c.events().map { it.type })
    }
    @Test fun lateCleanupCanBeRecordedButNeverKeepsSamplingInBackground() {
        val c=DiagnosticCapture(); c.start(0); c.stop("background")
        c.event(2000,DiagnosticEventType.MODEL_UNLOADED); c.add(2000,frame(2000))
        assertTrue(c.frames().isEmpty()); assertEquals(DiagnosticEventType.MODEL_UNLOADED,c.events().last().type)
        c.event(130000,DiagnosticEventType.MODEL_UNLOADED); assertEquals(2,c.events().size)
    }
}
