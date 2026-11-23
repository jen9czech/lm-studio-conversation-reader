package cz.jan.conversationreader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class LibraryEntry(val id: String, val conversation: Conversation, val importedAt: Long, val fileName: String = "", val fileSize: Long? = null, val sourceUri: Uri? = null, val textUri: Uri? = null, val words: Int = conversation.wordCount())

internal data class ImportMetadata(val modified: Long, val size: Long, val title: String, val createdAt: Long?, val words: Int, val models: List<String>, val prompts: Int? = null)
internal data class ImportedConversation(val id: String, val fileName: String, val metadata: ImportMetadata)

class LibraryStore(private val context: Context) {
    val preferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    val reading = ReadingState(preferences)
    val searchIndex = LibrarySearchIndex(context)
    private val directory = File(context.filesDir, "conversations").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "folder-index.json")
    data class FolderFile(val path: String, val uri: Uri, val modified: Long, val isDirectory: Boolean = false, val title: String = path.substringAfterLast('/'), val words: Int? = null, val size: Long? = null, val error: String? = null, val models: List<String>? = null, val prompts: Int? = null)
    private fun bytes(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use { stream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = stream.read(buffer)
        while (count != -1) {
            require(output.size() + count <= 20 * 1024 * 1024) { "File exceeds the 20 MB limit." }
            output.write(buffer, 0, count)
            count = stream.read(buffer)
        }
        output.toByteArray()
    } ?: error("Unable to open this file.")
    fun cachedFolder(): List<FolderFile> = runCatching {
        val root = JSONObject(indexFile.readText())
        if (root.optString("tree") != preferences.getString("linkedFolder", "")) return emptyList()
        val files = root.getJSONArray("files")
        (0 until files.length()).map { i -> files.getJSONObject(i).let { f ->
            FolderFile(f.getString("path"), Uri.parse(f.getString("uri")), f.getLong("modified"), f.getBoolean("directory"), f.getString("title"), if (f.isNull("words")) null else f.getInt("words"), if (f.isNull("size")) null else f.getLong("size"), if (f.isNull("error")) null else f.optString("error"), f.optJSONArray("models")?.let { a -> (0 until a.length()).map { a.getString(it) } }, if (f.isNull("prompts")) null else f.getInt("prompts"))
        } }
    }.getOrDefault(emptyList())
    private val folderScanLock = Mutex()

    suspend fun scanFolder(onProgress: (List<FolderFile>, Int, Int) -> Unit = { _, _, _ -> }): List<FolderFile> =
        folderScanLock.withLock { scanLinkedFolder(onProgress) }

    private suspend fun scanLinkedFolder(onProgress: (List<FolderFile>, Int, Int) -> Unit): List<FolderFile> = coroutineScope {
        val tree = Uri.parse(preferences.getString("linkedFolder", null) ?: return@coroutineScope emptyList())
        val cached = cachedFolder().associateBy { it.uri.toString() }
        val result = mutableListOf<FolderFile>()
        val visited = mutableSetOf<String>()
        fun scan(id: String, path: String) {
            check(visited.size < 10000) { "Folder contains too many directories." }
            if (!visited.add(id)) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            val cursor = context.contentResolver.query(children, arrayOf("document_id", "_display_name", "mime_type", "last_modified", "_size"), null, null, null) ?: error("Cannot read linked folder. Choose it again.")
            val directories = mutableListOf<Pair<String, String>>()
            cursor.use { c -> while (c.moveToNext()) {
                val child = c.getString(0); val name = c.getString(1) ?: "Untitled"
                val relative = if (path.isEmpty()) name else "$path/$name"
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, child)
                val folder = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (folder || name.endsWith(".json", true) || name.endsWith(".conversation", true)) {
                    result += FolderFile(relative, uri, c.getLong(3), folder, size = if (c.isNull(4)) null else c.getLong(4))
                    if (folder) directories += child to relative
                }
            } }
            directories.forEach { (child, relative) -> scan(child, relative) }
        }
        scan(DocumentsContract.getTreeDocumentId(tree), "")
        val provisional = result.map { f -> cached[f.uri.toString()]?.takeIf { reusableMetadata(it.modified, it.size, f.modified, f.size) }?.let { f.copy(title = it.title, words = it.words, error = it.error, models = it.models, prompts = it.prompts) } ?: f }.toMutableList()
        val totalFiles = result.count { !it.isDirectory }
        onProgress(provisional.toList(), 0, totalFiles)
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val limiter = Semaphore(4)
        val updated = result.mapIndexed { index, file -> async {
            val enriched =
            if (file.isDirectory) file else {
                val old = cached[file.uri.toString()]
                if (old != null && reusableMetadata(old.modified, old.size, file.modified, file.size) && old.words != null && old.models != null && old.prompts != null && searchIndex.contains(linkedId(file.uri))) file.copy(title = old.title, words = old.words, models = old.models, prompts = old.prompts)
                else limiter.withPermit {
                    runCatching {
                        val raw = bytes(file.uri).toString(Charsets.UTF_8).removePrefix("\uFEFF")
                        val title = (JSONObject(raw).opt("name") as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: file.title
                        val conversation = ConversationParser.parse(raw)
                        searchIndex.put(linkedId(file.uri), conversation)
                        file.copy(title = title, words = conversation.wordCount(), models = conversation.models, prompts = conversation.messageCounts(emptyMap()).prompts)
                    }.getOrElse {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        file.copy(error = it.message ?: "Unreadable JSON")
                    }
                }
            }
            synchronized(provisional) {
                provisional[index] = enriched
                if (!file.isDirectory) {
                    val done = completed.incrementAndGet()
                    if (done % 8 == 0 || done == totalFiles) onProgress(provisional.toList(), done, totalFiles)
                }
            }
            enriched
        } }.awaitAll()
        val json = JSONArray()
        updated.forEach { f -> json.put(JSONObject().put("path", f.path).put("uri", f.uri.toString()).put("modified", f.modified).put("directory", f.isDirectory).put("title", f.title).put("words", f.words ?: JSONObject.NULL).put("size", f.size ?: JSONObject.NULL).put("error", f.error ?: JSONObject.NULL).put("models", f.models?.let { JSONArray(it) } ?: JSONObject.NULL).put("prompts", f.prompts ?: JSONObject.NULL)) }
        synchronized(this@LibraryStore) {
            ensureActive()
            check(preferences.getString("linkedFolder", null) == tree.toString()) { "Linked folder changed during indexing." }
            atomicWrite(indexFile, JSONObject().put("tree", tree.toString()).put("files", json).toString().toByteArray(Charsets.UTF_8))
            val indexedIds = updated.filter { !it.isDirectory && it.error == null }.mapTo(mutableSetOf()) { linkedId(it.uri) }
            searchIndex.retainLinkedDocuments(indexedIds)
        }
        updated
    }
    fun openLinked(file: FolderFile): LibraryEntry {
        val raw = bytes(file.uri)
        val id = linkedId(file.uri)
        return LibraryEntry(id, ConversationParser.parse(raw.toString(Charsets.UTF_8)), file.modified, file.path.substringAfterLast('/'), raw.size.toLong(), file.uri)
    }
    @Synchronized fun link(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.edit().putString("linkedFolder", uri.toString()).apply()
    }
    private fun hiddenImports(): Set<String> {
        // Older releases created snapshots merely by reading linked files. Hide those copies, without deleting user data.
        val sources = preferences.all.filterKeys { it.startsWith("source_") }
        val automatic = sources.filterKeys { it.contains("/tree/") }.values.filterIsInstance<String>().toSet()
        val previousManual = sources.filterKeys { !it.contains("/tree/") }.values.filterIsInstance<String>().toSet()
        return (automatic - previousManual).filterNot { preferences.getBoolean("manual_$it", false) }.toSet()
    }

    private fun importMetadata(file: File, conversation: Conversation) =
        ImportMetadata(file.lastModified(), file.length(), conversation.title, conversation.createdAt, conversation.wordCount(), conversation.models, conversation.messageCounts(emptyMap()).prompts)

    @Synchronized internal fun load(): Pair<List<ImportedConversation>, Int> {
        var unreadable = 0
        val hidden = hiddenImports()
        val imports = directory.listFiles().orEmpty().filter {
            it.isFile && it.extension == "json" && it.nameWithoutExtension !in hidden
        }
        if (imports.isEmpty()) return emptyList<ImportedConversation>() to 0
        val cached = searchIndex.importedMetadata()
        val entries = imports.mapNotNull { file ->
            val id = file.nameWithoutExtension
            runCatching {
                val metadata = cached[id]?.takeIf { it.prompts != null && reusableMetadata(it.modified, it.size, file.lastModified(), file.length()) }
                    ?: run {
                        val conversation = ConversationParser.parse(file.readText())
                        importMetadata(file, conversation).also { searchIndex.putImport(id, conversation, it) }
                    }
                ImportedConversation(id, preferences.getString("filename_$id", null) ?: file.name, metadata)
            }.getOrElse { unreadable++; null }
        }
        return entries to unreadable
    }
    @Synchronized fun import(uri: Uri): Boolean {
        val raw = bytes(uri)
        val conversation = ConversationParser.parse(raw.toString(Charsets.UTF_8))
        val id = digest(raw)
        var name = uri.lastPathSegment ?: "Conversation JSON"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val destination = File(directory, "$id.json")
        val added = !destination.exists()
        if (added) atomicWrite(destination, raw)
        preferences.edit().putBoolean("manual_$id", true).putString("filename_$id", name).putString("uri_$id", uri.toString()).apply()
        if (added) searchIndex.putImport(id, conversation, importMetadata(destination, conversation))
        return added
    }
    private val linkedIds = java.util.concurrent.ConcurrentHashMap<Uri, String>()
    fun linkedId(uri: Uri): String = linkedIds.getOrPut(uri) { "linked_" + digest(uri.toString().toByteArray()) }
    fun reference(entry: LibraryEntry): String = JSONObject().put("id", entry.id).put("uri", entry.sourceUri?.toString().orEmpty()).put("name", entry.fileName).put("modified", entry.importedAt).toString()
    internal fun reference(entry: ImportedConversation): String = JSONObject().put("id", entry.id).toString()
    fun referenceId(reference: String): String? = readingReferenceId(reference)
    fun openReference(reference: String): LibraryEntry {
        val r = JSONObject(reference)
        val id = r.getString("id")
        if (id.startsWith("linked_")) {
            val uri = Uri.parse(r.getString("uri"))
            return openLinked(FolderFile(r.optString("name"), uri, r.optLong("modified")))
        }
        return openImport(id)
    }
    @Synchronized private fun openImport(id: String): LibraryEntry {
        require(id.isNotEmpty() && '/' !in id && '\\' !in id && id !in hiddenImports()) { "This imported conversation is no longer available." }
        val file = File(directory, "$id.json")
        check(file.isFile) { "This imported conversation is no longer available." }
        val modified = file.lastModified()
        val size = file.length()
        val conversation = ConversationParser.parse(file.readText())
        return LibraryEntry(id, conversation, modified,
            preferences.getString("filename_$id", null) ?: file.name, size,
            preferences.getString("uri_$id", null)?.let(Uri::parse),
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file),
            words = searchIndex.importWords(id, modified, size) ?: conversation.wordCount())
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @Synchronized fun remove(id: String) {
        val file = File(directory, "$id.json")
        check(!file.exists() || file.delete()) { "Unable to remove conversation." }
        reading.removeImport(id)
        searchIndex.removeImport(id)
    }
    fun opened(id: String) { preferences.edit().putLong("recent_$id", System.currentTimeMillis()).apply() }
    fun recent(id: String) = preferences.getLong("recent_$id", 0)
}
