package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ConversationMetricsTest {
    @Test fun folderCountsIncludeDescendantsButNotDirectories() {
        val counts = folderFileCounts(listOf("A" to true, "A/sub" to true, "A/one.json" to false, "A/sub/two.json" to false, "AB/three.json" to false))
        assertEquals(2, counts["A"])
        assertEquals(1, counts["A/sub"])
        assertEquals(1, counts["AB"])
    }
    @Test fun tokenTotalsUseSavedCountAndSelectedRepliesWithoutRepeatedPromptTokens() {
        val conversation = ConversationParser.parse("""{"tokenCount":90,"messages":[{"currentlySelected":1,"versions":[{"role":"assistant","steps":[{"type":"contentBlock","content":[],"genInfo":{"stats":{"predictedTokensCount":500,"promptTokensCount":1000}}}]},{"role":"assistant","steps":[{"type":"contentBlock","content":[],"genInfo":{"stats":{"predictedTokensCount":20,"promptTokensCount":100}}},{"type":"contentBlock","content":[],"genInfo":{"stats":{"predictedTokensCount":30,"promptTokensCount":200}}}]}]}]}""")
        assertEquals(90L, conversation.savedTokenCount)
        assertEquals(50L, conversation.selectedGeneratedTokens())
        assertEquals(500L, conversation.selectedGeneratedTokens(mapOf(0 to 0)))
        assertNull(conversation.copy(messages = emptyList()).selectedGeneratedTokens())
    }
}
