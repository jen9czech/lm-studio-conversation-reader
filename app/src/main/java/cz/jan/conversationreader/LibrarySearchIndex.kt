package cz.jan.conversationreader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class LibrarySearchHit(val id: String, val snippet: String)

/** Search-only index: never adds a linked conversation to Imported or modifies its source. */
class LibrarySearchIndex(context: Context) : SQLiteOpenHelper(context.applicationContext, "conversation-search.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE document_text USING fts4(document_key, title, body, tokenize=unicode61, notindexed=document_key)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { db.execSQL("DROP TABLE IF EXISTS document_text"); onCreate(db) }
    @Synchronized fun contains(id: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM document_text WHERE document_key=? LIMIT 1", arrayOf(id)).use { it.moveToFirst() }
    @Synchronized fun put(id: String, conversation: Conversation) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("document_text", "document_key=?", arrayOf(id))
            db.insertOrThrow("document_text", null, ContentValues().apply { put("document_key", id); put("title", conversation.title); put("body", conversation.searchable) })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun remove(id: String) { writableDatabase.delete("document_text", "document_key=?", arrayOf(id)) }
    private val queryTerms = Regex("[\\p{L}\\p{N}_]+")
    @Synchronized fun search(query: String): Map<String, LibrarySearchHit> {
        val terms = queryTerms.findAll(query).map { it.value }.toList()
        if (terms.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, LibrarySearchHit>()
        readableDatabase.rawQuery("SELECT document_key, snippet(document_text, '', '', ' … ', 2, 24) FROM document_text WHERE document_text MATCH ?", arrayOf("\"${terms.joinToString(" ")}\"" + if (terms.size == 1) "*" else "")).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = LibrarySearchHit(cursor.getString(0), cursor.getString(1).orEmpty())
        }
        return result
    }
}
