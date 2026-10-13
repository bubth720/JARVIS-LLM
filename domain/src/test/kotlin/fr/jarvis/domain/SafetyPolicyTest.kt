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
        assertNull(policy.decide(Profile.AUTO, normal.copy(availableBytes = RuntimeConfig.GIB), setOf(ModelKind.MAIN), 4L * RuntimeConfig.GIB).config)
        assertNotNull(policy.decide(Profile.AUTO, normal.copy(availableBytes = 2L * RuntimeConfig.GIB), setOf(ModelKind.MAIN), 4L * RuntimeConfig.GIB).config)
    }
    @Test fun heatWhileMainIsResidentRequiresStopBeforeFallback() {
        assertNull(policy.decide(Profile.AUTO, normal.copy(thermalStatus = 2), setOf(ModelKind.MAIN), 4L * RuntimeConfig.GIB).config)
    }
    @Test fun unavailableHeadroomDoesNotDiscardWorkingThermalStatus() {
        assertNotNull(policy.decide(Profile.ECO, normal.copy(thermalHeadroom = Float.NaN), both).config)
    }
    @Test fun ecoNeverUsesMainEvenIfSmallMissing() {
        assertNull(policy.decide(Profile.ECO, normal, setOf(ModelKind.MAIN)).config)
    }
    @Test fun smallResidencyCannotFundPromotionToMain() {
        val c = policy.decide(Profile.AUTO, normal.copy(availableBytes = 4L * RuntimeConfig.GIB), both, 2L * RuntimeConfig.GIB).config!!
        assertEquals(ModelKind.SMALL, c.model)
    }
}
