package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ConversationParserTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()
    @Test fun screenshotExampleMatchesActualGenerationStatistics() {
        val conversation = ConversationParser.parse(fixture("synthetic-short.conversation.json"))
        assertEquals("Example conversation", conversation.title)
        assertEquals(2, conversation.messages.size)
        val reply = conversation.messages[1].versions[0]
        assertEquals(1, reply.parts.size)
        assertTrue(reply.parts[0].text.contains("**formatted reply**"))
        assertFalse(reply.parts[0].text.contains("Conversation naming technique"))
        assertEquals(1, reply.debug.size)
        assertEquals(278, reply.parts[0].stats!!.generated)
        assertEquals(1.751851, reply.parts[0].stats!!.firstToken!!, 0.000001)
        assertEquals(88.695773, reply.parts[0].stats!!.duration!!, 0.000001)
    }
    @Test fun longExamplePreservesTextAndModel() {
        val conversation = ConversationParser.parse(fixture("synthetic-long.conversation.json"))
        assertEquals("Long example", conversation.title)
        assertTrue(conversation.messages[1].versions[0].parts[0].text.length > 10000)
        assertEquals(listOf("skyfall-31b-v4.2@q5_k_m"), conversation.models)
        assertEquals(4872, conversation.messages[1].versions[0].parts[0].stats!!.generated)
    }
    @Test fun multiTurnVersionsUnknownBlocksAndMissingMetadataArePreserved() {
        val conversation = ConversationParser.parse("""{"name":"Versions","messages":[
          {"currentlySelected":1,"versions":[{"role":"user","content":[{"type":"text","text":"old"}]},{"role":"user","content":[{"type":"text","text":"new"}]}]},
          {"versions":[{"role":"assistant","steps":[{"type":"contentBlock","content":[{"type":"text","text":"first"}]},{"type":"contentBlock","content":[{"type":"image"}]},{"type":"futureTool"}]}]},
          {"currentlySelected":99,"versions":[{"role":"user","content":"follow-up"}]}
        ]}""")
        assertEquals(1, conversation.messages[0].selected)
        assertEquals("new", conversation.messages[0].versions[1].parts[0].text)
        val parts = conversation.messages[1].versions[0].parts
        assertEquals(3, parts.size)
        assertEquals("first", parts[0].text)
        assertNull(parts[0].stats)
        assertTrue(parts[1].text.contains("Unsupported content"))
        assertTrue(parts[2].text.contains("futureTool"))
        assertEquals(0, conversation.messages[2].selected)
    }
    @Test(expected = IllegalStateException::class) fun unrelatedJsonRejected() { ConversationParser.parse("{\"name\":\"not a chat\"}") }
    @Test(expected = IllegalArgumentException::class) fun emptyVersionsRejected() { ConversationParser.parse("{\"messages\":[{\"versions\":[]}]}") }
    @Test fun emptyConversationAndBomSupported() { assertTrue(ConversationParser.parse("\uFEFF{\"messages\":[]}").messages.isEmpty()) }
}
