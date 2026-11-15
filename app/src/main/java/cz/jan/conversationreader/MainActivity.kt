package cz.jan.conversationreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ReaderApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp() {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var linkedEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var linkedReference by rememberSaveable { mutableStateOf<String?>(null) }
    var initialReaderQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var rescanRequest by remember { mutableIntStateOf(0) }
    var readerStyle by remember { mutableStateOf(store.reading.style()) }
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
    LaunchedEffect(linkedReference) {
        if (linkedReference != null && linkedEntry == null) {
            busy = true
            try { linkedEntry = withContext(Dispatchers.IO) { store.openReference(linkedReference!!) } }
            catch (e: Exception) { linkedReference = null; notice = "Cannot restore conversation: ${e.message}" }
            finally { busy = false }
        }
    }
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
    BackHandler(active == null && librarySearch) { librarySearch = false }
    MaterialTheme(colorScheme = if (theme == "Dark" || (theme == "System" && isSystemInDarkTheme())) Night else Day) {
        Scaffold(
            topBar = {
            }, snackbarHost = { SnackbarHost(snackbars) }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).imePadding()) {
                Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (active == null) placeable.placeRelative(0, 0)
                    }
                }.then(if (active != null) Modifier.clearAndSetSemantics { } else Modifier)) {
                Library(entries, busy, store, folderRequest, rescanRequest, libraryRevision, active == null, librarySearch, libraryQuery, onQuery = { libraryQuery = it }, onSearch = { librarySearch = true }, onCloseSearch = { librarySearch = false }, onSettings = { settings = true }, onFolderRequestHandled = { folderRequest = 0 }, onFolderOpen = { file, search -> scope.launch {
                    busy = true
                    try {
                        linkedEntry = withContext(Dispatchers.IO) { store.openLinked(file) }
                        linkedEntry?.let { linkedReference = store.reference(it); initialReaderQuery = search; store.opened(it.id); store.reading.record(it, store.reference(it)) }
                    } catch (e: Exception) { notice = "Cannot open file: ${e.message}" }
                    finally { busy = false }
                } }, onReferenceOpen = { reference, search -> scope.launch {
                    busy = true
                    try {
                        val opened = withContext(Dispatchers.IO) { store.openReference(reference) }
                        initialReaderQuery = search
                        if (opened.id.startsWith("linked_")) { linkedEntry = opened; linkedReference = reference; selected = null }
                        else { linkedReference = null; linkedEntry = null; selected = opened.id }
                        store.opened(opened.id); store.reading.record(opened, store.reference(opened))
                    } catch (e: Exception) { notice = "Cannot open conversation: ${e.message}" }
                    finally { busy = false }
                } }, onImport = { importer.launch(arrayOf("*/*")) }, onOpen = { entry, search ->
                    linkedReference = null; linkedEntry = null; initialReaderQuery = search; store.opened(entry.id); store.reading.record(entry, store.reference(entry)); selected = entry.id
                    entries = entries.sortedByDescending { entry -> maxOf(store.recent(entry.id), entry.importedAt) }
                }, onRemove = { entry -> scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.remove(entry.id) } }.onFailure { notice = it.message }
                    refresh()
                } })
                }
                if (active != null) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { key(active.id) { ConversationReader(active, store, fontSize, readerStyle, collapsePrompts, backRequest, initialReaderQuery, onBack = { linkedReference = null; linkedEntry = null; selected = null; initialReaderQuery = null; libraryRevision++ }, onPreferences = { settings = true }) } }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
        if (settings) PreferencesScreen(
            main = active == null, folder = store.preferences.getString("linkedFolder", null),
            fontSize = fontSize, theme = theme, collapse = collapsePrompts,
            onClose = { settings = false }, onFolder = { settings = false; folderRequest++ },
            onFont = { fontSize = it; store.preferences.edit().putFloat("font", it).apply() },
            onTheme = { theme = it; store.preferences.edit().putString("theme", it).apply() },
            onCollapse = { collapsePrompts = it; store.preferences.edit().putBoolean("collapsePrompts", it).apply() },
            style = readerStyle, onStyle = { readerStyle = it; store.reading.saveStyle(it) },
            onRescan = { settings = false; rescanRequest++ }
        )
    }
}

@Composable
private fun Library(entries: List<LibraryEntry>, busy: Boolean, store: LibraryStore, folderRequest: Int, rescanRequest: Int, revision: Int, visibleScreen: Boolean, showSearch: Boolean, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onCloseSearch: () -> Unit, onSettings: () -> Unit, onFolderRequestHandled: () -> Unit, onFolderOpen: (LibraryStore.FolderFile, String?) -> Unit, onReferenceOpen: (String, String?) -> Unit, onImport: () -> Unit, onOpen: (LibraryEntry, String?) -> Unit, onRemove: (LibraryEntry) -> Unit) {
    val searchScope = rememberCoroutineScope()
    val linkedList = rememberLazyListState()
    val importedList = rememberLazyListState()
    var searchItem by remember { mutableIntStateOf(0) }
    var sortField by rememberSaveable { mutableStateOf(store.preferences.getString("sortField", "Date") ?: "Date") }
    var descending by rememberSaveable { mutableStateOf(store.preferences.getBoolean("sortDescending", true)) }
    var sortMenu by remember { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf(if (store.preferences.contains("linkedFolder")) "Folders" else "Imported") }
    LaunchedEffect(query, source) { searchItem = 0 }
    var path by rememberSaveable { mutableStateOf("") }
    var folderPositions by rememberSaveable { mutableStateOf(mapOf<String, List<Int>>()) }
    var pendingPosition by remember { mutableStateOf<List<Int>?>(null) }
    fun rememberFolderPosition() {
        if (source in listOf("Folders", "All files")) folderPositions = (folderPositions + ("$source:$path" to listOf(linkedList.firstVisibleItemIndex, linkedList.firstVisibleItemScrollOffset))).entries.toList().takeLast(100).associate { it.key to it.value }
    }
    fun selectView(mode: String) {
        rememberFolderPosition()
        source = mode
        if (mode in listOf("Folders", "All files")) pendingPosition = folderPositions["$mode:$path"] ?: listOf(0, 0)
    }
    fun navigateFolder(destination: String) {
        rememberFolderPosition()
        path = destination
        pendingPosition = folderPositions["Folders:$destination"] ?: listOf(0, 0)
    }
    LaunchedEffect(pendingPosition, source, path) { pendingPosition?.let { linkedList.scrollToItem(it[0], it[1]); pendingPosition = null } }
    var files by remember { mutableStateOf<List<LibraryStore.FolderFile>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanDone by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var localRevision by remember { mutableIntStateOf(0) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { store.link(uri) }.onSuccess { path = ""; source = "Folders"; folderPositions = emptyMap(); pendingPosition = listOf(0, 0); refreshKey++ }.onFailure { folderError = it.message }
        }
    }
    LaunchedEffect(folderRequest) { if (folderRequest > 0) { folderPicker.launch(store.preferences.getString("linkedFolder", null)?.let { android.net.Uri.parse(it) }); onFolderRequestHandled() } }
    LaunchedEffect(refreshKey, rescanRequest) {
                if (store.preferences.contains("linkedFolder")) {
                    files = withContext(Dispatchers.IO) { store.cachedFolder() }
                    scanning = true
                    try { files = withContext(Dispatchers.IO) { store.scanFolder { discovered, done, total -> files = discovered; scanDone = done; scanTotal = total } }; folderError = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { folderError = "Folder unavailable: ${e.message}" }
                    finally { scanning = false }
                }
    }
    var removing by remember { mutableStateOf<LibraryEntry?>(null) }
    BackHandler(visibleScreen && source == "Folders" && path.isNotEmpty() && !showSearch) { navigateFolder(path.substringBeforeLast('/', "")) }
    val indexHits by produceState<Map<String, LibrarySearchHit>>(emptyMap(), query, showSearch, files, entries) {
        value = emptyMap()
        if (showSearch && query.isNotBlank()) { kotlinx.coroutines.delay(180); value = withContext(Dispatchers.IO) { store.searchIndex.search(query) } }
    }
    val recent = remember(revision, localRevision, files, entries) { store.reading.recent() }
    val folderCounts = remember(files) { folderFileCounts(files.map { it.path to it.isDirectory }) }
    val filtered = remember(entries, query, showSearch, indexHits) { entries.filter { !showSearch || query.isBlank() || it.id in indexHits || it.conversation.title.contains(query, true) } }
            val prefix = if (path.isEmpty()) "" else "$path/"
            val folderWords = remember(files) {
                buildMap<String, Long> {
                    files.filter { !it.isDirectory }.forEach { file ->
                        var parent = file.path.substringBeforeLast('/', "")
                        while (parent.isNotEmpty()) { put(parent, (get(parent) ?: 0L) + (file.words ?: 0)); parent = parent.substringBeforeLast('/', "") }
                    }
                }
            }
            val folderSorter = when (sortField) { "Date" -> compareBy<LibraryStore.FolderFile> { it.modified }; "Length" -> compareBy { folderWords[it.path] ?: 0L }; else -> compareBy { it.title.lowercase() } }
            val folders = files.filter { it.isDirectory && it.path.startsWith(prefix) && '/' !in it.path.removePrefix(prefix) }
                .sortedWith((if (descending) folderSorter.reversed() else folderSorter).thenBy { it.path }).map { it.path.removePrefix(prefix) }
            val sorter = when (sortField) { "Name" -> compareBy<LibraryStore.FolderFile> { it.title.lowercase() }; "Length" -> compareBy<LibraryStore.FolderFile> { it.words ?: -1 }; else -> compareBy<LibraryStore.FolderFile> { it.modified } }
            val visible = files.filter { !it.isDirectory && (source == "All files" || (it.path.startsWith(prefix) && '/' !in it.path.removePrefix(prefix))) && (!showSearch || query.isBlank() || store.linkedId(it.uri) in indexHits || it.title.contains(query, true) || it.path.contains(query, true)) }.sortedWith((if (descending) sorter.reversed() else sorter).thenBy { it.path })
            val resultCount = visible.size + if (source == "Folders") folders.count { it.contains(query, true) } else 0

    val bookmarkedIds = remember(revision, localRevision) { store.reading.bookmarkedIds() }
    val cards = remember(recent, entries, files, source, revision, localRevision, bookmarkedIds) { buildList {
        fun included(id: String) = source == "Recent" || id in bookmarkedIds
        val seen = mutableSetOf<String>()
        recent.forEach { r -> store.referenceId(r.reference)?.let { id -> if (included(id) && seen.add(id)) add(r) } }
        if (source != "Recent") {
            entries.forEach { e -> if (included(e.id) && seen.add(e.id)) add(RecentReading(store.reference(e), e.conversation.title, "", store.recent(e.id))) }
            files.filter { !it.isDirectory }.forEach { f ->
                val id = store.linkedId(f.uri)
                if (included(id) && seen.add(id)) add(RecentReading(org.json.JSONObject().put("id", id).put("uri", f.uri.toString()).put("name", f.path.substringAfterLast('/')).put("modified", f.modified).toString(), f.title, "", store.recent(id)))
            }
        }
    } }.filter { !showSearch || query.isBlank() || store.referenceId(it.reference) in indexHits || it.title.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        ReaderToolbar(showSearch && visibleScreen, query, onQuery, onSearch, onCloseSearch, onSettings, title = "Conversation Reader", currentView = source, onMenuView = { selectView(it) })
        val total = when (source) { "Imported" -> filtered.size; "Recent", "Bookmarked" -> cards.size; else -> resultCount }
        if (showSearch) SearchResultsBar(searchItem.coerceAtMost((total - 1).coerceAtLeast(0)), total,
            onPrevious = { searchItem = (searchItem - 1 + total) % total; searchScope.launch { (if (source in listOf("Imported", "Recent", "Bookmarked")) importedList else linkedList).animateScrollToItem(searchItem) } },
            onNext = { searchItem = (searchItem + 1) % total; searchScope.launch { (if (source in listOf("Imported", "Recent", "Bookmarked")) importedList else linkedList).animateScrollToItem(searchItem) } })
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {

        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryViews.forEach { mode -> FilterChip(selected = source == mode, onClick = { selectView(mode) }, label = { Text(mode) }) }
        }
        if (!showSearch && source !in listOf("Recent", "Bookmarked") && recent.isNotEmpty()) {
            val last = recent.first()
            OutlinedCard(onClick = { onReferenceOpen(last.reference, null) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Continue reading", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Text("Continue · ${last.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    if (last.excerpt.isNotBlank()) Text(last.excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (source in listOf("Recent", "Bookmarked")) {

            if (cards.isEmpty()) Text(if (source == "Recent") "No recently opened conversations." else "Tap a conversation’s star to add it to Bookmarked.", Modifier.padding(vertical = 20.dp))
            Box(Modifier.weight(1f)) {
                LazyColumn(state = importedList, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cards, key = { it.reference }) { card -> Card(onClick = { onReferenceOpen(card.reference, query.takeIf { showSearch && it.isNotBlank() }) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(card.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); BookmarkButton(store.referenceId(card.reference) in bookmarkedIds, { store.referenceId(card.reference)?.let { id -> store.reading.setBookmarked(id, id !in bookmarkedIds); localRevision++ } }) }
                        val snippet = indexHits[store.referenceId(card.reference)]?.snippet ?: card.excerpt
                        if (snippet.isNotBlank()) Text(snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } } }
                }
                LazyScrollSlider(importedList, cards.map { it.reference })
            }
            return@Column
        }
        if (source != "Imported") {
            Text("Linked folder · " + (store.preferences.getString("linkedFolder", null)?.let { android.net.Uri.decode(it).substringAfterLast(":") } ?: "Choose SyncFolder/conversations"), style = MaterialTheme.typography.labelSmall)
            if (scanning && files.isNotEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Indexed $scanDone of $scanTotal files", style = MaterialTheme.typography.labelSmall) }
            folderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (source == "Folders") Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = path.isNotEmpty(), onClick = { navigateFolder(path.substringBeforeLast('/', "")) }) { Text("↑ Up") }
                Text(path.ifEmpty { "/" }, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

            Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = linkedList, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (source == "Folders") items(folders.filter { !showSearch || it.contains(query, true) }) { folder ->
                    OutlinedButton(onClick = { navigateFolder(prefix + folder) }, modifier = Modifier.fillMaxWidth()) { Text("▸ $folder", Modifier.weight(1f)); Text("${folderCounts[prefix + folder] ?: 0}", style = MaterialTheme.typography.labelMedium) }
                }
                items(visible, key = { it.uri.toString() }) { file ->
                    Card(onClick = { if (!busy) onFolderOpen(file, query.takeIf { showSearch && it.isNotBlank() }) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(file.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            val id = store.linkedId(file.uri)
                            BookmarkButton(id in bookmarkedIds, { store.reading.setBookmarked(id, id !in bookmarkedIds); localRevision++ })
                        }
                        if (showSearch) indexHits[store.linkedId(file.uri)]?.snippet?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        file.error?.let { Text("Cannot read: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        Text("${file.words?.let { "${java.text.NumberFormat.getIntegerInstance().format(it)} words" } ?: "Length unavailable"} · ${date(file.modified.takeIf { it > 0 })}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (source == "All files" && '/' in file.path) Text(file.path.substringBeforeLast('/'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } }
                }
                if (!scanning && visible.isEmpty() && (source != "Folders" || folders.isEmpty())) item { Text("No conversation files here. Restart the app or update the index in Settings after adding files.") }
            }
            LazyScrollSlider(linkedList, listOf(source, path, sortField, descending, query, visible.map { it.uri.toString() }))
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
            Text("OFFLINE · PRIVATE · NO ACCOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (filtered.isEmpty()) Text("No matching conversations.", Modifier.padding(top = 24.dp))
            Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = importedList, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { entry ->
                    Card(onClick = { onOpen(entry, query.takeIf { showSearch && it.isNotBlank() }) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.conversation.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (showSearch) indexHits[entry.id]?.snippet?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Text("${entry.conversation.wordCount()} words · ${entry.conversation.messages.size} messages · ${date(entry.conversation.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            BookmarkButton(entry.id in bookmarkedIds, { store.reading.setBookmarked(entry.id, entry.id !in bookmarkedIds); localRevision++ })
                            TextButton(onClick = { removing = entry }) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            LazyScrollSlider(importedList)
            }
        }
        }
    }
    removing?.let { entry -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove from library?") }, text = { Text("The imported copy of “${entry.conversation.title}” will be removed. Your original JSON file will remain unchanged.") }, confirmButton = { TextButton(onClick = { onRemove(entry); removing = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
}

@Composable
internal fun ConversationInfoDialog(entry: LibraryEntry, versions: Map<Int, Int>, onDismiss: () -> Unit) {
    val conversation = entry.conversation
    val words = remember(conversation) { conversation.wordCount() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Conversation info") }, text = { SelectionContainer { Box {
        val infoScroll = rememberScrollState()
        Column(Modifier.verticalScroll(infoScroll)) {
        val context = LocalContext.current
        var browserError by remember { mutableStateOf<String?>(null) }
        val numberFormat = remember { java.text.NumberFormat.getIntegerInstance() }
        Text("Overview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        InfoRow("Created", date(conversation.createdAt))
        InfoRow("Messages", numberFormat.format(conversation.messages.size))
        InfoRow("Words", numberFormat.format(words))
        val tokenCount = conversation.savedTokenCount ?: conversation.selectedGeneratedTokens(versions)
        InfoRow("Tokens", tokenCount?.let { numberFormat.format(it) } ?: "Not recorded")
        if (tokenCount != null) Text(if (conversation.savedTokenCount != null) "Token count saved by LM Studio" else "Generated tokens in selected replies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("File", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        InfoRow("Size", entry.fileSize?.let { android.text.format.Formatter.formatShortFileSize(context, it) } ?: "Not available")
        val uriForText = entry.textUri ?: entry.sourceUri
        Text(entry.fileName.ifBlank { "Filename unavailable" }, modifier = Modifier.padding(vertical = 12.dp).then(if (uriForText != null) Modifier.clickable {
            browserError = try {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uriForText, "text/plain").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                null
            } catch (_: Exception) { "No text reader could open this file. Install a text editor or choose another reader." }
        } else Modifier), color = readerLinkColor(), style = MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))

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
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Models", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(conversation.models.joinToString("\n").ifBlank { "Not recorded" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        if (conversation.systemPrompt.isNotBlank()) {
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("System prompt", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(conversation.systemPrompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        browserError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    PlainScrollSlider(infoScroll)
    } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
internal fun Stats(stats: GenerationStats) {
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
internal fun rememberMarkdownRenderer(): Markwon {
    val context = LocalContext.current
    return remember(context) { createMarkdownRenderer(context) }
}

internal fun createMarkdownRenderer(context: android.content.Context): Markwon = Markwon.builder(context).usePlugin(TablePlugin.create(context)).usePlugin(StrikethroughPlugin.create()).usePlugin(LinkifyPlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: io.noties.markwon.core.MarkwonTheme.Builder) {
                builder.headingTextSizeMultipliers(floatArrayOf(1.3f, 1.15f, 1.08f, 1f, 1f, 1f))
                    .blockMargin(4)
                    .isLinkUnderlined(true)
            }
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    val uri = android.net.Uri.parse(link)
                    if (uri.scheme?.lowercase() in listOf("https", "http", "mailto")) runCatching {
                        view.context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                    }
                }
            }
        }).build()

internal fun date(timestamp: Long?) = timestamp?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "Date not recorded"
private fun decimal(value: Double) = String.format(Locale.getDefault(), "%.2f", value)

