package fr.jarvis.fixture.llm
import android.content.Context
import android.content.pm.PackageManager
import fr.jarvis.fixture.p2.*
import java.io.File
import java.io.FileOutputStream

/** Operator-provisioned laboratory pins only. No enrollment from DISCOVER/hello or default preferences. */
class RecoveryPlatform(private val context: Context) {
    companion object {
        /** Presence/configuration of both pinned components; this is not proof of the caller's Activity/Service identity. */
        fun requireComponents(pm: PackageManager,coreUid: Int,llmUid: Int) {
            for((pkg,name,uid) in listOf(Triple(Wire.CLIENT,Wire.CLIENT+".P2ArbiterService",coreUid),Triple(Wire.ENDPOINT,Wire.ENDPOINT+".P2EndpointService",llmUid))) {
                val component=android.content.ComponentName(pkg,name)
                val service=try { pm.getServiceInfo(component,0) } catch(_: Exception) { throw Refusal("ENROLMENT_CONFLICT") }
                val setting=pm.getComponentEnabledSetting(component)
                demand(service.packageName==pkg && service.name==name && service.applicationInfo.uid==uid &&
                    service.enabled && service.applicationInfo.enabled && service.exported && service.permission==Wire.PERMISSION &&
                    setting in setOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,PackageManager.COMPONENT_ENABLED_STATE_ENABLED),"ENROLMENT_CONFLICT")
            }
        }
    }
    val journal=RecoveryJournal(File(context.noBackupFilesDir,"recovery-v2"))
    var code="ENROLMENT_REQUIRED"; private set
    var pins: RecoveryPins?=null; private set
    var enrolledNow=false; private set
    private var coreUpdate=0L; private var ownUpdate=0L
    init {
        try {
            val pending=File(context.noBackupFilesDir,"lab-provisioning.json")
            val accepted=File(context.noBackupFilesDir,"lab-enrolment-accepted.json")
            demand(!(pending.exists() && accepted.exists()),"ENROLMENT_CONFLICT")
            val newIntent=pending.isFile && !accepted.exists()
            val config=if(newIntent) pending else accepted
            if(config.isFile) {
                demand(config.length() in 1..24576,"ENROLMENT_CONFLICT")
                val f=Wire.decode(config.readBytes(),setOf("version","contract","pilot_id","enrolment_id","core_cert_sha256","llm_cert_sha256","core_apk_sha256","llm_apk_sha256","enrolment_authorized"))
                demand(f["version"]==RecoveryProtocol.VERSION && f["contract"]==RecoveryProtocol.contract && f["enrolment_authorized"]=="true","ENROLMENT_CONFLICT")
                RecoveryProtocol.uuid(f.getValue("pilot_id")); RecoveryProtocol.uuid(f.getValue("enrolment_id"))
                val core=context.packageManager.getPackageInfo(Wire.CLIENT,PackageManager.GET_SIGNING_CERTIFICATES)
                val own=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
                demand(core.applicationInfo!!.splitSourceDirs.isNullOrEmpty() && own.applicationInfo!!.splitSourceDirs.isNullOrEmpty(),"ENROLMENT_CONFLICT")
                val uid=core.applicationInfo!!.uid; Identity(context).requireClient(uid)
                requireComponents(context.packageManager,uid,own.applicationInfo!!.uid)
                val permission=context.packageManager.getPermissionInfo(Wire.PERMISSION,0)
                demand(permission.packageName==Wire.ENDPOINT && permission.protectionLevel and 15 == 2,"ENROLMENT_CONFLICT")
                val coreCert=Wire.hash(core.signingInfo!!.apkContentsSigners.single().toByteArray())
                val ownCert=Wire.hash(own.signingInfo!!.apkContentsSigners.single().toByteArray())
                fun sha(path: String): String { val d=java.security.MessageDigest.getInstance("SHA-256"); File(path).inputStream().buffered().use { input -> val b=ByteArray(1024*1024); while(true) { val n=input.read(b); if(n<0)break; d.update(b,0,n) } }; return d.digest().joinToString("") { "%02x".format(it.toInt() and 255) } }
                demand(coreCert==f["core_cert_sha256"] && ownCert==f["llm_cert_sha256"] && sha(core.applicationInfo!!.sourceDir)==f["core_apk_sha256"] && sha(own.applicationInfo!!.sourceDir)==f["llm_apk_sha256"],"ENROLMENT_CONFLICT")
                coreUpdate=core.lastUpdateTime; ownUpdate=own.lastUpdateTime
                val digest=Wire.digest(f-"enrolment_authorized")
                val p=RecoveryPins(f.getValue("pilot_id"),Wire.digest(mapOf("core" to Wire.digest(f.filterKeys { it.startsWith("core_") }),"llm" to Wire.digest(f.filterKeys { it.startsWith("llm_") }))),uid.toString(),own.applicationInfo!!.uid.toString(),coreCert,ownCert,digest)
                val consumed=File(context.noBackupFilesDir,"lab-enrolment-consumed")
                if(!journal.enrolled()) {
                    demand(newIntent,"JOURNAL_MISSING")
                    val legacy=File(context.applicationInfo.dataDir,"shared_prefs/p2-technical-journal.xml")
                    demand(!legacy.exists() && !File(legacy.path+".bak").exists(),"ENROLMENT_CONFLICT")
                    demand(!consumed.exists() && !File(context.noBackupFilesDir,"recovery-v2").exists(),"JOURNAL_MISSING")
                    demand(pending.renameTo(accepted),"ENROLMENT_CONFLICT")
                    AndroidDirectorySync().sync(context.noBackupFilesDir)
                    demand(consumed.createNewFile(),"ENROLMENT_CONFLICT")
                    FileOutputStream(consumed).use { it.write(digest.toByteArray(Charsets.US_ASCII)); it.fd.sync() }; AndroidDirectorySync().sync(context.noBackupFilesDir)
                    journal.initialize(p.pilot,p.digest,f.getValue("enrolment_id")); enrolledNow=true
                }
                demand(consumed.isFile && consumed.readText()==digest && journal.marker()["pins_digest"]==digest,"ENROLMENT_CONFLICT")
                pins=p; code="NONE"
            }
        } catch(e: Refusal) { code=e.code } catch (_: Throwable) { code="JOURNAL_BLOCKED" }
    }
    fun requireCurrent(uid: Int) {
        Identity(context).requireClient(uid)
        demand(code=="NONE" && pins!=null,code)
        demand(uid.toString()==pins!!.coreUid && context.applicationInfo.uid.toString()==pins!!.llmUid,"ENROLMENT_CONFLICT")
        val pm=context.packageManager
        requireComponents(pm,pins!!.coreUid.toInt(),pins!!.llmUid.toInt())
        val permission=pm.getPermissionInfo(Wire.PERMISSION,0)
        demand(permission.packageName==Wire.ENDPOINT && permission.protectionLevel and 15 == 2,"ENROLMENT_CONFLICT")
        demand(pm.getPackageInfo(Wire.CLIENT,0).lastUpdateTime==coreUpdate && pm.getPackageInfo(context.packageName,0).lastUpdateTime==ownUpdate,"ENROLMENT_CONFLICT")
    }
}
