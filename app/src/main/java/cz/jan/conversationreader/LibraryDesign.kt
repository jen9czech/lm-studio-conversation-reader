package cz.jan.conversationreader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val BrowsingViews = listOf("Folders", "All files", "Models", "Imported")

@Composable
internal fun PinnedLibraryPane(modifier: Modifier, controls: @Composable ColumnScope.() -> Unit, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Give rows room even when the keyboard or a short window limits the viewport.
        val controlsHeight = maxHeight - minOf(112.dp, maxHeight / 2)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()), content = controls)
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }
}

@Composable
internal fun LibraryEmptyState(message: String, actionLabel: String? = null, action: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null) TextButton(onClick = action) { Text(actionLabel) }
    }
}

@Composable
internal fun androidx.compose.foundation.lazy.LazyItemScope.LibrarySearchEmpty(complete: Boolean, onClearSearch: () -> Unit, emptyMessage: String = "No matching conversations.") {
    Column(Modifier.fillParentMaxHeight(.67f).fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (complete) emptyMessage else "Searching…",
            Modifier.semantics { if (complete) liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (complete) TextButton(onClick = onClearSearch) { Text("Clear search") }
    }
}

@Composable
internal fun FolderBreadcrumb(path: String, onNavigate: (String) -> Unit) {
    SelectedGroupHeader(onBack = { onNavigate(path.substringBeforeLast('/', "")) }) {
        Text(path.substringAfterLast('/'), Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp).semantics { stateDescription = "Current folder" },
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun SelectedGroupHeader(onBack: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back to groups") }
        content()
    }
}

@Composable
internal fun LibrarySourceTabs(source: String, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        BoxWithConstraints {
            val textScale = androidx.compose.ui.platform.LocalDensity.current.fontScale *
                (MaterialTheme.typography.labelMedium.fontSize.value / 12f)
            val columns = if (maxWidth < 256.dp * textScale) 2 else 4
            Column(Modifier.fillMaxWidth().padding(4.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BrowsingViews.chunked(columns).forEach { views ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        views.forEach { view ->
                            Surface(color = if (source == view) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(source == view, role = Role.Tab, onClick = { onSelect(view) })) {
                                Box(Modifier.padding(horizontal = 3.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(view, maxLines = 2, style = MaterialTheme.typography.labelMedium,
                                        color = if (source == view) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryBottomNavigation(source: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, windowInsets = WindowInsets(0, 0, 0, 0)) {
        listOf(Triple("Library", R.drawable.ic_library, source in BrowsingViews), Triple("Recent", R.drawable.ic_history, source == "Recent"), Triple("Bookmarked", R.drawable.ic_bookmark, source == "Bookmarked")).forEach { (name, icon, selected) ->
            NavigationBarItem(selected = selected, onClick = { onSelect(name) }, icon = { Icon(painterResource(icon), null, Modifier.size(23.dp)) }, label = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@Composable
internal fun LibraryGroupRow(name: String, count: Int, model: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (model) 14.dp else 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(painterResource(if (model) R.drawable.ic_model else R.drawable.ic_folder), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                val formatted = java.text.NumberFormat.getIntegerInstance().format(count)
                Text(formatted, Modifier.padding(horizontal = 8.dp, vertical = 5.dp).clearAndSetSemantics {
                    contentDescription = "$formatted ${if (count == 1) "conversation" else "conversations"}"
                }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ImportedConversationMenu(title: String, onRemove: () -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_more), "Actions for $title", Modifier.size(22.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Remove from library") }, onClick = { open = false; onRemove() })
        }
    }
}

@Composable
internal fun LibraryConversationCard(
    title: String,
    words: Int?,
    timestamp: Long?,
    bookmarked: Boolean,
    onOpen: () -> Unit,
    onBookmark: () -> Unit,
    snippet: LibrarySearchHit? = null,
    error: String? = null,
    lastOpened: Boolean = false,
    prompts: Int? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Column {
        Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val length = words?.let { "${java.text.NumberFormat.getIntegerInstance().format(it)} words" } ?: "Length unavailable"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dateText = date(timestamp)
                        val promptText = prompts?.let { "${java.text.NumberFormat.getIntegerInstance().format(it)} ${if (it == 1) "prompt" else "prompts"} · " }.orEmpty()
                        Text("$promptText$length · ${if (lastOpened) "Opened $dateText" else dateText}", Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        trailing()
                    }
                    if (snippet != null && snippet.snippet.isNotBlank()) {
                        val colors = MaterialTheme.colorScheme
                        Text(buildAnnotatedString {
                            append(snippet.snippet)
                            snippet.highlights.forEach { range ->
                                addStyle(SpanStyle(background = colors.primaryContainer, color = colors.onPrimaryContainer), range.first, range.last + 1)
                            }
                        }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (error != null) Text("Cannot read: $error", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                BookmarkButton(bookmarked, onBookmark)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
