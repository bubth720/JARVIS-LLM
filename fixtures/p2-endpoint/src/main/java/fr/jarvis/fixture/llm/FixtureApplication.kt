package fr.jarvis.fixture.llm
import android.app.*
import android.os.Bundle
import fr.jarvis.llm.*
class FixtureApplication : Application(),Application.ActivityLifecycleCallbacks {
    lateinit var monitor: DeviceMonitor; private set
    lateinit var models: ModelStore; private set
    fun staged(model: fr.jarvis.domain.ModelKind): Pair<android.net.Uri,String>? {
        val prefs=getSharedPreferences("fixture-staged-public-model",MODE_PRIVATE)
        val uri=prefs.getString(model.name+".uri",null) ?: return null
        val sha=prefs.getString(model.name+".sha",null) ?: return null
        return android.net.Uri.parse(uri) to sha
    }
    var safetyChanged: (() -> Unit)? = null
    override fun onCreate() { super.onCreate(); monitor=DeviceMonitor(this) { safetyChanged?.invoke() }; models=ModelStore(this); registerActivityLifecycleCallbacks(this) }
    override fun onActivityResumed(a: Activity) { monitor.foreground=true }
    override fun onActivityPaused(a: Activity) { monitor.foreground=false; safetyChanged?.invoke() }
    override fun onActivityCreated(a: Activity,b: Bundle?) {} ; override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {} ; override fun onActivitySaveInstanceState(a: Activity,b: Bundle) {} ; override fun onActivityDestroyed(a: Activity) {}
}
