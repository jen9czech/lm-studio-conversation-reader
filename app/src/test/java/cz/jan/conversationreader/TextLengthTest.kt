package cz.jan.conversationreader

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLengthTest {
    @Test fun countsSelectedPromptAndReplyTextOnly() {
        val chat = Conversation("Title excluded", null, "System excluded", listOf(
            Message(listOf(MessageVersion("user", listOf(ReplyPart("old version excluded")), emptyList()), MessageVersion("user", listOf(ReplyPart("Hello world")), emptyList())), 1),
            Message(listOf(MessageVersion("assistant", listOf(ReplyPart("One\n two\tthree")), listOf("Debug excluded"))), 0)
        ))
        assertEquals(5, chat.wordCount())
        assertEquals(0, chat.copy(messages = emptyList()).wordCount())
    }
}
