package cz.jan.conversationreader

import org.json.JSONArray
import org.json.JSONObject

data class Conversation(val title: String, val createdAt: Long?, val systemPrompt: String, val messages: List<Message>) {
    val models get() = messages.flatMap { it.versions }.flatMap { it.parts }.mapNotNull { it.model }.distinct()
    val searchable get() = title + "\n" + messages.flatMap { it.versions }.flatMap { it.parts }.joinToString("\n") { it.text }
}
data class Message(val versions: List<MessageVersion>, val selected: Int)
data class MessageVersion(val role: String, val parts: List<ReplyPart>, val debug: List<String>)
data class ReplyPart(val text: String, val model: String? = null, val stats: GenerationStats? = null)
data class GenerationStats(val speed: Double?, val firstToken: Double?, val duration: Double?, val generated: Int?, val prompt: Int?, val total: Int?, val stop: String?)

/** Deliberately tolerant of unknown LM Studio fields; source JSON is retained unchanged. */
object ConversationParser {
    fun parse(raw: String): Conversation {
        val root = JSONObject(raw.removePrefix("\uFEFF"))
        val messages = root.optJSONArray("messages") ?: error("This JSON has no conversation messages.")
        val parsed = messages.objects().map { message ->
            val sourceVersions = message.optJSONArray("versions")?.objects().orEmpty()
            require(sourceVersions.isNotEmpty()) { "A message has no readable versions." }
            val versions = sourceVersions.map { version ->
                val parts = mutableListOf<ReplyPart>()
                val debug = mutableListOf<String>()
                val sender = version.optJSONObject("senderInfo")?.stringOrNull("senderName")
                if (version.has("content")) parts += ReplyPart(content(version.opt("content")), sender)
                version.optJSONArray("steps")?.objects()?.forEach { step ->
                    when (step.optString("type")) {
                        "debugInfoBlock" -> debug += step.optString("debugInfo")
                        "contentBlock" -> {
                            val info = step.optJSONObject("genInfo")
                            parts += ReplyPart(content(step.opt("content")), info?.stringOrNull("identifier") ?: sender,
                                info?.optJSONObject("stats")?.let { s -> GenerationStats(
                                    s.number("tokensPerSecond"), s.number("timeToFirstTokenSec"), s.number("totalTimeSec"),
                                    s.integer("predictedTokensCount"), s.integer("promptTokensCount"), s.integer("totalTokensCount"), s.stringOrNull("stopReason")) })
                        }
                        else -> parts += ReplyPart("[Unsupported step: ${step.optString("type", "unknown")}]")
                    }
                }
                if (parts.isEmpty()) parts += ReplyPart("[No displayable text]")
                MessageVersion(version.optString("role", "unknown"), parts, debug)
            }
            Message(versions, message.optInt("currentlySelected", 0).coerceIn(versions.indices))
        }
        return Conversation(root.optString("name").ifBlank { "Untitled conversation" }, root.number("createdAt")?.toLong(), root.optString("systemPrompt"), parsed)
    }
    private fun content(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> (0 until value.length()).joinToString("\n\n") { i ->
            val block = value.optJSONObject(i)
            if (block?.optString("type") == "text") block.optString("text")
            else "[Unsupported content: ${block?.optString("type") ?: "unknown"}]"
        }
        else -> "[No displayable text]"
    }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.number(key: String): Double? = (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
    private fun JSONObject.integer(key: String): Int? = number(key)?.toInt()
    private fun JSONObject.stringOrNull(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
}
