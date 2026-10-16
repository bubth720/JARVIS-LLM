package fr.jarvis.fixture.llm
import fr.jarvis.fixture.p2.*
import fr.jarvis.domain.*
import fr.jarvis.domain.generation.P2Exception
import fr.jarvis.llm.engine.FixtureGeneration
import fr.jarvis.llm.engine.FixtureContextExceeded
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

enum class Phase { AUTHORIZING, ADMITTED, LOADING, READY, RUNNING, STOP_REQUESTED, NATIVE_DONE, UNLOADING, UNLOADED, REJECTED, UNKNOWN, CLEANUP_FAILED }
interface RuntimePort {
    suspend fun admit(request: Request): RuntimeConfig
    suspend fun load(request: Request,config: RuntimeConfig,nativeDispatched: () -> Unit)
    suspend fun generate(request: Request,config: RuntimeConfig): FixtureGeneration
    suspend fun unload()
    fun stop()
    suspend fun close() {}
    fun safe(request: Request): Boolean
}
interface Journal { fun unresolved(): Boolean; fun begin(key: Map<String,String>): Boolean; fun complete(receipt: Map<String,String>): Boolean }
/** Observes actual backend returns. It does NOT instantiate P2Lifecycle or acquire backend leases. */
class EndpointController(private val runtime: RuntimePort,private val authority: Authority,private val journal: Journal,
                         private val clock: () -> Long,private val scope: CoroutineScope) {
    private var closed=false
    private var operationJob: Job? = null
    private var blocked=journal.unresolved()
    private var active: Request? = null
    private var phase=if(blocked) Phase.UNKNOWN else Phase.UNLOADED
    private var started=false; private var ended=false; private var unloaded=false; private var released=false
    private var code="NONE"; private var text=""; private var evidence: FixtureGeneration? = null
    private var effective: RuntimeConfig? = null
    private var deadline=0L; private var lastGeneration=0L
    private val seen=mutableSetOf<String>(); private val stopped=AtomicBoolean(false)
    @Synchronized fun submit(request: Request,uid: Int): Map<String,String> {
        demand(!closed,"CLOSED")
        demand(!blocked,"UNKNOWN_STATE")
        demand(active == null || phase in setOf(Phase.UNLOADED,Phase.REJECTED),"BUSY")
        demand(request.key.operationId !in seen && request.key.generation > lastGeneration,"REPLAY")
        demand(seen.size < 256,"SESSION_QUOTA")
        seen += request.key.operationId; lastGeneration=request.key.generation
        active=request; phase=Phase.AUTHORIZING; started=false; ended=false; unloaded=false; released=false
        text=""; evidence=null; effective=null; code="NONE"; stopped.set(false); deadline=clock()+request.p2.limits.remainingMillis
        operationJob=scope.launch { execute(request,uid) }
        return receipt(request)
    }
    @Synchronized private fun update(value: Phase) { phase=if(stopped.get() && value in setOf(Phase.LOADING,Phase.READY,Phase.RUNNING)) Phase.STOP_REQUESTED else value }
    private fun checkActive() { demand(!stopped.get(),"CANCELLED"); demand(clock() < deadline,"EXPIRED") }
    private suspend fun execute(r: Request,uid: Int) {
        var admitted=false
        var watch: Job? = null
        try {
            val expires=authority.authorize(r,uid,deadline)
            synchronized(this) { checkActive(); demand(expires > clock() && expires <= deadline,"EXPIRED_LEASE"); deadline=expires
                if (!journal.begin(r.keyFields)) { blocked=true; throw Refusal("JOURNAL_FAILED") }; admitted=true; phase=Phase.ADMITTED }
            watch=scope.launch {
                while (isActive) { delay(250); if (clock() >= deadline || !runtime.safe(r)) { stop(r.keyFields,"SAFETY_OR_DEADLINE"); break } }
            }
            checkActive(); val config=runtime.admit(r); synchronized(this) { effective=config }; checkActive()
            update(Phase.LOADING)
            // Cancellation never abandons a JNI call: drain all calls before proving end/unload.
            withContext(NonCancellable) { runtime.load(r,config) { synchronized(this@EndpointController) { started=true } } }
            update(Phase.READY); checkActive(); demand(runtime.safe(r),"SAFETY_REFUSAL")
            update(Phase.RUNNING)
            val result=withContext(NonCancellable) { runtime.generate(r,config) }
            synchronized(this) { evidence=result; if (!stopped.get() && clock() < deadline) text=result.text else code="CANCELLED_OR_EXPIRED" }
        } catch (e: Throwable) {
            synchronized(this) { code=when(e) { is Refusal -> e.code; is P2Exception -> e.code.name; is FixtureContextExceeded -> "CONTEXT_EXCEEDED"; else -> "NATIVE_FAILURE" }; text="" }
        } finally {
            watch?.cancel()
            if (!admitted) synchronized(this) { phase=if(blocked) Phase.UNKNOWN else Phase.REJECTED; text="" }
            else withContext(NonCancellable) {
                synchronized(this@EndpointController) { ended=true; phase=Phase.NATIVE_DONE }
                update(Phase.UNLOADING)
                try {
                    runtime.unload()
                    synchronized(this@EndpointController) {
                        unloaded=true; phase=Phase.UNLOADED; released=true
                        if (!journal.complete(receipt(r))) { released=false; blocked=true; phase=Phase.UNKNOWN; text=""; code="JOURNAL_FAILED" }
                    }
                } catch (_: Exception) { synchronized(this@EndpointController) { blocked=true; phase=Phase.CLEANUP_FAILED; text=""; code="CLEANUP_FAILED" } }
            }
        }
    }
    @Synchronized fun stop(key: Map<String,String>,reason: String="CANCELLED"): Map<String,String> {
        val r=active ?: throw Refusal("STALE_OPERATION")
        demand(r.keyFields == key,"STALE_OPERATION")
        text=""
        if (phase !in setOf(Phase.UNLOADED,Phase.REJECTED,Phase.CLEANUP_FAILED,Phase.UNKNOWN)) {
            stopped.set(true); code=reason; phase=Phase.STOP_REQUESTED; runtime.stop()
        }
        return receipt(r)
    }
    @Synchronized fun disconnect() { active?.let { stop(it.keyFields,"CLIENT_OR_ARBITER_LOST") } }
    @Synchronized fun status(key: Map<String,String>): Map<String,String> {
        val r=active ?: throw Refusal(if(blocked) "UNKNOWN_STATE" else "STALE_OPERATION")
        demand(key == r.keyFields,"STALE_OPERATION")
        return receipt(r)
    }
    @Synchronized fun shutdown() {
        if (closed) return
        closed=true; disconnect()
        val draining=operationJob
        scope.launch {
            draining?.join()
            withContext(NonCancellable) {
                try { runtime.close() }
                catch (_: Exception) { synchronized(this@EndpointController) {
                    blocked=true; phase=Phase.CLEANUP_FAILED; released=false; text=""; code="CLEANUP_FAILED"
                    active?.let { journal.begin(it.keyFields) }
                } }
            }
            scope.cancel()
        }
    }
    @Synchronized private fun receipt(r: Request): Map<String,String> {
        val valid=phase == Phase.UNLOADED && released && code == "NONE" && clock() < deadline && !stopped.get()
        return r.keyFields + mapOf("request_digest" to r.digest,"phase" to phase.name,"code" to if(clock() >= deadline && code == "NONE") "EXPIRED" else code,
            "native_ever_started" to started.toString(),"effective_end" to ended.toString(),"model_unloaded" to unloaded.toString(),"release_confirmed" to released.toString(),
            "model_sha256" to r.p2.modelSha256,"template_sha256" to (evidence?.templateSha256 ?: ""),"rendered_sha256" to (evidence?.renderedSha256 ?: ""),
            "input_tokens" to (evidence?.inputTokens?.toString() ?: ""),"context_tokens" to (evidence?.contextLimit?.toString() ?: ""),"loaded_context_tokens" to (effective?.context?.toString() ?: ""),"threads" to (effective?.threads?.toString() ?: ""),"output_tokens" to (effective?.maxTokens?.toString() ?: ""),"text" to if(valid) text else "")
    }
}
