package cz.jan.conversationreader

/** Saved count is LM Studio's value; generated count sums only selected replies, never repeated prompt context. */
fun Conversation.selectedGeneratedTokens(versions: Map<Int, Int> = emptyMap()): Long? {
    val counts = messages.flatMapIndexed { index, message -> message.versions[versions[index] ?: message.selected].parts }.mapNotNull { it.stats?.generated }
    return if (counts.isEmpty()) null else counts.sumOf { it.toLong() }
}

/** Counts conversation files recursively below each folder, excluding directory records. */
fun folderFileCounts(files: List<Pair<String, Boolean>>): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    files.filter { !it.second }.forEach { (path, _) ->
        var parent = path.substringBeforeLast('/', "")
        while (parent.isNotEmpty()) {
            counts[parent] = (counts[parent] ?: 0) + 1
            parent = parent.substringBeforeLast('/', "")
        }
    }
    return counts
}
