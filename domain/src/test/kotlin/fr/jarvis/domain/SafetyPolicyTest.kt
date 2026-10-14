package fr.jarvis.domain

import org.junit.Assert.*
import org.junit.Test

class SafetyPolicyTest {
    private val policy = SafetyPolicy()
    private val both = ModelKind.entries.toSet()
    private val normal = DeviceSnapshot(7L * RuntimeConfig.GIB, 8L * RuntimeConfig.GIB, false, 70, false, 0, 0.2f, true, true, 8)
    @Test fun normalPhoneUsesMainAndLimitsThreads() {
        val c = policy.decide(Profile.AUTO, normal, both).config!!
        assertEquals(ModelKind.MAIN, c.model); assertEquals(4, c.threads); assertEquals(2048, c.context)
    }
    @Test fun lowRamFallsBackInsteadOfUsingMemoryExtension() {
        val c = policy.decide(Profile.BALANCED, normal.copy(availableBytes = 3L * RuntimeConfig.GIB, totalBytes = 4L * RuntimeConfig.GIB), both).config!!
        assertEquals(ModelKind.SMALL, c.model)
    }
    @Test fun resourcesMissingNeverTriggerLoad() {
        val inputs = listOf(normal.copy(thermalStatus = null), normal.copy(batteryPercent = null), normal.copy(abiSupported = false),
            normal.copy(totalBytes = 0), normal.copy(availableBytes = 0), normal.copy(lowMemory = true), normal.copy(foreground = false),
            normal.copy(thermalStatus = -1), normal.copy(batteryPercent = 101))
        inputs.forEach { assertNull(policy.decide(Profile.AUTO, it, both).config) }
        assertNull(policy.decide(Profile.AUTO, normal, emptySet()).config)
    }
    @Test fun severeThermalAndLowBatteryStopEveryMobileProfile() {
        for (profile in Profile.entries) {
            assertNull(policy.decide(profile, normal.copy(thermalStatus = 3), both).config)
            assertNull(policy.decide(profile, normal.copy(batteryPercent = 9), both).config)
            assertNull(policy.decide(profile, normal.copy(thermalHeadroom = 1f), both).config)
        }
    }
    @Test fun powerSaveWarmBatteryAndForecastSelectEco() {
        val snapshots = listOf(normal.copy(powerSave = true), normal.copy(thermalStatus = 1), normal.copy(thermalStatus = 2),
            normal.copy(batteryPercent = 19), normal.copy(thermalHeadroom = 0.8f))
        snapshots.forEach { assertEquals(ModelKind.SMALL, policy.decide(Profile.AUTO, it, both).config!!.model) }
    }
    @Test fun fullPowerCannotBypassProtections() { assertNull(policy.decide(Profile.FULL_POWER, normal, both).config) }
    @Test fun memoryReserveIsMaintainedForResidentModel() {
        val loaded = normal.copy(pssBytes = 2L * RuntimeConfig.GIB)
        assertNull(policy.decide(Profile.AUTO, loaded.copy(availableBytes = RuntimeConfig.GIB), setOf(ModelKind.MAIN), Residency(ModelKind.MAIN, 2L * RuntimeConfig.GIB)).config)
        assertNotNull(policy.decide(Profile.AUTO, loaded.copy(availableBytes = 4L * RuntimeConfig.GIB), setOf(ModelKind.MAIN), Residency(ModelKind.MAIN, 2L * RuntimeConfig.GIB)).config)
    }
    @Test fun heatWhileMainIsResidentRequiresStopBeforeFallback() {
        assertNull(policy.decide(Profile.AUTO, normal.copy(thermalStatus = 2, pssBytes = 2L * RuntimeConfig.GIB), setOf(ModelKind.MAIN), Residency(ModelKind.MAIN, 2L * RuntimeConfig.GIB)).config)
    }
    @Test fun unavailableHeadroomDoesNotDiscardWorkingThermalStatus() {
        assertNotNull(policy.decide(Profile.ECO, normal.copy(thermalHeadroom = Float.NaN), both).config)
    }
    @Test fun ecoNeverUsesMainEvenIfSmallMissing() {
        assertNull(policy.decide(Profile.ECO, normal, setOf(ModelKind.MAIN)).config)
    }
    @Test fun smallResidencyCannotFundPromotionToMain() {
        val c = policy.decide(Profile.AUTO, normal.copy(availableBytes = 4L * RuntimeConfig.GIB, pssBytes = 2L * RuntimeConfig.GIB), both, Residency(ModelKind.SMALL, 2L * RuntimeConfig.GIB)).config!!
        assertEquals(ModelKind.SMALL, c.model)
    }
    @Test fun reportedPocoStillRefusedWithoutMeasuredModelEvidence() {
        val poco = normal.copy(totalBytes = (11.06 * RuntimeConfig.GIB).toLong(), availableBytes = (3.68 * RuntimeConfig.GIB).toLong())
        val d = policy.decide(Profile.ECO, poco, both)
        assertNull(d.config)
        assertEquals(4.212, d.memory!!.requiredAvailable.toDouble() / RuntimeConfig.GIB, 0.001)
        assertEquals(-0.532, d.memory.margin.toDouble() / RuntimeConfig.GIB, 0.001)
        assertEquals(0L, d.memory.measuredCredit)
        assertTrue(d.reason.contains("budget estimé"))
    }
    @Test fun observedResidencyNeverCreditsMoreThanMeasuredPss() {
        val s = normal.copy(pssBytes = 128L * 1024 * 1024)
        val m = policy.memory(RuntimeConfig(ModelKind.SMALL, 768, 96, 1), s, Residency(ModelKind.SMALL, 4L * RuntimeConfig.GIB))
        assertEquals(s.pssBytes, m.measuredCredit)
    }
    @Test fun androidPressureThresholdOnlyRaisesReserve() {
        val s = normal.copy(androidLowMemoryThreshold = 3L * RuntimeConfig.GIB)
        val m = policy.memory(RuntimeConfig(ModelKind.SMALL, 768, 96, 1), s)
        assertEquals(3L * RuntimeConfig.GIB + 256L * 1024 * 1024, m.reserve)
    }
    @Test fun strengthenedEcoSettingsDoNotChangeAdmissionBudget() {
        val c = policy.decide(Profile.ECO, normal, both).config!!
        assertEquals(768, c.context); assertEquals(96, c.maxTokens); assertEquals(1, c.threads)
        assertEquals(32, c.batch); assertEquals(8, c.microBatch); assertEquals(2L * RuntimeConfig.GIB, c.memoryBudget)
    }
}
