package cz.jan.conversationreader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArchiveStressAuditTest {
    @Test fun parsesAndSearchesLargeSyntheticArchive() {
        val messages = JSONArray()
        repeat(1000) { index -> messages.put(JSONObject().put("versions", JSONArray().put(
            JSONObject().put("role", if (index % 2 == 0) "user" else "assistant").put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Message $index: Příběh synthetic marker ${"sample text ".repeat(20)}")))
        ))) }
        val raw = JSONObject().put("name", "Synthetic stress archive").put("messages", messages).toString()
        val started = System.nanoTime()
        val conversation = ConversationParser.parse(raw)
        val parsed = System.nanoTime()
        val matches = findMatches(conversation.messages.map { m -> m.versions.map { v -> v.parts.map { it.text } } }, "MARKER")
        val searched = System.nanoTime()
        assertEquals(1000, conversation.messages.size)
        assertEquals(1000, matches.size)
        println("AUDIT synthetic=${raw.length} chars messages=1000 parseMs=${(parsed-started)/1_000_000.0} searchMs=${(searched-parsed)/1_000_000.0}; host JVM, not phone performance")
    }
    @Test fun caseInsensitiveCzechSearchKeepsCharacterOffsets() {
        assertEquals(listOf(0..5, 7..12), matchRanges("PŘÍBĚH příběh", "příběh"))
    }
}
