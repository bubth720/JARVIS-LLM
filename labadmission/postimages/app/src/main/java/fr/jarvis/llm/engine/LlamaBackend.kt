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
    fun loadSealedFd(handle: Long, fd: Int, exactSize: Long, context: Int, threads: Int, small: Boolean) = error("A02_NATIVE_FD_NOT_APPROVED")
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
    external override fun loadSealedFd(handle: Long, fd: Int, exactSize: Long, context: Int, threads: Int, small: Boolean)
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
class LlamaBackend internal constructor(private val native: NativeApi,
    private val labHostGuard: (RuntimeConfig) -> Unit) : LanguageModel {
    internal constructor(native: NativeApi) : this(native,LabHostBudget::requireFits)
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
    @Volatile private var labExclusive = false
    @Volatile private var labAttempt: LabAttempt? = null
    private var labResidence: LabResidence? = null
    private var labQuarantineHeavy: ResourceLease? = null
    private var activeStream: Pair<StreamRevocation, OwnedStreamBuffer>? = null
    private val generating = java.util.concurrent.atomic.AtomicBoolean(false)
    private var handle = native.create()
    @Volatile override var allocationDiagnostic: String = "Allocations natives non mesurées"
        private set
    @Volatile private var cleanupFailed = false
    private var loaded: Pair<String, RuntimeConfig>? = null
    override fun prepareOperation() = synchronized(handleLock) { check(!labExclusive) { "LAB_SCOPE_EXCLUSIVE" }; check(handle != 0L && !generating.get() && !cleanupFailed) { "BUSY ou moteur fermé" }; native.begin(handle) }
    override fun requestStop() = synchronized(handleLock) {
        labAttempt?.cancelledByBackend()
        activeStream?.let { it.first.revoke(); it.second.stop() }
        if (handle != 0L) native.cancel(handle)
    }
    override suspend fun load(path: String, config: RuntimeConfig) = withContext(worker) {
        check(!labExclusive) { "LAB_SCOPE_EXCLUSIVE" }
        check(!cleanupFailed) { "CLEANUP_FAILED : déchargement confirmé requis" }
        if (loaded != (path to config)) ProcessResources.requireHeavy(owner, Module.LLM, "chargement natif").use {
            loadOnWorker(path,config)
        }
    }
    /** Same native loader and leases; callers already hold this backend's HEAVY lease and worker. */
    private fun loadOnWorker(path: String, config: RuntimeConfig, observe: ((ResourceLease) -> Unit)? = null) {
        check(!cleanupFailed) { "CLEANUP_FAILED : déchargement confirmé requis" }
        if (loaded == (path to config)) return
        try { native.unload(handle) } catch (e: Throwable) { cleanupFailed = true; throw e }
        loaded = null; allocations = null; labResidence = null
        modelLease?.close(); modelLease = null
        allocationDiagnostic = "Allocations natives non mesurées"
        val lease = ProcessResources.requireLease(ResourceRequest(owner, Module.LLM, ResourceClass.MODEL,
            "résidence du modèle", config.memoryBudget, config.model.name))
        modelLease = lease
        streamResidentIncarnation = java.util.UUID.randomUUID().toString()
        try {
            observe?.invoke(lease) // pin the REAL pair before native load, never a fabricated lease id
            native.load(handle, path.toByteArray(Charsets.UTF_8), config.context, config.threads, config.model == ModelKind.SMALL)
            refreshAllocations()
            loaded = path to config
        } catch (e: Throwable) {
            try { native.unload(handle) }
            catch (cleanup: Throwable) { cleanupFailed = true; cleanup.addSuppressed(e); throw cleanup }
            allocations = null; modelLease = null; lease.close()
            throw e
        }
    }
    private fun refreshAllocations() {
        val sizes = native.allocations(handle)
        allocations = NativeAllocations(sizes[0], sizes[1], sizes[2])
        allocationDiagnostic = "Buffers llama.cpp modèle/contexte/calcul : " + sizes.joinToString(" / ") { "%.1f".format(java.util.Locale.FRANCE, it / 1048576.0) } + " Mio (hors tas JVM et autres allocations ; pas PSS ni pic)"
    }
    override suspend fun generate(request: GenerationRequest): String {
        check(!labExclusive) { "LAB_SCOPE_EXCLUSIVE" }
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
        check(!labExclusive) { "LAB_SCOPE_EXCLUSIVE" }
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
        check(!labExclusive) { "LAB_SCOPE_EXCLUSIVE" }
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
    /** A challenge identifies THIS existing backend/worker; it grants no permission by itself. */
    internal fun labOwnerChallenge() = LabOwnerChallenge(owner,streamWorkerIncarnation)
    private fun stopLabAttempt(expected: LabAttempt) = synchronized(handleLock) {
        if (labAttempt === expected && !cleanupFailed) {
            expected.cancelledByBackend()
            activeStream?.second?.stop()
            if (handle != 0L) try { native.cancel(handle) } catch(e: Throwable) { cleanupFailed=true;throw e }
        }
    }
    /** Review-only API: no call site, operator trust root or productive entry point is installed. */
    internal suspend fun qualifyLab(authority: LabCloudAuthority, signed: LabSignedPermit,
        privateRoot: java.nio.file.Path, path: java.nio.file.Path, request: GenerationRequest,
        buffer: OwnedStreamBuffer? = null): LabGenerationResult {
        val frozen=labFreezeRequest(request)
        val callerJob=currentCoroutineContext()[Job]
        val attempt = synchronized(handleLock) {
            check(handle != 0L && !cleanupFailed && generating.compareAndSet(false,true)) { "BUSY_CLOSED_OR_CLEANUP_FAILED" }
            try {
                authority.redeem(signed,labOwnerChallenge(),frozen).also {
                    if ((it.spec.variant == LabVariant.STREAM) != (buffer != null)) { it.close();error("LAB_VARIANT_MISMATCH") }
                    labExclusive = true;labAttempt = it
                }
            } catch (e: Throwable) { generating.set(false);throw e }
        }
        try {
            attempt.attachStop(monitor={ check(callerJob?.isActive != false) { "LAB_CALLER_CANCELLED" };labHostGuard(frozen.config) }) { stopLabAttempt(attempt) }
            return withContext(worker) {
                var heavy: ResourceLease? = null
                var proof: ResourceObservation? = null
                var nativeTouched = false
                try {
                    attempt.requireLive();labHostGuard(frozen.config)
                    heavy = ProcessResources.requireHeavy(owner,Module.LLM,"LAB_CLOUD tentative signée",frozen.config.memoryBudget)
                    val file = LabFileProof.verify(privateRoot,path,attempt::requireLive)
                    attempt.advance(LabPhase.AUTHORIZED,LabPhase.FILE_VERIFIED)
                    val same = loaded == (file.path.toString() to frozen.config) && labResidence?.let {
                        it.file.key == file.key && it.resident.lease === modelLease && it.resident.incarnation == streamResidentIncarnation
                    } == true
                    synchronized(handleLock) { attempt.requireLive();nativeTouched=true;native.begin(handle) }
                    if (!same) {
                        // A legacy/unattested residence never silently becomes a verified laboratory model.
                        if (loaded != null) unloadOnWorker()
                        loadOnWorker(file.path.toString(),frozen.config) { lease ->
                            proof = requireNotNull(ProcessResources.coordinator.observeOwnedPair(owner,heavy,lease)) { "LAB_RESOURCE_PROOF_MISSING" }
                            proof!!.onRevoked { stopLabAttempt(attempt) }
                            attempt.requireLive()
                        }
                        // Same immutable private snapshot is hashed again AFTER actual native load returns.
                        val after=LabFileProof.verify(privateRoot,path,attempt::requireLive)
                        check(after.key == file.key) { "LAB_LOADED_FILE_CHANGED" }
                    } else {
                        proof = requireNotNull(ProcessResources.coordinator.observeOwnedPair(owner,heavy,requireNotNull(modelLease))) { "LAB_RESOURCE_PROOF_MISSING" }
                        proof!!.onRevoked { stopLabAttempt(attempt) }
                    }
                    attempt.requireLive();labHostGuard(frozen.config)
                    val sizes=requireNotNull(allocations) { "LAB_NATIVE_ALLOCATIONS_UNKNOWN" }
                    val budget=frozen.config.memoryBudget
                    check(sizes.model in 0..budget && sizes.context in 0..(budget-sizes.model) &&
                        sizes.compute in 0..(budget-sizes.model-sizes.context)) { "LAB_NATIVE_BUFFERS_EXCEEDED" }
                    file.requireStable();check(proof!!.isLive()) { "LAB_RESOURCE_REVOKED" }
                    val resident=StreamResident(requireNotNull(modelLease),owner,streamWorkerIncarnation,streamResidentIncarnation,frozen.config)
                    labResidence = LabResidence(file,resident)
                    attempt.advance(LabPhase.FILE_VERIFIED,LabPhase.RESIDENT)
                    val system=requireNotNull(frozen.systemPrompt).toByteArray(Charsets.UTF_8)
                    val question=frozen.messages.single().text.toByteArray(Charsets.UTF_8)
                    val measured=native.measureFixtureP2(handle,system,question)
                    check(measured.size == 3 && measured[1].size <= 128*1024 && measured[2].size <= 128*1024)
                    val count=measured[0].toString(Charsets.US_ASCII).toInt()
                    val framed=java.io.ByteArrayOutputStream()
                    java.io.DataOutputStream(framed).use { out ->
                        for (part in listOf("jarvis.lab-prepared/1".toByteArray(Charsets.US_ASCII),
                            attempt.spec.requestSha256.toByteArray(Charsets.US_ASCII),measured[1],measured[2])) {
                            out.writeInt(part.size);out.write(part)
                        }
                        out.writeInt(count);out.writeInt(frozen.config.context);out.writeInt(frozen.config.maxTokens);out.writeInt(frozen.config.threads)
                    }
                    val prepared=StreamPrepared(streamHash(measured[1]),streamHash(measured[2]),streamHash(framed.toByteArray()),count,frozen.config.context,frozen.config,attempt.spec.requestSha256)
                    val binding=LabStreamBinding.admit(attempt,proof!!,prepared,resident) {
                        !cleanupFailed && labResidence?.resident === resident && modelLease === resident.lease &&
                            streamResidentIncarnation == resident.incarnation && loaded == (file.path.toString() to frozen.config)
                    }
                    attempt.advance(LabPhase.PREPARED,LabPhase.INVOKING)
                    val result = if (attempt.spec.variant == LabVariant.STREAM) {
                        val q=requireNotNull(buffer);q.bind(binding)
                        val callback=OwnedStreamCallback(binding,q)
                        synchronized(handleLock) { attempt.requireLive();activeStream = attempt.revocation to q }
                        try {
                            val terminal=native.generateStream(handle,system,question,frozen.config.maxTokens,frozen.config.context,
                                binding.bindingSha256.toByteArray(Charsets.US_ASCII),callback)
                            check(terminal in StreamKind.COMPLETED.ordinal..StreamKind.ERROR.ordinal)
                            val kind=StreamKind.entries[terminal]
                            check(q.acceptedTerminal() == kind && (kind != StreamKind.COMPLETED || callback.hasPrepared())) { "UNCONFIRMED_NATIVE_TERMINAL" }
                            val state=when(kind) { StreamKind.COMPLETED -> LabPhase.CONTENT_DONE;StreamKind.CANCELLED -> LabPhase.CANCELLED;else -> LabPhase.ERROR }
                            LabGenerationResult(LabVariant.STREAM,state,null,OwnedStreamOutcome(kind,true))
                        } catch(e: Throwable) { q.invocationFailed();throw e }
                        finally {
                            callback.close();q.nativeInvocationFinished()
                            synchronized(handleLock) { if(activeStream?.first === attempt.revocation) activeStream=null }
                        }
                    } else {
                        val bytes=native.generateFixtureP2(handle,system,question,frozen.config.maxTokens,frozen.config.context)
                        check(bytes.size <= 64*1024) { "LAB_OUTPUT_LIMIT" }
                        Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes))
                        LabGenerationResult(LabVariant.HISTORICAL,LabPhase.CONTENT_DONE,bytes.copyOf(),null)
                    }
                    check(!cleanupFailed) { "LAB_NATIVE_STOP_UNCONFIRMED" }
                    file.requireStable()
                    if(result.terminal == LabPhase.CONTENT_DONE) { attempt.requireLive();check(proof!!.isLive()) }
                    if(result.terminal != LabPhase.CONTENT_DONE) { unloadOnWorker() }
                    attempt.terminal(result.terminal)
                    result // CONTENT_DONE is not DRAINED or UNLOADED; a verified warm residence may remain.
                } catch(e: Throwable) {
                    buffer?.invocationFailed()
                    attempt.terminal(if(cleanupFailed) LabPhase.CLEANUP_FAILED else LabPhase.UNKNOWN)
                    if(cleanupFailed) throw e // preserve the FIRST critical cleanup failure; never automatically retry
                    // Cleanup even after cancellation; never release ownership before the native call has returned.
                    try { if(nativeTouched) unloadOnWorker() }
                    catch(cleanup: Throwable) {
                        cleanupFailed=true;attempt.terminal(LabPhase.CLEANUP_FAILED);cleanup.addSuppressed(e);throw cleanup
                    }
                    throw e
                } finally {
                    proof?.close()
                    if(cleanupFailed) { labQuarantineHeavy=heavy } else heavy?.close()
                }
            }
        } finally {
            attempt.close()
            synchronized(handleLock) { if(labAttempt === attempt) labAttempt=null }
            generating.set(false)
        }
    }
    override suspend fun unload() = withContext(worker) { unloadOnWorker() }
    private fun unloadOnWorker() {
        try { native.unload(handle) } catch (e: Throwable) { cleanupFailed = true; throw e }
        cleanupFailed = false; loaded = null; allocations = null; labResidence = null
        modelLease?.close(); modelLease = null
        labQuarantineHeavy?.close(); labQuarantineHeavy = null
        allocationDiagnostic = "Moteur déchargé ; buffers natifs non résidents"
    }
    override suspend fun close() {
        requestStop()
        withContext(worker) {
            synchronized(handleLock) { if (handle != 0L) {
                try { native.destroy(handle) } catch (e: Throwable) { cleanupFailed = true; throw e }
                handle = 0
            } }
            loaded = null; allocations = null; labResidence = null; modelLease?.close(); modelLease = null
            labQuarantineHeavy?.close(); labQuarantineHeavy = null
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
