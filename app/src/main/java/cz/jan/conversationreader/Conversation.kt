package cz.jan.conversationreader

import org.json.JSONArray
import org.json.JSONObject

data class Conversation(val title: String, val createdAt: Long?, val systemPrompt: String, val messages: List<Message>, val savedTokenCount: Long? = null) {
    val models get() = messages.flatMap { it.versions }.flatMap { it.parts }.mapNotNull { it.model }.distinct()
    val searchable get() = title + "\n" + messages.flatMap { it.versions }.flatMap { it.parts }.joinToString("\n") { it.text }

    internal fun messageCounts(versions: Map<Int, Int>): MessageCounts {
        var prompts = 0
        var replies = 0
        messages.forEachIndexed { index, message ->
            when (message.versions[versions[index] ?: message.selected].role) {
                "user" -> prompts++
                "assistant" -> replies++
            }
        }
        return MessageCounts(prompts, replies, messages.size)
    }
}
internal data class MessageCounts(val prompts: Int, val replies: Int, val total: Int)
data class Message(val versions: List<MessageVersion>, val selected: Int)
data class MessageVersion(val role: String, val parts: List<ReplyPart>, val debug: List<String>)
data class ReplyPart(val text: String, val model: String? = null, val stats: GenerationStats? = null, val thinking: Boolean = false)
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
                val assistant = version.optString("role") == "assistant"
                if (version.has("content")) parts += contentParts(version.opt("content"), sender, null, assistant, assistant && isThinking(version))
                version.optJSONArray("steps")?.objects()?.forEach { step ->
                    when (step.optString("type")) {
                        "debugInfoBlock" -> debug += step.optString("debugInfo")
                        "contentBlock" -> {
                            val info = step.optJSONObject("genInfo")
                            parts += contentParts(step.opt("content"), info?.stringOrNull("identifier") ?: sender,
                                info?.optJSONObject("stats")?.let { s -> GenerationStats(
                                    s.number("tokensPerSecond"), s.number("timeToFirstTokenSec"), s.number("totalTimeSec"),
                                    s.integer("predictedTokensCount"), s.integer("promptTokensCount"), s.integer("totalTokensCount"), s.stringOrNull("stopReason")) }, assistant, assistant && isThinking(step))
                        }
                        else -> parts += ReplyPart("[Unsupported step: ${step.optString("type", "unknown")}]")
                    }
                }
                if (parts.isEmpty()) parts += ReplyPart("[No displayable text]")
                MessageVersion(version.optString("role", "unknown"), parts, debug)
            }
            Message(versions, message.optInt("currentlySelected", 0).coerceIn(versions.indices))
        }
        return Conversation(root.optString("name").ifBlank { "Untitled conversation" }, root.number("createdAt")?.toLong(), root.optString("systemPrompt"), parsed, root.number("tokenCount")?.toLong())
    }
    private fun isThinking(block: JSONObject): Boolean = block.optJSONObject("style")?.optString("type") in setOf("thinking", "reasoning") || block.optString("type") in setOf("thinking", "reasoning") || block.optString("channel") in setOf("analysis", "thinking", "reasoning")
    private fun contentParts(value: Any?, model: String?, stats: GenerationStats?, assistant: Boolean, thinking: Boolean): List<ReplyPart> {
        val raw = if (assistant && value is JSONArray && value.contentObjects().any { isThinking(it) }) {
            value.contentObjects().map { block -> ReplyPart(if (block.optString("type") in setOf("text", "thinking", "reasoning")) block.stringOrNull("text").orEmpty() else content(JSONArray().put(block)), model, thinking = thinking || isThinking(block)) }
        } else listOf(ReplyPart(if (thinking && (value == null || value == JSONObject.NULL)) "" else content(value), model, thinking = thinking))
        val result = raw.flatMap { part ->
            when {
                !assistant -> listOf(part)
                part.thinking -> listOf(part.copy(text = thinkingMarkdown(part.text)))
                else -> splitThinkingText(part.text).map { part.copy(text = if (it.thinking) thinkingMarkdown(it.text) else it.text, thinking = it.thinking) }
            }
        }
        return result.mapIndexed { i, part -> part.copy(stats = stats.takeIf { i == result.lastIndex }) }
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
    private fun JSONArray.contentObjects(): List<JSONObject> = (0 until length()).map { optJSONObject(it) ?: JSONObject() }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.number(key: String): Double? = when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() && it >= 0 }
    private fun JSONObject.integer(key: String): Int? = number(key)?.toInt()
    private fun JSONObject.stringOrNull(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
}
