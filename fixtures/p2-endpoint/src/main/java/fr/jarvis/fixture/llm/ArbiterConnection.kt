package fr.jarvis.fixture.llm
import android.content.*
import android.os.*
import fr.jarvis.fixture.p2.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface Authority { suspend fun authorize(request: Request,uid: Int,deadline: Long): Long }
/** Real CORE service only. No simulated authority is packaged in the endpoint. */
class ArbiterConnection(private val context: Context,private val lost: () -> Unit) : Authority,ServiceConnection {
    @Volatile private var remote: IP2Arbiter? = null
    private val identity=Identity(context)
    private val death=IBinder.DeathRecipient { remote=null; lost() }
    private var bound=false
    fun connect() {
        bound=runCatching { context.bindService(Intent().setComponent(ComponentName(Wire.CLIENT,"${Wire.CLIENT}.P2ArbiterService")),this,Context.BIND_AUTO_CREATE) }.getOrDefault(false)
    }
    override fun onServiceConnected(name: ComponentName,binder: IBinder) {
        try {
            val uid=context.packageManager.getApplicationInfo(Wire.CLIENT,0).uid
            identity.requireClient(uid)
            demand(name == ComponentName(Wire.CLIENT,"${Wire.CLIENT}.P2ArbiterService"),"UNAUTHORIZED_ARBITER")
            binder.linkToDeath(death,0); remote=IP2Arbiter.Stub.asInterface(binder)
        } catch (_: Exception) { remote=null; lost() }
    }
    override fun onServiceDisconnected(name: ComponentName) { remote=null; lost() }
    override fun onBindingDied(name: ComponentName) { remote=null; lost() }
    override fun onNullBinding(name: ComponentName) { remote=null; lost() }
    override suspend fun authorize(request: Request,uid: Int,deadline: Long): Long {
        val broker=remote ?: throw Refusal("ARBITER_ABSENT")
        val query=request.keyFields + mapOf("request_digest" to request.digest,"model" to request.p2.model.name,"model_sha256" to request.p2.modelSha256,"profile" to request.profile.name,
            "context_tokens" to request.p2.limits.contextTokens.toString(),"output_tokens" to request.p2.limits.outputTokens.toString(),"remaining_ms" to request.p2.limits.remainingMillis.toString(),"client_uid" to uid.toString(),"server_uid" to Process.myUid().toString())
        val wait=(deadline-SystemClock.elapsedRealtime()).coerceAtMost(3000)
        demand(wait > 0,"EXPIRED")
        return try { withTimeout(wait) {
            suspendCancellableCoroutine { c ->
                val callback=object : IP2AuthorizationCallback.Stub() {
                    override fun onTransact(code: Int,data: Parcel,reply: Parcel?,flags: Int): Boolean {
                        if (code in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) {
                            try { identity.requireClient(Binder.getCallingUid()); demand(data.dataSize() <= 32768,"INPUT_TOO_LARGE") }
                            catch (_: Exception) { throw SecurityException("P2 authority refused") }
                        }
                        return super.onTransact(code,data,reply,flags)
                    }
                    override fun complete(receipt: ByteArray) {
                        // Capture real Binder UID before dispatch; a UUID in a body is never an authority.
                        try {
                            identity.requireClient(Binder.getCallingUid())
                            val response=Wire.decode(receipt,query.keys + setOf("approved","expires_elapsed_ms"))
                            val expires=AuthorityReceipts.verify(query,response,SystemClock.elapsedRealtime(),deadline)
                            if (c.isActive) c.resume(expires)
                        } catch (e: Exception) { if (c.isActive) c.resumeWithException(Refusal((e as? Refusal)?.code ?: "UNVERIFIED_LEASE")) }
                    }
                }
                try { broker.authorize(Wire.encode(query),callback) } catch (_: Exception) { if (c.isActive) c.resumeWithException(Refusal("ARBITER_LOST")) }
            }
        } } catch (_: TimeoutCancellationException) { throw Refusal("ARBITER_TIMEOUT") }
    }
    fun close() { remote?.asBinder()?.runCatching { unlinkToDeath(death,0) }; remote=null; if (bound) { context.unbindService(this); bound=false } }
}
