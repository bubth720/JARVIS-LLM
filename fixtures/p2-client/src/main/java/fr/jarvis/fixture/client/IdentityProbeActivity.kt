package fr.jarvis.fixture.client
import android.app.Activity
import android.content.*
import android.os.*
import android.widget.TextView
import fr.jarvis.fixture.p2.*
import java.util.UUID
/** Identity probe only. This is NOT CORE 004C and implements NO arbiter or model generation. */
class IdentityProbeActivity : Activity(),ServiceConnection {
    private lateinit var text: TextView
    private var bound=false
    override fun onCreate(state: Bundle?) { super.onCreate(state); text=TextView(this); text.text="Identity probe only; no model loading."; setContentView(text)
        try { bound=bindService(Intent().setComponent(ComponentName(Wire.ENDPOINT,"${Wire.ENDPOINT}.P2EndpointService")),this,BIND_AUTO_CREATE); if(!bound) text.text="Service absent" }
        catch (_: SecurityException) { text.text="Signature permission refused (expected for wrong-signature fixture)" }
    }
    override fun onServiceConnected(name: ComponentName,binder: IBinder) {
        val request=Wire.encode(mapOf("version" to Wire.VERSION,"contract" to Wire.contract,"session_id" to UUID.randomUUID().toString()))
        // Bounded control call only, never generation. No user text is read or sent.
        Thread {
            val response=runCatching { IP2Endpoint.Stub.asInterface(binder).hello(request,Binder()).toString(Charsets.UTF_8) }.getOrDefault("Identity/binding failure")
            runOnUiThread { text.text=response }
        }.start()
    }
    override fun onServiceDisconnected(name: ComponentName) { text.text="Disconnected; NOT a release proof" }
    override fun onDestroy() { if(bound) unbindService(this); super.onDestroy() }
}
