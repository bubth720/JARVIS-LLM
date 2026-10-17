package fr.jarvis.llm.engine

/** Proposed decorator of the existing API, NOT another backend or resource owner.
 * Tests compile this in the test source set only, with explicitly fake JNI.
 */
internal class F3NativeApi(private val delegate: NativeApi, private val trace: F3Trace): NativeApi by delegate {
    private fun completed(token: F3Trace.Token, success: Boolean, flags: LongArray? = null) {
        try { trace.returned(token,success,flags) } catch (_: Throwable) { trace.quarantine() }
    }
    private inline fun <T> call(kind: F3Trace.Call, block: () -> T): T {
        val token=trace.entered(kind)
        val result=try { block() } catch (failure: Throwable) { completed(token,false); throw failure }
        completed(token,true)
        return result
    }
    override fun create(): Long = call(F3Trace.Call.CREATE) { delegate.create().also { check(it != 0L) { "INVALID_NATIVE_HANDLE" } } }
    override fun begin(handle: Long) = call(F3Trace.Call.BEGIN) { delegate.begin(handle) }
    override fun cancel(handle: Long) = call(F3Trace.Call.CANCEL) { delegate.cancel(handle) }
    override fun load(handle: Long,path: ByteArray,context: Int,threads: Int,small: Boolean) =
        call(F3Trace.Call.LOAD) { delegate.load(handle,path,context,threads,small) }
    override fun generate(handle: Long,roles: Array<ByteArray>,texts: Array<ByteArray>,maxTokens: Int) =
        call(F3Trace.Call.GENERATE) { delegate.generate(handle,roles,texts,maxTokens) }
    override fun generateP2(handle: Long,roles: Array<ByteArray>,texts: Array<ByteArray>,maxTokens: Int,system: ByteArray) =
        call(F3Trace.Call.GENERATE) { delegate.generateP2(handle,roles,texts,maxTokens,system) }
    override fun measureFixtureP2(handle: Long,system: ByteArray,question: ByteArray) =
        call(F3Trace.Call.MEASURE) { delegate.measureFixtureP2(handle,system,question) }
    override fun generateFixtureP2(handle: Long,system: ByteArray,question: ByteArray,maxTokens: Int,contextLimit: Int) =
        call(F3Trace.Call.GENERATE) { delegate.generateFixtureP2(handle,system,question,maxTokens,contextLimit) }
    override fun allocations(handle: Long) = call(F3Trace.Call.ALLOCATIONS) { delegate.allocations(handle) }
    override fun unload(handle: Long) = call(F3Trace.Call.UNLOAD) { delegate.unload(handle) }
    override fun destroy(handle: Long) = call(F3Trace.Call.DESTROY) { delegate.destroy(handle) }
    fun probeExisting(handle: Long, reader: (Long) -> LongArray): LongArray {
        val token=trace.entered(F3Trace.Call.PROBE)
        val result=try { reader(handle) } catch (failure: Throwable) { completed(token,false); throw failure }
        completed(token,true,result)
        return result
    }
}
