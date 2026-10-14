package fr.jarvis.llm

import org.robolectric.RuntimeEnvironment
import fr.jarvis.domain.catalogue.*
import fr.jarvis.llm.catalogue.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CatalogueStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun record(id:String="test.answer", revision:Int=1, text:String="Réponse locale.") = AnswerRecord(id,id,"Test $id","test",revision,listOf("Demande précise $id"),listOf(text))
    private fun store(name:String): CatalogueStore { context.deleteDatabase(name); return CatalogueStore(context,name) }
    @Test fun demoHasOnlyEightAnswersAndPersistsUserModification() {
        val name="catalogue-demo-test.db"
        store(name).use { s -> assertEquals(8,s.count()); val r=s.get("demo.bonjour")!!; s.save(r.copy(revision=2,variants=listOf("Bonjour modifié.")),1) }
        CatalogueStore(context,name).use { assertEquals(8,it.count()); assertEquals("Bonjour modifié.",it.get("demo.bonjour")!!.variants.single()) }
    }
    @Test fun importIsAtomicIdempotentAndRejectsRevisionCollisions() {
        store("catalogue-merge-test.db").use { s ->
            val first=record(); assertEquals(1,s.merge(listOf(first))); assertEquals(0,s.merge(listOf(first)))
            assertThrows(IllegalArgumentException::class.java) { s.merge(listOf(record("new.answer"), first.copy(variants=listOf("Contenu différent.")))) }
            assertNull(s.get("new.answer")); assertEquals(first,s.get(first.id))
            assertEquals(1,s.merge(listOf(first.copy(revision=2))))
            assertThrows(IllegalArgumentException::class.java) { s.merge(listOf(first)) }
        }
    }
    @Test fun cancellationRollsBackAndDeletionPreventsOldImportResurrection() {
        store("catalogue-delete-test.db").use { s ->
            var calls=0
            assertThrows(IllegalStateException::class.java) { s.merge(listOf(record("first"),record("second"))) { ++calls>1 } }
            assertNull(s.get("first")); assertNull(s.get("second"))
            val r=record(); s.merge(listOf(r)); s.delete(r.id,1)
            assertTrue(s.get(r.id)!!.deleted)
            assertTrue(s.candidates(r.examples.single()).records.none { it.id==r.id })
            assertThrows(IllegalArgumentException::class.java) { s.merge(listOf(r)) }
            assertThrows(IllegalArgumentException::class.java) { s.save(r.copy(revision=2),1) }
        }
    }
    @Test fun exportRoundTripPreservesVariantsRevisionOriginAndTombstones() {
        store("catalogue-export-test.db").use { s ->
            val r=record().copy(variants=listOf("Une réponse.","Une variante."),origin=CatalogueOrigin.SYNTHETIC)
            s.merge(listOf(r)); s.delete("demo.stop",1)
            val out=ByteArrayOutputStream(); s.export(out)
            val decoded=CatalogueJson.read(ByteArrayInputStream(out.toByteArray()))
            assertEquals(r,decoded.single { it.id==r.id }); assertTrue(decoded.single { it.id=="demo.stop" }.deleted)
            store("catalogue-roundtrip-test.db").use { restored -> restored.merge(decoded); assertEquals(r,restored.get(r.id)); assertTrue(restored.get("demo.stop")!!.deleted) }
        }
    }
    @Test fun invalidJsonDuplicateIdsOversizeAndFutureFormatsAreRejected() {
        val r=record()
        assertThrows(IllegalArgumentException::class.java) { CatalogueJson.read(ByteArrayInputStream(CatalogueJson.export(listOf(r,r)).toByteArray())) }
        assertThrows(IllegalArgumentException::class.java) { CatalogueJson.read(ByteArrayInputStream("{\"formatVersion\":2,\"answers\":[]}".toByteArray())) }
        assertThrows(IllegalArgumentException::class.java) { CatalogueJson.read(ByteArrayInputStream(ByteArray(CatalogueJson.MAX_BYTES+1) { 32 })) }
        assertThrows(Exception::class.java) { CatalogueJson.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28))) }
    }
    @Test fun strictStreamingParserRejectsDuplicateFieldsAndWrongScalarTypes() {
        val valid=CatalogueJson.export(listOf(record()))
        assertThrows(IllegalArgumentException::class.java) { CatalogueJson.read(ByteArrayInputStream(valid.replace("\"revision\": 1", "\"revision\": 1.5").toByteArray())) }
        assertThrows(IllegalArgumentException::class.java) { CatalogueJson.read(ByteArrayInputStream("{\"formatVersion\":1,\"formatVersion\":1,\"answers\":[]}".toByteArray())) }
        assertThrows(Exception::class.java) { CatalogueJson.read(ByteArrayInputStream("{\"unknown\":${"[".repeat(2000)}}".toByteArray())) }
    }
    @Test fun staleEditorCannotOverwriteAnotherStoreAndClosedStoreDoesNotReopen() {
        val name="catalogue-concurrent-test.db"
        val first=store(name)
        CatalogueStore(context,name).use { second ->
            first.merge(listOf(record()))
            val old=first.get("test.answer")!!
            second.save(old.copy(revision=2,variants=listOf("Modification concurrente.")),1)
            assertThrows(IllegalArgumentException::class.java) { first.save(old.copy(revision=2,variants=listOf("Ancien éditeur.")),1) }
            assertEquals("Modification concurrente.",first.get(old.id)!!.variants.single())
        }
        first.close(); assertThrows(IllegalStateException::class.java) { first.count() }
    }
    @Test fun actualFtsParaphraseAndConflictsUseNoModel() {
        store("catalogue-fts-test.db").use { s ->
            val router=IntentRouter(s)
            assertTrue(router.route("Tu marches quand il n'y a pas de connexion ?") is CatalogueRoute.Answer)
            assertTrue(router.route("Tu ne fonctionnes pas hors ligne ?") is CatalogueRoute.Llm)
            val r=s.get("demo.offline")!!
            s.merge(listOf(r.copy(id="conflicting.offline",examples=listOf("Utilisation connectée obligatoire"),variants=listOf("Internet obligatoire."))))
            assertTrue(router.route("Fonctionnes-tu hors ligne ?") is CatalogueRoute.Clarify)
        }
    }
    @Test fun synthetic1500RecordsRemainPagedAndIndexedWithMemoryEvidence() {
        store("catalogue-scale-test.db").use { s ->
            val records=(0 until 1500).map { n -> AnswerRecord("synthetic.$n","synthetic.$n","TEST SYNTHÉTIQUE $n","TEST SYNTHÉTIQUE",1,listOf("Test synthétique référence $n"),listOf("Donnée synthétique $n, sans valeur métier."),CatalogueOrigin.SYNTHETIC) }
            val input=CatalogueJson.export(records).toByteArray()
            val runtime=Runtime.getRuntime(); fun heap()=runtime.totalMemory()-runtime.freeMemory()
            val baseline=heap(); val peak=AtomicLong(baseline); val done=AtomicBoolean(false)
            val sampler=Thread { while (!done.get()) { peak.accumulateAndGet(heap(),::maxOf); Thread.sleep(5) } }.apply { isDaemon=true; start() }
            try {
            val start=System.nanoTime()
            val decoded=CatalogueJson.read(ByteArrayInputStream(input)); s.merge(decoded)
            val importMs=(System.nanoTime()-start)/1_000_000.0
            assertEquals(1508,s.count()); assertEquals(50,s.list().size)
            val router=IntentRouter(s); val timings=(0 until 20).map { n -> val t=System.nanoTime(); val route=router.route("Test synthétique référence ${n*71}"); assertTrue(route is CatalogueRoute.Answer); (System.nanoTime()-t)/1_000_000.0 }.sorted()
            done.set(true); sampler.join()
            val report=org.json.JSONObject().put("platform","Linux JVM / Robolectric API35 native SQLite; NOT physical Android PSS")
                .put("records",1508).put("synthetic_records",1500).put("json_bytes",input.size).put("import_ms",importMs)
                .put("query_median_ms",timings[10]).put("query_max_ms",timings.last()).put("sampled_java_heap_baseline_bytes",baseline)
                .put("sampled_java_heap_peak_bytes",peak.get()).put("sampled_java_heap_increase_bytes",peak.get()-baseline).put("sqlite",s.stats()).put("llama_loaded",false)
            val file=File(requireNotNull(System.getProperty("jarvis.catalogue.report"))); requireNotNull(file.parentFile).mkdirs(); file.writeText(report.toString(2))
            } finally { done.set(true); sampler.join() }
        }
    }
}
