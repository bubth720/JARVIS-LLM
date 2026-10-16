package fr.jarvis.llm
import fr.jarvis.domain.*
import fr.jarvis.llm.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NativeFixtureBudgetTest {
    private class Api : NativeApi {
        var tokens=100; var generated=false; var effective=0; val threads=mutableSetOf<Long>()
        private fun thread() { threads += Thread.currentThread().id }
        override fun create()=1L
        override fun begin(handle: Long) {}
        override fun cancel(handle: Long) {}
        override fun load(handle: Long,path: ByteArray,context: Int,threads: Int,small: Boolean) { thread() }
        override fun generate(handle: Long,roles: Array<ByteArray>,texts: Array<ByteArray>,maxTokens: Int)=error("legacy not allowed")
        override fun generateP2(handle: Long,roles: Array<ByteArray>,texts: Array<ByteArray>,maxTokens: Int,system: ByteArray)=error("unbounded P2 not allowed")
        override fun measureFixtureP2(handle: Long,system: ByteArray,question: ByteArray): Array<ByteArray> { thread(); assertEquals("CORE",system.toString(Charsets.UTF_8)); return arrayOf(tokens.toString().toByteArray(),"canonical-render".toByteArray(),"real-template-fixture".toByteArray()) }
        override fun generateFixtureP2(handle: Long,system: ByteArray,question: ByteArray,maxTokens: Int,contextLimit: Int): ByteArray { thread(); generated=true; effective=contextLimit; return "Bonjour".toByteArray() }
        override fun allocations(handle: Long)=longArrayOf(1,2,3)
        override fun unload(handle: Long) {}
        override fun destroy(handle: Long) {}
    }
    @Test fun countAndGenerationShareWorkerAndUseRequestedCapWithoutSystemRecomposition() = runBlocking {
        val api=Api(); val backend=LlamaBackend(api); val config=RuntimeConfig(ModelKind.SMALL,768,8,1)
        try {
            backend.prepareOperation(); backend.load("synthetic",config)
            val result=backend.generateFixtureP2(GenerationRequest(listOf(Message("user","Question")),config,"CORE"),512)
            assertEquals(512,api.effective); assertEquals(1,api.threads.size); assertEquals(100,result.inputTokens)
            assertEquals(64,result.templateSha256.length); assertEquals(64,result.renderedSha256.length)
        } finally { backend.close() }
    }
    @Test fun requested512IsEnforcedBeforeGenerationEvenWhen768Loaded() = runBlocking {
        val api=Api().apply { tokens=500 }; val backend=LlamaBackend(api); val config=RuntimeConfig(ModelKind.SMALL,768,8,1)
        try {
            backend.prepareOperation(); backend.load("synthetic",config)
            try { backend.generateFixtureP2(GenerationRequest(listOf(Message("user","Question")),config,"CORE"),512); fail() } catch (_: IllegalArgumentException) {}
            assertFalse(api.generated); assertNotNull(ProcessResources.coordinator.snapshot().model)
            assertNull(ProcessResources.coordinator.snapshot().heavy)
            backend.unload(); assertNull(ProcessResources.coordinator.snapshot().model)
        } finally { backend.close() }
    }
}
