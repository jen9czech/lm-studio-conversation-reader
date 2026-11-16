package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class LibraryFilteringTest {
    @Test fun foldersSearchesMetadataOnlyWhileAllFilesCanMatchContent() {
        assertTrue(matchesLinkedSearch("Fairytale", "Stories/123.conversation.json", "FAIRY", false, false))
        assertTrue(matchesLinkedSearch("Fairytale", "Stories/123.conversation.json", "123", false, false))
        assertTrue(matchesLinkedSearch("Fairytale", "Stories/123.conversation.json", "stories/", false, false))
        assertFalse(matchesLinkedSearch("Fairytale", "Stories/123.conversation.json", "body-only", false, true))
        assertTrue(matchesLinkedSearch("Fairytale", "Stories/123.conversation.json", "body-only", true, true))
        assertFalse(matchesLinkedSearch("Fairytale", "Stories/123.conversation.json", "absent", true, false))
    }
}
