package cz.jan.conversationreader

/** Counts prompt/reply text in the selected versions, excluding debug and model settings. */
fun Conversation.wordCount(): Int = messages.sumOf { message ->
    message.versions[message.selected].parts.sumOf { part ->
        Regex("\\S+").findAll(part.text).count()
    }
}
