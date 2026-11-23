package cz.jan.conversationreader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

data class LibrarySearchHit(val id: String, val snippet: String, val highlights: List<IntRange> = emptyList())

/** Search-only index: never adds a linked conversation to Imported or modifies its source. */
class LibrarySearchIndex(context: Context) : SQLiteOpenHelper(context.applicationContext, "conversation-search.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE document_text USING fts4(document_key, title, body, tokenize=unicode61, notindexed=document_key)")
        createImportMetadata(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS document_text")
            onCreate(db)
        } else if (oldVersion < 3) createImportMetadata(db)
        if (oldVersion == 3) db.execSQL("ALTER TABLE import_metadata ADD COLUMN prompts INTEGER")
    }

    private fun createImportMetadata(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE import_metadata (id TEXT PRIMARY KEY, modified INTEGER NOT NULL, size INTEGER NOT NULL, title TEXT NOT NULL, created INTEGER, words INTEGER NOT NULL, models TEXT NOT NULL, prompts INTEGER)")
    }

    internal fun importedMetadata(): Map<String, ImportMetadata> =
        readableDatabase.query("import_metadata", arrayOf("id", "modified", "size", "title", "created", "words", "models", "prompts"), null, null, null, null, null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val models = JSONArray(cursor.getString(6))
                    put(cursor.getString(0), ImportMetadata(cursor.getLong(1), cursor.getLong(2), cursor.getString(3),
                        if (cursor.isNull(4)) null else cursor.getLong(4), cursor.getInt(5),
                        List(models.length()) { models.getString(it) }, if (cursor.isNull(7)) null else cursor.getInt(7)))
                }
            }
        }

    internal fun importWords(id: String, modified: Long, size: Long): Int? =
        readableDatabase.query("import_metadata", arrayOf("words"), "id=? AND modified=? AND size=?",
            arrayOf(id, modified.toString(), size.toString()), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }
    @Synchronized fun contains(id: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM document_text WHERE document_key=? LIMIT 1", arrayOf(id)).use { it.moveToFirst() }
    @Synchronized fun put(id: String, conversation: Conversation) = putDocument(id, conversation, null)

    @Synchronized internal fun putImport(id: String, conversation: Conversation, metadata: ImportMetadata) =
        putDocument(id, conversation, metadata)

    private fun putDocument(id: String, conversation: Conversation, metadata: ImportMetadata?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("document_text", "document_key=?", arrayOf(id))
            db.insertOrThrow("document_text", null, ContentValues().apply { put("document_key", id); put("title", conversation.title); put("body", conversation.searchable) })
            if (metadata != null) {
                db.replaceOrThrow("import_metadata", null, ContentValues().apply {
                    put("id", id); put("modified", metadata.modified); put("size", metadata.size)
                    put("title", metadata.title); put("created", metadata.createdAt); put("words", metadata.words)
                    put("models", JSONArray(metadata.models).toString()); put("prompts", metadata.prompts)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun remove(id: String) { writableDatabase.delete("document_text", "document_key=?", arrayOf(id)) }
    @Synchronized internal fun retainLinkedDocuments(activeIds: Set<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val obsolete = db.rawQuery("SELECT rowid, document_key FROM document_text WHERE document_key GLOB 'linked_*'", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1) !in activeIds) add(cursor.getLong(0))
                    }
                }
            }
            obsolete.forEach { db.delete("document_text", "rowid=?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized internal fun removeImport(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("document_text", "document_key=?", arrayOf(id))
            db.delete("import_metadata", "id=?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private val queryTerms = Regex("[\\p{L}\\p{M}\\p{N}]+")
    @Synchronized fun search(query: String): Map<String, LibrarySearchHit> {
        val terms = queryTerms.findAll(query).map { it.value }.toList()
        if (terms.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, LibrarySearchHit>()
        val marker = java.util.UUID.randomUUID().toString()
        val start = "$marker["
        val end = "$marker]"
        val phrase = "\"${terms.joinToString(" ")}*\""
        readableDatabase.rawQuery(
            "SELECT document_key, snippet(document_text, ?, ?, ' … ', 2, 24) FROM document_text WHERE document_text MATCH ?",
            arrayOf(start, end, phrase)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val marked = cursor.getString(1).orEmpty()
                val text = StringBuilder()
                val highlights = mutableListOf<IntRange>()
                var offset = 0
                while (offset < marked.length) {
                    val opening = marked.indexOf(start, offset)
                    val closing = if (opening < 0) -1 else marked.indexOf(end, opening + start.length)
                    if (closing < 0) {
                        text.append(marked, offset, marked.length)
                        break
                    }
                    text.append(marked, offset, opening)
                    val first = text.length
                    text.append(marked, opening + start.length, closing)
                    if (first < text.length) highlights += first until text.length
                    offset = closing + end.length
                }
                result[id] = LibrarySearchHit(id, text.toString(), highlights)
            }
        }
        return result
    }
}
