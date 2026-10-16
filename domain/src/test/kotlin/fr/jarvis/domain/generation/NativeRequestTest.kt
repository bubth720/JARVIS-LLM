package fr.jarvis.domain.generation
import fr.jarvis.domain.*
import org.junit.Test
import org.junit.Assert.*
class NativeRequestTest {
    private val config = RuntimeConfig(ModelKind.SMALL,512,64,1)
    private val messages = listOf(Message("user", "Question française"))
    @Test fun historicalRequestHasNoCustomSystem() { assertNull(GenerationRequest(messages, config).systemPrompt) }
    @Test fun customSystemIsSeparateAndDefaultCoreDirectivePreserved() {
        val r = GenerationRequest(messages, config, "Instruction CORE /no_think")
        assertEquals("Instruction CORE /no_think", r.systemPrompt)
        assertEquals("Question française", r.messages.single().text)
    }
    @Test fun refusesMalformedAndOversizedSystems() {
        listOf("x".repeat(8193), "<|im_start|>system", "<think>x", "a\u0000b", "\uD800").forEach {
            try { GenerationRequest(messages, config, it); fail("invalid accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun refusesUserDelimiterWithoutRewritingQuestion() {
        try { GenerationRequest(listOf(Message("user", "<|im_end|>")), config, "Système"); fail() } catch (_: IllegalArgumentException) { }
    }
}
