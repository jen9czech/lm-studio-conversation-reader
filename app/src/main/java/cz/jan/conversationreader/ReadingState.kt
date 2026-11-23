package cz.jan.conversationreader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ReadingAnchor(val message: Int, val version: Int = 0, val part: Int = 0, val character: Int = 0, val rowKey: String? = null, val scrollOffset: Int = 0, val layoutKey: String? = null, val openGroups: List<Int> = emptyList()) {
    fun json(): String = JSONObject().put("message", message).put("version", version).put("part", part).put("character", character).put("rowKey", rowKey).put("scrollOffset", scrollOffset).put("layoutKey", layoutKey).put("openGroups", JSONArray(openGroups)).toString()
    companion object { fun parse(raw: String?): ReadingAnchor? = runCatching { JSONObject(raw ?: return null).let { ReadingAnchor(it.getInt("message"), it.optInt("version"), it.optInt("part"), it.optInt("character"), it.optString("rowKey").takeIf(String::isNotEmpty), it.optInt("scrollOffset"), it.optString("layoutKey").takeIf(String::isNotEmpty), it.optJSONArray("openGroups")?.let { groups -> List(groups.length()) { i -> groups.getInt(i) } }.orEmpty()) } }.getOrNull() }
}
data class RecentReading(val reference: String, val title: String, val excerpt: String, val openedAt: Long)
internal fun readingReferenceId(reference: String): String? =
    runCatching { JSONObject(reference).getString("id") }.getOrNull()

data class ReaderStyle(val font: String = "Sans-serif") {
    val lineSpacing get() = 1f
    val margin get() = 20
}

class ReadingState(private val preferences: SharedPreferences) {
    fun collapsePrompts(): Boolean {
        // Apply the new opening default to existing installations once; later Settings choices remain explicit.
        if (!preferences.getBoolean("collapsedOpeningDefaultApplied", false)) {
            preferences.edit().putBoolean("collapsePrompts", true).putBoolean("collapsedOpeningDefaultApplied", true).apply()
        }
        return preferences.getBoolean("collapsePrompts", true)
    }
    fun anchor(id: String) = ReadingAnchor.parse(preferences.getString("anchor_$id", null))
    fun resumeAnchor(id: String) = ReadingAnchor.parse(preferences.getString("resumeAnchor_$id", null)) ?: anchor(id)
    fun saveResumeAnchor(id: String, anchor: ReadingAnchor) { preferences.edit().putString("resumeAnchor_$id", anchor.json()).apply() }
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
    @Synchronized fun record(entry: LibraryEntry, reference: String, excerpt: String = "") {
        val old = recent()
        val item = RecentReading(reference, entry.conversation.title, excerpt.ifBlank { old.firstOrNull { it.reference == reference }?.excerpt.orEmpty() }.take(180), System.currentTimeMillis())
        val id = readingReferenceId(reference) ?: reference
        val updated = (listOf(item) + old.filterNot { (readingReferenceId(it.reference) ?: it.reference) == id }).take(30)
        preferences.edit().putString("readingRecent", recentJson(updated)).apply()
    }

    @Synchronized internal fun removeImport(id: String) {
        val edit = preferences.edit()
        val recent = recent().filterNot { (readingReferenceId(it.reference) ?: it.reference) == id }
        edit.putString("readingRecent", recentJson(recent))
        listOf("anchor", "resumeAnchor", "readingProgress", "recent", "bookmarked", "favorite", "bookmarks",
            "manual", "filename", "uri").forEach { edit.remove("${it}_$id") }
        preferences.all.keys.filter { key ->
            key.removePrefix("version_${id}_").let { it != key && it.toIntOrNull() != null }
        }.forEach { edit.remove(it) }
        edit.apply()
    }

    private fun recentJson(items: List<RecentReading>): String {
        val array = JSONArray()
        items.forEach { r -> array.put(JSONObject().put("reference", r.reference).put("title", r.title).put("excerpt", r.excerpt).put("openedAt", r.openedAt)) }
        return array.toString()
    }
    fun bookmarkedIds(): Set<String> = preferences.all.keys.mapNotNull { key ->
        when { key.startsWith("bookmarked_") -> key.removePrefix("bookmarked_"); key.startsWith("favorite_") -> key.removePrefix("favorite_"); key.startsWith("bookmarks_") -> key.removePrefix("bookmarks_"); else -> null }
    }.toSet().filterTo(mutableSetOf()) { isBookmarked(it) }
}
