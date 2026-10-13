package fr.jarvis.domain

data class Message(val role: String, val text: String) {
    init { require(role in setOf("user", "assistant")); require(text.length <= 16_000) }
}
data class GenerationRequest(val messages: List<Message>, val config: RuntimeConfig) {
    init {
        require(messages.isNotEmpty() && messages.last().role == "user")
        require(messages.size <= 8)
        require(config.context in 1024..2048 && config.maxTokens in 1..256 && config.threads in 1..4)
    }
}

/** Replaceable backend. requestStop must reach native computation, not merely cancel a coroutine. */
interface LanguageModel {
    fun prepareOperation()
    suspend fun load(path: String, config: RuntimeConfig)
    suspend fun generate(request: GenerationRequest): String
    fun requestStop()
    suspend fun unload()
    suspend fun close()
}
