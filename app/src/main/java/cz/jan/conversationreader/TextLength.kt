package cz.jan.conversationreader

private val wordPattern = java.util.regex.Pattern.compile("\\S+")

internal fun countWords(text: CharSequence): Int {
    val matcher = wordPattern.matcher(text)
    var count = 0
    while (matcher.find()) count++
    return count
}

/** Counts prompt/reply text in the selected versions, excluding debug and model settings. */
fun Conversation.wordCount(): Int = messages.sumOf { message ->
    message.versions[message.selected].parts.sumOf { part ->
        countWords(part.text)
    }
}
