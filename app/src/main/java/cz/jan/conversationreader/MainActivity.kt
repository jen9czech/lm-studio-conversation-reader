package cz.jan.conversationreader

import android.os.Bundle
import android.widget.TextView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ReaderApp() }
    }
}

private val Night = darkColorScheme(primary = Color(0xFFA5D6C7), background = Color(0xFF141817), surface = Color(0xFF191E1C), surfaceVariant = Color(0xFF29332F), onSurface = Color(0xFFE6EAE6))
private val Day = lightColorScheme(primary = Color(0xFF286653), background = Color(0xFFF6F8F4), surface = Color(0xFFF6F8F4), surfaceVariant = Color(0xFFE0E9E2))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp() {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var linkedEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var fontSize by remember { mutableFloatStateOf(store.preferences.getFloat("font", 17f)) }
    var theme by remember { mutableStateOf(store.preferences.getString("theme", "Dark") ?: "Dark") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var librarySearch by rememberSaveable { mutableStateOf(false) }
    var folderRequest by remember { mutableIntStateOf(0) }
    var collapsePrompts by remember { mutableStateOf(store.preferences.getBoolean("collapsePrompts", false)) }
    var notice by remember { mutableStateOf<String?>(null) }
    val snackbars = remember { SnackbarHostState() }
    suspend fun refresh() {
        val loaded = withContext(Dispatchers.IO) { store.load() }
        entries = loaded.first.sortedByDescending { maxOf(store.recent(it.id), it.importedAt) }
        if (loaded.second > 0) notice = "${loaded.second} saved file(s) could not be read."
    }
    LaunchedEffect(Unit) { try { refresh() } finally { busy = false } }
    LaunchedEffect(notice) { notice?.let { snackbars.showSnackbar(it); notice = null } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            var added = 0; var duplicates = 0
            val failures = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching { store.import(uri) }.onSuccess { if (it) added++ else duplicates++ }
                        .onFailure { failures += it.message ?: "Unreadable file" }
                }
            }
            refresh()
            busy = false
            notice = "Imported $added • Already in library $duplicates" + if (failures.isEmpty()) "" else "\n${failures.size} failed: ${failures.first()}"
        }
    }
    val active = linkedEntry ?: entries.find { it.id == selected }
    var backRequest by remember { mutableIntStateOf(0) }
    BackHandler(active != null) { backRequest++ }
    BackHandler(active == null && librarySearch) { librarySearch = false; libraryQuery = "" }
    MaterialTheme(colorScheme = if (theme == "Dark" || (theme == "System" && isSystemInDarkTheme())) Night else Day) {
        Scaffold(
            topBar = {
                if (active == null) Box(Modifier.statusBarsPadding()) { ReaderToolbar(
                    searching = librarySearch, query = libraryQuery, onQuery = { libraryQuery = it },
                    onSearch = { librarySearch = true }, onCloseSearch = { librarySearch = false; libraryQuery = "" },
                    onPreferences = { settings = true }, title = "Conversation Reader", subtitle = "For LM Studio files · v${BuildConfig.VERSION_NAME}"
                ) }
            }, snackbarHost = { SnackbarHost(snackbars) }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).imePadding()) {
                Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (active == null) placeable.placeRelative(0, 0)
                    }
                }.then(if (active != null) Modifier.clearAndSetSemantics { } else Modifier)) {
                Library(entries, busy, store, folderRequest, librarySearch, libraryQuery, onFolderRequestHandled = { folderRequest = 0 }, onFolderOpen = { file -> scope.launch {
                    busy = true
                    try {
                        linkedEntry = withContext(Dispatchers.IO) { store.openLinked(file) }
                        linkedEntry?.let { store.opened(it.id) }
                    } catch (e: Exception) { notice = "Cannot open file: ${e.message}" }
                    finally { busy = false }
                } }, onImport = { importer.launch(arrayOf("*/*")) }, onOpen = {
                    linkedEntry = null; store.opened(it.id); selected = it.id
                    entries = entries.sortedByDescending { entry -> maxOf(store.recent(entry.id), entry.importedAt) }
                }, onRemove = { entry -> scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.remove(entry.id) } }.onFailure { notice = it.message }
                    refresh()
                } })
                }
                if (active != null) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { key(active.id) { ConversationView(active, store, fontSize, collapsePrompts, backRequest, onBack = { linkedEntry = null; selected = null }, onPreferences = { settings = true }) } }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        if (settings) PreferencesScreen(
            main = active == null, folder = store.preferences.getString("linkedFolder", null),
            fontSize = fontSize, theme = theme, collapse = collapsePrompts,
            onClose = { settings = false }, onFolder = { settings = false; folderRequest++ },
            onFont = { fontSize = it; store.preferences.edit().putFloat("font", it).apply() },
            onTheme = { theme = it; store.preferences.edit().putString("theme", it).apply() },
            onCollapse = { collapsePrompts = it; store.preferences.edit().putBoolean("collapsePrompts", it).apply() }
        )
    }
}

@Composable
private fun Library(entries: List<LibraryEntry>, busy: Boolean, store: LibraryStore, folderRequest: Int, showSearch: Boolean, query: String, onFolderRequestHandled: () -> Unit, onFolderOpen: (LibraryStore.FolderFile) -> Unit, onImport: () -> Unit, onOpen: (LibraryEntry) -> Unit, onRemove: (LibraryEntry) -> Unit) {
    var sortField by rememberSaveable { mutableStateOf(store.preferences.getString("sortField", "Date") ?: "Date") }
    var descending by rememberSaveable { mutableStateOf(store.preferences.getBoolean("sortDescending", true)) }
    var sortMenu by remember { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf(if (store.preferences.contains("linkedFolder")) "Folders" else "Imported") }
    var path by rememberSaveable { mutableStateOf("") }
    var files by remember { mutableStateOf<List<LibraryStore.FolderFile>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { store.link(uri) }.onSuccess { path = ""; source = "Folders"; refreshKey++ }.onFailure { folderError = it.message }
        }
    }
    LaunchedEffect(folderRequest) { if (folderRequest > 0) { folderPicker.launch(store.preferences.getString("linkedFolder", null)?.let { android.net.Uri.parse(it) }); onFolderRequestHandled() } }
    LaunchedEffect(refreshKey) {
                if (store.preferences.contains("linkedFolder")) {
                    files = withContext(Dispatchers.IO) { store.cachedFolder() }
                    scanning = true
                    try { files = withContext(Dispatchers.IO) { store.scanFolder() }; folderError = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { folderError = "Folder unavailable: ${e.message}" }
                    finally { scanning = false }
                }
    }
    var removing by remember { mutableStateOf<LibraryEntry?>(null) }
    val searchIndex = remember(entries) { entries.associate { it.id to it.conversation.searchable } }
    val filtered = remember(entries, query, showSearch) { entries.filter { !showSearch || searchIndex[it.id].orEmpty().contains(query, ignoreCase = true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Folders", "All files", "Imported").forEach { mode -> FilterChip(selected = source == mode, onClick = { source = mode }, label = { Text(mode) }) }
        }
        if (source != "Imported") {
            Text("Linked folder · " + (store.preferences.getString("linkedFolder", null)?.let { android.net.Uri.decode(it).substringAfterLast(":") } ?: "Choose SyncFolder/conversations"), style = MaterialTheme.typography.labelSmall)
            if (scanning && files.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            folderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (source == "Folders") Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = path.isNotEmpty(), onClick = { path = path.substringBeforeLast('/', "") }) { Text("↑ Up") }
                Text(path.ifEmpty { "/" }, style = MaterialTheme.typography.bodySmall)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$sortField · ${if (descending) "Descending" else "Ascending"}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort files") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("Date", "Name", "Length").forEach { field -> listOf(false, true).forEach { desc ->
                            DropdownMenuItem(text = { Text("${if (sortField == field && descending == desc) "✓ " else ""}$field ${if (desc) "↓ DESC" else "↑ ASC"}") }, onClick = {
                                sortField = field; descending = desc; sortMenu = false
                                store.preferences.edit().putString("sortField", field).putBoolean("sortDescending", desc).apply()
                            })
                        } }
                    }
                }
            }

            val prefix = if (path.isEmpty()) "" else "$path/"
            val folders = files.filter { it.isDirectory && it.path.startsWith(prefix) && '/' !in it.path.removePrefix(prefix) }.map { it.path.removePrefix(prefix) }.sortedBy { it.lowercase() }
            val sorter = when (sortField) { "Name" -> compareBy<LibraryStore.FolderFile> { it.title.lowercase() }; "Length" -> compareBy<LibraryStore.FolderFile> { it.words ?: -1 }; else -> compareBy<LibraryStore.FolderFile> { it.modified } }
            val visible = files.filter { !it.isDirectory && (source == "All files" || (it.path.startsWith(prefix) && '/' !in it.path.removePrefix(prefix))) && (!showSearch || it.title.contains(query, true) || it.path.contains(query, true)) }.sortedWith(if (source == "All files") (if (descending) sorter.reversed() else sorter).thenBy { it.path } else compareBy { it.title.lowercase() })
            Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (source == "Folders") items(folders.filter { !showSearch || it.contains(query, true) }) { folder ->
                    OutlinedButton(onClick = { path = prefix + folder }, modifier = Modifier.fillMaxWidth()) { Text("▸ $folder", Modifier.fillMaxWidth()) }
                }
                items(visible, key = { it.uri.toString() }) { file ->
                    Card(onClick = { if (!busy) onFolderOpen(file) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(file.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${file.words?.let { "${java.text.NumberFormat.getIntegerInstance().format(it)} words" } ?: "Length unavailable"} · ${date(file.modified.takeIf { it > 0 })}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (source == "All files" && '/' in file.path) Text(file.path.substringBeforeLast('/'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } }
                }
                if (!scanning && visible.isEmpty() && (source != "Folders" || folders.isEmpty())) item { Text("No conversation files here. The list refreshes automatically.") }
            }
            if (scanning && files.isEmpty()) Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text("Refreshing..")
                }
            }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${entries.size} conversations", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onImport, enabled = !busy) { Text("+ Import JSON") }
        }

        if (entries.isEmpty() && !busy) Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Your conversations,\nready to read.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text("Import LM Studio conversation JSON files to enjoy formatted replies, model details, and a quiet place to pick up where you left off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text("OFFLINE · PRIVATE · NO ACCOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            if (filtered.isEmpty()) Text("No matching conversations.", Modifier.padding(top = 24.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { entry ->
                    Card(onClick = { onOpen(entry) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.conversation.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${entry.conversation.wordCount()} words · ${entry.conversation.messages.size} messages · ${date(entry.conversation.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { removing = entry }) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
    removing?.let { entry -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove from library?") }, text = { Text("The imported copy of “${entry.conversation.title}” will be removed. Your original JSON file will remain unchanged.") }, confirmButton = { TextButton(onClick = { onRemove(entry); removing = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
}

@Composable
private fun ConversationView(entry: LibraryEntry, store: LibraryStore, fontSize: Float, collapsePrompts: Boolean, backRequest: Int, onBack: () -> Unit, onPreferences: () -> Unit) {
    val conversation = entry.conversation
    val list = rememberLazyListState(store.preferences.getInt("position_${entry.id}", 0).coerceIn(0, (conversation.messages.size - 1).coerceAtLeast(0)), store.preferences.getInt("offset_${entry.id}", 0))
    val scope = rememberCoroutineScope()
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    val groupStarts = remember(conversation) { conversation.messages.indices.map { index ->
        (index downTo 0).firstOrNull { conversation.messages[it].versions[conversation.messages[it].selected].role == "user" } ?: index
    } }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var handledBack by remember { mutableIntStateOf(backRequest) }
    LaunchedEffect(backRequest) {
        if (backRequest != handledBack) {
            handledBack = backRequest
            if (searching) { searching = false; query = "" } else if (collapsePrompts && expanded.values.any { it }) {
                val group = groupStarts.getOrNull(list.firstVisibleItemIndex) ?: 0
                expanded.clear()
                list.scrollToItem(group)
            } else onBack()
        }
    }
    var info by remember { mutableStateOf(false) }
    var hit by remember { mutableIntStateOf(-1) }
    val keyboard = LocalSoftwareKeyboardController.current
    val renderer = rememberMarkdownRenderer()
    val texts = remember(conversation, renderer) { conversation.messages.map { message -> message.versions.map { version -> version.parts.map { part ->
        if (version.role == "user") SpannableStringBuilder(part.text) else renderer.toMarkdown(part.text)
    } } } }
    val versions = remember { mutableStateMapOf<Int, Int>().apply { conversation.messages.forEachIndexed { index, message ->
        put(index, store.preferences.getInt("version_${entry.id}_$index", message.selected).coerceIn(message.versions.indices))
    } } }
    val hits = remember(query, texts) { findMatches(texts, query) }
    val activeHit = if (searching) hits.getOrNull(hit) else null
    var pending by remember { mutableStateOf<SearchMatch?>(null) }
    fun moveToMatch(target: Int) {
        keyboard?.hide()
        if (hit != target) hit = target else hits.getOrNull(target)?.let { match ->
            scope.launch {
                pending = null
                versions[match.message] = match.version
                list.scrollToItem(match.message)
                pending = match
            }
        }
    }
    LaunchedEffect(query, searching) { hit = if (searching && hits.isNotEmpty()) 0 else -1 }
    LaunchedEffect(activeHit) {
        pending = null
        activeHit?.let { match ->
            expanded[groupStarts[match.message]] = true
            versions[match.message] = match.version
            list.scrollToItem(match.message)
            pending = match
        }
    }
    val weights = conversation.messages.mapIndexed { index, message -> message.versions[versions[index] ?: message.selected].parts.sumOf { it.text.length }.coerceAtLeast(1) }
    val progress by remember(weights) { derivedStateOf {
        val first = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }
        readingProgress(weights, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset.toFloat() / (first?.size ?: 1).coerceAtLeast(1), !list.canScrollBackward, !list.canScrollForward)
    } }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (index, offset) ->
            store.preferences.edit().putInt("position_${entry.id}", index).putInt("offset_${entry.id}", offset).apply()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ReaderToolbar(searching, query, { query = it; hit = -1 }, { searching = true }, { searching = false; query = ""; keyboard?.hide() }, onPreferences, onBack = { if (searching) { searching = false; query = ""; keyboard?.hide() } else if (collapsePrompts && expanded.values.any { it }) { val group = groupStarts.getOrNull(list.firstVisibleItemIndex) ?: 0; expanded.clear(); scope.launch { list.scrollToItem(group) } } else onBack() })
        Column(Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(conversation.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
            Text("${conversation.messages.size} messages · ${conversation.wordCount()} words · ${date(conversation.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (searching) Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (query.isBlank()) "Enter text to find" else if (hits.isEmpty()) "No matches" else "${(hit + 1).coerceAtLeast(1)} of ${hits.size} matches", style = MaterialTheme.typography.labelMedium)
                TextButton(enabled = hits.isNotEmpty(), onClick = { moveToMatch((hit - 1 + hits.size) % hits.size) }) { Text("‹ Previous") }
                TextButton(enabled = hits.isNotEmpty(), onClick = { moveToMatch((hit + 1) % hits.size) }) { Text("Next ›") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
            Text("Reading ${if (progress in 1..99) "≈" else ""}$progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)) {
            itemsIndexed(conversation.messages) { index, message ->
                val group = groupStarts[index]
                val isPrompt = message.versions[versions[index] ?: message.selected].role == "user"
                val open = !collapsePrompts || expanded[group] == true
                if (collapsePrompts && index == group && isPrompt) {
                    Card(onClick = { expanded[group] = !open }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(if (open) "▾ Collapse prompt and replies" else "▸ Expand prompt and replies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (!open) Text(message.versions[versions[index] ?: message.selected].parts.joinToString("\n") { it.text }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (open) {
                MessageCard(message, versions[index] ?: message.selected, onVersion = { version ->
                    versions[index] = version
                    store.preferences.edit().putInt("version_${entry.id}_$index", version).apply()
                }, fontSize, query.takeIf { searching }.orEmpty(), activeHit?.takeIf { it.message == index }, texts[index], renderer,
                    onMatchPosition = { match, offset -> if (pending == match) {
                        pending = null
                        scope.launch { list.animateScrollToItem(index, (offset - 24).coerceAtLeast(0)) }
                    } })
                Spacer(Modifier.height(16.dp))
                }
            }
            if (conversation.messages.isEmpty()) item { Text("This conversation has no messages yet.") }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val prompts = conversation.messages.indices.filter { conversation.messages[it].versions[versions[it] ?: conversation.messages[it].selected].role == "user" }
            val previous = prompts.lastOrNull { it < list.firstVisibleItemIndex || (it == list.firstVisibleItemIndex && list.firstVisibleItemScrollOffset > 0) }
            val next = prompts.firstOrNull { it > list.firstVisibleItemIndex }
            TextButton(modifier = Modifier.weight(1f), enabled = previous != null, onClick = { keyboard?.hide(); scope.launch { list.animateScrollToItem(previous!!) } }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("↑ Previous", maxLines = 1); Text("prompt", style = MaterialTheme.typography.labelSmall) } }
            TextButton(onClick = { keyboard?.hide(); scope.launch { if (conversation.messages.isNotEmpty()) { if (collapsePrompts) expanded[groupStarts.last()] = true; list.scrollToItem(conversation.messages.lastIndex, Int.MAX_VALUE) } } }) { Text("End ↓") }
            TextButton(modifier = Modifier.weight(1f), enabled = next != null, onClick = { keyboard?.hide(); scope.launch { list.animateScrollToItem(next!!) } }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Next ↓", maxLines = 1); Text("prompt", style = MaterialTheme.typography.labelSmall) } }
        }
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text("Conversation info") }, text = { SelectionContainer { Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Created: ${date(conversation.createdAt)}\nMessages: ${conversation.messages.size}\nWords: ${conversation.wordCount()}\nFile: ${entry.fileName.ifBlank { "Not available" }}\nSize: ${entry.fileSize?.let { android.text.format.Formatter.formatShortFileSize(LocalContext.current, it) } ?: "Not available"}\n\nModels\n${conversation.models.joinToString("\n").ifBlank { "Not recorded" }}")
        if (conversation.systemPrompt.isNotBlank()) Text("System prompt\n${conversation.systemPrompt}", Modifier.padding(top = 12.dp))
        val context = LocalContext.current
        var browserError by remember { mutableStateOf<String?>(null) }
        entry.sourceUri?.let { uri ->
            Button(onClick = {
                val parent = runCatching {
                    if (android.provider.DocumentsContract.isTreeUri(uri)) {
                        val id = android.provider.DocumentsContract.getDocumentId(uri)
                        android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, id.substringBeforeLast('/', android.provider.DocumentsContract.getTreeDocumentId(uri)))
                    } else null
                }.getOrNull()
                browserError = try {
                    check(parent != null)
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(parent, "vnd.android.document/directory").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent); null
                } catch (_: Exception) {
                    try {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(android.content.Intent.CATEGORY_OPENABLE).putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, parent ?: uri))
                        null
                    } catch (_: Exception) { "No compatible file browser is available. Open your Files app and locate ${entry.fileName}." }
                }
            }, modifier = Modifier.padding(top = 12.dp)) { Text("Show in file browser") }
        }
        browserError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } } }, confirmButton = { TextButton(onClick = { info = false }) { Text("Done") } })
}

@Composable
private fun MessageCard(message: Message, selected: Int, onVersion: (Int) -> Unit, fontSize: Float, query: String, activeHit: SearchMatch?, texts: List<List<Spanned>>, renderer: Markwon, onMatchPosition: (SearchMatch, Int) -> Unit) {
    val version = message.versions[selected]
    val user = version.role == "user"
    val clipboard = LocalClipboardManager.current
    val cardTop = remember { floatArrayOf(0f) }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { cardTop[0] = it.positionInWindow().y }, horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        if (message.versions.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = selected > 0, onClick = { onVersion(selected - 1) }) { Text("‹") }
            Text("Version ${selected + 1}/${message.versions.size}", style = MaterialTheme.typography.labelSmall)
            TextButton(enabled = selected < message.versions.lastIndex, onClick = { onVersion(selected + 1) }) { Text("›") }
        }
        if (user) Surface(shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 640.dp)) {
            Column(Modifier.padding(16.dp)) { version.parts.forEachIndexed { partIndex, _ ->
                HighlightedText(texts[selected][partIndex], fontSize, query, activeHit?.takeIf { it.version == selected && it.part == partIndex }, renderer, { cardTop[0] }, onMatchPosition)
            } }
        } else version.parts.forEachIndexed { partIndex, part ->
            Text(part.model ?: version.role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            MarkdownText(part.text, fontSize, query, activeHit?.takeIf { it.version == selected && it.part == partIndex }, texts[selected][partIndex], renderer, { cardTop[0] }, onMatchPosition)
            part.stats?.let { Stats(it) }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(version.parts.joinToString("\n\n") { it.text })) }) { Text("Copy ${if (user) "prompt" else "reply"}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun Stats(stats: GenerationStats) {
    var expanded by remember { mutableStateOf(false) }
    val summary = listOfNotNull(stats.speed?.let { "${decimal(it)} tok/s" }, stats.generated?.let { "$it tokens" }, stats.firstToken?.let { "First token ${decimal(it)} s" })
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            summary.forEach { text -> Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall) } }
        }
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "Hide generation details ↑" else "Generation details ↓", style = MaterialTheme.typography.labelSmall) }
        if (expanded) Text(listOf(
            "Total generation time: ${stats.duration?.let { "${decimal(it)} s" } ?: "Not recorded"}",
            "Prompt tokens: ${stats.prompt ?: "Not recorded"}", "Generated tokens: ${stats.generated ?: "Not recorded"}",
            "Total tokens: ${stats.total ?: "Not recorded"}", "Stop reason: ${if (stats.stop == "eosFound") "EOS token found" else stats.stop ?: "Not recorded"}"
        ).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun rememberMarkdownRenderer(): Markwon {
    val context = LocalContext.current
    return remember(context) { Markwon.builder(context).usePlugin(TablePlugin.create(context)).usePlugin(StrikethroughPlugin.create()).usePlugin(LinkifyPlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: io.noties.markwon.core.MarkwonTheme.Builder) {
                builder.headingTextSizeMultipliers(floatArrayOf(1.3f, 1.15f, 1.08f, 1f, 1f, 1f))
                    .blockMargin(8)
            }
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    val uri = android.net.Uri.parse(link)
                    if (uri.scheme?.lowercase() in listOf("https", "http", "mailto")) runCatching {
                        view.context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                    }
                }
            }
        }).build() }
}

@Composable
private fun MarkdownText(text: String, size: Float, query: String, active: SearchMatch?, rendered: Spanned, markwon: Markwon, cardTop: () -> Float, onMatchPosition: (SearchMatch, Int) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val document = remember(text, markwon) { markwon.parse(text) }
    val codeBlocks = remember(document) {
        buildList<String> {
            document.accept(object : AbstractVisitor() {
                override fun visit(block: FencedCodeBlock) { add(block.literal) }
                override fun visit(block: IndentedCodeBlock) { add(block.literal) }
            })
        }
    }
    HighlightedText(rendered, size, query, active, markwon, cardTop, onMatchPosition)
    if (codeBlocks.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) {
        codeBlocks.forEachIndexed { index, code ->
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text(if (codeBlocks.size == 1) "Copy code" else "Copy code ${index + 1}") }
        }
    }
}

@Composable
private fun HighlightedText(rendered: Spanned, size: Float, query: String, active: SearchMatch?, markwon: Markwon, cardTop: () -> Float, onMatchPosition: (SearchMatch, Int) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlighted = remember(rendered, query, active) {
        SpannableStringBuilder(rendered).apply {
            matchRanges(rendered, query).forEach { range ->
                val current = active?.start == range.first
                setSpan(BackgroundColorSpan(if (current) 0xFFFFB74D.toInt() else 0xFFFFE082.toInt()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(0xFF171A17.toInt()), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    val report by rememberUpdatedState(onMatchPosition)
    val top by rememberUpdatedState(cardTop)
    AndroidView(factory = { TextView(it).apply { setTextIsSelectable(true); setPadding(0, 0, 0, 0); setLineSpacing(0f, 1.35f) } }, update = { view ->
        view.textSize = size; view.setTextColor(color); view.setLinkTextColor(linkColor)
        if (view.tag !== highlighted) { markwon.setParsedMarkdown(view, highlighted); view.tag = highlighted }
        active?.let { match -> view.post {
            val layout = view.layout
            if (view.isAttachedToWindow && layout != null && match.start < view.text.length) {
                val location = IntArray(2)
                view.getLocationInWindow(location)
                val lineY = layout.getLineTop(layout.getLineForOffset(match.start))
                report(match, (location[1] - top() + view.totalPaddingTop + lineY).toInt())
            }
        } }
    }, modifier = Modifier.fillMaxWidth())
}

private fun date(timestamp: Long?) = timestamp?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "Date not recorded"
private fun decimal(value: Double) = String.format(Locale.getDefault(), "%.2f", value)

