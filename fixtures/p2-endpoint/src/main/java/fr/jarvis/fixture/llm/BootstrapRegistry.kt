package fr.jarvis.fixture.llm

/** Birth-time registry. No factory/call/import can occur without invalidating NOT_STARTED first.
 * This revision does not have the real post-native observation port: once touched, UNKNOWN is final.
 * It is not a RAM scan or an interprocess resource arbiter.
 */
class BootstrapRegistry {
    private var runtime: Any?=null
    private var sealed=false
    private var touched=false
    @Synchronized fun register(owner: Any) { check(!sealed && runtime==null); runtime=owner }
    @Synchronized fun seal() { check(runtime!=null && !sealed); sealed=true }
    @Synchronized fun beforeNativeOrImport(owner: Any) { check(sealed && runtime===owner); touched=true }
    @Synchronized fun provesNeverStarted(owner: Any)=sealed && runtime===owner && !touched
}
