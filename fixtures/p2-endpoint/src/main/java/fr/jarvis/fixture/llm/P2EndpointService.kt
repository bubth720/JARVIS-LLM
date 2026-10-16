package fr.jarvis.fixture.llm
import android.app.Service
import android.content.Intent
import android.os.*
import fr.jarvis.fixture.p2.*
import kotlinx.coroutines.*
import java.util.UUID

class P2EndpointService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val epoch=UUID.randomUUID().toString()
    private lateinit var identity: Identity
    private lateinit var controller: EndpointController
    private lateinit var arbiter: ArbiterConnection
    @Volatile private var session: String?=null
    @Volatile private var token: IBinder?=null
    private val clientDeath=IBinder.DeathRecipient { controller.disconnect() }
    override fun onCreate() {
        super.onCreate(); identity=Identity(this)
        val app=application as FixtureApplication
        val prefs=getSharedPreferences("p2-technical-journal",MODE_PRIVATE)
        val journal=object : Journal {
            override fun unresolved()=prefs.getBoolean("unresolved",false)
            override fun begin(key: Map<String,String>)=prefs.edit().putBoolean("unresolved",true).putString("key",Wire.encode(key).toString(Charsets.UTF_8)).commit()
            override fun complete(receipt: Map<String,String>)=prefs.edit().putBoolean("unresolved",false).putString("receipt",Wire.encode(receipt - "text").toString(Charsets.UTF_8)).commit()
        }
        arbiter=ArbiterConnection(this) { controller.disconnect() }
        controller=EndpointController(AndroidRuntime(app),arbiter,journal,{ SystemClock.elapsedRealtime() },scope)
        app.safetyChanged={ if (!app.monitor.foreground) controller.disconnect() }
        arbiter.connect()
    }
    private fun call(body: (Int) -> Map<String,String>): ByteArray {
        // getCallingUid MUST run in the Binder entry thread, before asynchronous work or identity clearing.
        val uid=Binder.getCallingUid()
        return try { identity.requireClient(uid); Wire.encode(body(uid)) }
        catch (e: Refusal) { Wire.encode(mapOf("version" to Wire.VERSION,"contract" to Wire.contract,"code" to e.code)) }
        catch (_: Exception) { Wire.encode(mapOf("version" to Wire.VERSION,"contract" to Wire.contract,"code" to "INTERNAL_FAILURE")) }
    }
    private fun requireSession(key: Map<String,String>) {
        demand(key["session_id"] == session && key["epoch"] == epoch,"STALE_SESSION")
        Wire.key(key)
    }
    private val endpoint=object : IP2Endpoint.Stub() {
        override fun onTransact(code: Int,data: Parcel,reply: Parcel?,flags: Int): Boolean {
            if (code in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) {
                try { identity.requireClient(Binder.getCallingUid()); demand(data.dataSize() <= 32768,"INPUT_TOO_LARGE") }
                catch (_: Exception) { throw SecurityException("P2 caller or transaction refused") }
            }
            return super.onTransact(code,data,reply,flags)
        }
        override fun hello(payload: ByteArray,clientLiveness: IBinder): ByteArray = call {
            val fields=Wire.decode(payload,setOf("version","contract","session_id")); Wire.version(fields)
            demand(fields.getValue("session_id").let { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) },"INVALID_SESSION")
            synchronized(this@P2EndpointService) {
                demand(session == null || session == fields["session_id"],"BUSY_SESSION")
                if (token == null || token?.isBinderAlive == false) {
                    clientLiveness.linkToDeath(clientDeath,0); token=clientLiveness
                } else demand(token == clientLiveness,"SESSION_TOKEN_MISMATCH")
                session=fields["session_id"]
            }
            mapOf("version" to Wire.VERSION,"contract" to Wire.contract,"epoch" to epoch,"session_id" to fields.getValue("session_id"),
                "synthetic_text_only" to "true","canonical_native_p2" to "true","interprocess_authority" to "required_external_core","global_android_arbiter" to "false","android_interprocess_e2e" to "false","hardware_qualified" to "false","model_import_supported" to "local_saf_staged_after_real_grant")
        }
        override fun submit(payload: ByteArray): ByteArray = call { uid ->
            val request=Wire.request(payload); requireSession(request.keyFields); controller.submit(request,uid)
        }
        override fun status(payload: ByteArray): ByteArray = call {
            val key=Wire.decode(payload,Wire.keyNames); requireSession(key); controller.status(key)
        }
        override fun cancel(payload: ByteArray): ByteArray = call {
            val key=Wire.decode(payload,Wire.keyNames); requireSession(key); controller.stop(key)
        }
    }
    override fun onBind(intent: Intent): IBinder = endpoint
    override fun onUnbind(intent: Intent): Boolean { controller.disconnect(); return true }
    override fun onDestroy() {
        controller.shutdown(); arbiter.close(); token?.runCatching { unlinkToDeath(clientDeath,0) }
        (application as FixtureApplication).safetyChanged=null
        // Scope deliberately not canceled here: outstanding native work must drain and journal cleanup.
        super.onDestroy()
    }
}
