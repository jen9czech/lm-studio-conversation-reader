package cz.jan.conversationreader

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationReader(entry: LibraryEntry, store: LibraryStore, fontSize: Float, style: ReaderStyle, collapsePrompts: Boolean, backRequest: Int, resumeReading: Boolean, onBack: () -> Unit, onPreferences: () -> Unit) {
    val conversation = entry.conversation
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val viewportWidth = LocalWindowInfo.current.containerSize.width
    val titleSize = MaterialTheme.typography.titleLarge.fontSize
    val layoutKey = "$viewportWidth:${density.density}:${density.fontScale}:$fontSize:$style:$titleSize"
    val displayRenderer = rememberMarkdownRenderer()
    val cache = remember(entry.id) { ReadingRenderCache(conversation, createMarkdownRenderer(context.applicationContext)) }
    val saved = remember(entry.id) { ((if (resumeReading) store.reading.resumeAnchor(entry.id) else store.reading.anchor(entry.id)) ?: if (store.preferences.contains("position_${entry.id}")) ReadingAnchor(store.preferences.getInt("position_${entry.id}", 0)) else null)?.takeIf { conversation.messages.isNotEmpty() }?.let {
        val m = it.message.coerceIn(conversation.messages.indices)
        val v = it.version.coerceIn(conversation.messages[m].versions.indices)
        val parts = conversation.messages[m].versions[v].parts
        val answer = parts.indices.firstOrNull { p -> p >= it.part && !parts[p].thinking } ?: parts.indices.firstOrNull { p -> !parts[p].thinking }
        if (!resumeReading && parts.getOrNull(it.part)?.thinking == true && answer != null) it.copy(message = m, version = v, part = answer, character = 0)
        else it.copy(message = m, version = v)
    } }
    var selectedVersions by rememberSaveable { mutableStateOf(conversation.messages.mapIndexed { i, m -> (saved?.takeIf { it.message == i }?.version ?: store.preferences.getInt("version_${entry.id}_$i", m.selected)).coerceIn(m.versions.indices) }) }
    val versions = remember(selectedVersions) { selectedVersions.mapIndexed { i, v -> i to v }.toMap() }
    val groups = remember(conversation, versions) { promptGroups(conversation, versions) }
    var highlightedGroup by rememberSaveable {
        mutableStateOf(store.reading.resumeAnchor(entry.id)?.message?.let { groups.getOrNull(it) })
    }
    var expanded by rememberSaveable { mutableStateOf(if (resumeReading && saved != null) saved.openGroups.filter { it in groups }.ifEmpty { listOf(groups[saved.message]) } else emptyList()) }
    var pendingRaw by remember { mutableStateOf(saved?.json()) }
    var jumpGeneration by remember { mutableIntStateOf(0) }
    val pending = ReadingAnchor.parse(pendingRaw)
    var expandedPrompts by remember { mutableStateOf(saved?.takeIf { (it.character > 0 || (resumeReading && it.rowKey?.startsWith("text:") == true)) && conversation.messages[it.message].versions[it.version].role == "user" }?.let { setOf("${it.message}:${it.version}") } ?: emptySet<String>()) }
    var longPrompts by remember { mutableStateOf(emptySet<String>()) }
    var expandedThinking by remember {
        val anchor = saved?.takeIf { resumeReading && conversation.messages[it.message].versions[it.version].parts.getOrNull(it.part)?.thinking == true }
        mutableStateOf(anchor?.let { setOf("${it.message}:${it.version}:${it.part}") }.orEmpty())
    }
    var pendingFooter by remember { mutableStateOf<Int?>(null) }
    var liveAnchor by remember { mutableStateOf(saved ?: ReadingAnchor(0, versions[0] ?: 0)) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var hit by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(false) }
    var contentsAnchor by remember { mutableStateOf<ReadingAnchor?>(null) }
    var bookmarked by remember { mutableStateOf(store.reading.isBookmarked(entry.id)) }
    var notice by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val views = remember { mutableMapOf<String, WeakReference<TextView>>() }
    val words = entry.words
    val rendered by produceState<Map<Int, RenderedVersion>>(emptyMap(), versions, cache) {
        val previous = value
        value = withContext(Dispatchers.Default) {
            buildMap {
                conversation.messages.indices.forEach { i ->
                    ensureActive()
                    val version = versions.getValue(i)
                    put(i, previous[i]?.takeIf { it.parts.firstOrNull()?.blocks?.firstOrNull()?.version == version }
                        ?: cache.version(i, version))
                }
            }
        }
    }
    val rows = remember(rendered, versions, expanded, collapsePrompts, expandedThinking, expandedPrompts) { readerRows(conversation, versions, rendered, groups, expanded, collapsePrompts, expandedThinking, expandedPrompts) }
    val replyPreviews = remember(conversation, versions, rendered, groups) {
        conversation.messages.indices.filter { conversation.messages[it].versions[versions.getValue(it)].role == "assistant" }
            .groupBy { groups[it] }.mapNotNull { (group, messages) ->
                if (messages.any { it !in rendered }) null else {
                    val parts = messages.flatMap { rendered.getValue(it).parts }.filterNot { it.thinking }
                    group to (parts.sumOf { countWords(it.text) } to parts.flatMap { it.headings })
                }
            }.toMap()
    }
    val firstContentRow = remember(rows) { rows.indexOfFirst { it.block != null || it.kind == "preview" }.coerceAtLeast(0) }
    val beginningTolerance = with(density) { 8.dp.roundToPx() }
    val atBeginning by remember(list, firstContentRow, beginningTolerance) { derivedStateOf {
        list.firstVisibleItemIndex < firstContentRow || (list.firstVisibleItemIndex == firstContentRow && list.firstVisibleItemScrollOffset <= beginningTolerance)
    } }
    val rowIndexes = remember(rows) { rows.mapIndexed { i, row -> row.key to i }.toMap() }
    val targetRow = remember(rows, pending, layoutKey) {
        pending?.takeUnless { it.layoutKey == layoutKey && it.rowKey in rowIndexes }
            ?.let { rows.getOrNull(rowForAnchor(rows, it))?.key }
    }
    LaunchedEffect(rows, pendingFooter) {
        pendingFooter?.let { message ->
            val index = rows.indexOfFirst { it.kind == "footer" && it.message == message }
            if (index >= 0) list.scrollToItem(index)
            pendingFooter = null
        }
    }
    val currentRows by rememberUpdatedState(rows)
    val currentPending by rememberUpdatedState(pending)
    val currentRendered by rememberUpdatedState(rendered)
    val currentSearching by rememberUpdatedState(searching)
    val currentLayoutKey by rememberUpdatedState(layoutKey)
    val currentDensity by rememberUpdatedState(density)
    var observedLayoutKey by remember { mutableStateOf(layoutKey) }
    fun saveResumePosition(anchor: ReadingAnchor) {
        if (!collapsePrompts || expanded.isNotEmpty()) store.reading.saveResumeAnchor(entry.id, anchor.copy(openGroups = expanded))
    }
    fun capture(): ReadingAnchor {
        val row = currentRows.getOrNull(list.firstVisibleItemIndex) ?: return liveAnchor
        if (row.kind == "promptPreview") return row.anchor
        val block = row.block ?: return row.anchor
        val view = views[block.key]?.get()
        val layout = view?.layout
        val inset = block.thinking || conversation.messages[block.message].versions[block.version].role == "user"
        val y = (list.firstVisibleItemScrollOffset - with(currentDensity) { (if (inset) 8.dp else 2.dp).roundToPx() }).coerceAtLeast(0)
        if (layout == null) return liveAnchor.takeIf { it.message == block.message && it.version == block.version && it.part == block.part && it.character in block.start..block.end } ?: block.anchor
        val offset = layout.getLineStart(layout.getLineForVertical(y))
        return block.anchor.copy(character = (block.start + offset).coerceAtMost(block.end))
    }
    fun captureViewport(): ReadingAnchor {
        val row = currentRows.getOrNull(list.firstVisibleItemIndex) ?: return liveAnchor
        return capture().copy(rowKey = row.key, scrollOffset = list.firstVisibleItemScrollOffset, layoutKey = currentLayoutKey)
    }
    fun excerpt(anchor: ReadingAnchor): String = currentRendered[anchor.message]?.parts?.getOrNull(anchor.part)?.text?.toString()?.let { text -> text.substring(anchor.character.coerceIn(0, text.length)).take(150).trim() }.orEmpty()
    fun jump(anchor: ReadingAnchor) {
        if (conversation.messages.isEmpty()) return
        jumpGeneration++
        val m = anchor.message.coerceIn(conversation.messages.indices)
        val v = anchor.version.coerceIn(conversation.messages[m].versions.indices)
        if (conversation.messages[m].versions[v].role == "user") expandedPrompts = expandedPrompts + "$m:$v"
        selectedVersions = selectedVersions.toMutableList().apply { set(m, v) }
        highlightedGroup = groups[m]
        expanded = (expanded + groups[m]).distinct()
        if (conversation.messages[m].versions[v].parts.getOrNull(anchor.part)?.thinking == true) expandedThinking = expandedThinking + "$m:$v:${anchor.part}"
        pendingRaw = anchor.copy(message = m, version = v).json()
        contentsAnchor = null
        if (!searching) keyboard?.hide()
    }
    fun leaveSearch() { searching = false; keyboard?.hide() }
    fun back() {
        when {
            searching -> leaveSearch()
            collapsePrompts && expanded.isNotEmpty() -> { val anchor = currentPending ?: captureViewport(); saveResumePosition(anchor); val group = groups.getOrElse(anchor.message) { 0 }; expanded = emptyList(); scope.launch { groups.distinct().indexOf(group).takeIf { it >= 0 }?.let { list.scrollToItem(it) } } }
            else -> { if (currentPending == null) saveResumePosition(captureViewport()); onBack() }
        }
    }
    var handledBack by remember { mutableIntStateOf(backRequest) }
    LaunchedEffect(backRequest) { if (handledBack != backRequest) { handledBack = backRequest; back() } }
    LaunchedEffect(rows, pendingRaw) {
        if (pending != null && rows.isNotEmpty()) {
            // Restore the group location without automatically opening its prompt/replies.
            if (collapsePrompts && expanded.isEmpty() && groups[pending.message] !in expanded) {
                val index = rows.indexOfFirst { it.kind == "preview" && it.message == groups[pending.message] }
                if (index >= 0) {
                    list.scrollToItem(index)
                    liveAnchor = pending
                    pendingRaw = null
                }
                return@LaunchedEffect
            }
            if (pending.layoutKey == layoutKey && rendered.isNotEmpty()) {
                val index = rows.indexOfFirst { it.key == pending.rowKey }
                if (index >= 0) {
                    snapshotFlow { list.layoutInfo.totalItemsCount }.first { it == rows.size }
                    list.scrollToItem(index, pending.scrollOffset.coerceAtLeast(0))
                    liveAnchor = pending
                    pendingRaw = null
                    return@LaunchedEffect
                }
            }
            val previewIndex = rows.indexOfFirst { it.kind == "preview" && it.message == groups[pending.message] }
            if (previewIndex >= 0) {
                list.scrollToItem(previewIndex)
                liveAnchor = pending
                pendingRaw = null
                return@LaunchedEffect
            }
            val parts = rendered[pending.message]?.parts
            val available = parts?.flatMap { it.blocks }?.firstOrNull()?.version == pending.version
            val closedThinking = conversation.messages.getOrNull(pending.message)?.versions?.getOrNull(pending.version)?.parts?.getOrNull(pending.part)?.thinking == true && "${pending.message}:${pending.version}:${pending.part}" !in expandedThinking
            if (closedThinking) {
                val index = rows.indexOfFirst { it.kind == "thinking" && it.message == pending.message && it.version == pending.version && it.part == pending.part }
                if (index >= 0) { list.scrollToItem(index); liveAnchor = pending.copy(character = 0); pendingRaw = null }
            } else if (parts != null && parts.isEmpty()) {
                list.scrollToItem(rowForAnchor(rows, pending))
                liveAnchor = pending.copy(part = 0, character = 0)
                pendingRaw = null
            } else if (available) {
                val part = pending.part.coerceIn(0, parts!!.lastIndex)
                val normalized = pending.copy(part = part, character = pending.character.coerceIn(0, (parts[part].text.length - 1).coerceAtLeast(0)))
                if (normalized != pending) pendingRaw = normalized.json()
                else {
                    val index = rowForAnchor(rows, normalized)
                    if (list.layoutInfo.visibleItemsInfo.none { it.key == rows[index].key }) list.scrollToItem(index)
                }
            }
        }
    }
    // Re-layout uses the last semantic position, not a pixel offset from the old font or screen width.
    LaunchedEffect(layoutKey) {
        if (observedLayoutKey != layoutKey && liveAnchor.rowKey != null && pendingRaw == null) {
            val anchor = liveAnchor
            val keepRow = anchor.rowKey?.let { it in rowIndexes && !it.startsWith("text:") } == true
            pendingRaw = if (keepRow) anchor.copy(layoutKey = layoutKey, scrollOffset = 0).json()
                else anchor.copy(rowKey = null, layoutKey = null).json()
        }
        observedLayoutKey = layoutKey
    }
    LaunchedEffect(list, entry.id) {
        snapshotFlow {
            val index = list.firstVisibleItemIndex
            val key = currentRows.getOrNull(index)?.key
            val measuredKey = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key
            if (observedLayoutKey != currentLayoutKey || list.isScrollInProgress || currentPending != null || key == null || key != measuredKey) null
            else Triple(key, list.firstVisibleItemScrollOffset, currentLayoutKey)
        }
            .filterNotNull().distinctUntilChanged().collect {
                val anchor = captureViewport()
                liveAnchor = anchor
                if (!currentSearching) {
                    saveResumePosition(anchor)
                    store.reading.saveAnchor(entry.id, anchor)
                    store.reading.record(entry, store.reference(entry), excerpt(anchor))
                }
            }
    }
    DisposableEffect(entry.id) { onDispose {
        val anchor = liveAnchor
        store.reading.saveAnchor(entry.id, anchor)
        store.reading.record(entry, store.reference(entry), excerpt(anchor))
    } }
    val searchResult by produceState<Pair<String, List<SearchMatch>>?>(null, query, searching, cache) {
        value = null
        if (searching && query.isNotBlank()) {
            delay(180)
            val visibleVersions = currentRendered
            val normalizedQuery = normalizeReaderQuery(query)
            val found = withContext(Dispatchers.Default) {
                val result = mutableListOf<SearchMatch>()
                conversation.messages.forEachIndexed { m, message -> message.versions.indices.forEach { v ->
                    ensureActive()
                    val visible = visibleVersions[m]?.takeIf { it.parts.firstOrNull()?.blocks?.firstOrNull()?.version == v }
                    cache.searchParts(m, v, visible).forEachIndexed { p, text ->
                        text.ranges(normalizedQuery).forEach { result += SearchMatch(m, v, p, it.first, it.last + 1) }
                    }
                } }
                result
            }
            value = query to found
        }
    }
    val currentSearchResult = searchResult?.takeIf { searching && it.first == query }
    val matches = currentSearchResult?.second.orEmpty()
    fun goHit(index: Int) {
        if (matches.isEmpty()) return
        hit = (index + matches.size) % matches.size
        val match = matches[hit]
        jump(ReadingAnchor(match.message, match.version, match.part, match.start))
    }
    LaunchedEffect(matches) { hit = 0; if (searching && matches.isNotEmpty()) goHit(0) }
    val activeMatch = if (searching) matches.getOrNull(hit) else null
    val matchesByPart = remember(matches, searching) {
        if (searching) matches.groupBy { Triple(it.message, it.version, it.part) } else emptyMap()
    }
    val outline = remember(rendered, versions, expandedThinking) { buildList {
        conversation.messages.forEachIndexed { i, message ->
            val v = versions.getValue(i)
            if (message.versions[v].role == "user") add(OutlineEntry(message.versions[v].parts.joinToString(" ") { it.text }.take(130), 0, ReadingAnchor(i, v), true))
            rendered[i]?.parts?.forEachIndexed { part, renderedPart ->
                if (!renderedPart.thinking || "$i:$v:$part" in expandedThinking) addAll(renderedPart.headings)
            }
        }
    } }
    val visibleRow = rows.getOrNull(list.firstVisibleItemIndex)
    val position = visibleRow?.let { row ->
        if (liveAnchor.rowKey == row.key) liveAnchor else row.anchor
    } ?: liveAnchor
    val promptGroups = remember(groups, conversation, versions) {
        conversation.messages.indices.filter { conversation.messages[it].versions[versions.getValue(it)].role == "user" }.map { groups[it] }.distinct()
    }
    val currentGroup = groups.getOrNull(position.message)
    val promptNumber = promptGroups.indexOf(currentGroup).takeIf { it >= 0 }?.plus(1)
    val currentHeading = remember(rendered, groups, currentGroup, position) {
        val candidates = rendered.entries.sortedBy { it.key }.filter { groups.getOrNull(it.key) == currentGroup }
            .flatMap { (_, version) -> version.parts.filterNot { it.thinking }.flatMap { it.headings } }
            .filter { h -> h.anchor.message < position.message || (h.anchor.message == position.message &&
                compareValuesBy(h.anchor, position, ReadingAnchor::part, ReadingAnchor::character) <= 0) }
        candidates.minOfOrNull { it.level }?.let { level -> candidates.lastOrNull { it.level == level }?.label }
    }
    val readingWidth = with(density) {
        (fontSize.sp.toDp() * if (style.font == "Monospaced") 44f else 39f).coerceIn(540.dp, 880.dp)
    }
    val readingColumn = Modifier.widthIn(max = readingWidth).fillMaxWidth()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ReaderToolbar(searching, query, { query = it }, { searching = true }, { leaveSearch() }, onPreferences, onBack = { back() }, extraActions = {
            IconButton(onClick = { contentsAnchor = currentPending ?: captureViewport() }) { Icon(painterResource(R.drawable.ic_contents), "Contents") }
            BookmarkButton(bookmarked, { bookmarked = !bookmarked; store.reading.setBookmarked(entry.id, bookmarked) })
        })
        if (searching && query.isNotBlank()) SearchResultsBar(hit.coerceAtMost((matches.size - 1).coerceAtLeast(0)),
            matches.size, currentSearchResult != null, { goHit(hit - 1) }, { goHit(hit + 1) })
        Column(readingColumn.clickable(onClickLabel = "Open conversation info") { info = true }.padding(horizontal = style.margin.dp, vertical = 6.dp)) {
            Text(conversation.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, color = readerLinkColor())
            if ((!collapsePrompts || expanded.isNotEmpty()) && promptNumber != null) {
                Text(buildString {
                    append("Prompt $promptNumber of ${promptGroups.size}")
                    if (!currentHeading.isNullOrBlank()) append(" · ").append(currentHeading)
                }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (atBeginning) {
                val counts = remember(conversation, versions) { conversation.messageCounts(versions) }
                val numberFormat = remember { java.text.NumberFormat.getIntegerInstance() }
                val (count, unit) = when {
                    counts.prompts > 0 -> counts.prompts to if (counts.prompts == 1) "prompt" else "prompts"
                    counts.replies > 0 -> counts.replies to if (counts.replies == 1) "reply" else "replies"
                    else -> counts.total to if (counts.total == 1) "message" else "messages"
                }
                Text("${numberFormat.format(count)} $unit · ${numberFormat.format(words)} words",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (rows.isEmpty() && conversation.messages.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(readingColumn.weight(1f)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = style.margin.dp, vertical = 4.dp)) {
                items(rows, key = { it.key }, contentType = { if (it.key.startsWith("preview:")) "promptGroup" else it.kind }) { row ->
                    val message = conversation.messages[row.message]
                    val version = message.versions[row.version]
                    val disclosure = collapsePrompts && row.message == groups[row.message] && (row.kind == "preview" || row.kind == "header")
                    if (disclosure) {
                        val closed = row.kind == "preview"
                        val highlighted = closed && row.message == highlightedGroup
                        Card(onClick = {
                            if (closed) {
                                highlightedGroup = groups[row.message]
                                expanded = (expanded + groups[row.message]).distinct()
                            } else expanded = expanded - groups[row.message]
                        },
                            border = if (closed) androidx.compose.foundation.BorderStroke(1.dp, if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .8f)) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    highlighted -> MaterialTheme.colorScheme.primaryContainer
                                    closed -> MaterialTheme.colorScheme.surfaceContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (closed) 8.dp else 0.dp).semantics {
                                stateDescription = when {
                                    highlighted -> "Collapsed, last opened"
                                    closed -> "Collapsed"
                                    else -> "Expanded"
                                }
                                onClick(label = if (closed) "Expand prompt and replies" else "Collapse prompt and replies", action = null)
                            }) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (closed) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(version.parts.joinToString("\n") { it.text }.ifBlank { "Empty prompt" },
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            fontFamily = readerFontFamily(style.font), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
                                        replyPreviews[row.message]?.let { (words, headings) ->
                                            Text("${java.text.NumberFormat.getIntegerInstance().format(words)} words",
                                                Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = .75f))
                                            if (headings.isNotEmpty()) Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                headings.forEach { heading ->
                                                    Text(heading.label, Modifier.padding(start = ((heading.level - 1).coerceIn(0, 5) * 12).dp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (heading.level == 1) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }
                                } else Text("Collapse prompt and replies", Modifier.weight(1f), color = readerLinkColor(), style = MaterialTheme.typography.labelSmall)
                                Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(22.dp).rotate(if (closed) 0f else 90f), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    when (row.kind) {
                        "header" -> Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                            if (version.role != "user" || message.versions.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                                if (version.role != "user") Text(version.parts.mapNotNull { it.model }.distinct().joinToString(" · ").ifBlank { version.role }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                else Spacer(Modifier.weight(1f))
                                if (message.versions.size > 1) {
                                    TextButton(enabled = row.version > 0, modifier = Modifier.semantics { contentDescription = "Previous message version" }, onClick = { selectedVersions = selectedVersions.toMutableList().apply { set(row.message, row.version - 1) }; store.preferences.edit().putInt("version_${entry.id}_${row.message}", row.version - 1).apply() }) { Text("‹") }
                                    Text("${row.version + 1}/${message.versions.size}", Modifier.semantics { contentDescription = "Version ${row.version + 1} of ${message.versions.size}" }, style = MaterialTheme.typography.labelSmall)
                                    TextButton(enabled = row.version < message.versions.lastIndex, modifier = Modifier.semantics { contentDescription = "Next message version" }, onClick = { selectedVersions = selectedVersions.toMutableList().apply { set(row.message, row.version + 1) }; store.preferences.edit().putInt("version_${entry.id}_${row.message}", row.version + 1).apply() }) { Text("›") }
                                }
                            }
                        }
                        "thinking" -> Unit
                        "thinkingEnd" -> TextButton(onClick = {
                            expandedThinking = expandedThinking - "${row.message}:${row.version}:${row.part}"
                            pendingRaw = null
                            val owner = groups[row.message]
                            pendingFooter = if (conversation.messages[owner].versions[versions.getValue(owner)].role == "user") owner else row.message
                        }, modifier = Modifier.fillMaxWidth()) { Text("↑ Collapse thinking", style = MaterialTheme.typography.labelSmall) }
                        "text", "promptPreview" -> row.block?.let { block ->
                            val partMatches = matchesByPart[Triple(block.message, block.version, block.part)].orEmpty()
                            val target = pending?.takeIf { anchor ->
                                row.key == targetRow && anchor.message == block.message && anchor.version == block.version
                            }
                            ReadingTextBlock(block, displayRenderer, fontSize, style, partMatches, activeMatch, version.role == "user", highlighted = row.message == highlightedGroup, preview = row.kind == "promptPreview", target = target, onOverflow = { overflow ->
                                val key = "${row.message}:${row.version}"
                                longPrompts = if (overflow) longPrompts + key else longPrompts - key
                            }, onView = { view, anchor ->
                                views[block.key] = WeakReference(view)
                                if (anchor != null && currentPending == anchor && currentLayoutKey == layoutKey) {
                                    val layout = view.layout
                                    if (layout != null) {
                                        val offset = (anchor.character - block.start).coerceIn(0, block.text.length)
                                        val line = layout.getLineForOffset(offset)
                                        val y = layout.getLineTop(line) + with(density) { (if (version.role == "user" || block.thinking) 8.dp else 2.dp).roundToPx() }
                                        val centerMatch = activeMatch?.let {
                                            it.message == anchor.message && it.version == anchor.version && it.part == anchor.part && it.start == anchor.character
                                        } == true
                                        val generation = jumpGeneration
                                        scope.launch {
                                            if (generation == jumpGeneration && currentPending == anchor && currentLayoutKey == layoutKey) {
                                                val index = currentRows.indexOfFirst { it.key == row.key }
                                                if (index >= 0) {
                                                    val scrollOffset = if (centerMatch) {
                                                        y + (layout.getLineBottom(line) - layout.getLineTop(line)) / 2 - list.layoutInfo.viewportSize.height / 2
                                                    } else y.coerceAtLeast(0)
                                                    list.scrollToItem(index, scrollOffset)
                                                    if (generation == jumpGeneration && currentPending == anchor && currentLayoutKey == layoutKey) {
                                                        liveAnchor = anchor
                                                        pendingRaw = null
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            })
                            DisposableEffect(block.key) { onDispose { views.remove(block.key) } }
                        }
                        "footer" -> Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                            val statistics = version.parts.mapNotNull { it.stats }
                            val copyAction: @Composable RowScope.() -> Unit = {
                                IconButton(enabled = version.parts.any { !it.thinking && it.text.isNotBlank() }, onClick = { clipboard.setText(AnnotatedString(version.parts.filter { !it.thinking }.joinToString("\n\n") { it.text })) }) {
                                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_copy), if (version.role == "user") "Copy prompt" else "Copy reply", modifier = Modifier.size(18.dp))
                                }
                            }
                            rendered[row.message]?.parts?.filterIndexed { p, part -> !part.thinking || "${row.message}:${row.version}:$p" in expandedThinking }?.flatMap { it.code }?.forEachIndexed { index, code ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Code ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_copy), "Copy code ${index + 1}", Modifier.size(18.dp))
                                    }
                                }
                            }
                            val promptKey = "${row.message}:${row.version}"
                            val expandablePrompt = version.role == "user" && promptKey in longPrompts
                            val targets = thinkingTargets(conversation, versions, groups, row.message)
                            if (expandablePrompt || targets.isNotEmpty() || statistics.isEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (expandablePrompt) TextButton(onClick = {
                                        val opening = promptKey !in expandedPrompts
                                        expandedPrompts = if (opening) expandedPrompts + promptKey else expandedPrompts - promptKey
                                        pendingRaw = ReadingAnchor(row.message, row.version).json()
                                    }, modifier = Modifier.semantics {
                                        val open = promptKey in expandedPrompts
                                        contentDescription = if (open) "Collapse prompt" else "Expand prompt"
                                        stateDescription = if (open) "Expanded" else "Collapsed"
                                    }) {
                                        Text(if (promptKey in expandedPrompts) "▾ Collapse prompt" else "▸ Expand prompt", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (targets.isNotEmpty()) {
                                        val keys = targets.map { "${it.message}:${it.version}:${it.part}" }.toSet()
                                        val open = keys.any { it in expandedThinking }
                                        TextButton(onClick = {
                                            expandedThinking = if (open) expandedThinking - keys else expandedThinking + keys
                                            pendingRaw = null
                                        }, modifier = Modifier.semantics {
                                            contentDescription = if (open) "Hide thinking" else "Show thinking"
                                            stateDescription = if (open) "Expanded" else "Collapsed"
                                        }) { Text(if (open) "▾ Thinking" else "▸ Thinking", maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                                if (statistics.isEmpty()) copyAction()
                            }
                            statistics.forEachIndexed { i, stats -> Stats(stats, trailingActions = { if (i == statistics.lastIndex) copyAction() }) }
                        }
                    }
                }
                if (conversation.messages.isEmpty()) item { Text("This conversation has no messages yet.") }
            }
            LazyScrollSlider(list, listOf(rows.map { it.key }, fontSize, style.lineSpacing, style.font, style.margin, viewportWidth))
        }
        HorizontalDivider()
        ReaderBlockNavigation(list, rows, rendered, conversation, versions, !searching && (!collapsePrompts || expanded.isNotEmpty()), { anchor -> jump(anchor) }, { progress -> if (!searching && (!collapsePrompts || expanded.isNotEmpty())) store.preferences.edit().putInt("readingProgress_${entry.id}", progress).apply() })

    }
    if (info) ConversationInfoDialog(entry, versions) { info = false }
    contentsAnchor?.let { anchor -> ReaderContents(outline, replyPreviews, anchor, { contentsAnchor = null }, ::jump) }
    notice?.let { AlertDialog(onDismissRequest = { notice = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContents(outline: List<OutlineEntry>, replyPreviews: Map<Int, Pair<Int, List<OutlineEntry>>>, anchor: ReadingAnchor, onClose: () -> Unit, onJump: (ReadingAnchor) -> Unit) {
    val current = currentOutlineIndex(outline, anchor)
    val sections = remember(outline) {
        val grouped = mutableListOf<MutableList<OutlineEntry>>()
        outline.forEach { item ->
            if (item.prompt || grouped.isEmpty()) grouped.add(mutableListOf())
            grouped.last().add(item)
        }
        grouped
    }
    val currentSection = sections.indexOfFirst { group -> current >= 0 && outline[current] in group }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = currentSection)
    LaunchedEffect(currentSection) { if (sections.isNotEmpty()) state.scrollToItem(currentSection) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Contents", Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), state = state, contentPadding = PaddingValues(12.dp)) {
            if (outline.isEmpty()) item { Text("No prompts or headings in this conversation.") }
            items(sections.size) { index ->
                val section = sections[index]
                val first = section.first()
                val selected = index == currentSection && current >= 0
                Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .8f)),
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (selected) Text("Current section", style = MaterialTheme.typography.labelSmall)
                        Text(first.label, Modifier.fillMaxWidth().clickable { onJump(first.anchor) }.padding(vertical = 4.dp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (first.prompt) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                            fontWeight = if (!first.prompt && first.level == 1) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                        replyPreviews[first.anchor.message]?.let { (words, _) ->
                            Text("${java.text.NumberFormat.getIntegerInstance().format(words)} words", Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.labelSmall)
                        }
                        section.drop(1).forEach { item ->
                            Text(item.label, Modifier.fillMaxWidth().padding(start = ((item.level - 1).coerceIn(0, 5) * 12).dp)
                                .clickable { onJump(item.anchor) }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (item.level == 1) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingTextBlock(block: ReadingBlock, renderer: Markwon, fontSize: Float, style: ReaderStyle, matches: List<SearchMatch>, active: SearchMatch?, prompt: Boolean, highlighted: Boolean, preview: Boolean = false, target: ReadingAnchor?, onOverflow: (Boolean) -> Unit = {}, onView: (TextView, ReadingAnchor?) -> Unit) {
    val color = (if (prompt) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface).toArgb()
    val link = readerLinkColor().toArgb()
    val paragraphGap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val text = remember(block, matches, active, paragraphGap) { thinkingParagraphSpacing(block.text, if (block.thinking) paragraphGap else 0).apply {
        val index = matches.binarySearchBy(block.start + 1) { it.end }
        val first = if (index >= 0) index else -index - 1
        for (i in first until matches.size) {
            val match = matches[i]
            if (match.start >= block.end) break
            val range = match.rangeIn(block.start, block.end) ?: continue
            setSpan(BackgroundColorSpan(if (active == match) 0xFFFFB74D.toInt() else 0xFFFFE082.toInt()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(0xFF171A17.toInt()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } }
    val callback by rememberUpdatedState(onView)
    val overflowCallback by rememberUpdatedState(onOverflow)
    val inset = prompt || block.thinking
    Surface(modifier = Modifier.padding(start = if (inset) 16.dp else 0.dp), border = if (prompt && highlighted) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null, color = if (prompt && highlighted) androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primaryContainer, androidx.compose.ui.graphics.Color.White, .18f) else if (prompt) MaterialTheme.colorScheme.primaryContainer else if (block.thinking) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
        AndroidView(factory = { BlockTextView(it).apply { setTextIsSelectable(true); setPadding(0, 0, 0, 0) } }, update = { view ->
            if (view.currentTextColor != color) view.setTextColor(color)
            if (view.linkTextColors.defaultColor != link) view.setLinkTextColor(link)
            if (view.tag !== text) { renderer.setParsedMarkdown(view, text); view.tag = text }
            applyReaderTypography(view, fontSize, style, italic = prompt)
            view.maxLines = if (preview) 3 else Int.MAX_VALUE
            view.ellipsize = if (preview) android.text.TextUtils.TruncateAt.END else null
            view.doOnLayout {
                if (preview) {
                    val layout = view.layout
                    overflowCallback(layout != null && (layout.lineCount > 3 || (layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0)))
                }
                if (view.isAttachedToWindow) callback(view, target)
            }
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = if (inset) 10.dp else 0.dp, vertical = if (inset) 8.dp else 2.dp))
    }
}

private class BlockTextView(context: android.content.Context) : TextView(context)

@Composable
private fun ReaderBlockNavigation(list: androidx.compose.foundation.lazy.LazyListState, rows: List<ReaderRow>, rendered: Map<Int, RenderedVersion>, conversation: Conversation, versions: Map<Int, Int>, navigationEnabled: Boolean, jump: (ReadingAnchor) -> Unit, onProgress: (Int) -> Unit) {
    val promptTopTolerance = with(LocalDensity.current) { 8.dp.roundToPx() }
    val prompts = remember(conversation, versions) {
        conversation.messages.indices.filter { conversation.messages[it].versions[versions.getValue(it)].role == "user" }
    }
    val target by remember(list, rows, prompts, promptTopTolerance) { derivedStateOf {
        val row = rows.getOrNull(list.firstVisibleItemIndex)
        val current = row?.message ?: 0
        val atPromptStart = row?.kind == "header" || row?.kind == "promptPreview" || (row?.block?.start == 0 && list.firstVisibleItemScrollOffset <= promptTopTolerance)
        prompts.lastOrNull { it < current || (it == current && !atPromptStart) } to prompts.firstOrNull { it > current }
    } }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelSize = MaterialTheme.typography.labelMedium.fontSize
            val useText = with(LocalDensity.current) { maxWidth >= 270.dp * (labelSize.toDp() / 12.dp) }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (navigationEnabled) {
                    PromptNavigationButton(previous = true, expandedLabel = useText, enabled = target.first != null, modifier = Modifier.weight(1f)) {
                        target.first?.let { jump(ReadingAnchor(it, versions.getValue(it))) }
                    }
                    ReadingProgress(list, rows, rendered, conversation.messages.size, Modifier.weight(.65f), onProgress)
                    PromptNavigationButton(previous = false, expandedLabel = useText, enabled = target.second != null, modifier = Modifier.weight(1f)) {
                        target.second?.let { jump(ReadingAnchor(it, versions.getValue(it))) }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    ReadingProgress(list, rows, rendered, conversation.messages.size, Modifier.weight(.65f), onProgress)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PromptNavigationButton(previous: Boolean, expandedLabel: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = if (previous) "Previous prompt" else "Next prompt"
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.semantics { contentDescription = label },
        contentPadding = PaddingValues(horizontal = 2.dp)) {
        if (expandedLabel) Text(if (previous) "↑ Previous" else "Next ↓", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        else Icon(painterResource(if (previous) R.drawable.ic_up else R.drawable.ic_down), null, Modifier.size(24.dp))
    }
}
