package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ReadingInfrastructureTest {
    @Test fun contentsSelectsTheLatestSectionAtTheReadingPosition() {
        val outline = listOf(
            OutlineEntry("Prompt", 0, ReadingAnchor(0), true),
            OutlineEntry("First heading", 1, ReadingAnchor(1, part = 0, character = 20)),
            OutlineEntry("Second heading", 2, ReadingAnchor(1, part = 1, character = 18)),
            OutlineEntry("Next prompt", 0, ReadingAnchor(2), true)
        )
        assertEquals(-1, currentOutlineIndex(emptyList(), ReadingAnchor(0)))
        assertEquals(0, currentOutlineIndex(outline, ReadingAnchor(1, part = 0, character = 10)))
        assertEquals(1, currentOutlineIndex(outline, ReadingAnchor(1, part = 1, character = 17)))
        assertEquals(2, currentOutlineIndex(outline, ReadingAnchor(1, part = 1, character = 18)))
        assertEquals(2, currentOutlineIndex(outline, ReadingAnchor(1, rowKey = "footer:1:0")))
        assertEquals(3, currentOutlineIndex(outline, ReadingAnchor(2, rowKey = "preview:2")))
    }

    @Test fun slicesAdvanceThroughUnbrokenTextAndSingleCharacterWindows() {
        val text = "x".repeat(1_000_000)
        val slices = textSlices(text)
        assertEquals(556, slices.size)
        assertEquals(TextSlice(0, 1800), slices.first())
        assertEquals(text.length, slices.last().end)
        assertEquals(listOf(TextSlice(0, 2), TextSlice(2, 3)), textSlices("😀x", 1))
    }

    @Test fun matchHighlightSpansBothSidesOfABlockBoundary() {
        val match = SearchMatch(0, 0, 0, 1794, 1804)
        assertEquals(1794..1799, match.rangeIn(0, 1800))
        assertEquals(0..3, match.rangeIn(1800, 3600))
        assertNull(match.rangeIn(1804, 3600))
    }
    @Test fun slicesReassembleLongUnicodeTextWithoutBreakingPairs() {
        val text = "A paragraph with Žluťoučký kůň and 😀.\n\n".repeat(900)
        val slices = textSlices(text)
        assertTrue(slices.size > 10)
        assertEquals(text, slices.joinToString("") { text.substring(it.start, it.end) })
        slices.forEach { assertTrue(it.end > it.start); assertTrue(it.end - it.start <= 1802) }
        slices.dropLast(1).forEach { assertFalse(text[it.end - 1].isHighSurrogate()) }
    }

    @Test fun scrollbarUsesMeasurementsAndRebuildsWhenContentChanges() {
        val index = ScrollExtentIndex()
        index.update(3, 10, 20, listOf(0 to 100, 1 to 200, 2 to 300))
        assertEquals(640L, index.total)
        assertEquals(110L, index.position(1, 0))
        assertEquals(ScrollTarget(1, 160), index.target(.5f, 100))
        assertEquals(ScrollTarget(2, Int.MAX_VALUE), index.target(1f, 100))
        index.update(1, 0, 0, listOf(0 to 80))
        assertEquals(80L, index.total)
        assertEquals(ScrollTarget(0, 0), index.target(.5f, 100))
    }

    @Test fun accentInsensitiveSearchReturnsOriginalOffsets() {
        val text = "😀 Žluťoučký kůň. ŽLUŤOUČKÝ kůň."
        val ranges = matchRanges(text, "zlutoucky", true)
        assertEquals(2, ranges.size)
        assertEquals("Žluťoučký", text.substring(ranges[0]))
        assertEquals("ŽLUŤOUČKÝ", text.substring(ranges[1]))
    }

    @Test fun groupingHandlesLeadingAssistantMessagesAndLongRuns() {
        fun message(role: String) = Message(listOf(MessageVersion(role, listOf(ReplyPart("text")), emptyList())), 0)
        val conversation = Conversation("Groups", null, "", listOf(message("assistant"), message("assistant"), message("user")) + List(1000) { message("assistant") } + message("user"))
        val groups = promptGroups(conversation)
        assertEquals(listOf(0, 1, 2), groups.take(3))
        assertTrue(groups.subList(3, 1003).all { it == 2 })
        assertEquals(1003, groups.last())
    }
}
