package cz.jan.conversationreader

data class SearchMatch(val message: Int, val version: Int, val part: Int, val start: Int, val end: Int)

fun matchRanges(text: CharSequence, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
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
