package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.domain.resources.*
import fr.jarvis.llm.ProcessResources
import kotlinx.coroutines.*
import java.util.concurrent.Executors

internal interface NativeApi {
    fun create(): Long
    fun begin(handle: Long)
    fun cancel(handle: Long)
    fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean)
    fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int): ByteArray
    fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray
    fun measureFixtureP2(handle: Long, system: ByteArray, question: ByteArray): Array<ByteArray> = error("fixture measure unsupported")
    fun generateFixtureP2(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int): ByteArray = error("fixture generate unsupported")
    fun generateStream(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int,
                       binding: ByteArray, callback: OwnedStreamCallback): Int = error("native streaming not approved or unsupported")
    fun allocations(handle: Long): LongArray
    fun unload(handle: Long)
    fun destroy(handle: Long)
}

internal object NativeBridge : NativeApi {
    init { System.loadLibrary("jarvis_llm") }
    external override fun create(): Long
    external override fun begin(handle: Long)
    external override fun cancel(handle: Long)
    external override fun load(handle: Long, path: ByteArray, context: Int, threads: Int, small: Boolean)
    external override fun generate(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int): ByteArray
    external override fun generateP2(handle: Long, roles: Array<ByteArray>, texts: Array<ByteArray>, maxTokens: Int, system: ByteArray): ByteArray
    external override fun measureFixtureP2(handle: Long, system: ByteArray, question: ByteArray): Array<ByteArray>
    external override fun generateFixtureP2(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int): ByteArray
    external override fun generateStream(handle: Long, system: ByteArray, question: ByteArray, maxTokens: Int, contextLimit: Int,
                                        binding: ByteArray, callback: OwnedStreamCallback): Int
    external override fun allocations(handle: Long): LongArray
    external override fun unload(handle: Long)
    external override fun destroy(handle: Long)
}

/** All resource operations use one worker. Only the atomic stop flag is changed concurrently. */
class LlamaBackend internal constructor(private val native: NativeApi) : LanguageModel {
    constructor() : this(NativeBridge)
    override val capabilities = EngineCapabilities(separateSystemPrompt = true, nativeCancellation = true)
    private val owner = "llm-" + java.util.UUID.randomUUID().toString()
    private var modelLease: ResourceLease? = null
    @Volatile override var allocations: NativeAllocations? = null
        private set
    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val handleLock = Any()
    private val streamWorkerIncarnation = java.util.UUID.randomUUID().toString()
    private var streamResidentIncarnation = java.util.UUID.randomUUID().toString()
    private var lastStreamOwnerSequence = 0L
    private var activeStream: Pair<StreamRevocation, OwnedStreamBuffer>? = null
    private val generating = java.util.concurrent.atomic.AtomicBoolean(false)
    private var handle = native.create()
    @Volatile override var allocationDiagnostic: String = "Allocations natives non mesurées"
        private set
    @Volatile private var cleanupFailed = false
    private var loaded: Pair<String, RuntimeConfig>? = null
    override fun prepareOperation() = synchronized(handleLock) { check(handle != 0L && !generating.get() && !cleanupFailed) { "BUSY ou moteur fermé" }; native.begin(handle) }
    override fun requestStop() = synchronized(handleLock) {
        activeStream?.let { it.first.revoke(); it.second.stop() }
        if (handle != 0L) native.cancel(handle)
    }
    override suspend fun load(path: String, config: RuntimeConfig) = withContext(worker) {
        check(!cleanupFailed) { "CLEANUP_FAILED : déchargement confirmé requis" }
        if (loaded != (path to config)) {
            ProcessResources.requireHeavy(owner, Module.LLM, "chargement natif").use {
                try { native.unload(handle) } catch (e: Throwable) { cleanupFailed = true; throw e }
                loaded = null; allocations = null
                modelLease?.close(); modelLease = null
                allocationDiagnostic = "Allocations natives non mesurées"
                val lease = ProcessResources.requireLease(ResourceRequest(owner, Module.LLM, ResourceClass.MODEL,
                    "résidence du modèle", config.memoryBudget, config.model.name))
                modelLease = lease // retain even if load AND cleanup fail
                streamResidentIncarnation = java.util.UUID.randomUUID().toString()
                try {
                    native.load(handle, path.toByteArray(Charsets.UTF_8), config.context, config.threads, config.model == ModelKind.SMALL)
                    modelLease = lease
                    refreshAllocations()
                    loaded = path to config
                } catch (e: Throwable) {
                    // The native call has returned. Release only after actual native cleanup.
                    try { native.unload(handle) }
                    catch (cleanup: Throwable) { cleanupFailed = true; cleanup.addSuppressed(e); throw cleanup }
                    allocations = null; modelLease = null; lease.close()
                    throw e
                }
            }
        }
    }
    private fun refreshAllocations() {
        val sizes = native.allocations(handle)
        allocations = NativeAllocations(sizes[0], sizes[1], sizes[2])
        allocationDiagnostic = "Buffers llama.cpp modèle/contexte/calcul : " + sizes.joinToString(" / ") { "%.1f".format(java.util.Locale.FRANCE, it / 1048576.0) } + " Mio (hors tas JVM et autres allocations ; pas PSS ni pic)"
    }
    override suspend fun generate(request: GenerationRequest): String {
        check(!cleanupFailed) { "CLEANUP_FAILED : déchargement confirmé requis" }
        check(generating.compareAndSet(false, true)) { "BUSY : génération concurrente refusée" }
        try { return withContext(worker) {
        ProcessResources.requireHeavy(owner, Module.LLM, "génération native").use {
            check(loaded?.second == request.config) { "Configuration du modèle résident différente de la demande" }
            try {
                val roles = request.messages.map { it.role.toByteArray(Charsets.UTF_8) }.toTypedArray()
                val texts = request.messages.map { it.text.toByteArray(Charsets.UTF_8) }.toTypedArray()
                val output = request.systemPrompt?.let {
                    native.generateP2(handle, roles, texts, request.config.maxTokens, it.toByteArray(Charsets.UTF_8))
                } ?: native.generate(handle, roles, texts, request.config.maxTokens)
                String(output, Charsets.UTF_8)
            } finally {
                if (loaded != null) runCatching { refreshAllocations() }.onFailure { allocations = null; allocationDiagnostic = "Buffers natifs indisponibles" }
            }
        }
    }
        } finally { generating.set(false) }
    }
    /** Text-only fixture: never feed P2Planner's already-prefixed SYSTEM into this path. */
    suspend fun generateFixtureP2(request: GenerationRequest, contextLimit: Int): FixtureGeneration {
        val customSystem=requireNotNull(request.systemPrompt)
        require(request.messages.size == 1)
        require(contextLimit in 512..request.config.context && contextLimit % 256 == 0)
        if (request.config.model == ModelKind.MAIN) require(contextLimit >= 768)
        check(!cleanupFailed && generating.compareAndSet(false, true)) { "BUSY or CLEANUP_FAILED" }
        try { return withContext(worker) {
            ProcessResources.requireHeavy(owner, Module.LLM, "fixture comptage et génération P2").use {
                check(loaded?.second == request.config)
                val system = customSystem.toByteArray(Charsets.UTF_8)
                val question = request.messages.single().text.toByteArray(Charsets.UTF_8)
                val measured = native.measureFixtureP2(handle, system, question)
                check(measured.size == 3)
                val tokens = measured[0].toString(Charsets.US_ASCII).toInt()
                if (tokens <= 0 || tokens.toLong() + request.config.maxTokens + 8 > contextLimit) throw FixtureContextExceeded()
                fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                val template = hash(measured[2]); val rendered = hash(measured[1])
                val text = native.generateFixtureP2(handle, system, question, request.config.maxTokens, contextLimit).toString(Charsets.UTF_8)
                FixtureGeneration(text, tokens, template, rendered, contextLimit)
            }
        } } finally { generating.set(false) }
    }
    /** Opt-in internal candidate only. No authority is installed by historical clients. */
    internal suspend fun generateOwnedStream(request: GenerationRequest, contextLimit: Int,
        authority: StreamOwnerAuthority, buffer: OwnedStreamBuffer): OwnedStreamOutcome {
        // This first candidate is text-only P2; legacy history generation remains unchanged.
        require(request.messages.size == 1 && request.messages.single().role == "user")
        val customSystem = requireNotNull(request.systemPrompt)
        require(contextLimit in 512..request.config.context && contextLimit % 256 == 0)
        if (request.config.model == ModelKind.MAIN) require(contextLimit >= 768)
        val system = customSystem.toByteArray(Charsets.UTF_8)
        val question = request.messages.single().text.toByteArray(Charsets.UTF_8)
        check(!cleanupFailed && generating.compareAndSet(false, true)) { "BUSY or CLEANUP_FAILED" }
        try { return withContext(worker) {
            ProcessResources.requireHeavy(owner, Module.LLM, "streaming natif candidat").use {
                check(loaded?.second == request.config)
                val lease = requireNotNull(modelLease)
                val resident = StreamResident(lease, owner, streamWorkerIncarnation, streamResidentIncarnation, request.config)
                val measured = native.measureFixtureP2(handle, system, question)
                check(measured.size == 3)
                val count = measured[0].toString(Charsets.US_ASCII).toInt()
                if (count <= 0 || count.toLong() + request.config.maxTokens + 8 > contextLimit) throw FixtureContextExceeded()
                // Length framing prevents concatenation ambiguities; no prompt is logged/retained in metadata.
                val framed = java.io.ByteArrayOutputStream()
                java.io.DataOutputStream(framed).use { out ->
                    for (part in listOf("jarvis.llm.prepared/1".toByteArray(Charsets.US_ASCII), system, question,
                        measured[1], measured[2], request.config.model.name.toByteArray(Charsets.US_ASCII))) {
                        out.writeInt(part.size); out.write(part)
                    }
                    out.writeInt(contextLimit); out.writeInt(request.config.context)
                    out.writeInt(request.config.maxTokens); out.writeInt(request.config.threads)
                }
                val prepared = StreamPrepared(streamHash(measured[1]), streamHash(measured[2]),
                    streamHash(framed.toByteArray()), count, contextLimit, request.config)
                // Runs outside handleLock, broker lock and JNI callback. No productive adapter is provided.
                val grant = requireNotNull(authority.authorize(prepared, resident)) { "CORE_ADMISSION_UNQUALIFIED" }
                check(grant.prepared === prepared && grant.resident === resident && grant.revocation.isAllowed())
                check(grant.ownerSequence > lastStreamOwnerSequence) { "STALE_OR_REUSED_STREAM_OWNER_SEQUENCE" }
                lastStreamOwnerSequence = grant.ownerSequence // grant is consumed once even when callback/JNI fails
                val callback = OwnedStreamCallback(grant, buffer)
                buffer.bind(grant)
                synchronized(handleLock) { check(activeStream == null); activeStream = grant.revocation to buffer }
                try {
                    val terminal = native.generateStream(handle, system, question, request.config.maxTokens,
                        contextLimit, grant.bindingSha256.toByteArray(Charsets.US_ASCII), callback)
                    check(terminal in StreamKind.COMPLETED.ordinal..StreamKind.ERROR.ordinal)
                    val kind = StreamKind.entries[terminal]
                    check(buffer.acceptedTerminal() == kind && (kind != StreamKind.COMPLETED || callback.hasPrepared())) {
                        "UNCONFIRMED_NATIVE_TERMINAL"
                    }
                    OwnedStreamOutcome(kind, nativeReturned = true)
                } catch (failure: Throwable) {
                    buffer.invocationFailed() // discard unrendered text; previously displayed content stays explicitly partial
                    throw failure
                } finally {
                    // A returned JNI call is not a global drain, resource release or unload receipt.
                    callback.close() // no late callback can modify the UI buffer after this invocation
                    synchronized(handleLock) { if (activeStream?.first === grant.revocation) activeStream = null }
                    buffer.nativeInvocationFinished()
                    if (loaded != null) runCatching { refreshAllocations() }.onFailure {
                        allocations = null; allocationDiagnostic = "Buffers natifs indisponibles"
                    }
                }
            }
        } } finally { generating.set(false) }
    }
    override suspend fun unload() = withContext(worker) { try { native.unload(handle) } catch (e: Throwable) { cleanupFailed = true; throw e }; cleanupFailed = false; loaded = null; allocations = null; modelLease?.close(); modelLease = null; allocationDiagnostic = "Moteur déchargé ; buffers natifs non résidents" }
    override suspend fun close() {
        requestStop()
        withContext(worker) {
            synchronized(handleLock) { if (handle != 0L) {
                try { native.destroy(handle) } catch (e: Throwable) { cleanupFailed = true; throw e }
                handle = 0
            } }
            loaded = null; allocations = null; modelLease?.close(); modelLease = null
        }
        worker.close()
    }
}

/** No prompt or generated content in implicit logs. Digests attest to the canonical native rendering. */
class FixtureGeneration(val text: String, val inputTokens: Int, val templateSha256: String,
                        val renderedSha256: String, val contextLimit: Int) {
    override fun toString() = "FixtureGeneration(redacted)"
}

class FixtureContextExceeded : IllegalArgumentException("CONTEXT_EXCEEDED")
