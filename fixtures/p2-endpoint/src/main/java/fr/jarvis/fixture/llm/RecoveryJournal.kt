package fr.jarvis.fixture.llm

import fr.jarvis.fixture.p2.*
import java.io.File
import java.io.FileOutputStream

interface DirectorySync { fun sync(directory: File) }
class AndroidDirectorySync : DirectorySync {
    override fun sync(directory: File) {
        require(directory.isDirectory)
        val fd=android.system.Os.open(directory.path,android.system.OsConstants.O_RDONLY,0)
        try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
    }
}
/** Append-only records; all ambiguous sidecars and IO failures block. Never repairs/deletes a journal. */
class RecoveryJournal(private val directory: File,private val sync: DirectorySync=AndroidDirectorySync()) {
    data class Record(val id: String,val sequence: Long,val generation: Long,val phase: String,val payload: Map<String,String>,val sha256: String)
    private val envelope=setOf("format","journal_id","pilot_id","sequence","generation","phase","previous_sha256","payload","checksum")
    private var current: Record?=null
    private val recordsSeen=mutableListOf<Record>()
    @Synchronized fun history(): List<Record> { demand(failure==null,"JOURNAL_BLOCKED"); return recordsSeen.toList() }
    private var pilot=""; private var id=""
    var failure: String?=null; private set
    init { read() }
    @Synchronized fun snapshot(): Record { demand(failure==null,"JOURNAL_BLOCKED"); return current ?: throw Refusal("JOURNAL_MISSING") }
    private fun bytes(file: File): ByteArray { demand(file.length() in 1..24576,"JOURNAL_BLOCKED"); return file.readBytes() }
    private fun read() {
        try {
            if (!directory.exists()) return
            val entries=directory.listFiles() ?: throw Refusal("JOURNAL_BLOCKED")
            demand(entries.none { it.name.endsWith(".new") || it.name.endsWith(".bak") || it.name=="write.pending" || it.name=="FAULT" },"JOURNAL_BLOCKED")
            if(entries.isEmpty()) return
            val marker=Wire.decode(bytes(File(directory,"enrolment.marker")),setOf("pilot_id","journal_id","pins_digest","enrolment_id"))
            demand(Wire.decode(bytes(File(directory,"enrolment.intent")),marker.keys)==marker,"JOURNAL_BLOCKED")
            pilot=marker.getValue("pilot_id"); id=marker.getValue("journal_id")
            RecoveryProtocol.uuid(pilot); RecoveryProtocol.uuid(id)
            val records=entries.filter { it.name.matches(Regex("record-[0-9]{6}\\.json")) }.sortedBy { it.name }
            demand(records.isNotEmpty() && records.size<=512,"JOURNAL_BLOCKED")
            var prev="NONE"; var seq=0L; var generation=0L
            for (f in records) {
                val raw=bytes(f); val m=Wire.decode(raw,envelope)
                demand(m["format"]=="recovery-journal/2" && m["pilot_id"]==pilot && m["journal_id"]==id,"JOURNAL_BLOCKED")
                demand(m["checksum"]==Wire.digest(m-"checksum") && m["previous_sha256"]==prev,"JOURNAL_BLOCKED")
                val n=RecoveryProtocol.number(m.getValue("sequence")); val g=RecoveryProtocol.number(m.getValue("generation"))
                demand(n==++seq && g>=generation && f.name=="record-%06d.json".format(n),"JOURNAL_BLOCKED"); generation=g
                demand(m["phase"] in RecoveryProtocol.journalStates,"JOURNAL_BLOCKED")
                prev=Wire.hash(raw); current=Record(id,n,g,m.getValue("phase"),RecoveryProtocol.technical(m.getValue("payload").toByteArray(Charsets.UTF_8)),prev)
                recordsSeen.add(current!!)
            }
            val head=Wire.decode(bytes(File(directory,"journal.head")),setOf("sequence","generation","sha256"))
            demand(head==mapOf("sequence" to seq.toString(),"generation" to generation.toString(),"sha256" to prev),"JOURNAL_BLOCKED")
            val allowed=setOf("enrolment.marker","enrolment.intent","journal.head")+records.map { it.name }
            demand(entries.all { it.name in allowed },"JOURNAL_BLOCKED")
        } catch (_: Throwable) { failure="JOURNAL_BLOCKED"; current=null }
    }
    @Synchronized fun enrolled(): Boolean = current!=null && failure==null
    @Synchronized fun initialize(pilotId: String,pinsDigest: String,enrolmentId: String) {
        demand(failure==null && current==null && (!directory.exists() || directory.listFiles()?.isEmpty()==true),"ENROLMENT_CONFLICT")
        try {
            demand(directory.mkdirs() || directory.isDirectory,"JOURNAL_BLOCKED"); sync.sync(directory.parentFile!!)
            pilot=pilotId; id=java.util.UUID.randomUUID().toString()
            val marker=mapOf("pilot_id" to pilot,"journal_id" to id,"pins_digest" to pinsDigest,"enrolment_id" to enrolmentId)
            create(File(directory,"enrolment.intent"),Wire.encode(marker)); sync.sync(directory)
            create(File(directory,"enrolment.marker"),Wire.encode(marker)); sync.sync(directory)
            append("INITIAL",0,mapOf("owner" to "NONE","prior_key_state" to "NONE","pins_digest" to pinsDigest,"enrolment_id" to enrolmentId))
        } catch (e: Throwable) { fault(); throw Refusal("JOURNAL_BLOCKED") }
    }
    @Synchronized fun marker(): Map<String,String> {
        demand(failure==null,"JOURNAL_BLOCKED"); return try { Wire.decode(bytes(File(directory,"enrolment.marker")),setOf("pilot_id","journal_id","pins_digest","enrolment_id")) } catch (_: Throwable) { fault(); throw Refusal("JOURNAL_BLOCKED") }
    }
    private fun create(file: File,data: ByteArray) {
        demand(!file.exists() && file.createNewFile(),"JOURNAL_BLOCKED")
        FileOutputStream(file).use { it.write(data); it.fd.sync() }
    }
    private fun fault() { failure="JOURNAL_BLOCKED"; runCatching { val f=File(directory,"FAULT"); if(!f.exists()) create(f,"IO_OR_INTEGRITY_FAILURE".toByteArray()); sync.sync(directory) } }
    @Synchronized fun append(phase: String,generation: Long,payload: Map<String,String>): Record {
        demand(failure==null && phase in RecoveryProtocol.journalStates,"JOURNAL_BLOCKED")
        demand(generation >= (current?.generation ?: 0) && generation<=9007199254740991L,"JOURNAL_BLOCKED")
        val seq=(current?.sequence ?: 0)+1; demand(seq<=512,"QUEUE_FULL")
        val safe=RecoveryProtocol.technical(Wire.encode(payload))
        val m=mapOf("format" to "recovery-journal/2","pilot_id" to pilot,"journal_id" to id,"sequence" to seq.toString(),"generation" to generation.toString(),"phase" to phase,"previous_sha256" to (current?.sha256 ?: "NONE"),"payload" to Wire.encode(safe).toString(Charsets.UTF_8))
        try {
            create(File(directory,"write.pending"),Wire.encode(mapOf("sequence" to seq.toString(),"generation" to generation.toString(),"sha256" to Wire.digest(m)))); sync.sync(directory)
            val raw=Wire.encode(m+mapOf("checksum" to Wire.digest(m)))
            val temp=File(directory,"record-%06d.json.new".format(seq)); val dest=File(directory,"record-%06d.json".format(seq))
            create(temp,raw); demand(!dest.exists() && temp.renameTo(dest),"JOURNAL_BLOCKED"); sync.sync(directory)
            val record=Record(id,seq,generation,phase,safe,Wire.hash(raw))
            val head=File(directory,"journal.head.new"); create(head,Wire.encode(mapOf("sequence" to seq.toString(),"generation" to generation.toString(),"sha256" to record.sha256)))
            demand(head.renameTo(File(directory,"journal.head")),"JOURNAL_BLOCKED"); sync.sync(directory)
            demand(File(directory,"write.pending").delete(),"JOURNAL_BLOCKED"); sync.sync(directory)
            current=record; recordsSeen.add(record); return record
        } catch (_: Throwable) { fault(); throw Refusal("JOURNAL_BLOCKED") }
    }
}
