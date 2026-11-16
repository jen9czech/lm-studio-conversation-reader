package cz.jan.conversationreader

data class ThinkingText(val text: String, val thinking: Boolean)

/** Saved thinking can redundantly include model delimiters or a whole-response Markdown fence. */
fun thinkingMarkdown(text: String): String {
    val unwrapped = splitThinkingText(text).joinToString("") { it.text }
    val trimmed = unwrapped.trim()
    val firstBreak = trimmed.indexOf('\n')
    if (firstBreak < 0) return normalizeThinkingLists(unwrapped)
    val opener = trimmed.substring(0, firstBreak).trimEnd('\r')
    val fence = opener.takeWhile { it == '`' || it == '~' }
    val language = opener.removePrefix(fence).trim().lowercase()
    if (fence.length >= 3 && fence.all { it == fence.first() } && language in setOf("md", "markdown")) {
        val lastBreak = trimmed.lastIndexOf('\n')
        if (lastBreak > firstBreak && trimmed.substring(lastBreak + 1).trim() == fence) {
            return normalizeThinkingLists(trimmed.substring(firstBreak + 1, lastBreak))
        }
    }
    return normalizeThinkingLists(unwrapped)
}

private val thinkingListStart = Regex("^[ \\t]*(?:[-+*][ \\t]+|[0-9]+[.)][ \\t]+)")
private val thinkingFence = Regex("^(`{3,}|~{3,})(.*)$")

/** Model-written plans often over-indent a standalone list, turning emphasis into code. */
fun normalizeThinkingLists(text: String): String {
    var base: Int? = null
    var fence: String? = null
    fun indentation(line: String) = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 4 else 1 }
    fun removeIndent(line: String, columns: Int): String {
        var consumed = 0
        var index = 0
        while (index < line.length && consumed < columns && (line[index] == ' ' || line[index] == '\t')) {
            consumed += if (line[index] == '\t') 4 else 1
            index++
        }
        return if (consumed >= columns) " ".repeat(consumed - columns) + line.substring(index) else line
    }
    return text.split('\n').joinToString("\n") { line ->
        val indent = indentation(line)
        if (fence == null && line.isNotBlank()) {
            if (thinkingListStart.containsMatchIn(line)) {
                if (base == null || indent < base!!) base = if (indent >= 4) indent else 0
            } else if (indent == 0 || (base != null && indent < base!!)) base = null
        }
        val normalized = if (base != null && base!! > 0) removeIndent(line, base!!) else line
        val marker = thinkingFence.matchEntire(normalized.trim())
        if (marker != null) {
            val ticks = marker.groupValues[1]
            if (fence == null) fence = ticks
            else if (ticks.first() == fence!!.first() && ticks.length >= fence!!.length && marker.groupValues[2].isBlank()) fence = null
        }
        normalized
    }
}

/** Only assistant text is passed here. Literal tags inside Markdown code stay literal. */
fun splitThinkingText(text: String): List<ThinkingText> {
    val result = mutableListOf<ThinkingText>()
    var start = 0
    var index = 0
    var thinkingTag: String? = null
    var fence: Char? = null
    var fenceLength = 0
    var inlineLength = 0
    var lineStart = 0
    fun emit(end: Int) {
        if (end > start) result += ThinkingText(text.substring(start, end), thinkingTag != null)
    }
    while (index < text.length) {
        val character = text[index]
        val atLineStart = index - lineStart <= 3 && text.substring(lineStart, index).all { it == ' ' }
        if ((character == '`' || character == '~') && atLineStart && inlineLength == 0) {
            var end = index
            while (end < text.length && text[end] == character) end++
            val length = end - index
            if (length >= 3) {
                if (fence == null) { fence = character; fenceLength = length }
                else if (fence == character && length >= fenceLength && text.substring(end, text.indexOf('\n', end).takeIf { it >= 0 } ?: text.length).isBlank()) fence = null
                index = end
                continue
            }
        }
        if (fence == null && character == '`' && (index == 0 || text[index - 1] != '\\')) {
            var end = index
            while (end < text.length && text[end] == '`') end++
            val length = end - index
            if (inlineLength == 0) inlineLength = length else if (inlineLength == length) inlineLength = 0
            index = end
            continue
        }
        if (fence == null && inlineLength == 0 && character == '<' && (index == 0 || text[index - 1] != '\\')) {
            val end = (index + 1 until minOf(index + 15, text.length)).firstOrNull { text[it] == '>' } ?: -1
            if (end in index + 1..index + 14) {
                val tag = text.substring(index + 1, end).trim().lowercase()
                val closing = tag.startsWith('/')
                val name = tag.removePrefix("/")
                if (name in setOf("think", "thinking", "analysis", "reasoning") && ((!closing && thinkingTag == null) || (closing && thinkingTag == name))) {
                    emit(index)
                    thinkingTag = if (closing) null else name
                    start = end + 1
                    index = start
                    continue
                }
            }
        }
        if (character == '\n') lineStart = index + 1
        index++
    }
    emit(text.length)
    return result.filter { it.text.isNotBlank() }.ifEmpty { listOf(ThinkingText(if (result.isEmpty()) "" else result.joinToString("") { it.text }, false)) }
}
