package fr.jarvis.llm.engine

import fr.jarvis.domain.resources.*
import org.junit.Assert.*
import org.junit.Test

/** R02 isolated proposal test: no model, native inference or Android claim. */
class R02WatcherIsolationTest {
    @Test fun failingWatcherDoesNotPreventOtherRevocations() {
        val broker = ResourceCoordinator()
        val owner = "r02-fixture"
        val heavy = (broker.acquire(ResourceRequest(owner, Module.LLM, ResourceClass.HEAVY, "test")) as ResourceGrant.Granted).lease
        val model = (broker.acquire(ResourceRequest(owner, Module.LLM, ResourceClass.MODEL, "test", modelKey="SMALL")) as ResourceGrant.Granted).lease
        val first = requireNotNull(broker.observeOwnedPair(owner, heavy, model))
        val second = requireNotNull(broker.observeOwnedPair(owner, heavy, model))
        try {
            var notifications = 0
            first.onRevoked { notifications++; error("R02_EXPECTED_WATCHER_FAILURE") }
            second.onRevoked { notifications++ }
            val failure = runCatching { heavy.close() }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals("R02_EXPECTED_WATCHER_FAILURE", failure!!.message)
            assertEquals("Every observer must receive revocation", 2, notifications)
            assertFalse(first.isLive())
            assertFalse(second.isLive())
        } finally {
            first.close(); second.close(); model.close()
        }
    }

    @Test fun multipleFailuresPreservePrimaryAndSuppressedFailure() {
        val broker = ResourceCoordinator()
        val owner = "r02-multiple"
        val heavy = (broker.acquire(ResourceRequest(owner, Module.LLM, ResourceClass.HEAVY, "test")) as ResourceGrant.Granted).lease
        val model = (broker.acquire(ResourceRequest(owner, Module.LLM, ResourceClass.MODEL, "test", modelKey="SMALL")) as ResourceGrant.Granted).lease
        val first = requireNotNull(broker.observeOwnedPair(owner, heavy, model))
        val second = requireNotNull(broker.observeOwnedPair(owner, heavy, model))
        try {
            first.onRevoked { error("R02_FIRST") }
            second.onRevoked { error("R02_SECOND") }
            val failure = runCatching { heavy.close() }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(1, failure!!.suppressed.size)
            assertEquals(setOf("R02_FIRST", "R02_SECOND"), (listOf(failure.message) + failure.suppressed.map { it.message }).toSet())
        } finally {
            first.close(); second.close(); model.close()
        }
    }
}
