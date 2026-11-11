package fr.jarvis.llm.engine

import fr.jarvis.domain.RuntimeConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Linux cloud observations only. Never synthesizes Android ABI, thermal or battery readings. */
internal object LabHostBudget {
    internal fun assess(config: RuntimeConfig, total: Long, available: Long, rss: Long) {
        check(total > 0 && available in 0..total && rss >= 0) { "LAB_MEMORY_OBSERVATION_INVALID" }
        val reserve=maxOf(RuntimeConfig.GIB,total/5)
        check(available >= config.memoryBudget && available-config.memoryBudget >= reserve && rss <= config.memoryBudget) { "LAB_MEMORY_REFUSED" }
    }
    private fun read(path: Path) = Files.newBufferedReader(path,Charsets.UTF_8).use { it.readText() }
    private fun kib(text: String, key: String): Long {
        val line=text.lineSequence().single { it.startsWith("$key:") }.trim().split(Regex("\\s+"))
        check(line.size == 3 && line[2] == "kB");val value=line[1].toLong();check(value in 0..Long.MAX_VALUE/1024);return value*1024
    }
    fun requireFits(config: RuntimeConfig) {
        check(System.getProperty("os.name") == "Linux") { "LAB_HOST_UNSUPPORTED" }
        assessLinux(config,::read) { Files.isDirectory(it) }
    }
    internal fun assessLinux(config: RuntimeConfig, read: (Path) -> String, directory: (Path) -> Boolean) {
        val mem=read(Paths.get("/proc/meminfo"));var total=kib(mem,"MemTotal");var available=kib(mem,"MemAvailable")
        check(total > 0 && available in 0..total) { "LAB_MEMORY_OBSERVATION_INVALID" }
        val group=read(Paths.get("/proc/self/cgroup")).lineSequence().single { it.startsWith("0::") }.removePrefix("0::")
        check(group.startsWith("/") && group.split('/').none { it == ".." }) { "LAB_CGROUP_UNQUALIFIED" }
        val mount=read(Paths.get("/proc/self/mountinfo")).lineSequence().filter { it.contains(" - cgroup2 ") }.map { it.split(' ') }.single()
        val root=mount[3];val point=Paths.get(mount[4]);check(!root.contains('\\') && !point.toString().contains('\\'))
        check(root == "/" || group == root || group.startsWith(root.trimEnd('/')+"/")) { "LAB_CGROUP_MAPPING_UNKNOWN" }
        val suffix=if(root == "/")group.removePrefix("/")else group.removePrefix(root).removePrefix("/")
        var current=point.resolve(suffix).normalize();check(current.startsWith(point) && directory(current)) { "LAB_CGROUP_MAPPING_UNKNOWN" }
        // Include every visible ancestor limit: a leaf 'max' must never hide a tighter parent.
        while(true) {
            val maximum=read(current.resolve("memory.max")).trim()
            if(maximum != "max") {
                val limit=maximum.toLong();val used=read(current.resolve("memory.current")).trim().toLong();check(limit > 0 && used >= 0)
                // A periodic RSS sample cannot bound a transient peak. Record
                // the cgroup high-water mark and fail closed when it exceeds
                // the declared envelope; hard-limit admission remains separate.
                val peak=read(current.resolve("memory.peak")).trim().toLong()
                check(peak >= used && peak <= limit && peak <= config.memoryBudget) { "LAB_MEMORY_PEAK_REFUSED" }
                total=minOf(total,limit);available=minOf(available,(limit-used).coerceAtLeast(0))
            }
            if(current == point)break
            current=requireNotNull(current.parent);check(current.startsWith(point))
        }
        available=minOf(available,total)
        assess(config,total,available,kib(read(Paths.get("/proc/self/status")),"VmRSS"))
    }
}
