package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class LibraryFilteringTest {
    @Test fun defaultSearchMatchesDisplayedTitlesAndOnlyOptInSearchCanMatchContent() {
        assertTrue(matchesLibraryConversation("Fairytale", " FAIRY ", false, false))
        assertFalse(matchesLibraryConversation("Fairytale", "123.conversation.json", false, false))
        assertFalse(matchesLibraryConversation("Fairytale", "body-only", false, true))
        assertTrue(matchesLibraryConversation("Fairytale", "body-only", true, true))
        assertFalse(matchesLibraryConversation("Fairytale", "absent", true, false))
    }
}
