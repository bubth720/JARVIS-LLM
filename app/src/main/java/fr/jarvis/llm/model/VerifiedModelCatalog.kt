package fr.jarvis.llm.model

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import fr.jarvis.domain.ModelKind
import fr.jarvis.domain.model.ModelDescriptor

/** Separate, additive database. Never opens the response catalogue or historical model preferences. */
class VerifiedModelCatalog(context: Context, name: String = "verified-models-v1.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE models(sha TEXT PRIMARY KEY,size INTEGER NOT NULL,kind TEXT NOT NULL,architecture TEXT NOT NULL,quantization TEXT NOT NULL,license TEXT NOT NULL,source TEXT NOT NULL,verified INTEGER NOT NULL,capabilities TEXT NOT NULL)")
        db.execSQL("CREATE TABLE selection(singleton INTEGER PRIMARY KEY CHECK(singleton=1),sha TEXT REFERENCES models(sha))")
    }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Migration non autorisée") }
    @Synchronized fun record(model: ModelDescriptor) {
        val values = ContentValues().apply {
            put("sha", model.sha256); put("size", model.sizeBytes); put("kind", model.kind.name)
            put("architecture", model.architecture); put("quantization", model.quantization)
            put("license", model.license); put("source", model.source); put("verified", model.verifiedAtMillis)
            put("capabilities", model.capabilities.sorted().joinToString(","))
        }
        // Identical content is one identity; do not overwrite provenance silently on duplicates.
        writableDatabase.insertWithOnConflict("models", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
    @Synchronized fun list(): List<ModelDescriptor> = readableDatabase.rawQuery("SELECT * FROM models ORDER BY sha", null).use { c ->
        buildList { while (c.moveToNext()) {
            fun s(k: String) = c.getString(c.getColumnIndexOrThrow(k))
            add(ModelDescriptor(s("sha"), c.getLong(c.getColumnIndexOrThrow("size")), ModelKind.valueOf(s("kind")),
                s("architecture"), s("quantization"), s("license"), s("source"), c.getLong(c.getColumnIndexOrThrow("verified")),
                s("capabilities").split(',').filter { it.isNotEmpty() }.toSet()))
        } }
    }
    @Synchronized fun selected(): String? = readableDatabase.rawQuery("SELECT sha FROM selection WHERE singleton=1", null).use { if (it.moveToFirst()) it.getString(0) else null }
    @Synchronized fun select(sha: String?) {
        require(sha == null || list().any { it.sha256 == sha }) { "Modèle absent du catalogue" }
        val v = ContentValues().apply { put("singleton", 1); put("sha", sha) }
        if (selectedRowExists()) writableDatabase.update("selection", v, "singleton=1", null)
        else writableDatabase.insertOrThrow("selection", null, v)
    }
    private fun selectedRowExists(): Boolean = readableDatabase.rawQuery("SELECT 1 FROM selection", null).use { it.moveToFirst() }
    @Synchronized fun removeReference(sha: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("selection", "sha=?", arrayOf(sha))
            db.delete("models", "sha=?", arrayOf(sha))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
