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
/** D4E1 records: exact canonical JSON of five fields, domain-separated hash outside the record.
 * Unknown/legacy formats and every ambiguous sidecar are preserved and blocked, never migrated.
 */
class RecoveryJournal(private val directory: File,private val sync: DirectorySync=AndroidDirectorySync(),
    private val checkpoint: (String)->Unit={}) {
    data class Record(val id: String,val sequence: Long,val generation: Long,val phase: String,val payload: Map<String,String>,val sha256: String)
    private val envelope=setOf("journal_id","seq","prev_record_sha256","phase","payload_b64")
    private var current: Record?=null
    private val recordsSeen=mutableListOf<Record>()
    private var pilot=""; private var id=""
    var failure: String?=null; private set
    init { read() }
    @Synchronized fun history(): List<Record> { demand(failure==null,"JOURNAL_BLOCKED"); return recordsSeen.toList() }
    @Synchronized fun snapshot(): Record { demand(failure==null,"JOURNAL_BLOCKED"); return current ?: throw Refusal("JOURNAL_MISSING") }
    @Synchronized fun verifyDurable() {
        val disk=RecoveryJournal(directory,sync)
        if(!disk.enrolled() || disk.snapshot().sha256!=current?.sha256) { fault(); throw Refusal("JOURNAL_BLOCKED") }
    }
    @Synchronized fun requireCapacity(records: Int) { demand(failure==null,"JOURNAL_BLOCKED"); demand(records>0 && (current?.sequence ?: 0)+records<=512,"QUEUE_FULL") }
    private fun bytes(file: File): ByteArray { demand(file.length() in 1..24576,"JOURNAL_BLOCKED"); return file.readBytes() }
    private fun read() {
        try {
            if(!directory.exists()) return
            val entries=directory.listFiles() ?: throw Refusal("JOURNAL_BLOCKED")
            demand(entries.none { it.name.endsWith(".new") || it.name.endsWith(".bak") || it.name=="write.pending" || it.name=="FAULT" },"JOURNAL_BLOCKED")
            if(entries.isEmpty()) return
            val marker=Wire.decode(bytes(File(directory,"enrolment.marker")),setOf("pilot_id","journal_id","pins_digest","enrolment_id"))
            demand(Wire.decode(bytes(File(directory,"enrolment.intent")),marker.keys)==marker,"JOURNAL_BLOCKED")
            pilot=marker.getValue("pilot_id"); id=marker.getValue("journal_id")
            RecoveryProtocol.uuid(pilot); RecoveryProtocol.uuid(id)
            val records=entries.filter { it.name.matches(Regex("record-[0-9]{6}\\.json")) }.sortedBy { it.name }
            demand(records.isNotEmpty() && records.size<=512,"JOURNAL_BLOCKED")
            var previous="NONE"; var sequence=0L; var generation=0L
            for(file in records) {
                val raw=bytes(file); val m=Wire.decode(raw,envelope)
                demand(RecoveryCommit.json(m).contentEquals(raw),"JOURNAL_BLOCKED")
                val n=RecoveryProtocol.number(m.getValue("seq"))
                demand(m["journal_id"]==id && m["prev_record_sha256"]==previous && n==++sequence && file.name=="record-%06d.json".format(n),"JOURNAL_BLOCKED")
                demand(m["phase"] in RecoveryProtocol.journalStates,"JOURNAL_BLOCKED")
                val payload=RecoveryProtocol.technical(RecoveryCommit.json(RecoveryCommit.unb64(m.getValue("payload_b64"),18000)))
                val g=RecoveryProtocol.number(payload.getValue("generation"))
                demand(payload["pilot_id"]==pilot && g>=generation && (n!=1L || m["phase"]=="INITIAL" && g==0L),"JOURNAL_BLOCKED")
                generation=g; previous=RecoveryCommit.hash("record",m)
                current=Record(id,n,g,m.getValue("phase"),payload,previous); recordsSeen.add(current!!)
            }
            val head=Wire.decode(bytes(File(directory,"journal.head")),setOf("sequence","generation","sha256"))
            demand(head==mapOf("sequence" to sequence.toString(),"generation" to generation.toString(),"sha256" to previous),"JOURNAL_BLOCKED")
            val allowed=setOf("enrolment.marker","enrolment.intent","journal.head")+records.map { it.name }
            demand(entries.all { it.name in allowed },"JOURNAL_BLOCKED")
        } catch(_: Throwable) { failure="JOURNAL_BLOCKED"; current=null }
    }
    @Synchronized fun enrolled()=current!=null && failure==null
    @Synchronized fun initialize(pilotId: String,pinsDigest: String,enrolmentId: String) {
        demand(failure==null && current==null && (!directory.exists() || directory.listFiles()?.isEmpty()==true),"ENROLMENT_CONFLICT")
        try {
            demand(directory.mkdirs() || directory.isDirectory,"JOURNAL_BLOCKED"); sync.sync(directory.parentFile!!)
            pilot=pilotId; id=java.util.UUID.randomUUID().toString()
            val marker=mapOf("pilot_id" to pilot,"journal_id" to id,"pins_digest" to pinsDigest,"enrolment_id" to enrolmentId)
            create(File(directory,"enrolment.intent"),Wire.encode(marker)); sync.sync(directory)
            create(File(directory,"enrolment.marker"),Wire.encode(marker)); sync.sync(directory)
            append("INITIAL",0,mapOf("owner" to "NONE","prior_key_state" to "NONE","pins_digest" to pinsDigest,"enrolment_id" to enrolmentId))
        } catch(_: Throwable) { fault(); throw Refusal("JOURNAL_BLOCKED") }
    }
    @Synchronized fun marker(): Map<String,String> {
        demand(failure==null,"JOURNAL_BLOCKED")
        return try { Wire.decode(bytes(File(directory,"enrolment.marker")),setOf("pilot_id","journal_id","pins_digest","enrolment_id")) }
        catch(_: Throwable) { fault(); throw Refusal("JOURNAL_BLOCKED") }
    }
    private fun create(file: File,data: ByteArray) {
        demand(!file.exists() && file.createNewFile(),"JOURNAL_BLOCKED")
        FileOutputStream(file).use { it.write(data); it.fd.sync() }; checkpoint("file.fsync:${file.name}")
    }
    private fun directorySync(point: String) { sync.sync(directory); checkpoint(point) }
    private fun fault() { failure="JOURNAL_BLOCKED"; runCatching { val f=File(directory,"FAULT"); if(!f.exists()) create(f,"IO_OR_INTEGRITY_FAILURE".toByteArray()); sync.sync(directory) } }
    @Synchronized fun append(phase: String,generation: Long,payload: Map<String,String>): Record {
        demand(failure==null && phase in RecoveryProtocol.journalStates,"JOURNAL_BLOCKED")
        demand(generation >= (current?.generation ?: 0) && generation<=9007199254740991L,"JOURNAL_BLOCKED")
        requireCapacity(1)
        val seq=(current?.sequence ?: 0)+1
        val safe=RecoveryProtocol.technical(RecoveryCommit.json(payload+mapOf("pilot_id" to pilot,"generation" to generation.toString())))
        val m=mapOf("journal_id" to id,"seq" to seq.toString(),"prev_record_sha256" to (current?.sha256 ?: "NONE"),"phase" to phase,"payload_b64" to RecoveryCommit.b64(safe,18000))
        val raw=RecoveryCommit.json(m); demand(raw.size<=24576,"INPUT_TOO_LARGE")
        val record=Record(id,seq,generation,phase,safe,RecoveryCommit.hash("record",m))
        try {
            create(File(directory,"write.pending"),Wire.encode(mapOf("sequence" to seq.toString(),"generation" to generation.toString(),"sha256" to record.sha256))); directorySync("pending.directory")
            val temp=File(directory,"record-%06d.json.new".format(seq)); val dest=File(directory,"record-%06d.json".format(seq))
            create(temp,raw); demand(!dest.exists() && temp.renameTo(dest),"JOURNAL_BLOCKED"); checkpoint("record.published"); directorySync("record.directory")
            val head=File(directory,"journal.head.new"); create(head,Wire.encode(mapOf("sequence" to seq.toString(),"generation" to generation.toString(),"sha256" to record.sha256)))
            demand(head.renameTo(File(directory,"journal.head")),"JOURNAL_BLOCKED"); checkpoint("head.published"); directorySync("head.directory")
            demand(File(directory,"write.pending").delete(),"JOURNAL_BLOCKED"); checkpoint("pending.removed"); directorySync("complete.directory")
            current=record; recordsSeen.add(record)
        } catch(_: Throwable) { fault(); throw Refusal("JOURNAL_BLOCKED") }
        // Injection outside the I/O transaction models interruption after a fully durable transition.
        checkpoint("durable:$phase")
        return record
    }
}
