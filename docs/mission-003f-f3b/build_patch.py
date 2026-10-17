#!/usr/bin/env python3
"""Generate an UNAPPLIED review patch from immutable Git blobs. Never writes app/domain/fixtures."""
from pathlib import Path
import difflib,hashlib,json,subprocess,sys
ROOT=Path(__file__).resolve().parent
BASE='aa85ad4b506bb655dbe5b50ec4b125d19b48e81e'
changes={}
def source(path):return subprocess.check_output(['git','show',BASE+':'+path]).decode()
def change(path,fn):
 old=source(path);new=fn(old);assert new!=old,path;changes[path]=(old,new)
def replace(s,old,new,count=1):
 assert s.count(old)==count,(old,s.count(old),count)
 return s.replace(old,new)
engine='app/src/main/java/fr/jarvis/llm/engine/'
for name in ['F3Trace.kt','F3NativeApi.kt','F3BackendObservation.kt']:
 changes[engine+name]=('',(ROOT/'candidate'/name).read_text())
def backend(s):
 s=replace(s,'    fun allocations(handle: Long): LongArray','    fun observeOwned(handle: Long): LongArray = error("F3 read unsupported")\n    fun allocations(handle: Long): LongArray')
 s=replace(s,'    external override fun allocations(handle: Long): LongArray','    external override fun observeOwned(handle: Long): LongArray\n    external override fun allocations(handle: Long): LongArray')
 s=replace(s,'class LlamaBackend internal constructor(private val native: NativeApi)',
           'class LlamaBackend internal constructor(nativeApi: NativeApi, private val f3: F3Trace? = null)')
 marker='    private var modelLease: ResourceLease? = null'
 s=replace(s,marker,'''    private val observedNative = f3?.let { it.bindOwner(owner); F3NativeApi(nativeApi,it) }
    private val native: NativeApi = observedNative ?: nativeApi
'''+marker)
 # Track before dispatch; callback only inside synchronous worker body, not caller finally.
 helper='''    private suspend fun <T> onWorker(kind: F3Trace.Task, body: () -> T): T {
        val token=f3?.queued(kind)
        try { return kotlinx.coroutines.withContext(worker) {
            if (token!=null) f3!!.started(token)
            try { body() } finally { if (token!=null) {
                try { f3!!.taskReturned(token) } catch (_: Throwable) { f3!!.quarantine() }
            } }
        } } catch (failure: Throwable) {
            if (token!=null) {
                try { f3!!.dispatchAbandoned(token) } catch (_: Throwable) { f3!!.quarantine() }
            }
            throw failure
        }
    }
    /** Read existing ownership only. No create/load/unload, no acquisition or close of leases.
     * UNKNOWN remains mandatory unless the fixture has frozen an exhaustive participant census.
     */
    suspend fun observeF3(): F3BackendObservation? {
        val trace=f3 ?: return null
        return onWorker(F3Trace.Task.INSPECT) {
            synchronized(handleLock) {
                val before=ProcessResources.coordinator.observation()
                if (handle!=0L) runCatching { observedNative!!.probeExisting(handle) { native.observeOwned(it) } }
                val first=trace.snapshot()
                val after=ProcessResources.coordinator.observation()
                val last=trace.snapshot()
                F3BackendObservation(last,after,before==after && first.revision==last.revision)
            }
        }
    }
'''
 s=replace(s,'    override fun prepareOperation()',helper+'    override fun prepareOperation()')
 s=replace(s,'override suspend fun load(path: String, config: RuntimeConfig) = withContext(worker)',
           'override suspend fun load(path: String, config: RuntimeConfig) = onWorker(F3Trace.Task.LOAD)')

 # Helper also contains withContext(worker), replace only generation method occurrences via exact prefixes.
 s=replace(s,'        try { return withContext(worker) {','        try { return onWorker(F3Trace.Task.GENERATE) {',2)
 s=replace(s,'override suspend fun unload() = withContext(worker)','override suspend fun unload() = onWorker(F3Trace.Task.UNLOAD)')
 s=replace(s,'        withContext(worker) {\n            synchronized(handleLock)',
           '        onWorker(F3Trace.Task.CLOSE) {\n            synchronized(handleLock)')
 return s
change(engine+'LlamaBackend.kt',backend)
def resources(s):
 s=replace(s,'sealed interface ResourceGrant {','''/** Read-only exact identities. Epoch is process-local, not an OS attestation. */
data class ObservedLease(val coordinatorEpoch: String, val id: Long, val request: ResourceRequest)
data class ResourceObservation(val coordinatorEpoch: String, val revision: Long,
                               val heavy: ObservedLease?, val model: ObservedLease?)
sealed interface ResourceGrant {''')
 s=replace(s,'    private var nextId = 0L','    private val epoch=java.util.UUID.randomUUID().toString()\n    private var revision=0L\n    private var nextId = 0L')
 s=replace(s,'        val id = ++nextId','        val id = ++nextId\n        revision++')
 s=replace(s,'if (kind == ResourceClass.HEAVY && heavy?.first == id) heavy = null',
           'if (kind == ResourceClass.HEAVY && heavy?.first == id) { heavy = null; revision++ }')
 s=replace(s,'if (kind == ResourceClass.MODEL && model?.first == id) model = null',
           'if (kind == ResourceClass.MODEL && model?.first == id) { model = null; revision++ }')
 s=replace(s,'    @Synchronized fun snapshot()', '''    @Synchronized fun observation() = ResourceObservation(epoch,revision,
        heavy?.let { ObservedLease(epoch,it.first,it.second) },model?.let { ObservedLease(epoch,it.first,it.second) })
    @Synchronized fun snapshot()''')
 return s
change('domain/src/main/kotlin/fr/jarvis/domain/resources/ResourceCoordinator.kt',resources)
def bridge(s):
 marker='extern "C" JNIEXPORT void JNICALL Java_fr_jarvis_llm_engine_NativeBridge_unload('
 probe='''// F3 read-only ABI v1. Caller: same existing worker, handleLock, live handle only.
// No pointer/address leaks, model/context allocations, loading or cleanup.
extern "C" JNIEXPORT jlongArray JNICALL Java_fr_jarvis_llm_engine_NativeBridge_observeOwned(
    JNIEnv * env, jobject, jlong h) {
    try {
        const auto & e = engine(h);
        const jlong flags[4] = {1, 1, e.model ? 1 : 0, e.ctx ? 1 : 0};
        auto result = env->NewLongArray(4);
        if (!result) return nullptr;
        env->SetLongArrayRegion(result, 0, 4, flags);
        return result;
    } catch (const std::exception & ex) { error(env, ex); return nullptr; }
}
'''
 return replace(s,marker,probe+marker)
change('app/src/main/cpp/bridge.cpp',bridge)
def runtime(s):
 s=replace(s,'    private val importOwner=', '    private var f3Trace: F3Trace? = null\n    private val importOwner=')
 marker='    private fun decision(r: Request)'
 helper='''    // Internal-only review hook. No Binder entry, no grant, no creation while observing.
    // A new key cannot reuse this backend owner. Registration must precede all native/import work.
    private fun traceFor(request: Request): F3Trace = synchronized(nativeLock) {
        val pilot=app.recoveryPlatform.pins?.pilot ?: throw Refusal("ENROLMENT_REQUIRED")
        val digest=Wire.digest(request.keyFields+mapOf("request_digest" to request.digest,"pilot_id" to pilot))
        val trace=f3Trace ?: F3Trace(recovery.processEpoch,digest).also { f3Trace=it }
        trace.requireKey(digest)
        trace
    }
    internal suspend fun observeExistingF3(): F3BackendObservation? {
        val existing=synchronized(nativeLock) { backend }
        if (existing!=null) return existing.observeF3()
        // Positive destroy event from the SAME retained trace, never from backend==null.
        val trace=f3Trace ?: return null
        val before=trace.snapshot()
        if (!before.destroyed) return null
        val resources=ProcessResources.coordinator.observation()
        val after=trace.snapshot()
        return F3BackendObservation(after,resources,before.revision==after.revision)
    }
'''
 s=replace(s,marker,helper+marker)
 s=replace(s,'        val path=ProcessResources.requireHeavy(',
           '        val trace=traceFor(request)\n        val importing=trace.importEntered()\n        val path=try { ProcessResources.requireHeavy(')
 s=replace(s,'            app.models.verify(request.p2.model) { stop }\n        }',
           '            app.models.verify(request.p2.model) { stop }\n        } } finally { trace.importReturned(importing) }')
 s=replace(s,'backend ?: LlamaBackend().also { backend=it }',
           'backend ?: LlamaBackend(NativeBridge,trace).also { backend=it }')
 s=replace(s,'        demand(!stop && safe(request),"SAFETY_REFUSAL")',
           '        traceFor(request)\n        demand(!stop && safe(request),"SAFETY_REFUSAL")')
 return s
change('fixtures/p2-endpoint/src/main/java/fr/jarvis/fixture/llm/AndroidRuntime.kt',runtime)
patch=''
manifest=[]
for path,(old,new) in sorted(changes.items()):
 patch+='diff --git a/'+path+' b/'+path+'\n'
 if not old:patch+='new file mode 100644\n'
 patch+=''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+path if old else '/dev/null',tofile='b/'+path))
 manifest.append({'path':path,'beforeSha256':hashlib.sha256(old.encode()).hexdigest() if old else None,
                  'afterSha256':hashlib.sha256(new.encode()).hexdigest()})
raw=patch.replace("\n \n","\n\n").encode() # Git accepts empty context lines; avoid whitespace-only data lines.
manifest_text=json.dumps({'base':BASE,'patchSha256':hashlib.sha256(raw).hexdigest(),
 'applied':False,'historicalBackendCompiledWithPatch':False,'nativeProbeExecuted':False,
 'nativeAdmissionEnabled':False,'files':manifest},indent=2)+'\n'
if '--check' in sys.argv:
 assert (ROOT/'SENSITIVE.patch').read_bytes()==raw,'Patch generation drift'
 assert (ROOT/'PATCH.json').read_text()==manifest_text,'Manifest generation drift'
else:
 (ROOT/'SENSITIVE.patch').write_bytes(raw)
 (ROOT/'PATCH.json').write_text(manifest_text)
print('Unapplied patch',len(manifest),'files',hashlib.sha256(raw).hexdigest())
