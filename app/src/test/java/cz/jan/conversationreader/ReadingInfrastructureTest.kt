package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ReadingInfrastructureTest {
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
