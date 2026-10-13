package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors

internal object NativeBridge {
    init { System.loadLibrary("jarvis_llm") }
    external fun create(): Long
    external fun begin(handle: Long)
    external fun cancel(handle: Long)
    external fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean)
    external fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int): ByteArray
    external fun unload(handle: Long)
    external fun destroy(handle: Long)
}

/** All resource operations use one worker. Only the atomic stop flag is changed concurrently. */
class LlamaBackend : LanguageModel {
    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val handleLock = Any()
    private var handle = NativeBridge.create()
    private var loaded: Pair<String, RuntimeConfig>? = null
    override fun prepareOperation() = synchronized(handleLock) { check(handle != 0L); NativeBridge.begin(handle) }
    override fun requestStop() = synchronized(handleLock) { if (handle != 0L) NativeBridge.cancel(handle) }
    override suspend fun load(path: String, config: RuntimeConfig) = withContext(worker) {
        if (loaded != (path to config)) {
            loaded = null
            NativeBridge.load(handle, path.toByteArray(Charsets.UTF_8), config.context, config.threads, config.model == ModelKind.SMALL)
            loaded = path to config
        }
    }
    override suspend fun generate(request: GenerationRequest): String = withContext(worker) {
        String(NativeBridge.generate(handle, request.messages.map { it.role.toByteArray(Charsets.UTF_8) }.toTypedArray(),
            request.messages.map { it.text.toByteArray(Charsets.UTF_8) }.toTypedArray(), request.config.maxTokens), Charsets.UTF_8)
    }
    override suspend fun unload() = withContext(worker) { NativeBridge.unload(handle); loaded = null }
    override suspend fun close() {
        requestStop()
        withContext(worker) {
            synchronized(handleLock) { if (handle != 0L) { NativeBridge.destroy(handle); handle = 0 } }
            loaded = null
        }
        worker.close()
    }
}
