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

/** Scroll observation lives in small components, so scrolling doesn't recompose/re-measure every reply. */
@Composable
fun ReadingProgress(list: LazyListState, weights: List<Int>, modifier: Modifier = Modifier) {
    val prefix = remember(weights) { LongArray(weights.size + 1).apply { weights.forEachIndexed { i, weight -> this[i + 1] = this[i] + weight.coerceAtLeast(1) } } }
    val progress by remember(list, prefix) { derivedStateOf {
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }
        when {
            weights.isEmpty() || !list.canScrollBackward -> 0
            !list.canScrollForward -> 100
            else -> {
                val index = list.firstVisibleItemIndex.coerceIn(weights.indices)
                val fraction = (list.firstVisibleItemScrollOffset.toFloat() / (first?.size ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                ((prefix[index] + weights[index].coerceAtLeast(1) * fraction) / prefix.last() * 100).toInt().coerceIn(0, 99)
            }
        }
    } }
    Text("${if (progress in 1..99) "≈" else ""}$progress%", modifier = modifier.semantics { contentDescription = "Reading progress: $progress percent" }, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Start, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
