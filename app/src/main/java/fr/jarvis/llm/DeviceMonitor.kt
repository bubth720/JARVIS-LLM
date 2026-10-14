package fr.jarvis.llm

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.system.Os
import android.system.OsConstants
import fr.jarvis.domain.*

class DeviceMonitor(private val context: Context, private val thermalChanged: () -> Unit) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val activity = context.getSystemService(ActivityManager::class.java)
    private var headroom: Float? = null
    private var lastHeadroom = -20_000L
    val telemetry = MemoryTelemetry()
    private val listener = PowerManager.OnThermalStatusChangedListener { thermalChanged() }
    @Volatile var foreground = false
    init { runCatching { power.addThermalStatusListener(context.mainExecutor, listener) } }
    @Synchronized fun snapshot(samplePss: Boolean = true): DeviceSnapshot {
        val mem = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val now = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= 30 && now - lastHeadroom >= 15_000) {
            headroom = runCatching { power.getThermalHeadroom(10).takeIf { it.isFinite() && it >= 0f } }.getOrNull()
            lastHeadroom = now
        }
        val pss = if (samplePss) Debug.getPss() * 1024L else telemetry.currentPss
        if (samplePss) telemetry.sample(pss, mem.availMem)
        return DeviceSnapshot(mem.availMem, mem.totalMem, mem.lowMemory,
            if (level in 0..scale && scale > 0) level * 100 / scale else null,
            power.isPowerSaveMode, runCatching { power.currentThermalStatus }.getOrNull(), headroom,
            foreground, "arm64-v8a" in Build.SUPPORTED_ABIS, Runtime.getRuntime().availableProcessors(), pss, mem.threshold)
    }
    fun processMemory(): ProcessMemory = runCatching {
        java.io.File("/proc/self/status").inputStream().buffered().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val block = ByteArray(4096)
            while (output.size() < 64 * 1024) {
                val n = input.read(block, 0, minOf(block.size, 64 * 1024 - output.size()))
                if (n < 0) break
                output.write(block, 0, n)
            }
            ProcessMemory.parse(output.toString("US-ASCII"))
        }
    }.getOrElse { ProcessMemory(null, null) }
    fun diagnostic(resident: Residency? = null, s: DeviceSnapshot = snapshot()): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} / ${Build.SOC_MODEL}" else "non exposé"
        fun gib(n: Long) = "%.2f".format(n / 1073741824.0)
        val free = android.os.StatFs(context.filesDir.path).availableBytes
        val process = processMemory()
        fun mib(n: Long?) = n?.let { "%.1f".format(java.util.Locale.FRANCE, it / 1048576.0) } ?: "non disponible"
        val memory = SafetyPolicy().memory(RuntimeConfig(ModelKind.SMALL, 768, 96, 1), s, resident)
        return "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n" +
            "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\nSoC déclaré : $soc\n" +
            "ABI : ${Build.SUPPORTED_ABIS.joinToString()} ; page : ${Os.sysconf(OsConstants._SC_PAGESIZE)} octets\n" +
            "RAM accessible au noyau : ${gib(s.totalBytes)} Gio (hors réserve matérielle ; pas une mesure de RAM commerciale)\n" +
            "RAM disponible : ${gib(s.availableBytes)} Gio ; stockage libre : ${gib(free)} Gio\n" +
            "Batterie : ${s.batteryPercent ?: "?"} % ; thermique : ${s.thermalStatus ?: "?"}\n" +
            "ÉCO renforcé : contexte 768, sortie 96, batch 32/8, 1 thread, poids Q4_K_M\n" +
            "TABLETTE ÉCO : contexte 512, sortie 64, batch 16/4, 1 thread ; veille après 10 s\n" +
            "Budget estimé application + modèle : ${gib(memory.estimatedBudget)} Gio (pas une mesure)\n" +
            "Réserve système : ${gib(memory.reserve)} Gio ; seuil Android : ${gib(s.androidLowMemoryThreshold)} Gio\n" +
            "Crédit résidence PSS observé : ${gib(memory.measuredCredit)} Gio ; requis : ${gib(memory.requiredAvailable)} Gio\n" +
            "Marge admission : ${gib(memory.margin)} Gio ; RAM au-dessus de la réserve : ${gib(s.availableBytes - memory.reserve)} Gio\n" +
            "RSS / maximum RSS depuis création du processus : ${mib(process.rssBytes)} / ${mib(process.rssHighWaterBytes)} Mio (pas PSS)\n" +
            telemetry.summary() + "\nCPU uniquement ; GPU/NPU non qualifiés\n" +
            "Extension mémoire non comptée. Aucun benchmark exécuté par ce diagnostic."
    }
    fun close() { runCatching { power.removeThermalStatusListener(listener) } }
}
