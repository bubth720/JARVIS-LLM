package fr.jarvis.domain

import fr.jarvis.domain.catalogue.*
import org.junit.Assert.*
import org.junit.Test

class IntentRouterTest {
    private fun answer(id: String = "offline", intent: String = id, examples: List<String> = listOf("Fonctionnes-tu sans Internet ?"), variants: List<String> = listOf("Fonctionnement entièrement local.")) = AnswerRecord(id,intent,id,"test",1,examples,variants)
    private fun router(vararg answers: AnswerRecord) = IntentRouter(object : KnowledgeSource { override fun candidates(question: String, limit: Int) = CandidatePage(answers.toList()) })
    @Test fun unseenParaphraseUsesPhraseGrammarAndSynonyms() {
        val route = router(answer()).route("Tu marches quand il n'y a pas de connexion ?")
        assertTrue(route is CatalogueRoute.Answer)
        assertTrue(router(answer()).route("Tu fonctionnes hors ligne ?") is CatalogueRoute.Answer)
    }
    @Test fun reorderedFrenchQuestionCanMatchWithoutExactNormalizedSentence() {
        val r = router(answer("privacy", examples=listOf("Envoies-tu mes messages sur Internet ?")))
        assertNotEquals(FrenchForms.of("Envoies-tu mes messages sur Internet ?").text, FrenchForms.of("Mes messages sont-ils envoyés sur Internet ?").text)
        assertTrue(r.route("Mes messages sont-ils envoyés sur Internet ?") is CatalogueRoute.Answer)
    }
    @Test fun oneVariantIsEnoughAndMultipleVariantsRotateWithinSameAnswer() {
        val single = router(answer()); repeat(3) { assertEquals("Fonctionnement entièrement local.", (single.route("Fonctionnes-tu sans Internet ?") as CatalogueRoute.Answer).text) }
        val multiple = router(answer(variants = listOf("Premier texte.", "Second texte.")))
        assertEquals(listOf("Premier texte.", "Second texte.", "Premier texte."), (1..3).map { (multiple.route("Fonctionnes-tu sans Internet ?") as CatalogueRoute.Answer).text })
    }
    @Test fun negationAndDifferentQuestionTypeNeverMatchPositiveAnswer() {
        val r = router(answer())
        assertTrue(r.route("Tu ne fonctionnes pas hors ligne ?") is CatalogueRoute.Llm)
        assertTrue(r.route("Pourquoi fonctionnes-tu hors ligne ?") is CatalogueRoute.Llm)
        assertTrue(r.route("Internet est lent et mon appareil chauffe") is CatalogueRoute.Llm)
    }
    @Test fun changedNumericValueCannotBeIgnored() {
        val r = router(answer(examples=listOf("Batterie sous 10 pour cent")))
        assertTrue(r.route("Batterie sous 20 pour cent") is CatalogueRoute.Llm)
    }
    @Test fun contradictoryEntriesAndExactPhraseCollisionAskForClarification() {
        val first = answer(variants=listOf("Fonctionnement local."))
        assertTrue(router(first, answer("other", "offline", variants=listOf("Internet obligatoire."))).route("Fonctionnes-tu sans Internet ?") is CatalogueRoute.Clarify)
        assertTrue(router(first, answer("other", "different")).route("Fonctionnes-tu sans Internet ?") is CatalogueRoute.Clarify)
    }
    @Test fun explicitOppositeVariantsCannotBeRotatedAsEquivalent() {
        val r = router(answer(variants=listOf("Le mode est disponible.", "Le mode est indisponible.")))
        assertTrue(r.route("Fonctionnes-tu sans Internet ?") is CatalogueRoute.Clarify)
    }
    @Test fun identicalVariantClaimsWithOppositeNumbersNeedReview() {
        val r = router(answer(variants=listOf("Seuil batterie 10 pour cent.", "Seuil batterie 20 pour cent.")))
        assertTrue(r.route("Fonctionnes-tu sans Internet ?") is CatalogueRoute.Clarify)
    }
    @Test fun vagueRequestClarifiesInsteadOfGuessing() {
        val r = router(answer("battery", examples=listOf("Protection batterie")))
        val result = r.route("batterie") as CatalogueRoute.Clarify
        assertEquals(listOf("Protection batterie"), result.suggestedQueries)
    }
    @Test fun overflowNeverProducesConfidentAnswer() {
        val r = IntentRouter(object: KnowledgeSource { override fun candidates(question:String,limit:Int)=CandidatePage(listOf(answer()),true) })
        assertTrue(r.route("Fonctionnes-tu sans Internet ?") is CatalogueRoute.Clarify)
    }
    @Test fun reversedRelationAndUncoveredQualifierDoNotMatch() {
        val r = router(answer(examples=listOf("Envoyer appareil ordinateur")))
        assertTrue(r.route("Envoyer ordinateur appareil") is CatalogueRoute.Llm)
        assertTrue(router(answer()).route("Fonctionnes-tu hors ligne uniquement demain ?") is CatalogueRoute.Llm)
    }
    @Test fun deletedAnswersAreNotUsed() { assertTrue(router(answer().copy(deleted=true)).route("Fonctionnes-tu sans Internet ?") is CatalogueRoute.Llm) }
    @Test fun invalidOrEmptyVariantsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { answer(variants=emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { answer(examples=emptyList()) }
    }
}
