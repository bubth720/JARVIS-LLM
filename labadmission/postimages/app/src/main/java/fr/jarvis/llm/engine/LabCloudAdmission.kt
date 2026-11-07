package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import fr.jarvis.domain.resources.ResourceObservation
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.security.*
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Proposed host-only, operator-signed scope. No CORE credential, entry point, key or permit is installed. */
internal object LabAuthorizedModel {
    const val repository = "Qwen/Qwen3-0.6B-GGUF"
    const val revision = "8e42d41f70cb6c571f58c3f31bd9287b372d97cc"
    const val fileName = "Qwen3-0.6B-Q4_K_M.gguf"
    const val size = 396704416L
    const val sha256 = "b0638f08417a2d3c8652760462eb5407c6e30173cf9608ad0820757a281eea0e"
}
internal interface LabClock { fun monotonicNanos(): Long; fun unixMillis(): Long }
internal object LabSystemClock : LabClock {
    override fun monotonicNanos() = System.nanoTime()
    override fun unixMillis() = System.currentTimeMillis()
}
internal class LabExecution(val run: String, val attempt: String, val commit: String) {
    init { require(run.matches(Regex("[1-9][0-9]{0,19}")) && attempt.matches(Regex("[1-9][0-9]{0,5}")))
        require(commit.matches(Regex("[0-9a-f]{40}"))) }
    override fun toString() = "LabExecution(redacted)"
    companion object {
        fun observe(): LabExecution {
            check(System.getenv("GITHUB_ACTIONS") == "true" && System.getenv("RUNNER_OS") == "Linux" &&
                System.getenv("GITHUB_REPOSITORY") == "bubth720/JARVIS-LLM") { "LAB_EXECUTION_UNQUALIFIED" }
            return LabExecution(requireNotNull(System.getenv("GITHUB_RUN_ID")), requireNotNull(System.getenv("GITHUB_RUN_ATTEMPT")),
                requireNotNull(System.getenv("GITHUB_SHA")))
        }
    }
}
internal class LabOwnerChallenge(val owner: String, val worker: String) {
    override fun toString() = "LabOwnerChallenge(redacted)"
}
/** No caller-selected trust anchor. Provisioning a real public-key fingerprint requires another exact reviewed diff. */
internal class LabOperatorTrust private constructor(val publicKey: PublicKey) {
    override fun toString() = "LabOperatorTrust(redacted)"
    companion object {
        private const val APPROVED_OPERATOR_KEY_SHA256 = "" // CLOSED: no operator root is authorized/provisioned by this review.
        fun fromApprovedKey(key: PublicKey): LabOperatorTrust {
            check(APPROVED_OPERATOR_KEY_SHA256.matches(Regex("[0-9a-f]{64}"))) { "LAB_TRUST_ROOT_NOT_CONFIGURED" }
            check(streamHash(key.encoded) == APPROVED_OPERATOR_KEY_SHA256) { "LAB_OPERATOR_KEY_UNTRUSTED" }
            return LabOperatorTrust(key)
        }
    }
}
internal enum class LabVariant { HISTORICAL, STREAM }
internal class LabPermitSpec(val execution: LabExecution, val nonce: String, val session: String,
    val owner: String, val worker: String, val issuedUnixMillis: Long, val expiresUnixMillis: Long,
    val modelSha256: String, val templateSha256: String, val requestSha256: String,
    val config: RuntimeConfig, val variant: LabVariant) {
    init {
        require(listOf(nonce,session,worker).all { it.length == 36 && runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) })
        require(owner.length in 1..120 && issuedUnixMillis >= 0 && expiresUnixMillis > issuedUnixMillis &&
            expiresUnixMillis - issuedUnixMillis <= 300_000)
        require(listOf(modelSha256,templateSha256,requestSha256).all { it.matches(Regex("[0-9a-f]{64}")) })
        require(modelSha256 == LabAuthorizedModel.sha256 && config.model == ModelKind.SMALL &&
            config.context == 512 && config.threads == 1 && config.maxTokens in setOf(8,24)) { "LAB_MODEL_OR_CONFIGURATION_UNAPPROVED" }
    }
    fun canonical(): ByteArray = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
        for (s in listOf("jarvis.lab-cloud.operator/1", "bubth720/JARVIS-LLM", execution.run, execution.attempt,
            execution.commit, nonce,session,owner,worker,issuedUnixMillis.toString(),expiresUnixMillis.toString(),
            LabAuthorizedModel.repository,LabAuthorizedModel.revision,LabAuthorizedModel.fileName,LabAuthorizedModel.size.toString(),
            modelSha256,templateSha256,requestSha256,config.model.name,config.context.toString(),config.maxTokens.toString(),
            config.threads.toString(),variant.name)) { val b=s.toByteArray(Charsets.UTF_8);out.writeInt(b.size);out.write(b) }
    } }.toByteArray()
    override fun toString() = "LabPermitSpec(scope=LAB_CLOUD, redacted)"
}
internal class LabSignedPermit(val spec: LabPermitSpec, signature: ByteArray) {
    private val signature = signature.copyOf()
    fun verify(key: PublicKey): Boolean = signature.size == 64 && Signature.getInstance("Ed25519").run {
        initVerify(key);update(spec.canonical());verify(signature)
    }
    override fun toString() = "LabSignedPermit(redacted)"
}
/** Process-scoped, bounded ledger shared by all authority instances. Never evict a consumed nonce to allow replay. */
private object LabReplayLedger {
    private class Entry(var consumed: Boolean=false, var revoked: Boolean=false, var stop: (() -> Unit)?=null, var stopDelivered: Boolean=false)
    private val entries = HashMap<String,Entry>()
    private fun key(s: LabPermitSpec) = streamHash((s.execution.run+"\n"+s.nonce).toByteArray(Charsets.US_ASCII))
    private fun entry(s: LabPermitSpec): Entry = entries[key(s)] ?: run {
        check(entries.size < 4096) { "LAB_LEDGER_FULL" };Entry().also { entries[key(s)]=it }
    }
    @Synchronized fun consume(spec: LabPermitSpec) {
        val e=entry(spec);check(!e.consumed && !e.revoked) { "LAB_REPLAY_OR_REVOKED" };e.consumed=true
    }
    @Synchronized fun live(spec: LabPermitSpec) = entries[key(spec)]?.let { it.consumed && !it.revoked } == true
    fun revoke(spec: LabPermitSpec) {
        val action=synchronized(this) {
            val e=entry(spec);e.revoked=true
            if(e.stop != null && !e.stopDelivered) { e.stopDelivered=true;e.stop } else null
        };action?.invoke()
    }
    fun watch(spec: LabPermitSpec, stop: () -> Unit) {
        val stale=synchronized(this) { val e=entry(spec);check(e.stop == null);e.stop=stop
            if(e.revoked && !e.stopDelivered) { e.stopDelivered=true;true } else false }
        if(stale) stop()
    }
    @Synchronized fun detach(spec: LabPermitSpec) { entries[key(spec)]?.stop=null }
}
private object LabDeadlines {
    // A control watchdog only: it never loads, prepares, generates or owns a second native worker.
    val executor = ScheduledThreadPoolExecutor(1) { r -> Thread(r,"jarvis-lab-revocation").apply { isDaemon=true } }
        .apply { removeOnCancelPolicy=true }
}
internal class LabCloudAuthority(private val trust: LabOperatorTrust, private val execution: LabExecution,
    private val clock: LabClock = LabSystemClock) {
    fun revoke(signed: LabSignedPermit) {
        check(signed.verify(trust.publicKey)) { "LAB_REVOCATION_SIGNATURE_INVALID" }
        val e=signed.spec.execution
        check(e.run == execution.run && e.attempt == execution.attempt && e.commit == execution.commit) { "LAB_REVOCATION_EXECUTION_MISMATCH" }
        LabReplayLedger.revoke(signed.spec)
    }
    fun redeem(signed: LabSignedPermit, owner: LabOwnerChallenge, request: GenerationRequest): LabAttempt {
        val s=signed.spec
        check(signed.verify(trust.publicKey)) { "LAB_SIGNATURE_INVALID" }
        check(s.execution.run == execution.run && s.execution.attempt == execution.attempt && s.execution.commit == execution.commit &&
            s.owner == owner.owner && s.worker == owner.worker) { "LAB_EXECUTION_OR_OWNER_MISMATCH" }
        check(s.config == request.config && s.requestSha256 == labRequestHash(request)) { "LAB_REQUEST_MISMATCH" }
        val now=clock.unixMillis()
        check(now >= s.issuedUnixMillis && now < s.expiresUnixMillis) { "LAB_PERMIT_EXPIRED_OR_NOT_YET_VALID" }
        val remaining=s.expiresUnixMillis-now
        val mono=clock.monotonicNanos(); check(mono >= 0 && mono <= Long.MAX_VALUE-remaining*1_000_000) { "LAB_CLOCK_INVALID" }
        LabReplayLedger.consume(s) // permanently consumed before any file/model/native operation, even if the next step fails
        return LabAttempt(s,clock,mono+remaining*1_000_000)
    }
}
/** Deep copy of the mutable-list boundary BEFORE signature verification; backend keeps this snapshot private. */
internal fun labFreezeRequest(r: GenerationRequest) = GenerationRequest(r.messages.map { it.copy() },r.config.copy(),r.systemPrompt)
internal fun labRequestHash(r: GenerationRequest): String {
    require(r.messages.size == 1 && r.messages.single().role == "user" && r.systemPrompt != null)
    val system=requireNotNull(r.systemPrompt);val question=r.messages.single().text
    require(system.length <= 8192 && question.length <= 128*1024) { "LAB_INPUT_LIMIT" }
    fun exactUtf8(text: String): ByteArray {
        val buffer=Charsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }
    require(exactUtf8(system).size <= 8192 && exactUtf8(question).size <= 128*1024) { "LAB_INPUT_LIMIT" }
    val b=ByteArrayOutputStream()
    DataOutputStream(b).use { out ->
        for(s in listOf("jarvis.lab-request/1",r.systemPrompt!!,r.messages.single().text,r.config.model.name)) {
            val bytes=s.toByteArray(Charsets.UTF_8);out.writeInt(bytes.size);out.write(bytes)
        }
        out.writeInt(r.config.context);out.writeInt(r.config.maxTokens);out.writeInt(r.config.threads)
    }
    return streamHash(b.toByteArray())
}
internal enum class LabPhase { AUTHORIZED, FILE_VERIFIED, RESIDENT, PREPARED, INVOKING, CONTENT_DONE, CANCELLED, ERROR, UNKNOWN, CLEANUP_FAILED }
internal class LabAttempt internal constructor(val spec: LabPermitSpec, private val clock: LabClock, private val deadline: Long) : AutoCloseable {
    val revocation = StreamRevocation()
    @Volatile var phase = LabPhase.AUTHORIZED; private set
    private val closed = AtomicBoolean(false)
    private val stopLock=Any()
    private var stop: (() -> Unit)? = null
    private var timer: java.util.concurrent.ScheduledFuture<*>? = null
    private var monitorTask: java.util.concurrent.ScheduledFuture<*>? = null
    @Volatile var faultCode: String? = null; private set
    fun isLive() = !closed.get() && revocation.isAllowed() && LabReplayLedger.live(spec) && clock.monotonicNanos() in 0 until deadline
    fun requireLive() { check(isLive()) { "LAB_PERMIT_REVOKED_OR_EXPIRED" } }
    fun attachStop(monitor: () -> Unit = { LabHostBudget.requireFits(spec.config) }, action: () -> Unit) {
        synchronized(stopLock) {
            check(stop == null && !closed.get()); stop=action
            monitorTask=LabDeadlines.executor.scheduleAtFixedRate({
                try { monitor() } catch (failure: Throwable) { faultCode=if(failure.message == "LAB_CALLER_CANCELLED") "LAB_CALLER_CANCELLED" else "LAB_HOST_MONITOR_REFUSED";revoke() }
            },100,100,TimeUnit.MILLISECONDS)
            timer=LabDeadlines.executor.schedule({ revoke() },(deadline-clock.monotonicNanos()).coerceAtLeast(0),TimeUnit.NANOSECONDS)
        }
        LabReplayLedger.watch(spec,action)
        if(!isLive()) revoke()
    }
    /** All native stop callbacks run outside the permit/broker monitors. */
    fun revoke() { revocation.revoke(); LabReplayLedger.revoke(spec) }
    fun cancelledByBackend() { revocation.revoke() }
    fun advance(expected: LabPhase, next: LabPhase) { requireLive(); check(phase == expected); phase=next }
    fun terminal(next: LabPhase) { check(next in setOf(LabPhase.CONTENT_DONE,LabPhase.CANCELLED,LabPhase.ERROR,LabPhase.UNKNOWN,LabPhase.CLEANUP_FAILED));phase=next }
    override fun close() { closed.set(true);LabReplayLedger.detach(spec); synchronized(stopLock) { timer?.cancel(false);monitorTask?.cancel(false);timer=null;monitorTask=null;stop=null } }
    override fun toString() = "LabAttempt(phase=$phase, redacted)"
}
/** Cooperative private snapshot contract; no hostile same-UID writer is within this laboratory boundary. */
internal class LabFileProof private constructor(val path: Path, val key: Any, val modifiedMillis: Long) {
    fun requireStable() {
        val a=Files.readAttributes(path,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
        check(a.isRegularFile && !a.isSymbolicLink && a.size() == LabAuthorizedModel.size && a.fileKey() == key &&
            a.lastModifiedTime().toMillis() == modifiedMillis) { "LAB_FILE_CHANGED" }
        check(Files.getPosixFilePermissions(path) == setOf(PosixFilePermission.OWNER_READ) &&
            Files.getAttribute(path,"unix:nlink") == 1) { "LAB_FILE_WRITABLE_OR_ALIASED" }
    }
    override fun toString() = "LabFileProof(redacted)"
    companion object {
        fun verify(root: Path, path: Path, alive: () -> Unit): LabFileProof {
            val r=root.toAbsolutePath().normalize(); val p=path.toAbsolutePath().normalize()
            check(r == r.toRealPath() && p.parent == r && p == p.toRealPath() && p.fileName.toString() == LabAuthorizedModel.fileName &&
                Files.getPosixFilePermissions(r) == setOf(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_EXECUTE)) { "LAB_PRIVATE_SNAPSHOT_REQUIRED" }
            val a=Files.readAttributes(p,BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS)
            check(a.isRegularFile && a.size() == LabAuthorizedModel.size && a.fileKey() != null) { "LAB_FILE_SIZE_OR_TYPE" }
            val proof=LabFileProof(p,a.fileKey(),a.lastModifiedTime().toMillis());proof.requireStable()
            val digest=MessageDigest.getInstance("SHA-256");var count=0L
            Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer=ByteArray(64*1024)
                while(true) { alive();val n=input.read(buffer);if(n<0)break;count+=n
                    check(count <= LabAuthorizedModel.size) { "LAB_FILE_TOO_LARGE" };digest.update(buffer,0,n) }
            }
            check(count == LabAuthorizedModel.size && streamHashDigest(digest.digest()) == LabAuthorizedModel.sha256) { "LAB_FILE_SHA256" }
            proof.requireStable();return proof
        }
        private fun streamHashDigest(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(java.util.Locale.ROOT,it.toInt() and 255) }
    }
}
internal class LabResidence(val file: LabFileProof, val resident: StreamResident) {
    override fun toString() = "LabResidence(redacted)"
}
internal class LabStreamBinding private constructor(private val attempt: LabAttempt,
    private val resources: ResourceObservation, override val prepared: StreamPrepared,
    override val resident: StreamResident, private val residenceLive: () -> Boolean) : StreamBinding {
    override val revocation = attempt.revocation
    override val bindingSha256 = streamHash((listOf("jarvis.lab-cloud.stream/1",streamHash(attempt.spec.canonical()),
        prepared.requestSha256,prepared.renderedSha256,prepared.templateSha256,prepared.inputTokens.toString(),prepared.contextLimit.toString(),
        resources.brokerIncarnation,resources.revision.toString(),resources.heavyIdentity.toString(),resources.modelIdentity.toString(),
        resident.worker,resident.incarnation).joinToString("\n")).toByteArray(Charsets.US_ASCII))
    override fun isLive() = attempt.isLive() && resources.isLive() && residenceLive()
    override fun toString() = "LabStreamBinding(scope=LAB_CLOUD, redacted)"
    companion object {
        fun admit(a: LabAttempt, resources: ResourceObservation, p: StreamPrepared, r: StreamResident, residenceLive: () -> Boolean): LabStreamBinding {
            a.requireLive();check(resources.isLive() && residenceLive() && a.phase == LabPhase.RESIDENT && p.config == a.spec.config &&
                p.templateSha256 == a.spec.templateSha256 && p.originRequestSha256 == a.spec.requestSha256 && p.config == r.config && r.owner == a.spec.owner && r.worker == a.spec.worker) { "LAB_PREPARATION_OR_RESIDENCE_MISMATCH" }
            a.advance(LabPhase.RESIDENT,LabPhase.PREPARED)
            return LabStreamBinding(a,resources,p,r,residenceLive)
        }
    }
}
internal class LabGenerationResult(val variant: LabVariant, val terminal: LabPhase,
    private val completeBytes: ByteArray?, val streaming: OwnedStreamOutcome?) {
    fun completeCopy() = completeBytes?.copyOf()
    override fun toString() = "LabGenerationResult(variant=$variant, terminal=$terminal, noDrainOrUnloadProof)"
}
