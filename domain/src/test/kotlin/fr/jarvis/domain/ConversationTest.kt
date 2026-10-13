package fr.jarvis.domain
import org.junit.Assert.*
import org.junit.Test
class ConversationTest {
    private val config = RuntimeConfig(ModelKind.SMALL, 1024, 128, 2)
    @Test fun generatedActionsCannotBeEncodedAsMessageRoles() {
        assertThrows(IllegalArgumentException::class.java) { Message("execute_action", "shell") }
    }
    @Test fun limitsCannotBeBypassedByCaller() {
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), config.copy(context = 8192)) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), config.copy(threads = 64)) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), config.copy(maxTokens = 4096)) }
    }
    @Test fun requestNeedsAUserQuestion() {
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(emptyList(), config) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("assistant", "test")), config) }
    }
}
