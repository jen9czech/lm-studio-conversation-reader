package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ThinkingTest {
    @Test fun styledThinkingAndAnswerKeepMarkdownAndStats() {
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"assistant","senderInfo":{"senderName":"Test model"},"steps":[{"type":"contentBlock","style":{"type":"thinking","ended":true},"content":[{"type":"text","text":"## Plan\n\n**Compare** the options."}]},{"type":"contentBlock","content":[{"type":"text","text":"The answer."}],"genInfo":{"stats":{"predictedTokensCount":42}}}]}]}]}""")
        val parts = conversation.messages.single().versions.single().parts
        assertEquals(2, parts.size)
        assertTrue(parts[0].thinking)
        assertTrue(parts[0].text.contains("**Compare**"))
        assertFalse(parts[1].thinking)
        assertEquals("Test model", parts[0].model)
        assertEquals(42L, conversation.selectedGeneratedTokens(mapOf(0 to 0)))
    }

    @Test fun inlineThinkingSplitsOnlyAssistantAndCountsGenerationOnce() {
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"user","content":"<think>literal prompt</think>"}]},{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","content":"<think>**Plan** first.</think>\n# Answer\nDone.","genInfo":{"stats":{"predictedTokensCount":99}}}]}]}]}""")
        assertEquals("<think>literal prompt</think>", conversation.messages[0].versions[0].parts.single().text)
        val parts = conversation.messages[1].versions[0].parts
        assertEquals(listOf(true, false), parts.map { it.thinking })
        assertFalse(parts.any { "<think>" in it.text || "</think>" in it.text })
        assertEquals(1, parts.count { it.stats != null })
        assertEquals(99L, conversation.selectedGeneratedTokens(mapOf(0 to 0, 1 to 0)))
    }

    @Test fun typedThinkingContentAndTruncatedReasoningArePreserved() {
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"assistant","content":[{"type":"reasoning","text":"A saved plan"},{"type":"text","text":"Final answer"}]}]}]}""")
        assertEquals(listOf(true, false), conversation.messages[0].versions[0].parts.map { it.thinking })
        assertEquals(listOf(ThinkingText("unfinished reasoning", true)), splitThinkingText("<think>unfinished reasoning"))
    }

    @Test fun codeExamplesAndEscapedTagsAreNotThinking() {
        val text = "An example: `<think>literal</think>`.\n\n```xml\n<think>code</think>\n```\n\\<think>escaped\\</think>"
        assertEquals(listOf(ThinkingText(text, false)), splitThinkingText(text))
        val long = "x".repeat(200_000) + "<think>plan</think>answer"
        assertEquals(3, splitThinkingText(long).size)
    }

    @Test fun promptActionsOwnThinkingInTheirRepliesWithoutDuplicateToggles() {
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"user","content":"First"}]},{"versions":[{"role":"assistant","content":"<think>Plan</think>Answer"}]},{"versions":[{"role":"assistant","content":"<think>Another plan</think>More"}]},{"versions":[{"role":"user","content":"Second"}]},{"versions":[{"role":"assistant","content":"<think>Next plan</think>Final"}]}]}""")
        val groups = promptGroups(conversation)
        assertEquals(listOf(1, 2), thinkingTargets(conversation, emptyMap(), groups, 0).map { it.message })
        assertTrue(thinkingTargets(conversation, emptyMap(), groups, 1).isEmpty())
        assertEquals(listOf(4), thinkingTargets(conversation, emptyMap(), groups, 3).map { it.message })
    }

    @Test fun redundantThinkingWrappersAndMarkdownFencesAreNormalized() {
        assertEquals("# Plan\n\n**Compare**", thinkingMarkdown("<think>```markdown\n# Plan\n\n**Compare**\n```</think>"))
        assertEquals("```python\nprint(1)\n```", thinkingMarkdown("```python\nprint(1)\n```"))
        assertEquals("Example: `<think>literal</think>`", thinkingMarkdown("Example: `<think>literal</think>`"))
        val conversation = ConversationParser.parse("""{"messages":[{"versions":[{"role":"user","content":"Prompt"}]},{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","style":{"type":"thinking"},"content":"<think>  </think>"},{"type":"contentBlock","content":"Final"}]}]}]}""")
        assertTrue(thinkingTargets(conversation, emptyMap(), promptGroups(conversation), 0).isEmpty())
        val missing = ConversationParser.parse("""{"messages":[{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","style":{"type":"thinking"}},{"type":"contentBlock","content":"Answer"}]}]}]}""")
        assertTrue(thinkingTargets(missing, emptyMap(), promptGroups(missing), 0).isEmpty())
    }

    @Test fun thinkingListIndentationIsNormalizedWithoutFlatteningRealNestedListsOrCode() {
        val overindented = "A plan.\n\n        * First\n            * Nested\n\n    * **Chapter 1**"
        assertEquals("A plan.\n\n* First\n    * Nested\n\n* **Chapter 1**", normalizeThinkingLists(overindented))
        val valid = "1. Parent\n\n    * Child\n        * Grandchild\n\n```python\n        * not_a_list\n```"
        assertEquals(valid, normalizeThinkingLists(valid))
    }
}
