package cz.jan.conversationreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Compose UI 1.9.2 eagerly recalculates Material3 child semantics after lazy
        // rows detach during prompt jumps (visitAncestors on an unattached node).
        // Use the legacy lazy semantics path; this reader has no autofill fields.
        // Normal accessibility semantics remain enabled.
        androidx.compose.ui.ComposeUiFlags.isSemanticAutofillEnabled = false
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
    var entries by remember { mutableStateOf<List<ImportedConversation>>(emptyList()) }
    var resumeReading by rememberSaveable { mutableStateOf(false) }
    var activeEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var activeReference by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var readerStyle by remember { mutableStateOf(store.reading.style()) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var fontSize by remember { mutableFloatStateOf(store.preferences.getFloat("font", 17f).coerceIn(14f, 26f)) }
    var theme by remember { mutableStateOf(store.preferences.getString("theme", "Dark") ?: "Dark") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var librarySearch by rememberSaveable { mutableStateOf(false) }
    var folderRequest by remember { mutableIntStateOf(0) }
    var collapsePrompts by remember { mutableStateOf(store.reading.collapsePrompts()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val snackbars = remember { SnackbarHostState() }
    suspend fun refresh() {
        val loaded = withContext(Dispatchers.IO) { store.load() }
        entries = loaded.first.sortedByDescending { maxOf(store.recent(it.id), it.metadata.modified) }
        if (loaded.second > 0) notice = "${loaded.second} saved file(s) could not be read."
    }
    LaunchedEffect(Unit) { try { refresh() } finally { busy = false } }
    LaunchedEffect(activeReference) {
        if (activeReference != null && activeEntry == null) {
            busy = true
            try { activeEntry = withContext(Dispatchers.IO) { store.openReference(activeReference!!) } }
            catch (e: Exception) { activeReference = null; notice = "Cannot restore conversation: ${e.message}" }
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
    val active = activeEntry
    var backRequest by remember { mutableIntStateOf(0) }
    BackHandler(active != null) { backRequest++ }
    BackHandler(active == null && librarySearch) { librarySearch = false }
    val interfaceScale = (fontSize / 17f).coerceIn(.85f, 1.30f)
    val typography = remember(interfaceScale) { readerInterfaceTypography(interfaceScale) }
    MaterialTheme(colorScheme = if (theme == "Dark" || (theme == "System" && isSystemInDarkTheme())) Night else Day, typography = typography) {
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
                Library(entries, busy, store, folderRequest, libraryRevision, active == null, librarySearch, libraryQuery, onQuery = { libraryQuery = it }, onSearch = { librarySearch = true }, onCloseSearch = { librarySearch = false }, onSettings = { settings = true }, onFolderRequestHandled = { folderRequest = 0 }, onFolderOpen = { file -> scope.launch {
                    resumeReading = false
                    busy = true
                    try {
                        activeEntry = withContext(Dispatchers.IO) { store.openLinked(file) }
                        activeEntry?.let { activeReference = store.reference(it); store.opened(it.id); store.reading.record(it, store.reference(it)) }
                    } catch (e: Exception) { notice = "Cannot open file: ${e.message}" }
                    finally { busy = false }
                } }, onReferenceOpen = { reference, resume -> scope.launch {
                    if (busy) return@launch
                    busy = true
                    try {
                        val opened = withContext(Dispatchers.IO) { store.openReference(reference) }
                        resumeReading = resume
                        activeEntry = opened
                        activeReference = reference
                        store.opened(opened.id); store.reading.record(opened, store.reference(opened))
                    } catch (e: Exception) { notice = "Cannot open conversation: ${e.message}" }
                    finally { busy = false }
                } }, onImport = { importer.launch(arrayOf("*/*")) }, onRemove = { entry -> scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.remove(entry.id) } }.onFailure { notice = it.message }
                    refresh()
                } })
                }
                if (active != null) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { key(active.id) { ConversationReader(active, store, fontSize, readerStyle, collapsePrompts, backRequest, resumeReading, onBack = { activeReference = null; activeEntry = null; libraryRevision++ }, onPreferences = { settings = true }) } }
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
            style = readerStyle, onStyle = { readerStyle = it; store.reading.saveStyle(it) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Library(entries: List<ImportedConversation>, busy: Boolean, store: LibraryStore, folderRequest: Int, revision: Int, visibleScreen: Boolean, showSearch: Boolean, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onCloseSearch: () -> Unit, onSettings: () -> Unit, onFolderRequestHandled: () -> Unit, onFolderOpen: (LibraryStore.FolderFile) -> Unit, onReferenceOpen: (String, Boolean) -> Unit, onImport: () -> Unit, onRemove: (ImportedConversation) -> Unit) {
    val searchScope = rememberCoroutineScope()
    val linkedList = rememberLazyListState()
    val importedList = rememberLazyListState()
    val recentList = rememberLazyListState()
    val bookmarkedList = rememberLazyListState()
    var modelGroupDescending by rememberSaveable { mutableStateOf(store.preferences.getBoolean("modelGroupDescending", false)) }
    var modelGroupField by rememberSaveable { mutableStateOf(if (store.preferences.getString("modelGroupSort", "Name") == "Conversation count") "Count" else store.preferences.getString("modelGroupSort", "Name") ?: "Name") }
    var linkedFolder by remember { mutableStateOf(store.preferences.getString("linkedFolder", null)) }
    var source by rememberSaveable { mutableStateOf(if (linkedFolder != null) "Folders" else "Imported") }
    var librarySource by rememberSaveable { mutableStateOf(source.takeIf { it in BrowsingViews } ?: "Folders") }
    var path by rememberSaveable { mutableStateOf("") }
    var selectedModel by rememberSaveable { mutableStateOf<String?>(null) }
    val folderScope = "$linkedFolder:$path"
    val conversationScope = if (source == "Folders") "$source:$folderScope" else source
    var sortField by rememberLibrarySortField(store.preferences, "sortField", conversationScope, "Date")
    var descending by rememberLibrarySortDirection(store.preferences, "sortDescending", conversationScope, true)
    var folderGroupDescending by rememberLibrarySortDirection(store.preferences, "folderGroupDescending", folderScope, false)
    var folderGroupField by rememberLibrarySortField(store.preferences, "folderGroupSort", folderScope, "Name")
    var modelSortField by rememberLibrarySortField(store.preferences, "modelConversationSort", selectedModel.orEmpty(), "Date")
    var modelDescending by rememberLibrarySortDirection(store.preferences, "modelConversationDescending", selectedModel.orEmpty(), true)
    var includeConversationText by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showSearch, source) {
        if (!showSearch || source != "All files") includeConversationText = false
    }
    val modelGroupList = rememberLazyListState()
    val modelConversationList = rememberLazyListState()

    var folderPositions by rememberSaveable { mutableStateOf(mapOf<String, List<Int>>()) }
    var pendingPosition by remember { mutableStateOf<List<Int>?>(null) }
    fun rememberFolderPosition() {
        if (source in listOf("Folders", "All files")) folderPositions = (folderPositions + ("$source:$path" to listOf(linkedList.firstVisibleItemIndex, linkedList.firstVisibleItemScrollOffset))).entries.toList().takeLast(100).associate { it.key to it.value }
    }
    fun navigateFolder(destination: String) {
        rememberFolderPosition()
        path = destination
        pendingPosition = folderPositions["Folders:$destination"] ?: listOf(0, 0)
        if (showSearch) { onQuery(""); onCloseSearch() }
    }
    fun selectView(mode: String) {
        if (source == mode && mode == "Folders" && path.isNotEmpty()) {
            navigateFolder(path.substringBeforeLast('/', ""))
            return
        }
        if (source == mode && mode == "Models" && selectedModel != null) {
            selectedModel = null
            if (showSearch) { onQuery(""); onCloseSearch() }
            return
        }
        if (source != mode && showSearch) onQuery("")
        rememberFolderPosition()
        source = mode
        if (mode in BrowsingViews) librarySource = mode
        if (mode in listOf("Folders", "All files")) pendingPosition = folderPositions["$mode:$path"] ?: listOf(0, 0)
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
            runCatching { store.link(uri) }.onSuccess {
                linkedFolder = uri.toString()
                path = ""
                source = "Folders"
                librarySource = "Folders"
                folderPositions = emptyMap()
                pendingPosition = listOf(0, 0)
                if (showSearch) onCloseSearch()
                onQuery("")
                refreshKey++
            }.onFailure { folderError = it.message }
        }
    }
    LaunchedEffect(folderRequest) { if (folderRequest > 0) { folderPicker.launch(store.preferences.getString("linkedFolder", null)?.let { android.net.Uri.parse(it) }); onFolderRequestHandled() } }
    LaunchedEffect(refreshKey) {
                if (linkedFolder != null) {
                    files = withContext(Dispatchers.IO) { store.cachedFolder() }
                    scanning = true
                    try { files = withContext(Dispatchers.IO) { store.scanFolder { discovered, done, total -> files = discovered; scanDone = done; scanTotal = total } }; folderError = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { folderError = "Folder unavailable: ${e.message}" }
                    finally { scanning = false }
                }
    }
    var removing by remember { mutableStateOf<ImportedConversation?>(null) }
    BackHandler(visibleScreen && source == "Folders" && path.isNotEmpty() && !showSearch) { navigateFolder(path.substringBeforeLast('/', "")) }
    val filtering = showSearch && query.isNotBlank()
    val searchConversationText = showSearch && source == "All files" && includeConversationText
    val indexedSearch by produceState<Pair<String, Map<String, LibrarySearchHit>>?>(null, query, searchConversationText, files) {
        value = null
        if (query.isNotBlank() && searchConversationText) {
            kotlinx.coroutines.delay(180)
            value = query to withContext(Dispatchers.IO) { store.searchIndex.search(query) }
        }
    }
    val currentSearch = indexedSearch?.takeIf { searchConversationText && it.first == query }
    val indexHits = currentSearch?.second.orEmpty()
    val searchComplete = !filtering || !searchConversationText || currentSearch != null
    val clearSearch = { onQuery("") }
    val linkFolder = { folderPicker.launch(null) }
    val sortConversations = { field: String, desc: Boolean ->
        sortField = field
        descending = desc
        store.preferences.edit().putString("sortField:$conversationScope", field).putBoolean("sortDescending:$conversationScope", desc).apply()
    }
    val recent = remember(revision, localRevision, files, entries) {
        val importIds = entries.mapTo(mutableSetOf()) { it.id }
        store.reading.recent().filter { item ->
            val id = store.referenceId(item.reference)
            id != null && (id.startsWith("linked_") || id in importIds)
        }
    }
    val folderCounts = remember(files) { folderFileCounts(files.map { it.path to it.isDirectory }) }
    val filtered = remember(entries, query, showSearch, sortField, descending) {
        val comparator = when (sortField) {
            "Name" -> compareBy<ImportedConversation> { it.metadata.title.lowercase() }
            "Length" -> compareBy { it.metadata.words }
            else -> compareBy { it.metadata.createdAt ?: it.metadata.modified }
        }
        entries.filter { !showSearch || matchesLibraryName(it.metadata.title, query) }
            .sortedWith((if (descending) comparator.reversed() else comparator).thenBy { it.metadata.title.lowercase() }.thenBy { it.id })
    }
            val prefix = if (path.isEmpty()) "" else "$path/"
            val folderSorter = if (folderGroupField == "Count" || folderGroupField == "Conversation count") compareBy<String> { folderCounts[prefix + it] ?: 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it } else String.CASE_INSENSITIVE_ORDER
            val folders = files.filter { it.isDirectory && it.path.startsWith(prefix) && '/' !in it.path.removePrefix(prefix) }
                .map { it.path.removePrefix(prefix) }
                .sortedWith((if (folderGroupDescending) folderSorter.reversed() else folderSorter).thenBy { it })
            val sorter = when (sortField) { "Name" -> compareBy<LibraryStore.FolderFile> { it.title.lowercase() }; "Length" -> compareBy<LibraryStore.FolderFile> { it.words ?: -1 }; else -> compareBy<LibraryStore.FolderFile> { it.modified } }
            val visible = files.filter { file ->
                if (file.isDirectory) return@filter false
                val allFiles = source == "All files"
                val inScope = allFiles || (file.path.startsWith(prefix) && '/' !in file.path.removePrefix(prefix))
                inScope && (!showSearch || matchesLibraryConversation(file.title, query, searchConversationText, store.linkedId(file.uri) in indexHits))
            }.sortedWith((if (descending) sorter.reversed() else sorter).thenBy { it.title.lowercase() }.thenBy { it.path })
            val visibleFolders = folders.filter { !showSearch || matchesLibraryName(it, query) }

    val modelItems = remember(entries, files) {
        entries.map { ModelConversation(it.id, store.reference(it), it.metadata.title, it.metadata.models, it.metadata.words, it.metadata.createdAt ?: it.metadata.modified, it.metadata.prompts) } +
            files.filter { !it.isDirectory }.map { f ->
                val id = store.linkedId(f.uri)
                val ref = org.json.JSONObject().put("id", id).put("uri", f.uri.toString()).put("name", f.path.substringAfterLast('/')).put("modified", f.modified).toString()
                ModelConversation(id, ref, f.title, f.models.orEmpty(), f.words, f.modified, f.prompts)
            }
    }
    val bookmarkedIds = remember(revision, localRevision) { store.reading.bookmarkedIds() }
    val cardWords = remember(entries, files) { entries.associate { it.id to it.metadata.words } + files.filter { !it.isDirectory }.associate { store.linkedId(it.uri) to it.words } }
    val cardPrompts = remember(entries, files) { entries.associate { it.id to it.metadata.prompts } + files.filter { !it.isDirectory }.associate { store.linkedId(it.uri) to it.prompts } }
    val cardDates = remember(entries, files) { entries.associate { it.id to (it.metadata.createdAt ?: it.metadata.modified) } + files.filter { !it.isDirectory }.associate { store.linkedId(it.uri) to it.modified } }
    val cards = remember(recent, entries, files, source, revision, localRevision, bookmarkedIds) { buildList {
        fun included(id: String) = source == "Recent" || id in bookmarkedIds
        val seen = mutableSetOf<String>()
        recent.forEach { r -> store.referenceId(r.reference)?.let { id -> if (included(id) && seen.add(id)) add(r) } }
        if (source != "Recent") {
            entries.forEach { e -> if (included(e.id) && seen.add(e.id)) add(RecentReading(store.reference(e), e.metadata.title, "", store.recent(e.id))) }
            files.filter { !it.isDirectory }.forEach { f ->
                val id = store.linkedId(f.uri)
                if (included(id) && seen.add(id)) add(RecentReading(org.json.JSONObject().put("id", id).put("uri", f.uri.toString()).put("name", f.path.substringAfterLast('/')).put("modified", f.modified).toString(), f.title, "", store.recent(id)))
            }
        }
    } }.filter { !showSearch || matchesLibraryName(it.title, query) }
    val sortedCards = remember(cards, source, sortField, descending, cardWords, cardDates) {
        val comparator = when (sortField) {
            "Name" -> compareBy<RecentReading> { it.title.lowercase() }
            "Length" -> compareBy { cardWords[store.referenceId(it.reference)] ?: -1 }
            else -> compareBy<RecentReading> { cardSortDate(source == "Recent", it, cardDates[store.referenceId(it.reference)]) }
        }
        if (source == "Recent") cards.sortedWith(compareByDescending<RecentReading> { it.openedAt }.thenBy { it.title.lowercase() }.thenBy { it.reference })
        else cards.sortedWith((if (descending) comparator.reversed() else comparator).thenBy { it.title.lowercase() }.thenBy { it.reference })
    }
    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(8.dp))
            if (!showSearch) Text(if (source in BrowsingViews) "Your library" else if (source == "Recent") "Recent conversations" else "Saved conversations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            if (source in BrowsingViews) {
                LibrarySourceTabs(source) { selectView(it) }
                Spacer(Modifier.height(8.dp))
            }
            if (showSearch && source == "All files") {
                FilterChip(includeConversationText, { includeConversationText = !includeConversationText },
                    label = { Text("Include conversation text") })
            }
            val last = recent.firstOrNull { item ->
                source != "Imported" || entries.any { it.id == store.referenceId(item.reference) }
            }
            if (!showSearch && source !in listOf("Recent", "Bookmarked") && (source != "Models" || selectedModel == null) && (source != "Folders" || path.isEmpty()) && last != null) {
                Card(onClick = { onReferenceOpen(last.reference, true) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Continue reading", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                        Text(last.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        if (last.excerpt.isNotBlank()) Text(last.excerpt, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        store.referenceId(last.reference)?.let { id ->
                            if (store.preferences.contains("readingProgress_$id")) {
                                val progress = store.preferences.getInt("readingProgress_$id", 0).coerceIn(0, 100)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.weight(1f).height(4.dp))
                                    Text("$progress%", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val activeList = when (source) {
        "Imported" -> importedList
        "Recent" -> recentList
        "Bookmarked" -> bookmarkedList
        "Models" -> if (selectedModel == null) modelGroupList else modelConversationList
        else -> linkedList
    }
    Column(Modifier.fillMaxSize()) {
        ReaderToolbar(showSearch && visibleScreen, query, onQuery, {
            onSearch()
            searchScope.launch { activeList.scrollToItem(0) }
        }, onCloseSearch, onSettings, title = "Conversation Reader", currentView = source, onMenuView = { selectView(it) }, searchHint = when (source) {
            "Folders" -> "Search this folder"
            "All files" -> if (searchConversationText) "Search titles and text" else "Search titles"
            "Models" -> if (selectedModel == null) "Search models" else "Search this model"
            "Imported" -> "Search imported titles"
            else -> "Search titles"
        })
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) content@{
            if (source == "Models") {
                ModelLibraryView(header, modelItems, if (showSearch) query else "", showSearch, clearSearch, bookmarkedIds, modelSortField, modelDescending,
                    onSort = { field, desc -> modelSortField = field; modelDescending = desc; store.preferences.edit().putString("modelConversationSort:${selectedModel.orEmpty()}", field).putBoolean("modelConversationDescending:${selectedModel.orEmpty()}", desc).apply() },
                    groupField = modelGroupField, groupDescending = modelGroupDescending, active = visibleScreen, selectedModel = selectedModel, onSelectModel = {
                        selectedModel = it
                        if (showSearch) { onQuery(""); onCloseSearch() }
                        searchScope.launch { modelConversationList.scrollToItem(0) }
                    }, groupList = modelGroupList, conversationList = modelConversationList,
                    onGroupSort = { field, desc -> modelGroupField = field; modelGroupDescending = desc; store.preferences.edit().putString("modelGroupSort", field).putBoolean("modelGroupDescending", desc).apply() },
                    onOpen = { onReferenceOpen(it, false) },
                    onBookmark = { id -> store.reading.setBookmarked(id, id !in bookmarkedIds); localRevision++ })
                return@content
            }
            if (source in listOf("Recent", "Bookmarked")) {
                PinnedLibraryPane(Modifier.weight(1f), controls = {
                    header()
                    if (source == "Bookmarked") LibrarySortControl("Conversations", listOf("Date", "Name", "Length"), sortField, descending) { field, desc ->
                        sortConversations(field, desc)
                    }
                }) {
                    LazyColumn(Modifier.fillMaxSize(), state = activeList, contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (cards.isEmpty()) item(key = "library:empty") {
                            if (filtering) LibrarySearchEmpty(searchComplete, clearSearch)
                            else LibraryEmptyState(if (source == "Recent") "No recently opened conversations." else "Tap a conversation’s star to add it to Bookmarked.")
                        }
                        items(sortedCards, key = { it.reference }) { card ->
                            val id = store.referenceId(card.reference)
                            val isRecent = source == "Recent"
                            val displayedDate = cardSortDate(isRecent, card, cardDates[id]).takeIf { it > 0 }
                            LibraryConversationCard(card.title, cardWords[id], displayedDate,
                                id in bookmarkedIds, onOpen = { onReferenceOpen(card.reference, false) },
                                onBookmark = { id?.let { store.reading.setBookmarked(it, it !in bookmarkedIds); localRevision++ } },
                                lastOpened = isRecent, prompts = cardPrompts[id])
                        }
                    }
                    LazyScrollSlider(activeList, sortedCards.map { it.reference })
                }
                return@content
            }
            if (source != "Imported") {
                PinnedLibraryPane(Modifier.weight(1f), controls = {
                        header()
                        if (linkedFolder != null) {
                            Column {
                                if (scanning && files.isNotEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Indexed $scanDone of $scanTotal files", style = MaterialTheme.typography.labelSmall) }
                                folderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (source == "Folders" && path.isNotEmpty()) FolderBreadcrumb(path, ::navigateFolder)
                                val groupCount = visibleFolders.size
                                val groupsVisible = source == "Folders" && groupCount > 0
                                if (groupsVisible) {
                                    val summary = if (showSearch) null else "$groupCount ${if (groupCount == 1) "folder" else "folders"}"
                                    LibraryGroupSortControl("Folders", if (folderGroupField == "Conversation count") "Count" else folderGroupField, folderGroupDescending, summary) { field, desc ->
                                        folderGroupField = field; folderGroupDescending = desc
                                        store.preferences.edit().putString("folderGroupSort:$folderScope", field).putBoolean("folderGroupDescending:$folderScope", desc).apply()
                                    }
                                }
                                if (source == "All files" || (visible.isNotEmpty() && (!groupsVisible || path.isNotEmpty()))) {
                                    val count = visible.size
                                    val summary = if (showSearch || (source == "Folders" && path.isEmpty())) null
                                        else "$count ${if (count == 1) "conversation" else "conversations"}"
                                    LibrarySortControl("Conversations", listOf("Date", "Name", "Length"), sortField, descending,
                                        summary = summary, onSort = sortConversations)
                                }
                            }
                        }
                }) {
                    LazyColumn(Modifier.fillMaxSize(), state = linkedList, contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (source == "Folders") items(visibleFolders) { folder ->
                            LibraryGroupRow(folder, folderCounts[prefix + folder] ?: 0, onClick = { navigateFolder(prefix + folder) })
                        }
                        items(visible, key = { it.uri.toString() }) { file ->
                            val id = store.linkedId(file.uri)
                            LibraryConversationCard(file.title, file.words, file.modified.takeIf { it > 0 },
                                id in bookmarkedIds, onOpen = { if (!busy) onFolderOpen(file) },
                                onBookmark = { store.reading.setBookmarked(id, id !in bookmarkedIds); localRevision++ },
                                snippet = indexHits[id], error = file.error, prompts = file.prompts)
                        }
                        if (!scanning && visible.isEmpty() && (source != "Folders" || visibleFolders.isEmpty())) item(key = "library:empty") {
                            when {
                                linkedFolder == null -> LibraryEmptyState(folderError ?: "No linked folder", "Link a folder", linkFolder)
                                filtering && (files.isNotEmpty() || folderError == null) -> LibrarySearchEmpty(searchComplete, clearSearch,
                                    if (source == "Folders") "No matches in this folder." else "No matching conversations.")
                                folderError != null -> Unit
                                else -> LibraryEmptyState("No conversations in this folder.")
                            }
                        }
                    }
                    LazyScrollSlider(linkedList, listOf(source, path, sortField, descending, query, visible.map { it.uri.toString() }))
                    if (scanning && files.isEmpty()) Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                            Text("Refreshing..")
                        }
                    }
                }
                return@content
            }
            PinnedLibraryPane(Modifier.weight(1f), controls = {
                    header()
                    if (entries.isNotEmpty() || busy) {
                        LibrarySortControl("Conversations", listOf("Date", "Name", "Length"), sortField, descending,
                            summary = "${filtered.size} ${if (filtered.size == 1) "file" else "files"}",
                            action = { TextButton(onClick = onImport, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text("+ Import JSON", style = MaterialTheme.typography.labelMedium)
                            } },
                            onSort = sortConversations)
                    }
            }) {
                LazyColumn(Modifier.fillMaxSize(), state = importedList, contentPadding = PaddingValues(bottom = 12.dp)) {
                    if (entries.isEmpty() && !busy) item(key = "library:empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (linkedFolder == null) "Open your LM Studio conversations" else "No imported conversations",
                                style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (linkedFolder == null) Button(onClick = linkFolder) { Text("Link a folder") }
                                FilledTonalButton(onClick = onImport) { Text("Import files") }
                            }
                            if (linkedFolder == null) folderError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    } else if (filtered.isEmpty() && filtering && !busy) item(key = "library:empty") {
                        LibrarySearchEmpty(searchComplete, clearSearch)
                    }
                    items(filtered, key = { it.id }) { entry ->
                        LibraryConversationCard(entry.metadata.title, entry.metadata.words,
                            entry.metadata.createdAt ?: entry.metadata.modified, entry.id in bookmarkedIds,
                            onOpen = { onReferenceOpen(store.reference(entry), false) },
                            onBookmark = { store.reading.setBookmarked(entry.id, entry.id !in bookmarkedIds); localRevision++ }, prompts = entry.metadata.prompts) {
                            ImportedConversationMenu(entry.metadata.title) { removing = entry }
                        }
                    }
                }
                LazyScrollSlider(importedList, listOf(showSearch, query, sortField, descending))
            }
        }
        LibraryBottomNavigation(source) { mode -> selectView(if (mode == "Library") librarySource else mode) }
    }
    removing?.let { entry -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove from library?") }, text = { Text("The imported copy of “${entry.metadata.title}” will be removed. Your original JSON file will remain unchanged.") }, confirmButton = { TextButton(onClick = { onRemove(entry); removing = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
}

@Composable
internal fun ConversationInfoDialog(entry: LibraryEntry, versions: Map<Int, Int>, onDismiss: () -> Unit) {
    val conversation = entry.conversation
    val words = entry.words
    val context = LocalContext.current
    var browserError by remember { mutableStateOf<String?>(null) }
    val numberFormat = remember { java.text.NumberFormat.getIntegerInstance() }
    ReaderDetailScreen("Conversation info", onDismiss, compact = true) {
        Text(conversation.title, style = MaterialTheme.typography.headlineSmall)
        DetailSection("Overview", compact = true) {
            InfoRow("Created", date(conversation.createdAt))
            val counts = remember(conversation, versions) { conversation.messageCounts(versions) }
            InfoRow("Prompts", numberFormat.format(counts.prompts))
            InfoRow("Replies", numberFormat.format(counts.replies))
            InfoRow("Total messages", numberFormat.format(counts.total))
            InfoRow("Words", numberFormat.format(words))
            val tokenCount = conversation.savedTokenCount ?: conversation.selectedGeneratedTokens(versions)
            InfoRow("Tokens", tokenCount?.let { numberFormat.format(it) } ?: "Not recorded")
            if (tokenCount != null) Text(if (conversation.savedTokenCount != null) "Token count saved by LM Studio" else "Generated tokens in selected replies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        DetailSection("File", compact = true) {
            val uriForText = entry.textUri ?: entry.sourceUri
            Text(entry.fileName.ifBlank { "Filename unavailable" }, modifier = Modifier.padding(vertical = 4.dp).then(if (uriForText != null) Modifier.clickable {
                browserError = try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uriForText, "text/plain").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    null
                } catch (_: Exception) { "No text reader could open this file. Install a text editor or choose another reader." }
            } else Modifier), color = if (uriForText != null) readerLinkColor() else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (uriForText != null) androidx.compose.ui.text.style.TextDecoration.Underline else null))

            InfoRow("Size", entry.fileSize?.let { android.text.format.Formatter.formatShortFileSize(context, it) } ?: "Not available")

            entry.sourceUri?.let { uri ->
                FilledTonalButton(onClick = {
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
                }, modifier = Modifier.padding(top = 4.dp)) { Text("Show in file browser") }
            }
            browserError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        DetailSection("Models", compact = true) {
            if (conversation.models.isEmpty()) Text("Not recorded", style = MaterialTheme.typography.bodyMedium)
            conversation.models.forEach { model -> ConversationModelAction(model) }
        }
        if (conversation.systemPrompt.isNotBlank()) {
            DetailSection("System prompt", compact = true) {
                SelectionContainer { Text(conversation.systemPrompt, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
internal fun Stats(stats: GenerationStats, trailingActions: @Composable RowScope.() -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "Hide generation info ↑" else "Generation info ↓", style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.weight(1f))
            trailingActions()
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "Generation speed" to (stats.speed?.let { "${decimal(it)} tok/s" } ?: "Not recorded"),
                "Time to first token" to (stats.firstToken?.let { "${decimal(it)} s" } ?: "Not recorded"),
                "Total generation time" to (stats.duration?.let { "${decimal(it)} s" } ?: "Not recorded"),
                "Prompt tokens" to (stats.prompt?.toString() ?: "Not recorded"),
                "Generated tokens" to (stats.generated?.toString() ?: "Not recorded"),
                "Total tokens" to (stats.total?.toString() ?: "Not recorded"),
                "Stop reason" to (if (stats.stop == "eosFound") "EOS token found" else stats.stop ?: "Not recorded")
            ).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun rememberMarkdownRenderer(): Markwon {
    val context = LocalContext.current
    return remember(context) { createMarkdownRenderer(context) }
}

internal fun createMarkdownRenderer(context: android.content.Context): Markwon = Markwon.builder(context).usePlugin(TablePlugin.create(context)).usePlugin(StrikethroughPlugin.create()).usePlugin(LinkifyPlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureSpansFactory(builder: io.noties.markwon.MarkwonSpansFactory.Builder) {
                builder.appendFactory(org.commonmark.node.Heading::class.java) { _, props ->
                    val level = io.noties.markwon.core.CoreProps.HEADING_LEVEL.require(props)
                    if (level == 1) arrayOf(ReadingHeading(level), android.text.style.StyleSpan(android.graphics.Typeface.BOLD))
                    else ReadingHeading(level)
                }
            }

            override fun configureTheme(builder: io.noties.markwon.core.MarkwonTheme.Builder) {
                builder.headingTextSizeMultipliers(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f))
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

