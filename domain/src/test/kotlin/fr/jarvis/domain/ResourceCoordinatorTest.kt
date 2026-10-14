package fr.jarvis.domain

import fr.jarvis.domain.resources.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch

class ResourceCoordinatorTest {
    private fun request(owner: String, kind: ResourceClass = ResourceClass.HEAVY) = ResourceRequest(owner, Module.LLM, kind,"test",2L*RuntimeConfig.GIB,if(kind==ResourceClass.MODEL) "QWEN_SMALL" else null)
    private fun lease(grant: ResourceGrant) = (grant as ResourceGrant.Granted).lease
    @Test fun onlyOneConcurrentHeavyTaskCanOwnTheBroker() {
        val broker=ResourceCoordinator(); val start=CountDownLatch(1); val results=java.util.Collections.synchronizedList(mutableListOf<ResourceGrant>())
        val workers=(1..8).map { n -> Thread { start.await(); results.add(broker.acquire(request("worker-$n"))) }.apply { start() } }
        start.countDown(); workers.forEach { it.join() }
        assertEquals(1,results.count { it is ResourceGrant.Granted })
        results.filterIsInstance<ResourceGrant.Granted>().forEach { it.lease.close() }
        assertNull(broker.snapshot().heavy)
    }
    @Test fun duplicateModelsAndOtherModulesWaitForActualResidentRelease() {
        val broker=ResourceCoordinator(); val heavy=lease(broker.acquire(request("llm")))
        val model=lease(broker.acquire(request("llm",ResourceClass.MODEL))); heavy.close()
        assertTrue(broker.acquire(request("second",ResourceClass.MODEL)) is ResourceGrant.Refused)
        assertTrue(broker.acquire(request("voice").copy(module=Module.VOICE)) is ResourceGrant.Refused)
        val generation=lease(broker.acquire(request("llm")))
        // A stop request does not free either token. The native operation must return first.
        assertNotNull(broker.snapshot().model); assertNotNull(broker.snapshot().heavy)
        generation.close(); assertNotNull(broker.snapshot().model)
        model.close(); lease(broker.acquire(request("voice").copy(module=Module.VOICE))).close()
    }
    @Test fun lateDoubleReleaseCannotClearAnotherOperation() {
        val broker=ResourceCoordinator(); val old=lease(broker.acquire(request("old"))); old.close()
        val current=lease(broker.acquire(request("current"))); old.close()
        assertEquals("current",broker.snapshot().heavy!!.owner)
        current.close()
    }
    @Test fun modelNeedsAnExclusiveHeavyGrantFromSameOwner() {
        val broker=ResourceCoordinator()
        assertTrue(broker.acquire(request("llm",ResourceClass.MODEL)) is ResourceGrant.Refused)
        val heavy=lease(broker.acquire(request("one")))
        assertTrue(broker.acquire(request("two",ResourceClass.MODEL)) is ResourceGrant.Refused)
        heavy.close()
    }
    @Test fun brokerIsCoordinationNotPermissionToReduceMemoryBudget() {
        val broker=ResourceCoordinator(); val token=lease(broker.acquire(request("llm")))
        val tablet=DeviceSnapshot((3.46*RuntimeConfig.GIB).toLong(),(7.51*RuntimeConfig.GIB).toLong(),false,76,false,0,null,true,true,8)
        val decision=SafetyPolicy().decide(Profile.TABLET_ECO,tablet,setOf(ModelKind.SMALL))
        assertNull(decision.config)
        assertEquals(3.502,decision.memory!!.requiredAvailable.toDouble()/RuntimeConfig.GIB,0.001)
        assertEquals(-0.042,decision.memory.margin.toDouble()/RuntimeConfig.GIB,0.001)
        token.close()
    }
}
