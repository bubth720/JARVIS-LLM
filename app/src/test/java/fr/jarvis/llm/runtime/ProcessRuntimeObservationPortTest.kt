package fr.jarvis.llm.runtime

import fr.jarvis.domain.resources.Module
import fr.jarvis.domain.runtime.*
import fr.jarvis.llm.ProcessResources
import org.junit.Assert.*
import org.junit.Test

class ProcessRuntimeObservationPortTest {
    @Test fun adapterReadsActualSharedBrokerWithoutLoadingOrAuthorizingNativeWork() {
        val port=ProcessRuntimeObservationPort()
        val before=port.observe()
        ProcessResources.requireHeavy("synthetic-observer-test",Module.MODEL_IMPORT,"synthetic observation").use {
            val during=port.observe()
            assertEquals(Module.MODEL_IMPORT,during.heavyModule)
            assertEquals(NativeProofStatus.UNATTESTED,during.nativeUnload)
            assertFalse(during.toString().contains("synthetic-observer-test"))
        }
        assertEquals(before,port.observe())
    }
}
