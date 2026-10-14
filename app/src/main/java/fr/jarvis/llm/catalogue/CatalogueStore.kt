package fr.jarvis.llm.catalogue

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import fr.jarvis.domain.catalogue.*

/** Private SQLite store. All public operations serialize; callers use an IO dispatcher. */
class CatalogueStore(private val context: Context, name: String = "catalogue.db") : KnowledgeSource, AutoCloseable {
    private var closed = false
    private fun database(writable: Boolean = false): SQLiteDatabase { check(!closed) { "Catalogue fermé" }; return if (writable) helper.writableDatabase else helper.readableDatabase }
    private val helper = object : SQLiteOpenHelper(context, name, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
            db.execSQL("PRAGMA cache_size=-2048")
            db.execSQL("PRAGMA temp_store=FILE")
            db.rawQuery("PRAGMA page_size", null).use { it.moveToFirst(); val pages = 64L * 1024 * 1024 / it.getLong(0); db.rawQuery("PRAGMA max_page_count=$pages", null).use { q -> q.moveToFirst() } }
        }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE catalogue_meta(name TEXT PRIMARY KEY, value INTEGER NOT NULL)")
            db.execSQL("INSERT INTO catalogue_meta VALUES('normalizer_version',${FrenchForms.VERSION})")
            db.execSQL("CREATE TABLE answers(id TEXT PRIMARY KEY, intent TEXT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL, revision INTEGER NOT NULL, origin TEXT NOT NULL, deleted INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX answers_intent ON answers(intent,deleted)")
            db.execSQL("CREATE TABLE variants(answer_id TEXT NOT NULL REFERENCES answers(id) ON DELETE CASCADE, position INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(answer_id,position))")
            db.execSQL("CREATE TABLE phrases(id INTEGER PRIMARY KEY, answer_id TEXT NOT NULL REFERENCES answers(id) ON DELETE CASCADE, raw TEXT NOT NULL, normalized TEXT NOT NULL)")
            db.execSQL("CREATE INDEX phrases_normalized ON phrases(normalized)")
            db.execSQL("CREATE VIRTUAL TABLE phrase_search USING fts4(answer_id, normalized, notindexed=answer_id, tokenize=simple)")
            db.execSQL("CREATE TABLE revisions(answer_id TEXT NOT NULL, revision INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(answer_id,revision))")
            context.assets.open("catalogue-demo.json").use { input -> CatalogueJson.read(input).forEach { write(db, it) } }
        }
        override fun onOpen(db: SQLiteDatabase) {
            db.rawQuery("SELECT value FROM catalogue_meta WHERE name='normalizer_version'", null).use { require(it.moveToFirst() && it.getInt(0) == FrenchForms.VERSION) { "Index lexical à migrer explicitement" } }
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Migration SQLite explicite requise ; aucune suppression automatique") }
    }
    private fun values(record: AnswerRecord) = ContentValues().apply {
        put("id", record.id); put("intent", record.intent); put("title", record.title); put("category", record.category)
        put("revision", record.revision); put("origin", record.origin.name); put("deleted", if (record.deleted) 1 else 0)
        put("payload", CatalogueJson.encode(record).toString())
    }
    private fun write(db: SQLiteDatabase, record: AnswerRecord) {
        // Avoid REPLACE: it would delete the parent and undermine version history/foreign keys.
        if (db.update("answers", values(record), "id=?", arrayOf(record.id)) == 0) db.insertOrThrow("answers", null, values(record))
        db.delete("variants", "answer_id=?", arrayOf(record.id)); db.delete("phrases", "answer_id=?", arrayOf(record.id)); db.delete("phrase_search", "answer_id=?", arrayOf(record.id))
        if (!record.deleted) {
            record.variants.forEachIndexed { index, text -> db.insertOrThrow("variants", null, ContentValues().apply { put("answer_id", record.id); put("position", index); put("text", text) }) }
            record.examples.forEach { example ->
                val normalized = FrenchForms.of(example).text
                require(normalized.isNotBlank()) { "Formulation sans contenu reconnaissable" }
                db.insertOrThrow("phrases", null, ContentValues().apply { put("answer_id", record.id); put("raw", example); put("normalized", normalized) })
                db.insertOrThrow("phrase_search", null, ContentValues().apply { put("answer_id", record.id); put("normalized", normalized) })
            }
        }
        db.insertOrThrow("revisions", null, ContentValues().apply { put("answer_id", record.id); put("revision", record.revision); put("payload", CatalogueJson.encode(record).toString()) })
    }
    private fun get(db: SQLiteDatabase, id: String): AnswerRecord? = db.rawQuery("SELECT payload FROM answers WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) CatalogueJson.decode(org.json.JSONObject(it.getString(0))) else null }
    @Synchronized fun get(id: String): AnswerRecord? = get(database(), id)
    @Synchronized fun count(): Int = database().rawQuery("SELECT count(*) FROM answers WHERE deleted=0", null).use { it.moveToFirst(); it.getInt(0) }
    @Synchronized fun list(offset: Int = 0, limit: Int = 50): List<AnswerRecord> {
        require(offset >= 0 && limit in 1..50)
        return database().rawQuery("SELECT payload FROM answers WHERE deleted=0 ORDER BY category,id LIMIT ? OFFSET ?", arrayOf(limit.toString(), offset.toString())).use { c -> buildList { while (c.moveToNext()) add(CatalogueJson.decode(org.json.JSONObject(c.getString(0)))) } }
    }
    @Synchronized fun merge(records: List<AnswerRecord>, cancelled: () -> Boolean = { false }): Int {
        require(records.size <= CatalogueJson.MAX_RECORDS && records.map { it.id }.distinct().size == records.size)
        val db = database(true); var changed = 0
        db.beginTransaction()
        try {
            records.forEach { incoming ->
                check(!cancelled()) { "Opération arrêtée" }
                val current = get(db, incoming.id)
                require(current == null || incoming.revision >= current.revision) { "Révision obsolète : ${incoming.id}" }
                require(current == null || incoming.revision != current.revision || incoming == current) { "Même révision, contenu différent : ${incoming.id}" }
                if (incoming != current) { write(db, incoming); changed++ }
            }
            db.rawQuery("SELECT count(*) FROM answers", null).use { it.moveToFirst(); require(it.getInt(0) <= CatalogueJson.MAX_RECORDS) { "Maximum 5 000 identifiants, suppressions incluses" } }
            db.rawQuery("SELECT sum(length(CAST(payload AS BLOB))) FROM answers", null).use { it.moveToFirst(); require(it.getLong(0) <= CatalogueJson.MAX_BYTES - 64 * 1024) { "Catalogue courant trop volumineux pour un export réimportable" } }
            check(!cancelled()) { "Opération arrêtée" }; db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return changed
    }
    @Synchronized fun save(record: AnswerRecord, expectedRevision: Int?) {
        val db = database(true); db.beginTransaction()
        try {
            val current = get(db, record.id)
            require(current?.revision == expectedRevision && record.revision == (expectedRevision ?: 0) + 1) { "Réponse modifiée ailleurs ; rechargez-la" }
            merge(listOf(record)); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun delete(id: String, expectedRevision: Int) {
        val current = get(id) ?: error("Réponse absente")
        save(current.copy(revision = current.revision + 1, deleted = true), expectedRevision)
    }
    @Synchronized fun export(output: java.io.OutputStream, cancelled: () -> Boolean = { false }) {
        // Stream one record at a time; do not materialize the entire database for export.
        val db = database()
        db.beginTransaction()
        try {
            output.write("{\"formatVersion\":1,\"answers\":[".toByteArray())
            db.rawQuery("SELECT payload FROM answers ORDER BY id", null).use { c ->
                var first = true
                while (c.moveToNext()) {
                    check(!cancelled()) { "Opération arrêtée" }
                    if (!first) output.write(','.code); first = false
                    output.write(c.getString(0).toByteArray(Charsets.UTF_8))
                }
            }
            output.write("]}".toByteArray())
            check(!cancelled()) { "Opération arrêtée" }; db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized override fun candidates(question: String, limit: Int): CandidatePage {
        require(limit in 1..20)
        val form = FrenchForms.of(question); if (form.words.isEmpty() || form.words.size > 32) return CandidatePage(emptyList())
        val db = database()
        val ids = linkedSetOf<String>()
        db.rawQuery("SELECT DISTINCT p.answer_id FROM phrases p JOIN answers a ON a.id=p.answer_id WHERE p.normalized=? AND a.deleted=0 LIMIT ?", arrayOf(form.text, (limit+1).toString())).use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
        val expression = form.words.distinct().joinToString(" AND ") { "\"$it\"" }
        // Exact editorial formulations take priority; otherwise bounded conjunctive FTS retrieval.
        if (ids.isEmpty()) db.rawQuery("SELECT DISTINCT answer_id FROM phrase_search WHERE phrase_search MATCH ? LIMIT ?", arrayOf(expression, (limit+1).toString())).use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
        if (ids.size > limit) return CandidatePage(emptyList(), true)
        val records = ids.mapNotNull { get(db, it) }.filterNot { it.deleted }.toMutableList()
        // A same-intent conflict must remain visible even if its formulations differ completely.
        for (intent in records.map { it.intent }.distinct()) {
            db.rawQuery("SELECT payload FROM answers WHERE intent=? AND deleted=0 LIMIT ?", arrayOf(intent, (limit+1).toString())).use { c -> while (c.moveToNext()) {
                val record = CatalogueJson.decode(org.json.JSONObject(c.getString(0)))
                if (records.none { it.id == record.id }) records.add(record)
            } }
            if (records.size > limit) return CandidatePage(emptyList(), true)
        }
        return CandidatePage(records)
    }
    @Synchronized fun stats(): String {
        val db = database()
        fun scalar(sql: String) = db.rawQuery(sql, null).use { it.moveToFirst(); it.getLong(0) }
        return "Catalogue : ${scalar("SELECT count(*) FROM answers WHERE deleted=0")} réponses actives ; ${scalar("SELECT count(*) FROM revisions")} révisions ; SQLite ${scalar("PRAGMA page_count") * scalar("PRAGMA page_size") / 1024} Kio ; cache pages cible 2 Mio. Embeddings désactivés."
    }
    @Synchronized override fun close() { closed = true; helper.close() }
}
