package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.domain.resources.*
import fr.jarvis.llm.ProcessResources
import kotlinx.coroutines.*
import java.util.concurrent.Executors

internal object NativeBridge {
    init { System.loadLibrary("jarvis_llm") }
    external fun create(): Long
    external fun begin(handle: Long)
    external fun cancel(handle: Long)
    external fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean)
    external fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int): ByteArray
    external fun allocations(handle: Long): LongArray
    external fun unload(handle: Long)
    external fun destroy(handle: Long)
}

/** All resource operations use one worker. Only the atomic stop flag is changed concurrently. */
class LlamaBackend : LanguageModel {
    private val owner = "llm-" + java.util.UUID.randomUUID().toString()
    private var modelLease: ResourceLease? = null
    @Volatile override var allocations: NativeAllocations? = null
        private set
    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val handleLock = Any()
    private var handle = NativeBridge.create()
    @Volatile override var allocationDiagnostic: String = "Allocations natives non mesurées"
        private set
    private var loaded: Pair<String, RuntimeConfig>? = null
    override fun prepareOperation() = synchronized(handleLock) { check(handle != 0L); NativeBridge.begin(handle) }
    override fun requestStop() = synchronized(handleLock) { if (handle != 0L) NativeBridge.cancel(handle) }
    override suspend fun load(path: String, config: RuntimeConfig) = withContext(worker) {
        if (loaded != (path to config)) {
            ProcessResources.requireHeavy(owner, Module.LLM, "chargement natif").use {
                NativeBridge.unload(handle); loaded = null; allocations = null
                modelLease?.close(); modelLease = null
                allocationDiagnostic = "Allocations natives non mesurées"
                val lease = ProcessResources.requireLease(ResourceRequest(owner, Module.LLM, ResourceClass.MODEL,
                    "résidence du modèle", config.memoryBudget, config.model.name))
                try {
                    NativeBridge.load(handle, path.toByteArray(Charsets.UTF_8), config.context, config.threads, config.model == ModelKind.SMALL)
                    modelLease = lease
                    refreshAllocations()
                    loaded = path to config
                } catch (e: Throwable) {
                    // The native call has returned. Release only after actual native cleanup.
                    NativeBridge.unload(handle); allocations = null; modelLease = null; lease.close()
                    throw e
                }
            }
        }
    }
    private fun refreshAllocations() {
        val sizes = NativeBridge.allocations(handle)
        allocations = NativeAllocations(sizes[0], sizes[1], sizes[2])
        allocationDiagnostic = "Buffers llama.cpp modèle/contexte/calcul : " + sizes.joinToString(" / ") { "%.1f".format(java.util.Locale.FRANCE, it / 1048576.0) } + " Mio (hors tas JVM et autres allocations ; pas PSS ni pic)"
    }
    override suspend fun generate(request: GenerationRequest): String = withContext(worker) {
        ProcessResources.requireHeavy(owner, Module.LLM, "génération native").use {
            check(loaded?.second == request.config) { "Configuration du modèle résident différente de la demande" }
            try {
                String(NativeBridge.generate(handle, request.messages.map { it.role.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                    request.messages.map { it.text.toByteArray(Charsets.UTF_8) }.toTypedArray(), request.config.maxTokens), Charsets.UTF_8)
            } finally {
                if (loaded != null) runCatching { refreshAllocations() }.onFailure { allocations = null; allocationDiagnostic = "Buffers natifs indisponibles" }
            }
        }
    }
    override suspend fun unload() = withContext(worker) { NativeBridge.unload(handle); loaded = null; allocations = null; modelLease?.close(); modelLease = null; allocationDiagnostic = "Moteur déchargé ; buffers natifs non résidents" }
    override suspend fun close() {
        requestStop()
        withContext(worker) {
            synchronized(handleLock) { if (handle != 0L) { NativeBridge.destroy(handle); handle = 0 } }
            loaded = null; allocations = null; modelLease?.close(); modelLease = null
        }
        worker.close()
    }
}
