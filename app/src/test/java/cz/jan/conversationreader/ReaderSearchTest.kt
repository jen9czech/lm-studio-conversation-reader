package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ReaderSearchTest {
    @Test fun matchesEveryOccurrenceCaseInsensitively() {
        assertEquals(listOf(0..4, 10..14, 17..21), matchRanges("Truck and truck, TRUCK!", "truck"))
        assertTrue(matchRanges("text", " ").isEmpty())
        assertTrue(matchRanges("text", "missing").isEmpty())
    }
    @Test fun matchesRetainVersionPartAndVisibleTextOffsets() {
        val hits = findMatches(listOf(listOf(listOf("Heading\nA truck and truck."), listOf("Other", "Truck")), listOf(listOf("truck"))), "truck")
        assertEquals(4, hits.size)
        assertEquals(SearchMatch(0, 0, 0, 10, 15), hits[0])
        assertEquals(SearchMatch(0, 1, 1, 0, 5), hits[2])
        assertEquals(SearchMatch(1, 0, 0, 0, 5), hits[3])
    }
    @Test fun progressMovesWithinLongRepliesAndHandlesBoundaries() {
        assertEquals(0, readingProgress(listOf(10, 990), 0, 0f, true, false))
        assertEquals(50, readingProgress(listOf(10, 990), 1, .5f, false, false))
        assertEquals(100, readingProgress(listOf(10, 990), 1, .9f, false, true))
        assertEquals(0, readingProgress(emptyList(), 0, 0f, true, true))
    }
}
