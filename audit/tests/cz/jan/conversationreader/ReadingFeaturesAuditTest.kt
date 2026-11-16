package cz.jan.conversationreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ReadingFeaturesAuditTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun sample() = ConversationParser.parse("""{"name":"Story","messages":[{"versions":[{"role":"assistant","content":[{"type":"text","text":"# Příběh\n\nA hidden needle here.\n\n## Chapter two\n\n```kotlin\nprintln(1)\n```"}]}]}]}""")

    @Test fun bookmarksMigrateAndUnstarReliablyAcrossStoreInstances() {
        val preferences = context.getSharedPreferences("feature-test", Context.MODE_PRIVATE)
        preferences.edit().clear().putBoolean("favorite_old", true).putString("bookmarks_passage", "[{\"id\":\"old\"}]").putFloat("lineSpacing", 2f).putInt("readingMargin", 40).putString("readingFont", "Mono").commit()
        val reading = ReadingState(preferences)
        assertEquals(setOf("old", "passage"), reading.bookmarkedIds())
        reading.setBookmarked("old", false)
        reading.setBookmarked("passage", false)
        assertTrue(ReadingState(preferences).bookmarkedIds().isEmpty())
        reading.setBookmarked("new", true)
        assertEquals(setOf("new"), ReadingState(preferences).bookmarkedIds())
        val anchor = ReadingAnchor(4, 1, 2, 3759)
        reading.saveAnchor("new", anchor)
        assertEquals(anchor, ReadingState(preferences).anchor("new"))
        assertEquals("Monospaced", reading.style().font)
        assertEquals(1f, reading.style().lineSpacing, 0f)
        assertEquals(4, reading.style().margin)
        reading.saveStyle(ReaderStyle("Serif"))
        assertEquals(ReaderStyle("Serif"), ReadingState(preferences).style())
        assertNull(ReadingAnchor.parse("broken"))
    }

    @Test fun indexFindsBodyAndCzechTextAndReplacesStaleContent() {
        context.deleteDatabase("conversation-search.db")
        LibrarySearchIndex(context).use { index ->
            index.put("linked_story", sample())
            assertTrue(index.search("needle").getValue("linked_story").snippet.contains("needle"))
            assertTrue(index.search("pribeh").containsKey("linked_story"))
            assertTrue(index.search("hidden needle").containsKey("linked_story"))
            assertTrue(index.search("needle hidden").isEmpty())
            assertTrue(index.search("linked").isEmpty())
            index.put("linked_story", ConversationParser.parse("""{"name":"Replacement","messages":[]}"""))
            assertTrue(index.search("needle").isEmpty())
            index.remove("linked_story")
            assertFalse(index.contains("linked_story"))
        }
    }

    @Test fun outlineUsesRenderedOffsetsAndCodeRemainsCopyable() {
        val rendered = ReadingRenderCache(sample(), createMarkdownRenderer(context)).version(0, 0)
        val part = rendered.parts.single()
        assertEquals(listOf("Příběh", "Chapter two"), part.headings.map { it.label })
        part.headings.forEach { assertTrue(part.text.substring(it.anchor.character).startsWith(it.label)) }
        assertEquals("println(1)\n", part.code.single())
        val rows = part.blocks.map { ReaderRow("text:${it.key}", 0, 0, "text", it) }
        assertEquals(0, rowForAnchor(rows, part.headings.last().anchor))
    }

    @Test fun recentConversationsDeduplicateByStableId() {
        val preferences = context.getSharedPreferences("recent-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val reading = ReadingState(preferences)
        val entry = LibraryEntry("one", sample(), 1)
        reading.record(entry, """{"id":"one","modified":1}""", "Saved passage")
        reading.record(entry, """{"id":"one","modified":2}""", "New passage")
        assertEquals(1, reading.recent().size)
        assertEquals("New passage", reading.recent().single().excerpt)
    }

    @Test fun longRepliesUseBoundedNativeViewsAndStableOffsets() {
        val source = "# Chapter\n\n" + "A **synthetic** paragraph with Czech text: Žluťoučký kůň.\n\n".repeat(3500)
        val conversation = Conversation("Long story", null, "", listOf(Message(listOf(MessageVersion("assistant", listOf(ReplyPart(source)), emptyList())), 0)))
        val part = ReadingRenderCache(conversation, createMarkdownRenderer(context)).version(0, 0).parts.single()
        assertTrue(part.blocks.size > 50)
        assertEquals(part.text.toString(), part.blocks.joinToString("") { it.text.toString() })
        listOf(part.blocks.first(), part.blocks[part.blocks.size / 2], part.blocks.last()).forEach { block ->
            val view = android.widget.TextView(context).apply { textSize = 17f; text = block.text; setLineSpacing(0f, 1.35f) }
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            assertTrue("Visible paragraph chunks should not create giant native views", view.measuredHeight < 4000)
        }
        val rows = part.blocks.map { ReaderRow("text:${it.key}", 0, 0, "text", it) }
        val middle = part.blocks.size / 2
        assertEquals(middle, rowForAnchor(rows, part.blocks[middle].anchor.copy(character = part.blocks[middle].start + 40)))
    }

    @Test fun iconUsesBrandPurpleWithTransparentRoundedCorners() {
        val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        context.getDrawable(R.drawable.ic_reader)!!.apply { setBounds(0, 0, 48, 48); draw(android.graphics.Canvas(bitmap)) }
        assertEquals(android.graphics.Color.parseColor("#6760CF"), bitmap.getPixel(24, 8))
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(4, 5)))
    }

    @Test fun markdownLinksHaveAnUnderlineBeyondTheirColor() {
        val text = createMarkdownRenderer(context).toMarkdown("Information and a [link](https://example.org).")
        val links = text.getSpans(0, text.length, io.noties.markwon.core.spans.LinkSpan::class.java)
        assertEquals(1, links.size)
        val paint = android.text.TextPaint().apply { linkColor = android.graphics.Color.parseColor("#6760CF") }
        links.single().updateDrawState(paint)
        assertTrue("Links need a non-color cue", paint.isUnderlineText)
        assertEquals(paint.linkColor, paint.color)
    }

    @Test fun bundledFontsHaveDistinctRenderedGlyphsAndCourierStyleFixedWidths() {
        val names = listOf("Sans-serif", "Serif", "Monospaced")
        val preview = android.graphics.Bitmap.createBitmap(1000, 330, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(preview)
        canvas.drawColor(android.graphics.Color.WHITE)
        val glyphHashes = names.mapIndexed { index, name ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = readerTypeface(context, name); textSize = 32f; color = android.graphics.Color.BLACK
            }
            if (name == "Monospaced") assertEquals(paint.measureText("iiii"), paint.measureText("WWWW"), .01f)
            else assertTrue(paint.measureText("iiii") < paint.measureText("WWWW"))
            assertTrue(paint.hasGlyph("Ž"))
            val sample = android.graphics.Bitmap.createBitmap(950, 60, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(sample).drawText("Times Courier · Wim 012345 Žluťoučký", 0f, 42f, paint)
            canvas.drawText(name, 12f, index * 110f + 35f, paint)
            canvas.drawBitmap(sample, 12f, index * 110f + 44f, null)
            IntArray(950 * 60).also { sample.getPixels(it, 0, 950, 0, 0, 950, 60) }.contentHashCode()
        }
        assertEquals("The actual glyph images must differ, not just the typeface objects", 3, glyphHashes.toSet().size)
        java.io.File("build/reports/font-preview-0.8.3.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { preview.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun thinkingDefaultsToClosedAndUsesTheMarkdownRenderer() {
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"assistant","content":"<think>## Plan\n\n**Compare** and `check`.\n\n- First\n- Second</think>\nThe final answer."}]}]}""")
        val rendered = mapOf(0 to ReadingRenderCache(conversation, createMarkdownRenderer(context)).version(0, 0))
        val thinking = rendered.getValue(0).parts.first()
        assertTrue(thinking.thinking)
        assertFalse(thinking.text.toString().contains("**"))
        assertFalse(thinking.text.toString().contains("##"))
        assertTrue(thinking.text.getSpans(0, thinking.text.length, android.text.style.MetricAffectingSpan::class.java).isNotEmpty())
        val closed = readerRows(conversation, mapOf(0 to 0), rendered, listOf(0), emptyList(), false)
        assertEquals(1, closed.count { it.kind == "thinking" })
        assertTrue(closed.filter { it.kind == "text" }.all { it.block!!.part == 1 })
        val open = readerRows(conversation, mapOf(0 to 0), rendered, listOf(0), emptyList(), false, setOf("0:0:0"))
        assertTrue(open.any { it.block?.part == 0 })
        assertEquals(1, open.count { it.kind == "thinkingEnd" })
        assertTrue(closed.none { it.kind == "thinkingEnd" })
        assertEquals("Plan", thinking.headings.single().label)
    }

    @Test fun thinkingParagraphSpacingIncreasesHeightWithoutChangingOffsetsOrCode() {
        val renderer = createMarkdownRenderer(context)
        val source = renderer.toMarkdown("First paragraph.\n\nSecond paragraph.")
        val spaced = thinkingParagraphSpacing(source, 12)
        assertEquals(source.toString(), spaced.toString())
        assertTrue(spaced.getSpans(0, spaced.length, ThinkingParagraphGap::class.java).isNotEmpty())
        fun height(text: CharSequence): Int {
            val view = android.widget.TextView(context).apply { textSize = 17f; this.text = text }
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            return view.measuredHeight
        }
        assertTrue(height(spaced) > height(source))
        val code = thinkingParagraphSpacing(renderer.toMarkdown("```python\nfirst\n\nsecond\n```"), 12)
        assertTrue(code.getSpans(0, code.length, ThinkingParagraphGap::class.java).isEmpty())
    }

    @Test fun styledThinkingWithRedundantTagsStillRendersMarkdownHeadings() {
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","style":{"type":"thinking"},"content":"<think>\n## Plan\n\n**Compare** the options.\n</think>"},{"type":"contentBlock","content":"Final answer"}]}]}]}""")
        val part = ReadingRenderCache(conversation, createMarkdownRenderer(context)).version(0, 0).parts.first()
        assertEquals(listOf("Plan"), part.headings.map { it.label })
        assertFalse(part.text.contains("<think>"))
        assertFalse(part.text.contains("##"))
        assertFalse(part.text.contains("**"))
    }

    @Test fun overindentedThinkingListsRenderAsListsAndBoldInsteadOfCode() {
        val text = "Planning a story.\n\n        *   First idea.\n        *   Second idea.\n\n    *   *Characters:* A detective and a guide.\n    *   **Chapter 1: The Arrival.** Meet the characters."
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","style":{"type":"thinking"},"content":${org.json.JSONObject.quote(text)}}]}]}]}""")
        val part = ReadingRenderCache(conversation, createMarkdownRenderer(context)).version(0, 0).parts.single()
        assertTrue("Overindented prose lists should not become code blocks", part.code.isEmpty())
        assertFalse(part.text.contains("**Chapter"))
        assertFalse(part.text.contains("*Characters:*"))
        assertTrue(part.text.contains("Chapter 1: The Arrival."))
    }
}
