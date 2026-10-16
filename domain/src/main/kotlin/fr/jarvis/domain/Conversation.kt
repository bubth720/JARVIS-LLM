package fr.jarvis.domain

data class Message(val role: String, val text: String) {
    init { require(role in setOf("user", "assistant")); require(text.length <= 16_000) }
}
data class GenerationRequest(val messages: List<Message>, val config: RuntimeConfig, val systemPrompt: String? = null) {
    init {
        systemPrompt?.let {
            fun strict(text: String) {
                try { Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text)) }
                catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("UTF-8 invalide") }
            }
            strict(it); messages.forEach { m -> strict(m.text) }
            val forbidden = listOf("/think", "[INST]", "[/INST]", "<<SYS>>")
            require(forbidden.none { token -> it.contains(token) })
            require(messages.all { m -> forbidden.none { token -> m.text.contains(token) } && !m.text.contains("/no_think") })
            require(it.toByteArray(Charsets.UTF_8).size <= 8192)
            require(messages.indices.all { i -> messages[i].role == if (i % 2 == 0) "user" else "assistant" })
            require(!it.contains("<|") && !it.contains("<think>") && !it.contains("</think>"))
            require(it.none { c -> c == '\u0000' || (c.code == 127 || c.code < 32 && c !in "\n\r\t") })
            require(messages.all { m -> !m.text.contains("<|") && !m.text.contains("<think>") && !m.text.contains("</think>") && m.text.none { c -> c == '\u0000' } })
        }
        require(messages.isNotEmpty() && messages.last().role == "user")
        require(messages.size <= 8)
        require(config.context in 512..2048 && config.context % 256 == 0 && config.maxTokens in 1..256 && config.threads in 1..4)
        if (config.model == ModelKind.SMALL) require(config.context in setOf(512, 768) && config.maxTokens <= (if (config.tabletEco) 64 else 96) && config.threads == 1)
        else require(config.context >= 768)
    }
}

/** Replaceable backend. requestStop must reach native computation, not merely cancel a coroutine. */
interface LanguageModel {
    val allocations: NativeAllocations? get() = null
    val allocationDiagnostic: String get() = "Allocations natives non mesurées"
    fun prepareOperation()
    suspend fun load(path: String, config: RuntimeConfig)
    suspend fun generate(request: GenerationRequest): String
    fun requestStop()
    suspend fun unload()
    suspend fun close()
}
