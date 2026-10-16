package fr.jarvis.fixture.llm

import android.content.Context
import android.content.pm.PackageManager
import fr.jarvis.fixture.p2.*

class Identity(private val context: Context) {
    data class Principal(val uid: Int,val packages: List<String>,val expectedUid: Int,val certificates: List<String>,val localCertificates: List<String>,val rotated: Boolean)
    companion object {
        fun verify(p: Principal): Int {
            demand(p.uid == p.expectedUid && p.packages == listOf(Wire.CLIENT),"UNAUTHORIZED_UID")
            demand(!p.rotated && p.certificates.size == 1 && p.localCertificates.size == 1 && p.certificates == p.localCertificates,"UNAUTHORIZED_CERTIFICATE")
            return p.uid
        }
    }
    fun requireClient(uid: Int): Int {
        try {
            val pm=context.packageManager
            val client=pm.getPackageInfo(Wire.CLIENT,PackageManager.GET_SIGNING_CERTIFICATES)
            val local=pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
            val remoteInfo=client.signingInfo ?: throw Refusal("UNAUTHORIZED_CERTIFICATE")
            val localInfo=local.signingInfo ?: throw Refusal("UNAUTHORIZED_CERTIFICATE")
            val current=remoteInfo.apkContentsSigners.map { Wire.hash(it.toByteArray()) }
            val own=localInfo.apkContentsSigners.map { Wire.hash(it.toByteArray()) }
            val rotated=(remoteInfo.signingCertificateHistory?.size ?: 0) > 1 || (localInfo.signingCertificateHistory?.size ?: 0) > 1
            return verify(Principal(uid,pm.getPackagesForUid(uid)?.toList() ?: emptyList(),client.applicationInfo!!.uid,current,own,rotated))
        } catch (e: Refusal) { throw e } catch (_: Exception) { throw Refusal("UNAUTHORIZED") }
    }
}
