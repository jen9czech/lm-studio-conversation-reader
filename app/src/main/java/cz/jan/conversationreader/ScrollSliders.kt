package cz.jan.conversationreader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

data class ScrollTarget(val index: Int, val offset: Int)

/** Map a fraction to estimated pixel extents, so a reply taller than the screen remains seekable. */
fun scrollTarget(sizes: List<Int>, spacing: Int, viewport: Int, padding: Int, fraction: Float): ScrollTarget {
    if (sizes.isEmpty() || fraction <= 0f) return ScrollTarget(0, 0)
    if (fraction >= 1f) return ScrollTarget(sizes.lastIndex, Int.MAX_VALUE)
    val extent = sizes.sumOf { it.toLong() } + spacing.toLong() * (sizes.size - 1).coerceAtLeast(0) + padding
    var remaining = ((extent - viewport).coerceAtLeast(0) * fraction).toLong()
    sizes.forEachIndexed { index, height ->
        val length = (height + spacing).coerceAtLeast(0)
        if (remaining < length || index == sizes.lastIndex) return ScrollTarget(index, remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        remaining -= length
    }
    return ScrollTarget(0, 0)
}

private data class SliderGeometry(val fraction: Float, val visibleFraction: Float, val viewport: Int = 0, val count: Int = 0)

@Composable
fun BoxScope.LazyScrollSlider(list: LazyListState, contentKey: Any? = null) {
    val extents = remember(list, contentKey) { ScrollExtentIndex() }
    val geometry by remember(list, extents) { derivedStateOf {
        val layout = list.layoutInfo
        val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
        extents.update(layout.totalItemsCount, layout.mainAxisItemSpacing, layout.beforeContentPadding + layout.afterContentPadding, layout.visibleItemsInfo.map { it.index to it.size })
        val position = extents.position(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        val fraction = when { !list.canScrollBackward -> 0f; !list.canScrollForward -> 1f; else -> position.toFloat() / (extents.total - viewport).coerceAtLeast(1) }
        SliderGeometry(fraction.coerceIn(0f, 1f), (viewport.toFloat() / extents.total.coerceAtLeast(1)).coerceIn(0f, 1f), viewport, layout.totalItemsCount)
    } }
    val current by rememberUpdatedState(geometry)
    val seeks = remember(list) { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(list, extents) { seeks.receiveAsFlow().collectLatest { fraction ->
        val g = current
        val target = extents.target(fraction, g.viewport)
        if (g.count > 0) list.scrollToItem(target.index, target.offset)
    } }
    DisposableEffect(seeks) { onDispose { seeks.close() } }
    ScrollRail(list.isScrollInProgress, list.canScrollBackward || list.canScrollForward, geometry.fraction, geometry.visibleFraction) { seeks.trySend(it) }
}

@Composable
fun BoxScope.PlainScrollSlider(state: ScrollState) {
    val fraction by remember(state) { derivedStateOf { if (state.maxValue > 0) state.value.toFloat() / state.maxValue else 0f } }
    val seeks = remember(state) { Channel<Float>(Channel.CONFLATED) }
    LaunchedEffect(state) { seeks.receiveAsFlow().collectLatest { state.scrollTo((state.maxValue * it).toInt()) } }
    DisposableEffect(seeks) { onDispose { seeks.close() } }
    ScrollRail(state.isScrollInProgress, state.maxValue > 0 && state.maxValue < Int.MAX_VALUE, fraction, state.viewportSize.toFloat() / (state.maxValue.toLong() + state.viewportSize).coerceAtLeast(1)) { seeks.trySend(it) }
}

@Composable
private fun BoxScope.ScrollRail(scrolling: Boolean, scrollable: Boolean, fraction: Float, visibleFraction: Float, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(1) }
    val seek by rememberUpdatedState(onSeek)
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val currentVisible by rememberUpdatedState(visibleFraction)
    LaunchedEffect(scrolling, dragging, scrollable) {
        if (!scrollable) visible = false
        else if (scrolling || dragging) visible = true
        else { delay(1400); visible = false }
    }
    val color = MaterialTheme.colorScheme.primary
    AnimatedVisibility(visible && scrollable, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(48.dp), enter = fadeIn(), exit = fadeOut()) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { height = it.height.coerceAtLeast(1) }
            .semantics {
                contentDescription = "Scroll position"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { seek(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(height) {
                var travel = 1f
                detectVerticalDragGestures(
                    onDragStart = {
                        val thumb = (height * currentVisible).coerceIn(48.dp.toPx().coerceAtMost(height.toFloat()), height.toFloat())
                        travel = (height - thumb).coerceAtLeast(1f)
                        dragging = true; dragFraction = ((it.y - thumb / 2) / travel).coerceIn(0f, 1f); seek(dragFraction)
                    },
                    onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
                    onVerticalDrag = { change, amount -> change.consume(); dragFraction = (dragFraction + amount / travel).coerceIn(0f, 1f); seek(dragFraction) }
                )
            }) {
            val thumb = (size.height * visibleFraction).coerceIn(48.dp.toPx().coerceAtMost(size.height), size.height)
            val x = size.width - 8.dp.toPx()
            drawRoundRect(color.copy(alpha = .18f), Offset(x, 0f), Size(4.dp.toPx(), size.height), CornerRadius(4.dp.toPx()))
            drawRoundRect(color, Offset(x - 1.dp.toPx(), (size.height - thumb) * fraction), Size(6.dp.toPx(), thumb), CornerRadius(6.dp.toPx()))
        }
    }
}
