package cz.jan.conversationreader

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import io.noties.markwon.Markwon
import org.commonmark.node.*

data class TextSlice(val start: Int, val end: Int)

fun textSlices(text: CharSequence, maxChars: Int = 1800): List<TextSlice> {
    if (text.isEmpty()) return listOf(TextSlice(0, 0))
    val source = text.toString()
    val result = mutableListOf<TextSlice>()
    var start = 0
    while (start < source.length) {
        var end = (start + maxChars).coerceAtMost(source.length)
        if (end < source.length) {
            val paragraph = source.lastIndexOf("\n\n", end)
            val line = source.lastIndexOf('\n', end)
            val space = source.lastIndexOf(' ', end)
            end = when { paragraph > start + maxChars / 3 -> paragraph + 2; line > start + maxChars / 3 -> line + 1; space > start -> space + 1; else -> end }
            // Do not cut a Unicode surrogate pair.
            if (end > 0 && end < source.length && source[end - 1].isHighSurrogate() && source[end].isLowSurrogate()) end--
        }
        if (text is Spanned && end < source.length) text.getSpans(start, end, ReplacementSpan::class.java).forEach { span ->
            if (text.getSpanStart(span) < end && text.getSpanEnd(span) > end) end = text.getSpanEnd(span)
        }
        result += TextSlice(start, end)
        start = end
    }
    return result
}

data class ReadingBlock(val message: Int, val version: Int, val part: Int, val start: Int, val end: Int, val text: Spanned) {
    val key get() = "$message:$version:$part:$start"
    val anchor get() = ReadingAnchor(message, version, part, start)
}
data class OutlineEntry(val label: String, val level: Int, val anchor: ReadingAnchor, val prompt: Boolean = false)
data class RenderedPart(val text: Spanned, val blocks: List<ReadingBlock>, val headings: List<OutlineEntry>, val code: List<String> = emptyList())
data class RenderedVersion(val parts: List<RenderedPart>)

/** Background-only parser cache. View binding uses a separate renderer and only visible blocks. */
class ReadingRenderCache(private val conversation: Conversation, private val renderer: Markwon) {
    private val cache = object : LinkedHashMap<Pair<Int, Int>, RenderedVersion>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, RenderedVersion>?) = size > 32
    }
    @Synchronized fun version(message: Int, version: Int): RenderedVersion = cache.getOrPut(message to version) {
        val value = conversation.messages[message].versions[version]
        RenderedVersion(value.parts.mapIndexed { part, content ->
            val node = if (value.role == "user") null else renderer.parse(content.text)
            val text = if (node == null) SpannableStringBuilder(content.text) else renderer.render(node)
            val renderedText = text.toString()
            val blocks = textSlices(text).map { slice -> ReadingBlock(message, version, part, slice.start, slice.end, SpannableStringBuilder(text, slice.start, slice.end)) }
            val headings = mutableListOf<OutlineEntry>()
            val code = mutableListOf<String>()
            var from = 0
            node?.accept(object : AbstractVisitor() {
                override fun visit(block: FencedCodeBlock) { code += block.literal }
                override fun visit(block: IndentedCodeBlock) { code += block.literal }
                override fun visit(heading: Heading) {
                    val label = StringBuilder()
                    heading.accept(object : AbstractVisitor() {
                        override fun visit(text: Text) { label.append(text.literal) }
                        override fun visit(code: Code) { label.append(code.literal) }
                        override fun visit(softLineBreak: SoftLineBreak) { label.append(' ') }
                    })
                    val title = label.toString()
                    val position = renderedText.indexOf(title, from).takeIf { it >= 0 } ?: from
                    from = position + title.length
                    headings += OutlineEntry(title, heading.level, ReadingAnchor(message, version, part, position))
                }
            })
            RenderedPart(text, blocks, headings, code)
        })
    }
}

/** One pass even when a prompt has thousands of assistant messages. */
fun promptGroups(conversation: Conversation): List<Int> {
    var prompt = -1
    return conversation.messages.mapIndexed { index, message ->
        if (message.versions[message.selected].role == "user") prompt = index
        if (prompt >= 0) prompt else index
    }
}

data class ReaderRow(val key: String, val message: Int, val version: Int, val kind: String, val block: ReadingBlock? = null) {
    val anchor get() = block?.anchor ?: ReadingAnchor(message, version)
}

fun readerRows(conversation: Conversation, versions: Map<Int, Int>, rendered: Map<Int, RenderedVersion>, groups: List<Int>, expanded: List<Int>, collapsed: Boolean): List<ReaderRow> = buildList {
    conversation.messages.forEachIndexed { index, message ->
        val version = versions[index] ?: message.selected
        val group = groups[index]
        if (collapsed && group !in expanded) {
            if (index == group) add(ReaderRow("preview:$index", index, version, "preview"))
        } else {
            add(ReaderRow("header:$index:$version", index, version, "header"))
            rendered[index]?.parts?.forEach { part -> part.blocks.filter { it.version == version }.forEach { add(ReaderRow("text:${it.key}", index, version, "text", it)) } }
            add(ReaderRow("footer:$index:$version", index, version, "footer"))
        }
    }
}

fun rowForAnchor(rows: List<ReaderRow>, anchor: ReadingAnchor): Int {
    val exact = rows.indexOfFirst { row -> row.block?.let { it.message == anchor.message && it.version == anchor.version && it.part == anchor.part && anchor.character >= it.start && (anchor.character < it.end || it.start == it.end) } == true }
    return if (exact >= 0) exact else rows.indexOfFirst { it.message == anchor.message }.coerceAtLeast(0)
}
