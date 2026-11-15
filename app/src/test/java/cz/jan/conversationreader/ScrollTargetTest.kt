package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ScrollTargetTest {
    @Test fun seeksWithinOneLongReply() {
        assertEquals(ScrollTarget(0, 4500), scrollTarget(listOf(10000), 0, 1000, 0, .5f))
    }
    @Test fun seeksPastPromptIntoLongReply() {
        assertEquals(ScrollTarget(1, 4500), scrollTarget(listOf(100, 10000), 0, 1000, 0, 4600f / 9100f))
    }
    @Test fun handlesEmptyCollapsedAndEndPositions() {
        assertEquals(ScrollTarget(0, 0), scrollTarget(emptyList(), 0, 500, 0, .5f))
        assertEquals(ScrollTarget(2, Int.MAX_VALUE), scrollTarget(listOf(100, 0, 0), 0, 500, 0, 1f))
        assertEquals(ScrollTarget(0, 0), scrollTarget(listOf(100), 0, 500, 0, 0f))
    }
}
