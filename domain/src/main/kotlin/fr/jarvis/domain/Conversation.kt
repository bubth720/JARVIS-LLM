package fr.jarvis.domain

data class Message(val role: String, val text: String) {
    init { require(role in setOf("user", "assistant")); require(text.length <= 16_000) }
}
data class GenerationRequest(val messages: List<Message>, val config: RuntimeConfig) {
    init {
        require(messages.isNotEmpty() && messages.last().role == "user")
        require(messages.size <= 8)
        require(config.context in 512..2048 && config.context % 256 == 0 && config.maxTokens in 1..256 && config.threads in 1..4)
        if (config.model == ModelKind.SMALL) require(config.context in setOf(512, 768) && config.maxTokens <= (if (config.tabletEco) 64 else 96) && config.threads == 1)
        else require(config.context >= 768)
    }
}

/** Replaceable backend. requestStop must reach native computation, not merely cancel a coroutine. */
interface LanguageModel {
    val allocationDiagnostic: String get() = "Allocations natives non mesurées"
    fun prepareOperation()
    suspend fun load(path: String, config: RuntimeConfig)
    suspend fun generate(request: GenerationRequest): String
    fun requestStop()
    suspend fun unload()
    suspend fun close()
}
