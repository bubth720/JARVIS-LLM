package fr.jarvis.fixture.llm
import android.app.Application
import android.content.pm.*
import fr.jarvis.fixture.p2.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

/** PackageManager component configuration under Robolectric, not Binder between two APKs. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=Application::class)
class RecoveryComponentTest {
    private val pm=RuntimeEnvironment.getApplication().packageManager
    private fun install(pkg: String,uid: Int,exported: Boolean=true,permission: String=Wire.PERMISSION,enabled: Boolean=true,name: String=if(pkg==Wire.CLIENT) "$pkg.P2ArbiterService" else "$pkg.P2EndpointService") {
        val app=ApplicationInfo().apply { packageName=pkg;this.uid=uid;this.enabled=true }
        val info=PackageInfo().apply { packageName=pkg;applicationInfo=app;services=arrayOf(ServiceInfo().apply { packageName=pkg;this.name=name;applicationInfo=app;this.exported=exported;this.permission=permission;this.enabled=enabled }) }
        Shadows.shadowOf(pm).installPackage(info)
    }
    @Before fun setup() { install(Wire.ENDPOINT,124);install(Wire.CLIENT,123) }
    @Test fun pinnedComponentsPresentWithExpectedPermissionAndUid() { RecoveryPlatform.requireComponents(pm,123,124) }
    @Test fun missingCoreArbiterIsNotReplacedBySamePackageIdentityProbe() {
        install(Wire.CLIENT,123,name=Wire.CLIENT+".IdentityProbeActivity")
        try { RecoveryPlatform.requireComponents(pm,123,124);fail() } catch(e: Refusal) { assertEquals("ENROLMENT_CONFLICT",e.code) }
    }
    @Test fun wrongUidUnprotectedPrivateOrDisabledComponentsAreRejected() {
        for(kind in 0..3) {
            install(Wire.CLIENT,if(kind==0)999 else 123,exported=kind!=1,permission=if(kind==2)"" else Wire.PERMISSION,enabled=kind!=3)
            try { RecoveryPlatform.requireComponents(pm,123,124);fail() } catch(e: Refusal) { assertEquals("ENROLMENT_CONFLICT",e.code) }
        }
    }
}
