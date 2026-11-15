package cz.jan.conversationreader

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.lang.ref.WeakReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationReader(entry: LibraryEntry, store: LibraryStore, fontSize: Float, style: ReaderStyle, collapsePrompts: Boolean, backRequest: Int, initialQuery: String?, onBack: () -> Unit, onPreferences: () -> Unit) {
    val conversation = entry.conversation
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val viewportWidth = LocalWindowInfo.current.containerSize.width
    val displayRenderer = rememberMarkdownRenderer()
    val cache = remember(entry.id) { ReadingRenderCache(conversation, createMarkdownRenderer(context.applicationContext)) }
    val saved = remember(entry.id) { (store.reading.anchor(entry.id) ?: if (store.preferences.contains("position_${entry.id}")) ReadingAnchor(store.preferences.getInt("position_${entry.id}", 0)) else null)?.takeIf { conversation.messages.isNotEmpty() }?.let {
        val m = it.message.coerceIn(conversation.messages.indices)
        it.copy(message = m, version = it.version.coerceIn(conversation.messages[m].versions.indices))
    } }
    var selectedVersions by rememberSaveable { mutableStateOf(conversation.messages.mapIndexed { i, m -> (saved?.takeIf { it.message == i }?.version ?: store.preferences.getInt("version_${entry.id}_$i", m.selected)).coerceIn(m.versions.indices) }) }
    val versions = remember(selectedVersions) { selectedVersions.mapIndexed { i, v -> i to v }.toMap() }
    val groups = remember(conversation) { promptGroups(conversation) }
    var expanded by rememberSaveable { mutableStateOf(saved?.message?.let { listOf(groups.getOrElse(it) { 0 }) } ?: emptyList<Int>()) }
    var pendingRaw by remember { mutableStateOf(saved?.json()) }
    val pending = ReadingAnchor.parse(pendingRaw)
    var liveAnchor by remember { mutableStateOf(saved ?: ReadingAnchor(0, versions[0] ?: 0)) }
    var returnRaw by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var searching by rememberSaveable { mutableStateOf(!initialQuery.isNullOrBlank()) }
    var hit by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    var bookmarked by remember { mutableStateOf(store.reading.isBookmarked(entry.id)) }
    var selectReply by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val views = remember { mutableMapOf<String, WeakReference<TextView>>() }
    val words = remember(conversation) { conversation.wordCount() }
    val rendered by produceState<Map<Int, RenderedVersion>>(emptyMap(), versions, cache) {
        value = withContext(Dispatchers.Default) { buildMap { conversation.messages.indices.forEach { i -> ensureActive(); put(i, cache.version(i, versions.getValue(i))) } } }
    }
    val rows = remember(rendered, versions, expanded, collapsePrompts) { readerRows(conversation, versions, rendered, groups, expanded, collapsePrompts) }
    val rowIndexes = remember(rows) { rows.mapIndexed { i, row -> row.key to i }.toMap() }
    val currentRows by rememberUpdatedState(rows)
    val currentPending by rememberUpdatedState(pending)
    val currentRendered by rememberUpdatedState(rendered)
    val currentSearching by rememberUpdatedState(searching)
    val currentReturn by rememberUpdatedState(returnRaw)
    fun capture(): ReadingAnchor {
        val row = currentRows.getOrNull(list.firstVisibleItemIndex) ?: return liveAnchor
        val block = row.block ?: return row.anchor
        val view = views[block.key]?.get()
        val layout = view?.layout
        val y = (list.firstVisibleItemScrollOffset - with(density) { 2.dp.roundToPx() }).coerceAtLeast(0)
        if (layout == null) return liveAnchor.takeIf { it.message == block.message && it.version == block.version && it.part == block.part && it.character in block.start..block.end } ?: block.anchor
        val offset = layout.getLineStart(layout.getLineForVertical(y))
        return block.anchor.copy(character = (block.start + offset).coerceAtMost(block.end))
    }
    fun excerpt(anchor: ReadingAnchor): String = currentRendered[anchor.message]?.parts?.getOrNull(anchor.part)?.text?.toString()?.let { text -> text.substring(anchor.character.coerceIn(0, text.length)).take(150).trim() }.orEmpty()
    fun jump(anchor: ReadingAnchor, keepReturn: Boolean = true) {
        if (conversation.messages.isEmpty()) return
        if (keepReturn && returnRaw == null) returnRaw = capture().json()
        val m = anchor.message.coerceIn(conversation.messages.indices)
        val v = anchor.version.coerceIn(conversation.messages[m].versions.indices)
        selectedVersions = selectedVersions.toMutableList().apply { set(m, v) }
        expanded = (expanded + groups[m]).distinct()
        pendingRaw = anchor.copy(message = m, version = v).json()
        contents = false
        if (!searching) keyboard?.hide()
    }
    fun leaveSearch() { searching = false; keyboard?.hide() }
    fun back() {
        when {
            searching -> leaveSearch()
            returnRaw != null -> { val anchor = ReadingAnchor.parse(returnRaw); returnRaw = null; anchor?.let { jump(it, false) } }
            collapsePrompts && expanded.isNotEmpty() -> { val group = groups.getOrElse(capture().message) { 0 }; expanded = emptyList(); scope.launch { groups.distinct().indexOf(group).takeIf { it >= 0 }?.let { list.scrollToItem(it) } } }
            else -> onBack()
        }
    }
    var handledBack by remember { mutableIntStateOf(backRequest) }
    LaunchedEffect(backRequest) { if (handledBack != backRequest) { handledBack = backRequest; back() } }
    LaunchedEffect(rows, pendingRaw) {
        if (pending != null && rows.isNotEmpty()) {
            val parts = rendered[pending.message]?.parts
            val available = parts?.flatMap { it.blocks }?.firstOrNull()?.version == pending.version
            if (parts != null && parts.isEmpty()) {
                list.scrollToItem(rowForAnchor(rows, pending))
                liveAnchor = pending.copy(part = 0, character = 0)
                pendingRaw = null
            } else if (available) {
                val part = pending.part.coerceIn(0, parts!!.lastIndex)
                val normalized = pending.copy(part = part, character = pending.character.coerceIn(0, (parts[part].text.length - 1).coerceAtLeast(0)))
                if (normalized != pending) pendingRaw = normalized.json()
                else list.scrollToItem(rowForAnchor(rows, normalized))
            }
        }
    }
    // Re-layout uses the last semantic position, not a pixel offset from the old font or screen width.
    LaunchedEffect(fontSize, style.lineSpacing, style.font, style.margin, viewportWidth) {
        if (rendered.isNotEmpty() && pendingRaw == null) pendingRaw = liveAnchor.json()
    }
    LaunchedEffect(list, entry.id) {
        snapshotFlow { if (list.isScrollInProgress || currentPending != null) null else list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
            .filterNotNull().distinctUntilChanged().collect {
                val anchor = capture()
                liveAnchor = anchor
                if (!currentSearching && currentReturn == null) {
                    store.reading.saveAnchor(entry.id, anchor)
                    store.reading.record(entry, store.reference(entry), excerpt(anchor))
                }
            }
    }
    DisposableEffect(entry.id) { onDispose {
        val anchor = ReadingAnchor.parse(currentReturn) ?: liveAnchor
        store.reading.saveAnchor(entry.id, anchor)
        store.reading.record(entry, store.reference(entry), excerpt(anchor))
    } }
    val matches by produceState<List<SearchMatch>>(emptyList(), query, searching, cache) {
        if (!searching || query.isBlank()) value = emptyList()
        else {
            value = emptyList()
            delay(180)
            value = withContext(Dispatchers.Default) {
                val result = mutableListOf<SearchMatch>()
                conversation.messages.forEachIndexed { m, message -> message.versions.indices.forEach { v ->
                    ensureActive()
                    cache.version(m, v).parts.forEachIndexed { p, part -> matchRanges(part.text, query, true).forEach { result += SearchMatch(m, v, p, it.first, it.last + 1) } }
                } }
                result
            }
        }
    }
    fun goHit(index: Int) {
        if (matches.isEmpty()) return
        hit = (index + matches.size) % matches.size
        val match = matches[hit]
        jump(ReadingAnchor(match.message, match.version, match.part, match.start))
    }
    LaunchedEffect(matches) { hit = 0; if (searching && matches.isNotEmpty()) goHit(0) }
    val activeMatch = if (searching) matches.getOrNull(hit) else null
    val outline = remember(rendered, versions) { buildList {
        conversation.messages.forEachIndexed { i, message ->
            val v = versions.getValue(i)
            if (message.versions[v].role == "user") add(OutlineEntry(message.versions[v].parts.joinToString(" ") { it.text }.take(130), 0, ReadingAnchor(i, v), true))
            rendered[i]?.parts?.forEach { addAll(it.headings) }
        }
    } }
    Column(Modifier.fillMaxSize()) {
        ReaderToolbar(searching, query, { query = it }, { if (returnRaw == null) returnRaw = capture().json(); searching = true }, { leaveSearch() }, onPreferences, onBack = { back() }, extraActions = {
            IconButton(onClick = { contents = true }) { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_contents), "Contents") }
            BookmarkButton(bookmarked, { bookmarked = !bookmarked; store.reading.setBookmarked(entry.id, bookmarked) })
        })
        if (searching) SearchResultsBar(hit.coerceAtMost((matches.size - 1).coerceAtLeast(0)), matches.size, { goHit(hit - 1) }, { goHit(hit + 1) })
        Column(Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(conversation.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, color = readerLinkColor())
            if (collapsePrompts && expanded.isEmpty()) Text("${conversation.messages.size} messages · ${java.text.NumberFormat.getIntegerInstance().format(words)} words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (returnRaw != null && !searching) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { val anchor = ReadingAnchor.parse(returnRaw); returnRaw = null; anchor?.let { jump(it, false) } }) { Text("Return to reading") }
            TextButton(onClick = { returnRaw = null; store.reading.saveAnchor(entry.id, capture()) }) { Text("Read from here") }
        }
        if (rows.isEmpty() && conversation.messages.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = style.margin.dp, vertical = 4.dp)) {
                items(rows, key = { it.key }, contentType = { it.kind }) { row ->
                    val message = conversation.messages[row.message]
                    val version = message.versions[row.version]
                    when (row.kind) {
                        "preview" -> Card(onClick = { expanded = (expanded + groups[row.message]).distinct() }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Column(Modifier.padding(14.dp)) {
                            Text("▸ Expand prompt and replies", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            Text(version.parts.joinToString("\n") { it.text }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = readerFontFamily(style.font))
                        } }
                        "header" -> Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                            if (version.role != "user" || message.versions.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                                if (version.role != "user") Text(version.parts.mapNotNull { it.model }.distinct().joinToString(" · ").ifBlank { version.role }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else Spacer(Modifier.weight(1f))
                                if (message.versions.size > 1) {
                                    TextButton(enabled = row.version > 0, onClick = { selectedVersions = selectedVersions.toMutableList().apply { set(row.message, row.version - 1) }; store.preferences.edit().putInt("version_${entry.id}_${row.message}", row.version - 1).apply() }) { Text("‹") }
                                    Text("${row.version + 1}/${message.versions.size}", style = MaterialTheme.typography.labelSmall)
                                    TextButton(enabled = row.version < message.versions.lastIndex, onClick = { selectedVersions = selectedVersions.toMutableList().apply { set(row.message, row.version + 1) }; store.preferences.edit().putInt("version_${entry.id}_${row.message}", row.version + 1).apply() }) { Text("›") }
                                }
                            }
                            if (collapsePrompts && row.message == groups[row.message]) TextButton(onClick = { expanded = expanded - groups[row.message] }) { Text("▾ Collapse prompt and replies") }
                        }
                        "text" -> row.block?.let { block ->
                            val relevant = activeMatch?.takeIf { it.message == block.message && it.version == block.version && it.part == block.part }
                            ReadingTextBlock(block, displayRenderer, fontSize, style, if (searching) query else "", relevant, version.role == "user", onView = { view ->
                                views[block.key] = WeakReference(view)
                                val target = currentPending
                                if (target != null && rowForAnchor(currentRows, target) == rowIndexes[row.key] && target.message == block.message && target.version == block.version) {
                                    val layout = view.layout
                                    if (layout != null) {
                                        val offset = (target.character - block.start).coerceIn(0, block.text.length)
                                        val y = layout.getLineTop(layout.getLineForOffset(offset)) + with(density) { 2.dp.roundToPx() }
                                        pendingRaw = null
                                        liveAnchor = target
                                        scope.launch { list.scrollToItem(rowIndexes[row.key] ?: 0, y.coerceAtLeast(0)) }
                                    }
                                }
                            })
                            DisposableEffect(block.key) { onDispose { views.remove(block.key) } }
                        }
                        "footer" -> Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                            version.parts.forEach { it.stats?.let { stats -> Stats(stats) } }
                            rendered[row.message]?.parts?.flatMap { it.code }?.forEachIndexed { index, code ->
                                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy code ${index + 1}") }
                            }
                            Row {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(version.parts.joinToString("\n\n") { it.text })) }) { Text(if (version.role == "user") "Copy prompt" else "Copy reply") }
                                TextButton(onClick = { selectReply = row.message }) { Text("Select text") }
                            }
                        }
                    }
                }
                if (conversation.messages.isEmpty()) item { Text("This conversation has no messages yet.") }
            }
            LazyScrollSlider(list, listOf(rows.map { it.key }, fontSize, style.lineSpacing, style.font, style.margin, viewportWidth))
        }
        HorizontalDivider()
        val endBlock = rendered[conversation.messages.lastIndex]?.parts?.lastOrNull()?.blocks?.lastOrNull()
        ReaderBlockNavigation(list, rows, conversation, endBlock?.anchor?.copy(character = (endBlock.end - 1).coerceAtLeast(endBlock.start)), !searching && (!collapsePrompts || expanded.isNotEmpty()), { anchor -> jump(anchor) })

    }
    if (info) ConversationInfoDialog(entry, versions) { info = false }
    if (contents) ModalBottomSheet(onDismissRequest = { contents = false }) {
        Text("Contents", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(8.dp)) {
            if (outline.isEmpty()) item { Text("No prompts or headings in this conversation.") }
            items(outline) { item -> TextButton(onClick = { jump(item.anchor) }, modifier = Modifier.fillMaxWidth().padding(start = (item.level.coerceAtMost(4) * 10).dp)) { Text(if (item.prompt) "Prompt · ${item.label}" else item.label, Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis) } }
        }
    }
    selectReply?.let { message -> FullReplySelection(rendered[message]?.parts?.map { it.text }.orEmpty(), displayRenderer, fontSize, style) { selectReply = null } }
    notice?.let { AlertDialog(onDismissRequest = { notice = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }) }
}

@Composable
private fun ReadingTextBlock(block: ReadingBlock, renderer: Markwon, fontSize: Float, style: ReaderStyle, query: String, active: SearchMatch?, prompt: Boolean, onView: (TextView) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = readerLinkColor().toArgb()
    val text = remember(block, query, active) { SpannableStringBuilder(block.text).apply {
        matchRanges(block.text, query, true).forEach { range ->
            setSpan(BackgroundColorSpan(if (active?.start == block.start + range.first) 0xFFFFB74D.toInt() else 0xFFFFE082.toInt()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(0xFF171A17.toInt()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } }
    val callback by rememberUpdatedState(onView)
    Surface(color = if (prompt) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        AndroidView(factory = { BlockTextView(it).apply { setTextIsSelectable(true); setPadding(0, 0, 0, 0) } }, update = { view ->
            if (view.currentTextColor != color) view.setTextColor(color)
            if (view.linkTextColors.defaultColor != link) view.setLinkTextColor(link)
            if (view.tag !== text) { renderer.setParsedMarkdown(view, text); view.tag = text }
            applyReaderTypography(view, fontSize, style)
            view.doOnLayout { if (view.isAttachedToWindow) callback(view) }
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = if (prompt) 4.dp else 0.dp, vertical = 2.dp))
    }
}

private class BlockTextView(context: android.content.Context) : TextView(context)

@Composable
private fun ReaderBlockNavigation(list: androidx.compose.foundation.lazy.LazyListState, rows: List<ReaderRow>, conversation: Conversation, endAnchor: ReadingAnchor?, navigationEnabled: Boolean, jump: (ReadingAnchor) -> Unit) {
    val target by remember(list, rows) { derivedStateOf {
        val current = rows.getOrNull(list.firstVisibleItemIndex)?.message ?: 0
        val prompts = conversation.messages.indices.filter { conversation.messages[it].versions[conversation.messages[it].selected].role == "user" }
        prompts.lastOrNull { it < current || (it == current && list.firstVisibleItemScrollOffset > 0) } to prompts.firstOrNull { it > current }
    } }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ReadingProgress(list, rows.map { it.block?.text?.length?.coerceAtLeast(1) ?: 1 }, Modifier.weight(.65f))
        if (navigationEnabled) {
            TextButton(enabled = target.first != null, onClick = { target.first?.let { jump(ReadingAnchor(it, conversation.messages[it].selected)) } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("↑ Previous", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) }
            TextButton(enabled = endAnchor != null, onClick = { endAnchor?.let(jump) }, modifier = Modifier.weight(.65f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("End ↓", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            TextButton(enabled = target.second != null, onClick = { target.second?.let { jump(ReadingAnchor(it, conversation.messages[it].selected)) } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("Next ↓", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) }
        } else Spacer(Modifier.weight(2.3f))
    }
}

@Composable
private fun FullReplySelection(parts: List<Spanned>, renderer: Markwon, size: Float, style: ReaderStyle, close: () -> Unit) {
    val text = remember(parts) { SpannableStringBuilder().apply { parts.forEachIndexed { i, p -> if (i > 0) append("\n\n"); append(p) } } }
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val clipboard = LocalClipboardManager.current
    val readerSelectionLink = readerLinkColor().toArgb()
    AlertDialog(onDismissRequest = close, title = { Text("Select reply text") }, text = {
        val scroll = androidx.compose.foundation.rememberScrollState()
        Box { Column(Modifier.then(Modifier).verticalScrollCompat(scroll)) {
            AndroidView(factory = { TextView(it).apply { setTextIsSelectable(true) } }, update = { view -> view.setTextColor(color); view.setLinkTextColor(readerSelectionLink); if (view.tag !== text) { renderer.setParsedMarkdown(view, text); view.tag = text }; applyReaderTypography(view, size, style) }, modifier = Modifier.fillMaxWidth())
        }; PlainScrollSlider(scroll) }
    }, confirmButton = { TextButton(onClick = close) { Text("Done") } }, dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(text.toString())) }) { Text("Copy all") } })
}

private fun Modifier.verticalScrollCompat(state: androidx.compose.foundation.ScrollState) = this.then(Modifier.verticalScroll(state))
