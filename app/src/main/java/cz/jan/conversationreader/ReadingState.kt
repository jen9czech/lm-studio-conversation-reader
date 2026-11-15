package cz.jan.conversationreader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ReadingAnchor(val message: Int, val version: Int = 0, val part: Int = 0, val character: Int = 0) {
    fun json(): String = JSONObject().put("message", message).put("version", version).put("part", part).put("character", character).toString()
    companion object { fun parse(raw: String?): ReadingAnchor? = runCatching { JSONObject(raw ?: return null).let { ReadingAnchor(it.getInt("message"), it.optInt("version"), it.optInt("part"), it.optInt("character")) } }.getOrNull() }
}
data class RecentReading(val reference: String, val title: String, val excerpt: String, val openedAt: Long)
data class ReaderStyle(val font: String = "Sans-serif") {
    val lineSpacing get() = 1f
    val margin get() = 4
}

class ReadingState(private val preferences: SharedPreferences) {
    fun anchor(id: String) = ReadingAnchor.parse(preferences.getString("anchor_$id", null))
    fun saveAnchor(id: String, anchor: ReadingAnchor) { preferences.edit().putString("anchor_$id", anchor.json()).apply() }
    // Explicit new values override legacy data, so removing a migrated star stays removed.
    fun isBookmarked(id: String): Boolean = if (preferences.contains("bookmarked_$id")) preferences.getBoolean("bookmarked_$id", false)
        else preferences.getBoolean("favorite_$id", false) || runCatching { JSONArray(preferences.getString("bookmarks_$id", "[]")).length() > 0 }.getOrDefault(false)
    fun setBookmarked(id: String, bookmarked: Boolean) { preferences.edit().putBoolean("bookmarked_$id", bookmarked).apply() }
    fun style() = ReaderStyle(when (preferences.getString("readingFont", "Sans-serif")) { "Serif" -> "Serif"; "Mono", "Monospaced" -> "Monospaced"; else -> "Sans-serif" })
    fun saveStyle(style: ReaderStyle) { preferences.edit().putString("readingFont", style.font).apply() }
    fun recent(): List<RecentReading> = runCatching {
        val array = JSONArray(preferences.getString("readingRecent", "[]"))
        (0 until array.length()).map { array.getJSONObject(it).let { r -> RecentReading(r.getString("reference"), r.getString("title"), r.optString("excerpt"), r.getLong("openedAt")) } }
    }.getOrDefault(emptyList())
    fun record(entry: LibraryEntry, reference: String, excerpt: String = "") {
        val old = recent()
        val item = RecentReading(reference, entry.conversation.title, excerpt.ifBlank { old.firstOrNull { it.reference == reference }?.excerpt.orEmpty() }.take(180), System.currentTimeMillis())
        val array = JSONArray()
        val id = runCatching { JSONObject(reference).optString("id") }.getOrDefault(reference)
        (listOf(item) + old.filterNot { runCatching { JSONObject(it.reference).optString("id") }.getOrDefault(it.reference) == id }).take(30).forEach { r -> array.put(JSONObject().put("reference", r.reference).put("title", r.title).put("excerpt", r.excerpt).put("openedAt", r.openedAt)) }
        preferences.edit().putString("readingRecent", array.toString()).apply()
    }
    fun bookmarkedIds(): Set<String> = preferences.all.keys.mapNotNull { key ->
        when { key.startsWith("bookmarked_") -> key.removePrefix("bookmarked_"); key.startsWith("favorite_") -> key.removePrefix("favorite_"); key.startsWith("bookmarks_") -> key.removePrefix("bookmarks_"); else -> null }
    }.toSet().filterTo(mutableSetOf()) { isBookmarked(it) }
}
