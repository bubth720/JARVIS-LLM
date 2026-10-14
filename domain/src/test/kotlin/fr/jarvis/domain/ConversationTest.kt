package fr.jarvis.domain
import org.junit.Assert.*
import org.junit.Test
class ConversationTest {
    private val config = RuntimeConfig(ModelKind.SMALL, 768, 96, 1)
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
    @Test fun reducedContextRequiresAlignedNativeAndOutputLimits() {
        GenerationRequest(listOf(Message("user", "bonjour")), config)
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), config.copy(context = 767)) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), config.copy(maxTokens = 97)) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), config.copy(threads = 2)) }
    }
    @Test fun tabletRequestCannotExpandOutputOrUseMainAt512() {
        val tablet = config.copy(context = 512, maxTokens = 64)
        GenerationRequest(listOf(Message("user", "bonjour")), tablet)
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), tablet.copy(maxTokens = 65)) }
        assertThrows(IllegalArgumentException::class.java) { GenerationRequest(listOf(Message("user", "bonjour")), tablet.copy(model = ModelKind.MAIN)) }
    }
}
