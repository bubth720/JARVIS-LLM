package fr.jarvis.fixture.llm

import fr.jarvis.fixture.p2.Refusal
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Actual fixture Application initialization under Robolectric; no JNI/emulator or hardware proof. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],application=FixtureApplication::class)
class BootstrapApplicationTest {
    @Test fun noProvisioningNeverTouchesModelOrAllocatesBackend() {
        val app=RuntimeEnvironment.getApplication() as FixtureApplication
        assertEquals("ENROLMENT_REQUIRED",app.recoveryPlatform.code)
        assertEquals("RECOVERY_BLOCKED",app.recovery.inspectState())
        val snap=app.runtime.bootstrapInventory()
        assertEquals("COMPLETE",snap["backend_registry_state"])
        assertEquals("NOT_STARTED",snap["native_state"])
        assertEquals("0",snap["backend_count"])
        assertSame(app.runtime,app.runtime)
        assertFalse(File(app.filesDir,"models").exists())
        try { app.recovery.requireNativeAdmission(); fail() } catch(e: Refusal) { assertEquals("RECOVERY_REQUIRED",e.code) }
    }
}
