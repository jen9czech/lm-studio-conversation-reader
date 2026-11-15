package cz.jan.conversationreader

data class SearchMatch(val message: Int, val version: Int, val part: Int, val start: Int, val end: Int)
private val combiningMarks = Regex("\\p{M}+")

fun matchRanges(text: CharSequence, query: String, ignoreDiacritics: Boolean = false): List<IntRange> {
    if (query.isBlank()) return emptyList()
    if (ignoreDiacritics) {
        val mapped = StringBuilder()
        val indexes = mutableListOf<Int>()
        text.forEachIndexed { index, character ->
            val normalized = if (character.code < 128) character.toString() else java.text.Normalizer.normalize(character.toString(), java.text.Normalizer.Form.NFD).replace(combiningMarks, "")
            normalized.forEach { mapped.append(it); indexes += index }
        }
        val normalizedQuery = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD).replace(combiningMarks, "")
        if (normalizedQuery.isBlank()) return emptyList()
        return matchRanges(mapped, normalizedQuery).map { indexes[it.first]..indexes[it.last] }
    }
    val result = mutableListOf<IntRange>()
    var start = text.indexOf(query, ignoreCase = true)
    while (start >= 0) {
        result += start until start + query.length
        start = text.indexOf(query, start + query.length, ignoreCase = true)
    }
    return result
}

fun findMatches(texts: List<List<List<CharSequence>>>, query: String): List<SearchMatch> = buildList {
    texts.forEachIndexed { message, versions -> versions.forEachIndexed { version, parts ->
        parts.forEachIndexed { part, text -> matchRanges(text, query).forEach { range ->
            add(SearchMatch(message, version, part, range.first, range.last + 1))
        } }
    } }
}

/** Approximate progress by text weight, including the scrolled fraction of a long message. */
fun readingProgress(weights: List<Int>, index: Int, fraction: Float, atStart: Boolean, atEnd: Boolean): Int {
    if (weights.isEmpty() || atStart) return 0
    if (atEnd) return 100
    val safe = weights.map { it.coerceAtLeast(1).toLong() }
    val i = index.coerceIn(safe.indices)
    return ((safe.take(i).sum() + safe[i] * fraction.coerceIn(0f, 1f)) / safe.sum() * 100).toInt().coerceIn(0, 99)
}
