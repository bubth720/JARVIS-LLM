package fr.jarvis.fixture.llm
import android.app.Service
import android.content.Intent
import android.os.*
import fr.jarvis.fixture.p2.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** /1 transaction indices preserved; mixed peers fail closed. No model work on the recovery executor. */
class P2EndpointService : Service() {
    private lateinit var app: FixtureApplication
    private lateinit var identity: Identity
    private var serviceEpoch=""
    private val control=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue<Runnable>(1))
    private var peer: IBinder?=null
    private val bindingId=java.util.UUID.randomUUID().toString()
    private val death=IBinder.DeathRecipient { app.recovery.detachService(serviceEpoch) }
    override fun onCreate() { super.onCreate(); app=application as FixtureApplication; identity=Identity(this); serviceEpoch=app.recovery.attachService() }
    private fun legacy(): ByteArray { identity.requireClient(Binder.getCallingUid()); return Wire.encode(mapOf("version" to Wire.VERSION,"contract" to Wire.contract,"code" to "INCOMPATIBLE")) }
    private val endpoint=object : IP2Endpoint.Stub() {
        override fun onTransact(code: Int,data: Parcel,reply: Parcel?,flags: Int): Boolean {
            if(code in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) {
                try { identity.requireClient(Binder.getCallingUid()); app.recoveryPlatform.requireCurrent(Binder.getCallingUid()); demand(data.dataSize()<=32768,"INPUT_TOO_LARGE") }
                catch (_: Throwable) { app.recovery.detachService(serviceEpoch); throw SecurityException("P2 identity/size refused") }
            }
            return super.onTransact(code,data,reply,flags)
        }
        override fun hello(payload: ByteArray,clientLiveness: IBinder)=legacy()
        override fun submit(payload: ByteArray)=legacy()
        override fun status(payload: ByteArray)=legacy()
        override fun cancel(payload: ByteArray)=legacy()
        override fun recovery(message: ByteArray,peerLiveness: IBinder,callback: IRecoveryCallback) {
            val uid=Binder.getCallingUid()
            try { identity.requireClient(uid); app.recoveryPlatform.requireCurrent(uid) }
            catch(e: Throwable) { app.recovery.detachService(serviceEpoch); throw e }
            val f=RecoveryProtocol.envelope(message)
            synchronized(this@P2EndpointService) {
                demand(peer==null || peer==peerLiveness,"BUSY")
                if(peer==null) { peerLiveness.linkToDeath(death,0); peer=peerLiveness }
            }
            try {
                control.execute {
                    val response=try {
                        demand(peerLiveness.isBinderAlive,"STALE_INCARNATION")
                        app.recoveryPlatform.requireCurrent(uid)
                        val result=app.recovery.handle(f,serviceEpoch,RecoveryChannel(uid.toString(),app.recoveryPlatform.pins!!.coreCert,bindingId))
                        if(result["kind"]!="REFUSED") {
                            demand(peerLiveness.isBinderAlive,"STALE_INCARNATION")
                            app.recoveryPlatform.requireCurrent(uid)
                            RecoveryProtocol.fresh(f,SystemClock.elapsedRealtime())
                        }
                        result
                    } catch(e: Refusal) { app.recovery.detachService(serviceEpoch); RecoveryProtocol.refused(f,e.code) }
                    catch(_: Throwable) { app.recovery.detachService(serviceEpoch); RecoveryProtocol.refused(f,"UNKNOWN") }
                    // No authority lock held while making the callback transaction.
                    runCatching { callback.complete(RecoveryProtocol.encode(response)) }.onFailure { app.recovery.invalidate() }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                throw IllegalStateException("QUEUE_FULL")
            }
        }
    }
    override fun onBind(intent: Intent): IBinder=endpoint
    override fun onUnbind(intent: Intent): Boolean { app.recovery.detachService(serviceEpoch); return false }
    override fun onDestroy() { app.recovery.detachService(serviceEpoch); peer?.runCatching { unlinkToDeath(death,0) }; control.shutdown(); super.onDestroy() }
}
