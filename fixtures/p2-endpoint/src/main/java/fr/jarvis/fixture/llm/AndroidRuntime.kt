package fr.jarvis.fixture.llm
import fr.jarvis.domain.*
import fr.jarvis.fixture.p2.*
import fr.jarvis.llm.engine.*
import fr.jarvis.llm.ProcessResources
import fr.jarvis.domain.resources.Module
/** Same source-set backend and native library, instantiated lazily only after real authority and safety checks. */
class AndroidRuntime(private val app: FixtureApplication) : RuntimePort {
    private val nativeLock=Any()
    @Volatile private var backend: LlamaBackend? = null
    private val importOwner="fixture-model-import-"+java.util.UUID.randomUUID()
    private var baseline=0L; private var resident: Residency? = null
    @Volatile private var stop=false
    private fun decision(r: Request) = SafetyPolicy().decide(r.profile,app.monitor.snapshot(),setOf(r.p2.model),resident)
    override suspend fun admit(request: Request): RuntimeConfig {
        stop=false
        val existing=request.p2.model in app.models.available() && app.models.expectedDigest(request.p2.model) == request.p2.modelSha256
        val staged=app.staged(request.p2.model)?.second == request.p2.modelSha256
        demand(existing || staged,"MODEL_UNAVAILABLE_OR_MISMATCH")
        val d=decision(request); val config=d.config ?: throw Refusal("SAFETY_REFUSAL")
        demand(config.model == request.p2.model,"MODEL_MISMATCH")
        // Never raise the requested context. Native enforcement will also cap a larger loaded context.
        demand(request.p2.limits.contextTokens >= if(config.model == ModelKind.MAIN) 768 else 512,"INVALID_LIMIT")
        baseline=app.monitor.snapshot().pssBytes
        return config.copy(maxTokens=minOf(config.maxTokens,request.p2.limits.outputTokens))
    }
    override suspend fun load(request: Request,config: RuntimeConfig,nativeDispatched: () -> Unit) {
        val path=ProcessResources.requireHeavy(importOwner,Module.MODEL_IMPORT,"fixture import et SHA",128L*1024*1024).use {
            if (app.models.expectedDigest(request.p2.model) != request.p2.modelSha256 || request.p2.model !in app.models.available()) {
                val staged=app.staged(request.p2.model) ?: throw Refusal("MODEL_UNAVAILABLE")
                demand(staged.second == request.p2.modelSha256,"MODEL_MISMATCH")
                app.models.import(staged.first,request.p2.model,staged.second) { stop }
            }
            app.models.verify(request.p2.model) { stop }
        }
        demand(!stop && decision(request).config != null,"SAFETY_REFUSAL")
        baseline=app.monitor.snapshot().pssBytes // exclude the completed import/hash phase from residency credit
        val b=synchronized(nativeLock) {
            demand(!stop,"CANCELLED")
            (backend ?: LlamaBackend().also { backend=it }).also { it.prepareOperation() }
        }
        nativeDispatched() // create() has really returned; no controller lock held while acquiring nativeLock
        b.load(path,config)
        resident=Residency(config.model,(app.monitor.snapshot().pssBytes-baseline).coerceAtLeast(0))
    }
    override suspend fun generate(request: Request,config: RuntimeConfig): FixtureGeneration {
        demand(!stop && safe(request),"SAFETY_REFUSAL")
        val input=GenerationRequest(listOf(Message("user",request.p2.question)),config,request.p2.system)
        return backend!!.generateFixtureP2(input,minOf(config.context,request.p2.limits.contextTokens))
    }
    override suspend fun unload() { backend?.unload(); resident=null }
    override fun stop() { synchronized(nativeLock) { stop=true; backend?.requestStop() } }
    override suspend fun close() { backend?.close(); backend=null }
    override fun safe(request: Request) = !stop && decision(request).config != null
}
