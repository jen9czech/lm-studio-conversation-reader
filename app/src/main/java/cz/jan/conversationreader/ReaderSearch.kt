package cz.jan.conversationreader

data class SearchMatch(val message: Int, val version: Int, val part: Int, val start: Int, val end: Int)
private val combiningMarks = Regex("\\p{M}+")

internal fun normalizeReaderQuery(query: String): String =
    java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD).replace(combiningMarks, "")

internal class SearchableReaderText(text: CharSequence) {
    private val normalized: String
    private val offsets: IntArray?

    init {
        if (text.all { it.code < 128 }) {
            normalized = text.toString()
            offsets = null
        } else {
            val mapped = StringBuilder(text.length)
            val indexes = mutableListOf<Int>()
            text.forEachIndexed { index, character ->
                val folded = if (character.code < 128) character.toString() else normalizeReaderQuery(character.toString())
                mapped.append(folded)
                repeat(folded.length) { indexes.add(index) }
            }
            normalized = mapped.toString()
            offsets = if (indexes.size == text.length && indexes.indices.all { indexes[it] == it }) null else indexes.toIntArray()
        }
    }

    fun ranges(normalizedQuery: String): List<IntRange> = matchRanges(normalized, normalizedQuery).let { ranges ->
        val map = offsets ?: return ranges
        ranges.map { map[it.first]..map[it.last] }
    }
}

internal fun SearchMatch.rangeIn(start: Int, end: Int): IntRange? {
    val first = maxOf(this.start, start)
    val limit = minOf(this.end, end)
    return if (first < limit) (first - start) until (limit - start) else null
}

fun matchRanges(text: CharSequence, query: String, ignoreDiacritics: Boolean = false): List<IntRange> {
    if (query.isBlank()) return emptyList()
    if (ignoreDiacritics) return SearchableReaderText(text).ranges(normalizeReaderQuery(query))
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
