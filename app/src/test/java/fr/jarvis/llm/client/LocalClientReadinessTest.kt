package fr.jarvis.llm.client

import fr.jarvis.domain.ModelKind
import fr.jarvis.domain.model.ModelDescriptor
import fr.jarvis.domain.resources.*
import fr.jarvis.domain.runtime.*
import fr.jarvis.llm.ProcessResources
import fr.jarvis.llm.model.VerifiedModelCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29,35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalClientReadinessTest {
    @Test fun selectionIsPassiveAndUsesExistingCatalogueWithoutChangingIt() {
        val context=RuntimeEnvironment.getApplication();val name="fusion-readiness.db";context.deleteDatabase(name)
        VerifiedModelCatalog(context,name).use { c ->
            val port=LocalClientReadinessPort(c)
            assertEquals(CatalogueSelectionState.NONE,port.observe().selection)
            val model=ModelDescriptor("a".repeat(64),2048,ModelKind.SMALL,"qwen3","Q4_K_M","synthetic","synthetic",0)
            c.record(model);c.select(model.sha256)
            val result=port.observe()
            assertEquals(CatalogueSelectionState.CATALOGUE_SELECTED_NOT_RUNNING,result.selection)
            assertFalse(result.capabilities.nativeReady);assertFalse(result.fileRevalidatedNow)
            assertEquals(listOf(model),c.list());assertEquals(model.sha256,c.selected())
            assertEquals(DeclaredBrokerActivity.EMPTY_UNATTESTED,result.resources.observation!!.activity)
            assertFalse(result.resources.mayAdmitAnotherEngine)
        }
    }
    @Test fun observationCannotReleaseOrDuplicateTheExistingProcessLease() {
        val context=RuntimeEnvironment.getApplication();val name="fusion-lease.db";context.deleteDatabase(name)
        val broker=ProcessResources.coordinator
        val heavy=(broker.acquire(ResourceRequest("fusion-fixture",Module.LLM,ResourceClass.HEAVY,"synthetic load")) as ResourceGrant.Granted).lease
        val lease=try { (broker.acquire(ResourceRequest("fusion-fixture",Module.LLM,ResourceClass.MODEL,"synthetic",modelKey="SMALL")) as ResourceGrant.Granted).lease } finally { heavy.close() }
        try {
            VerifiedModelCatalog(context,name).use { c ->
                val port=LocalClientReadinessPort(c)
                repeat(3) {
                    val state=port.observe().resources
                    assertEquals(DeclaredBrokerActivity.MODEL_DECLARED,state.observation!!.activity)
                    assertEquals(NativeProofStatus.UNATTESTED,state.unload)
                    assertTrue(broker.acquire(ResourceRequest("other",Module.VOICE,ResourceClass.HEAVY,"synthetic")) is ResourceGrant.Refused)
                }
            }
        } finally { lease.close() }
    }
}
