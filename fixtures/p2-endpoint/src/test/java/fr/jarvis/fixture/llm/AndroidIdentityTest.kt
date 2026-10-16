package fr.jarvis.fixture.llm
import android.app.Application
import android.content.pm.*
import android.os.Binder
import fr.jarvis.fixture.p2.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

/** Real resolver and Binder.getCallingUid API under Robolectric. NOT a cross-APK kernel Binder test. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=Application::class)
class AndroidIdentityTest {
    private val context=RuntimeEnvironment.getApplication()
    private val uid=12345
    private fun install(name: String,signature: String,packageUid: Int) {
        val info=PackageInfo(); info.packageName=name
        info.applicationInfo=ApplicationInfo().apply { packageName=name; this.uid=packageUid }
        info.signingInfo=SigningInfo().also { Shadows.shadowOf(it).setSignatures(arrayOf(Signature(signature.toByteArray()))) }
        Shadows.shadowOf(context.packageManager).installPackage(info)
    }
    @Before fun prepare() { install(context.packageName,"common-fixture-key",10001); install(Wire.CLIENT,"common-fixture-key",uid); Shadows.shadowOf(context.packageManager).setPackagesForUid(uid,Wire.CLIENT) }
    @After fun reset() { ShadowBinder.reset() }
    @Test fun authorizedFixtureResolvedFromRealCallingUidApi() {
        ShadowBinder.setCallingUid(uid); assertEquals(uid,Identity(context).requireClient(Binder.getCallingUid()))
    }
    @Test fun refusedFixtureWithDifferentUidEvenIfPackageNameClaimed() {
        ShadowBinder.setCallingUid(uid+1)
        try { Identity(context).requireClient(Binder.getCallingUid()); fail() } catch (_: Refusal) {}
    }
    @Test fun refusedFixtureWithWrongCertificate() {
        install(Wire.CLIENT,"refused-fixture-key",uid)
        try { Identity(context).requireClient(uid); fail() } catch (e: Refusal) { assertEquals("UNAUTHORIZED_CERTIFICATE",e.code) }
    }
}
