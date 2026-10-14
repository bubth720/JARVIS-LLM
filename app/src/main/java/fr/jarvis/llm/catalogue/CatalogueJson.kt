package fr.jarvis.llm.catalogue

import fr.jarvis.domain.catalogue.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.CodingErrorAction

object CatalogueJson {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_RECORDS = 5000
    fun read(input: InputStream, cancelled: () -> Boolean = { false }): List<AnswerRecord> {
        var bytes = 0
        val bounded = object : java.io.FilterInputStream(input) {
            private fun account(count: Int) { check(!cancelled()) { "Opération arrêtée" }; if (count > 0) { bytes += count; require(bytes <= MAX_BYTES) { "Catalogue limité à 8 Mio" } } }
            override fun read(): Int { val value = input.read(); account(if (value >= 0) 1 else 0); return value }
            override fun read(block: ByteArray, offset: Int, length: Int): Int { val count = input.read(block, offset, length); account(count); return count }
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val reader = android.util.JsonReader(java.io.InputStreamReader(bounded, decoder))
        fun string(max: Int): String { require(reader.peek() == android.util.JsonToken.STRING); return reader.nextString().also { require(it.length <= max) } }
        fun number(): Int { require(reader.peek() == android.util.JsonToken.NUMBER); val value = reader.nextString(); require(value.matches(Regex("[1-9][0-9]{0,9}"))); return value.toInt() }
        fun strings(maxCount: Int, maxLength: Int): List<String> {
            reader.beginArray(); val result = mutableListOf<String>()
            while (reader.hasNext()) { require(result.size < maxCount); result.add(string(maxLength)) }
            reader.endArray(); return result
        }
        fun record(): AnswerRecord {
            val fields = mutableMapOf<String, Any>()
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName(); require(key !in fields) { "Champ dupliqué : $key" }
                fields[key] = when (key) {
                    "id", "intent", "category" -> string(64)
                    "title" -> string(160)
                    "origin" -> string(16)
                    "revision" -> number()
                    "examples" -> strings(12, 500)
                    "variants" -> strings(8, 2000)
                    "deleted" -> { require(reader.peek() == android.util.JsonToken.BOOLEAN); reader.nextBoolean() }
                    else -> error("Champ de réponse non reconnu : $key")
                }
            }
            reader.endObject()
            require(fields.keys == setOf("id", "intent", "title", "category", "origin", "revision", "examples", "variants", "deleted"))
            @Suppress("UNCHECKED_CAST")
            return AnswerRecord(fields["id"] as String, fields["intent"] as String, fields["title"] as String, fields["category"] as String,
                fields["revision"] as Int, fields["examples"] as List<String>, fields["variants"] as List<String>, CatalogueOrigin.valueOf(fields["origin"] as String), fields["deleted"] as Boolean)
        }
        val seen = mutableSetOf<String>(); val records = mutableListOf<AnswerRecord>(); var version = 0
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName(); require(seen.add(key)) { "Champ dupliqué : $key" }
            when (key) {
                "formatVersion" -> version = number()
                "answers" -> { reader.beginArray(); while (reader.hasNext()) { check(!cancelled()); require(records.size < MAX_RECORDS) { "Maximum 5 000 réponses" }; records.add(record()) }; reader.endArray() }
                else -> error("Champ de catalogue non reconnu : $key")
            }
        }
        reader.endObject()
        require(reader.peek() == android.util.JsonToken.END_DOCUMENT && seen == setOf("formatVersion", "answers") && version == 1) { "Format ou version de catalogue non pris en charge" }
        require(records.map { it.id }.distinct().size == records.size) { "Identifiants dupliqués dans le fichier" }
        return records
    }
    private fun integer(o: JSONObject, key: String): Int {
        val value = o.get(key)
        require(value is Int || value is Long) { "Entier requis : $key" }
        val long = (value as Number).toLong(); require(long in 1..Int.MAX_VALUE.toLong())
        return long.toInt()
    }
    fun decode(o: JSONObject): AnswerRecord {
        require(o.keys().asSequence().toSet() == setOf("id", "intent", "title", "category", "revision", "examples", "variants", "origin", "deleted")) { "Champs de réponse non reconnus" }
        fun string(key: String): String { val value = o.get(key); require(value is String); return value }
        fun strings(key: String): List<String> { val a = o.getJSONArray(key); require(a.length() <= 12); return (0 until a.length()).map { val v = a.get(it); require(v is String); v } }
        val deleted = o.get("deleted"); require(deleted is Boolean)
        return AnswerRecord(string("id"), string("intent"), string("title"), string("category"), integer(o, "revision"), strings("examples"), strings("variants"), CatalogueOrigin.valueOf(string("origin")), deleted)
    }
    fun encode(record: AnswerRecord): JSONObject = JSONObject().put("id", record.id).put("intent", record.intent).put("title", record.title)
        .put("category", record.category).put("revision", record.revision).put("examples", JSONArray(record.examples))
        .put("variants", JSONArray(record.variants)).put("origin", record.origin.name).put("deleted", record.deleted)
    fun export(records: List<AnswerRecord>): String = JSONObject().put("formatVersion", 1).put("answers", JSONArray(records.map(::encode))).toString(2)
}
