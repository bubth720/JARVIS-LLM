package fr.jarvis.domain.catalogue

import java.text.Normalizer
import java.util.Locale

enum class CatalogueOrigin { DEMO, USER, SYNTHETIC }
data class AnswerRecord(
    val id: String, val intent: String, val title: String, val category: String,
    val revision: Int, val examples: List<String>, val variants: List<String>,
    val origin: CatalogueOrigin = CatalogueOrigin.USER, val deleted: Boolean = false
) {
    init {
        require(id.matches(Regex("[a-z0-9._-]{1,64}")) && intent.matches(Regex("[a-z0-9._-]{1,64}"))) { "Identifiant ou intention invalide" }
        require(revision in 1 until Int.MAX_VALUE && title.isNotBlank() && title.length <= 160 && category.isNotBlank() && category.length <= 64)
        require(examples.size in 1..12 && variants.size in 1..8) { "Une formulation et une variante au minimum" }
        require(examples.all { it.isNotBlank() && it.length <= 500 } && variants.all { it.isNotBlank() && it.length <= 2000 })
        require(examples.distinct().size == examples.size && variants.distinct().size == variants.size) { "Doublons dans la réponse" }
    }
}
data class CandidatePage(val records: List<AnswerRecord>, val truncated: Boolean = false)
/** Editorial catalogue candidates. Blocking implementations run on IO. */
interface KnowledgeSource { fun candidates(question: String, limit: Int = 20): CandidatePage }
enum class KnowledgeKind { CATALOGUE, DOCUMENT, MEMORY }
data class KnowledgeHit(val kind: KnowledgeKind, val sourceId: String, val revision: String,
                        val content: String, val sourceFingerprint: String)
/** Future document/memory retrieval returns evidence, never an automatic editorial answer. */
interface RetrievalIndex { fun retrieve(question: String, limit: Int): List<KnowledgeHit> }
/** No embedding model is packaged or instantiated in Mission 003. */
interface SemanticRetriever : RetrievalIndex {
    val modelFingerprint: String
    val dimensions: Int
    val measuredPeakBytes: Long
}

data class Form(val text: String, val words: List<String>, val negative: Boolean)
object FrenchForms {
    const val VERSION = 1
    private val stops = setOf("je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "au", "aux", "ce", "ces", "cette", "mon", "ma", "mes", "ton", "ta", "tes", "est", "es", "sont", "suis", "a", "as", "avez", "ai", "et", "que", "qu", "qui", "quoi", "quel", "quelle", "quels", "quelles", "peux", "peut", "puis", "pour", "faire", "s", "t", "y", "en", "me", "moi", "avec", "si", "svp", "stp")
    private val aliases = mapOf("marches" to "fonctionner", "marche" to "fonctionner", "fonctionnes" to "fonctionner", "fonctionne" to "fonctionner", "fonctionnement" to "fonctionner", "utiliser" to "fonctionner", "utilisable" to "fonctionner", "connexion" to "internet", "reseau" to "internet", "wifi" to "internet", "arreter" to "stop", "arret" to "stop", "stopper" to "stop", "interrompre" to "stop", "interruption" to "stop", "calculs" to "calcul", "reponses" to "reponse", "modeles" to "modele", "proteger" to "protection", "proteges" to "protection", "protections" to "protection", "surchauffe" to "chauffe", "chaud" to "chauffe", "chauffage" to "chauffe", "chauffe" to "chauffe", "telephone" to "appareil", "tablette" to "appareil", "confidentialite" to "prive", "confidentielles" to "prive", "confidentiel" to "prive", "privees" to "prive", "privee" to "prive", "donnees" to "donnee", "messages" to "message", "envoies" to "envoyer", "envoie" to "envoyer", "envoyes" to "envoyer", "modes" to "mode", "puissances" to "puissance", "salut" to "bonjour", "coucou" to "bonjour")
    fun of(raw: String): Form {
        var text = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
        // Small, auditable paraphrase grammar; it does not claim general semantic understanding.
        text = text.replace(Regex("(?:hors ligne|sans (?:internet|connexion|reseau|wifi)|quand il n y a pas de connexion)"), "offline")
            .replace(Regex("(?:s il te plait|s il vous plait)"), "")
            .replace("indisponible", "pas disponible")
        val tokens = text.split(' ').filter { it.isNotBlank() }
        val negative = tokens.any { it in setOf("ne", "n", "pas", "jamais", "aucun", "aucune", "non", "sans") }
        val words = tokens.filter { it !in stops && it !in setOf("ne", "n", "pas", "non") }.map { aliases[it] ?: it }.take(65)
        return Form(words.joinToString(" "), words, negative)
    }
}

sealed interface CatalogueRoute {
    data class Answer(val record: AnswerRecord, val text: String, val variant: Int, val score: Double) : CatalogueRoute
    data class Clarify(val text: String, val suggestedQueries: List<String> = emptyList()) : CatalogueRoute
    data class Llm(val reason: String) : CatalogueRoute
}

class IntentRouter(private val source: KnowledgeSource) {
    // Rotation is per session, only between variants within the same editorial answer.
    private val rotations = mutableMapOf<String, Int>()
    private data class Scored(val record: AnswerRecord, val score: Double, val coverage: Double, val exact: Boolean)
    private fun score(query: Form, example: String): Pair<Double, Double> {
        val e = FrenchForms.of(example)
        if (query.negative != e.negative || query.words.isEmpty() || e.words.isEmpty()) return 0.0 to 0.0
        // Numeric values and question types carry meaning and must not be erased by fuzzy overlap.
        if (query.words.filter { it.any(Char::isDigit) }.toSet() != e.words.filter { it.any(Char::isDigit) }.toSet()) return 0.0 to 0.0
        if (query.words.filter { it in setOf("comment", "pourquoi", "quand", "combien") }.toSet() != e.words.filter { it in setOf("comment", "pourquoi", "quand", "combien") }.toSet()) return 0.0 to 0.0
        if (query.text == e.text) return 1.0 to 1.0
        val q = query.words.toSet(); val f = e.words.toSet(); val common = q.intersect(f).size
        val coverage = common.toDouble() / q.size
        val completeness = common.toDouble() / f.size
        val qb = query.words.zipWithNext().toSet(); val eb = e.words.zipWithNext().toSet()
        val sequence = if (qb.isEmpty()) 0.0 else qb.intersect(eb).size.toDouble() / qb.size
        return (0.45 * coverage + 0.35 * completeness + 0.20 * sequence) to coverage
    }
    fun route(question: String): CatalogueRoute {
        val query = FrenchForms.of(question)
        if (question.length > 500 || query.words.size > 32 || query.words.isEmpty()) return CatalogueRoute.Llm("Demande non couverte par le catalogue")
        val page = source.candidates(question)
        if (page.truncated) return CatalogueRoute.Clarify("Trop de réponses possibles. Précisez votre demande.")
        val scored = page.records.filterNot { it.deleted }.map { record ->
            val best = record.examples.map { score(query, it) }.maxByOrNull { it.first } ?: (0.0 to 0.0)
            Scored(record, best.first, best.second, record.examples.any { FrenchForms.of(it).let { f -> f.text == query.text && f.negative == query.negative } })
        }.sortedByDescending { it.score }
        val best = scored.firstOrNull() ?: return CatalogueRoute.Llm("Aucune réponse enregistrée pertinente")
        val second = scored.drop(1).firstOrNull { it.record.id != best.record.id }
        if (best.score >= 0.86 && best.coverage >= 0.95 && (best.exact || (second?.score ?: 0.0) <= best.score - 0.16)) {
            if (scored.count { it.record.intent == best.record.intent } > 1 || second?.let { it.score >= best.score - 0.16 } == true || conflictingVariants(best.record))
                return CatalogueRoute.Clarify("Le catalogue contient des réponses concurrentes ou contradictoires. Vérifiez-les dans Catalogue ; aucune réponse n'est choisie automatiquement.")
            val index = rotations[best.record.id] ?: 0
            rotations[best.record.id] = (index + 1) % best.record.variants.size
            if (rotations.size > 128) rotations.clear()
            return CatalogueRoute.Answer(best.record, best.record.variants[index % best.record.variants.size], index % best.record.variants.size, best.score)
        }
        val plausible = scored.filter { it.score >= 0.55 }.distinctBy { it.record.intent }.take(3)
        if (plausible.size >= 2 || (query.words.size <= 2 && plausible.isNotEmpty())) {
            val choices = plausible.map { it.record.examples.first() }
            return CatalogueRoute.Clarify("Précisez votre demande : " + plausible.mapIndexed { index, s -> "${index + 1}. ${s.record.title}" }.joinToString(" ; ") + ". Répondez par le numéro ou reformulez.", choices)
        }
        return CatalogueRoute.Llm("Couverture ou confiance insuffisante ; pas de réponse préenregistrée forcée")
    }
    private fun conflictingVariants(record: AnswerRecord): Boolean {
        val forms = record.variants.map(FrenchForms::of)
        return forms.indices.any { i -> forms.drop(i + 1).any { (it.text == forms[i].text && it.negative != forms[i].negative) ||
            (it.words.filterNot { word -> word.any(Char::isDigit) } == forms[i].words.filterNot { word -> word.any(Char::isDigit) } &&
                it.words.filter { word -> word.any(Char::isDigit) } != forms[i].words.filter { word -> word.any(Char::isDigit) }) } }
    }
}
