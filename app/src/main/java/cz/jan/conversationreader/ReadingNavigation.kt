package cz.jan.conversationreader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

/** Content offsets include every message, even when its group is collapsed. Thinking is excluded. */
internal fun readingOffsets(rows: List<ReaderRow>, rendered: Map<Int, RenderedVersion>, messageCount: Int): Pair<List<LongRange>, Long> {
    val messageStarts = LongArray(messageCount + 1)
    val partStarts = List(messageCount) { message ->
        var length = 0L
        val starts = rendered[message]?.parts.orEmpty().map { part ->
            val start = length
            if (!part.thinking) length += part.text.length
            start
        }
        messageStarts[message + 1] = messageStarts[message] + length
        starts
    }
    val offsets = rows.map { row ->
        val start = messageStarts[row.message]
        val block = row.block
        when {
            row.kind == "footer" -> messageStarts[row.message + 1].let { it..it }
            row.kind == "promptPreview" || block == null -> start..start
            else -> {
                val partStart = start + partStarts[row.message].getOrElse(block.part) { 0L }
                if (block.thinking) partStart..partStart
                else (partStart + block.start)..(partStart + block.end)
            }
        }
    }
    return offsets to messageStarts.last()
}

/** Observe scrolling here to avoid remeasuring every reply on each update. */
@Composable
internal fun ReadingProgress(list: LazyListState, rows: List<ReaderRow>, rendered: Map<Int, RenderedVersion>, messageCount: Int, modifier: Modifier = Modifier, onProgress: (Int) -> Unit = {}) {
    val (offsets, total) = remember(rows, rendered, messageCount) { readingOffsets(rows, rendered, messageCount) }
    val progress by remember(list, rows, offsets, total) { derivedStateOf {
        val index = list.firstVisibleItemIndex
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val offset = offsets.getOrNull(index)
        when {
            total == 0L || offset == null || !list.canScrollBackward -> 0
            !list.canScrollForward && rows.lastOrNull()?.let { it.kind == "footer" && it.message == messageCount - 1 } == true -> 100
            else -> {
                val fraction = (list.firstVisibleItemScrollOffset.toDouble() / (first?.size ?: 1).coerceAtLeast(1)).coerceIn(0.0, 1.0)
                ((offset.first + (offset.last - offset.first) * fraction) / total * 100).toInt().coerceIn(0, 100)
            }
        }
    } }
    LaunchedEffect(progress) { onProgress(progress) }
    Text("$progress%", modifier = modifier.semantics { contentDescription = "Reading progress: $progress percent" }, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
