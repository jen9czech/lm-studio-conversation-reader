package cz.jan.conversationreader

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import io.noties.markwon.Markwon
import org.commonmark.node.*

data class TextSlice(val start: Int, val end: Int)

fun textSlices(text: CharSequence, maxChars: Int = 1800): List<TextSlice> {
    require(maxChars > 0)
    if (text.isEmpty()) return listOf(TextSlice(0, 0))
    val source = text.toString()
    val result = mutableListOf<TextSlice>()
    var start = 0
    while (start < source.length) {
        var end = (start + maxChars).coerceAtMost(source.length)
        if (end < source.length) {
            val preferredStart = start + maxChars / 3
            var line = -1
            var space = -1
            var paragraph = -1
            for (index in end downTo start + 1) {
                if (index > preferredStart && source[index] == '\n') {
                    if (line < 0) line = index
                    if (source.getOrNull(index + 1) == '\n') {
                        paragraph = index
                        break
                    }
                }
                if (space < 0 && source[index] == ' ') space = index
            }
            end = when { paragraph >= 0 -> paragraph + 2; line >= 0 -> line + 1; space >= 0 -> space + 1; else -> end }
            // Do not cut a Unicode surrogate pair.
            if (end < source.length && source[end - 1].isHighSurrogate() && source[end].isLowSurrogate()) {
                end += if (end - 1 == start) 1 else -1
            }
        }
        if (text is Spanned && end < source.length) text.getSpans(start, end, ReplacementSpan::class.java).forEach { span ->
            if (text.getSpanStart(span) < end && text.getSpanEnd(span) > end) end = text.getSpanEnd(span)
        }
        result += TextSlice(start, end)
        start = end
    }
    return result
}

data class ReadingBlock(val message: Int, val version: Int, val part: Int, val start: Int, val end: Int, val text: Spanned, val thinking: Boolean = false) {
    val key get() = "$message:$version:$part:$start"
    val anchor get() = ReadingAnchor(message, version, part, start)
}
data class OutlineEntry(val label: String, val level: Int, val anchor: ReadingAnchor, val prompt: Boolean = false)

internal fun currentOutlineIndex(outline: List<OutlineEntry>, position: ReadingAnchor): Int {
    val messageEnded = position.rowKey?.startsWith("footer:") == true
    return outline.indexOfLast { entry ->
        val anchor = entry.anchor
        anchor.message < position.message ||
            (anchor.message == position.message && anchor.version == position.version &&
                (messageEnded || compareValuesBy(anchor, position, ReadingAnchor::part, ReadingAnchor::character) <= 0))
    }
}

internal class ReadingHeading(val level: Int)
data class RenderedPart(val text: Spanned, val blocks: List<ReadingBlock>, val headings: List<OutlineEntry>, val code: List<String> = emptyList(), val thinking: Boolean = false)
data class RenderedVersion(val parts: List<RenderedPart>)

/** Background-only parser cache. View binding uses a separate renderer and only visible blocks. */
class ReadingRenderCache(private val conversation: Conversation, private val renderer: Markwon) {
    private val searchable = mutableMapOf<Pair<Int, Int>, List<SearchableReaderText>>()

    @Synchronized internal fun searchParts(message: Int, version: Int, alreadyRendered: RenderedVersion?): List<SearchableReaderText> =
        searchable.getOrPut(message to version) {
            (alreadyRendered ?: version(message, version)).parts.map { SearchableReaderText(it.text) }
        }

    private val cache = object : LinkedHashMap<Pair<Int, Int>, RenderedVersion>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, RenderedVersion>?) = size > 32
    }
    @Synchronized fun version(message: Int, version: Int): RenderedVersion = cache.getOrPut(message to version) {
        val value = conversation.messages[message].versions[version]
        RenderedVersion(value.parts.mapIndexed { part, content ->
            val node = if (value.role == "user") null else renderer.parse(content.text)
            val text = if (node == null) SpannableStringBuilder(content.text) else renderer.render(node)
            val blocks = textSlices(text).map { slice -> ReadingBlock(message, version, part, slice.start, slice.end, SpannableStringBuilder(text, slice.start, slice.end), content.thinking) }
            val headings = text.getSpans(0, text.length, ReadingHeading::class.java)
                .sortedBy(text::getSpanStart).map { heading ->
                    val start = text.getSpanStart(heading)
                    val title = text.subSequence(start, text.getSpanEnd(heading)).toString()
                    OutlineEntry(title, heading.level, ReadingAnchor(message, version, part, start))
                }
            val code = mutableListOf<String>()
            node?.accept(object : AbstractVisitor() {
                override fun visit(block: FencedCodeBlock) { code += block.literal }
                override fun visit(block: IndentedCodeBlock) { code += block.literal }
            })
            RenderedPart(text, blocks, headings, code, content.thinking)
        })
    }
}

/** One pass even when a prompt has thousands of assistant messages. */
fun promptGroups(conversation: Conversation, versions: Map<Int, Int> = emptyMap()): List<Int> {
    var prompt = -1
    return conversation.messages.mapIndexed { index, message ->
        if (message.versions[versions[index] ?: message.selected].role == "user") prompt = index
        if (prompt >= 0) prompt else index
    }
}

/** A prompt owns the thinking in its following replies; leading assistant messages own theirs. */
fun thinkingTargets(conversation: Conversation, versions: Map<Int, Int>, groups: List<Int>, owner: Int): List<ReadingAnchor> {
    val ownVersion = versions[owner] ?: conversation.messages[owner].selected
    val user = conversation.messages[owner].versions[ownVersion].role == "user"
    if (!user && groups[owner] != owner) return emptyList()
    val messages = if (user) (owner + 1 until conversation.messages.size).takeWhile { groups[it] == owner } else listOf(owner)
    return messages.flatMap { message ->
        val version = versions[message] ?: conversation.messages[message].selected
        conversation.messages[message].versions[version].parts.mapIndexedNotNull { part, value ->
            if (value.thinking && value.text.isNotBlank()) ReadingAnchor(message, version, part) else null
        }
    }
}

data class ReaderRow(val key: String, val message: Int, val version: Int, val kind: String, val block: ReadingBlock? = null, val part: Int = 0) {
    val anchor get() = block?.anchor ?: ReadingAnchor(message, version, part)
}

fun readerRows(conversation: Conversation, versions: Map<Int, Int>, rendered: Map<Int, RenderedVersion>, groups: List<Int>, expanded: List<Int>, collapsed: Boolean, expandedThinking: Set<String> = emptySet(), expandedPrompts: Set<String> = emptySet()): List<ReaderRow> = buildList {
    conversation.messages.forEachIndexed { index, message ->
        val version = versions[index] ?: message.selected
        val group = groups[index]
        if (collapsed && group !in expanded) {
            if (index == group) add(ReaderRow("preview:$index", index, version, "preview"))
        } else {
            val headerKey = if (collapsed && index == group) "preview:$index" else "header:$index:$version"
            add(ReaderRow(headerKey, index, version, "header"))
            val promptPreview = message.versions[version].role == "user" && "$index:$version" !in expandedPrompts
            if (promptPreview) {
                rendered[index]?.let { value ->
                    val text = SpannableStringBuilder(value.parts.joinToString("\n\n") { it.text })
                    val block = ReadingBlock(index, version, 0, 0, text.length, text)
                    add(ReaderRow("promptPreview:$index:$version", index, version, "promptPreview", block))
                }
            }
            if (!promptPreview) rendered[index]?.parts?.forEachIndexed { p, part ->
                if (part.blocks.firstOrNull()?.version == version) {
                    val thinkingKey = "$index:$version:$p"
                    if (part.thinking) add(ReaderRow("thinking:$thinkingKey", index, version, "thinking", part = p))
                    if (!part.thinking || thinkingKey in expandedThinking) part.blocks.forEach { add(ReaderRow("text:${it.key}", index, version, "text", it)) }
                    if (part.thinking && thinkingKey in expandedThinking) add(ReaderRow("thinkingEnd:$thinkingKey", index, version, "thinkingEnd", part = p))
                }
            }
            add(ReaderRow("footer:$index:$version", index, version, "footer"))
        }
    }
}

fun rowForAnchor(rows: List<ReaderRow>, anchor: ReadingAnchor): Int {
    val exact = rows.indexOfFirst { row -> row.block?.let { it.message == anchor.message && it.version == anchor.version && it.part == anchor.part && anchor.character >= it.start && (anchor.character < it.end || it.start == it.end) } == true }
    return if (exact >= 0) exact else rows.indexOfFirst { it.message == anchor.message }.coerceAtLeast(0)
}
