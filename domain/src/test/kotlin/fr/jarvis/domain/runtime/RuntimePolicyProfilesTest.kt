package fr.jarvis.domain.runtime

import fr.jarvis.domain.*
import fr.jarvis.domain.resources.*
import org.junit.Assert.*
import org.junit.Test

class RuntimePolicyProfilesTest {
    private fun device(thermal: Int? = 0,battery: Int? = 80,available: Long = 8*RuntimeConfig.GIB,abi: Boolean = true) = DeviceSnapshot(
        available,12*RuntimeConfig.GIB,false,battery,false,thermal,null,true,abi,8)
    @Test fun profilesDelegateExactlyToExistingPolicyIncludingRefusalsAndBudgets() {
        for (p in RuntimePolicyProfile.entries) for (d in listOf(device(),device(thermal=3),device(thermal=null),device(battery=9),device(available=1),device(abi=false))) {
            val models=ModelKind.entries.toSet()
            assertEquals(SafetyPolicy().decide(p.legacy,d,models),RuntimePolicyProfiles.androidDecision(p,d,models))
        }
    }
    @Test fun fullPowerCannotActivateAndCentralRemainsUnqualified() {
        assertNull(RuntimePolicyProfiles.androidDecision(RuntimePolicyProfile.DEDICATED_FULL_POWER,device(),ModelKind.entries.toSet()).config)
        assertEquals("UNQUALIFIED_NOT_ACTIVATABLE",RuntimePolicyProfiles.centralStatus)
        assertFalse(HardwareCapabilityDetector.reported(device()).androidQualified)
    }
    @Test fun processBrokerLifecycleIsProjectedWithoutNativeProofOrNewAuthority() {
        val broker=ResourceCoordinator()
        fun observed()=RuntimeResourceProjection.project(broker.snapshot())
        assertEquals(DeclaredBrokerActivity.EMPTY_UNATTESTED,observed().activity)
        val heavy=(broker.acquire(ResourceRequest("fixture-owner",Module.LLM,ResourceClass.HEAVY,"synthetic load")) as ResourceGrant.Granted).lease
        assertEquals(DeclaredBrokerActivity.HEAVY_DECLARED,observed().activity)
        val model=(broker.acquire(ResourceRequest("fixture-owner",Module.LLM,ResourceClass.MODEL,"synthetic residency",modelKey="SMALL")) as ResourceGrant.Granted).lease
        assertEquals(DeclaredBrokerActivity.HEAVY_AND_MODEL_DECLARED,observed().activity)
        heavy.close(); assertEquals(DeclaredBrokerActivity.MODEL_DECLARED,observed().activity)
        assertTrue(broker.acquire(ResourceRequest("other",Module.VOICE,ResourceClass.HEAVY,"synthetic refusal")) is ResourceGrant.Refused)
        model.close(); assertEquals(DeclaredBrokerActivity.EMPTY_UNATTESTED,observed().activity)
        assertEquals(NativeProofStatus.UNATTESTED,observed().nativeUnload)
        assertEquals(NativeProofStatus.UNATTESTED,observed().nativeTermination)
        assertFalse(observed().toString().contains("fixture-owner"))
    }
    @Test fun residencyCreditAndTabletLimitsAreUnchanged() {
        val d=device().copy(pssBytes=RuntimeConfig.GIB)
        val resident=Residency(ModelKind.SMALL,RuntimeConfig.GIB)
        assertEquals(SafetyPolicy().decide(Profile.TABLET_ECO,d,setOf(ModelKind.SMALL),resident),
            RuntimePolicyProfiles.androidDecision(RuntimePolicyProfile.TABLET_ECO,d,setOf(ModelKind.SMALL),resident))
        val cfg=RuntimePolicyProfiles.androidDecision(RuntimePolicyProfile.TABLET_ECO,d,setOf(ModelKind.SMALL),resident).config!!
        assertEquals(512,cfg.context);assertEquals(64,cfg.maxTokens);assertEquals(1,cfg.threads)
    }
}
