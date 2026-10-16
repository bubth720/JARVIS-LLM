package fr.jarvis.domain.generation

import fr.jarvis.domain.ModelKind
import org.junit.Assert.*
import org.junit.Test

/** Fixtures of the fields inspected at CORE 07cd282; no CORE code/backend is imported or executed. */
class Core004BMappingTest {
    private fun input(context: String="", sources:Boolean=false, version:Int=1, requestId:String=ID, contextSize:Int=512) =
        Core004BRequestView(version,ID,requestId,"Système CORE distinct, synthétique.","Question CORE exacte.",context,sources,contextSize,64)
    private fun map(i:Core004BRequestView) = Core004BMapping.textOnly(i,key(),ModelKind.SMALL,SHA,1000,65536)
    @Test fun inspectedCoreTextOnlyFieldsMapWithoutRenderingSystemAsUser() {
        val core=input();val r=map(core);val p=prepared(r)
        assertEquals(core.systemPrompt,r.system);assertEquals(core.question,r.question)
        assertEquals(core.sessionId,r.key.sessionId);assertEquals(core.requestId,r.key.operationId)
        assertTrue(r.history.isEmpty());assertTrue(r.documents.isEmpty())
        assertTrue(p.prompt.messages.first().text.contains(core.systemPrompt))
        assertFalse(p.prompt.messages.last().text.contains(core.systemPrompt))
        assertTrue(p.prompt.messages.last().text.endsWith(core.question))
    }
    @Test fun flatContextSourcesAndWhitespaceAreNeverSilentlyDroppedOrReparsed() {
        listOf(input(context="HISTORIQUE RÉCENT: user: texte"),input(context=" "),input(sources=true)).forEach {
            assertEquals(P2Error.CONTEXT_NOT_REPRESENTABLE,assertThrows(P2Exception::class.java) { map(it) }.code)
        }
        assertEquals("INVALID_INPUT",P2Error.CONTEXT_NOT_REPRESENTABLE.unifiedCode())
    }
    @Test fun coreVersionIdsAndNonAlignedLimitsNeedExplicitCompatibility() {
        assertEquals(P2Error.UNSUPPORTED_VERSION,assertThrows(P2Exception::class.java) { map(input(version=2)) }.code)
        assertEquals(P2Error.STALE_OPERATION,assertThrows(P2Exception::class.java) { map(input(requestId="legacy")) }.code)
        assertEquals(P2Error.INVALID_LIMIT,assertThrows(P2Exception::class.java) { map(input(contextSize=1000)) }.code)
    }
}
