package fr.jarvis.llm.engine

import fr.jarvis.domain.*
import java.security.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** NONPRODUCTIVE unit fixtures. Ephemeral signing keys stay in memory; no operator credential, JNI or GGUF. */
internal class LabFixtures {
    class Clock : LabClock { var wall=1_000_000L;var mono=1_000_000_000L
        override fun unixMillis()=wall;override fun monotonicNanos()=mono }
    val clock=Clock()
    val keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val execution=LabExecution(runs.incrementAndGet().toString(),"1","a".repeat(40))
    val owner=LabOwnerChallenge("unit-owner",UUID.randomUUID().toString())
    val config=RuntimeConfig(ModelKind.SMALL,512,8,1)
    val request=GenerationRequest(listOf(Message("user","SYNTHETIC public unit question")),config,"SYNTHETIC public instructions")
    val template="SYNTHETIC unit template".toByteArray()
    val rendered="SYNTHETIC canonical unit prompt".toByteArray()
    // Explicit unit-only reflection: production factory remains CLOSED with no real trust anchor.
    val trust=LabOperatorTrust::class.java.getDeclaredConstructor(PublicKey::class.java).apply { isAccessible=true }.newInstance(keys.public)
    val authority=LabCloudAuthority(trust,execution,clock)
    fun spec(nonce: String=UUID.randomUUID().toString(),session: String=UUID.randomUUID().toString(),
             challenge: LabOwnerChallenge=owner, expiry: Long=clock.wall+60_000, issued: Long=clock.wall,
             templateSha: String=streamHash(template), requestSha: String=labRequestHash(request), variant: LabVariant=LabVariant.STREAM,
             execution: LabExecution=this.execution, config: RuntimeConfig=this.config, modelSha: String=LabAuthorizedModel.sha256) =
        LabPermitSpec(execution,nonce,session,challenge.owner,challenge.worker,issued,expiry,modelSha,templateSha,requestSha,config,variant)
    fun sign(spec: LabPermitSpec=spec())=LabSignedPermit(spec,Signature.getInstance("Ed25519").run {
        initSign(keys.private);update(spec.canonical());sign()
    })
    fun attempt(): LabAttempt=authority.redeem(sign(),owner,request)
    companion object { private val runs=AtomicLong(80000000) }
}
