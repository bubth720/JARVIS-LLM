package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import org.junit.Assert.*
import org.junit.Test

class LabHostBudgetTest {
    private val config=RuntimeConfig(ModelKind.SMALL,512,8,1)
    private val gib=RuntimeConfig.GIB
    @Test fun adequateObservedHeadroomKeepsExistingBudget() { LabHostBudget.assess(config,8*gib,6*gib,gib);assertEquals(2*gib,config.memoryBudget) }
    @Test fun lowAvailableMemoryRefuses() { assertTrue(runCatching { LabHostBudget.assess(config,8*gib,2*gib,gib) }.isFailure) }
    @Test fun rssBeyondExistingBudgetRefuses() { assertTrue(runCatching { LabHostBudget.assess(config,8*gib,6*gib,2*gib+1) }.isFailure) }
    @Test fun unknownAndContradictoryObservationRefuses() {
        assertTrue(runCatching { LabHostBudget.assess(config,0,0,0) }.isFailure)
        assertTrue(runCatching { LabHostBudget.assess(config,8*gib,9*gib,0) }.isFailure)
        assertTrue(runCatching { LabHostBudget.assess(config,8*gib,6*gib,-1) }.isFailure)
    }
    @Test fun minimumOneGiBReserveIsNotRemoved() { assertTrue(runCatching { LabHostBudget.assess(config,3*gib,3*gib-1,0) }.isFailure) }
    private fun linux(leaf: String="max", parent: String=(4*gib).toString(), group: String="/job"): Map<String,String> = mapOf(
        "/proc/meminfo" to "MemTotal: 8388608 kB\nMemAvailable: 6291456 kB\n",
        "/proc/self/status" to "VmRSS: 524288 kB\n",
        "/proc/self/cgroup" to "0::$group\n",
        "/proc/self/mountinfo" to "23 1 0:1 / /cg rw - cgroup2 cgroup rw\n",
        "/cg/job/memory.max" to leaf,"/cg/job/memory.current" to gib.toString(),
        "/cg/memory.max" to parent,"/cg/memory.current" to gib.toString())
    private fun checkLinux(data: Map<String,String>) = LabHostBudget.assessLinux(config,{ data.getValue(it.toString()) },{true})
    @Test fun cgroupAncestorLimitIsObservedEvenWhenLeafIsMax() {
        checkLinux(linux());assertTrue(runCatching { checkLinux(linux(parent=(3*gib).toString())) }.isFailure)
    }
    @Test fun tighterLeafIsAlsoObserved() { assertTrue(runCatching { checkLinux(linux(leaf=(2*gib).toString())) }.isFailure) }
    @Test fun absentCgroupOrMemoryObservationNeverFallsBack() { assertTrue(runCatching { checkLinux(linux()-"/cg/memory.max") }.isFailure) }
    @Test fun escapingOrUnmappedCgroupRefuses() { assertTrue(runCatching { checkLinux(linux(group="/../foreign")) }.isFailure) }
    @Test fun contradictoryProcDataIsNotSilentlyClamped() {
        assertTrue(runCatching { checkLinux(linux()+mapOf("/proc/meminfo" to "MemTotal: 8388608 kB\nMemAvailable: 999999999 kB\n")) }.isFailure)
    }
    @Test fun hostMonitorFailureRevokesAndCancels() {
        val f=LabFixtures();val a=f.attempt();val stop=java.util.concurrent.CountDownLatch(1)
        try { a.attachStop(monitor={ error("UNIT_MEMORY_PRESSURE") }) { stop.countDown() }
            assertTrue(stop.await(3,java.util.concurrent.TimeUnit.SECONDS));assertFalse(a.isLive());assertEquals("LAB_HOST_MONITOR_REFUSED",a.faultCode)
        }finally { a.close() }
    }
}
