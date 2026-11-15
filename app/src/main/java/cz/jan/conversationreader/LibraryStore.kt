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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class LibraryEntry(val id: String, val conversation: Conversation, val importedAt: Long, val fileName: String = "", val fileSize: Long? = null, val sourceUri: Uri? = null, val textUri: Uri? = null)

class LibraryStore(private val context: Context) {
    val preferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    val reading = ReadingState(preferences)
    val searchIndex = LibrarySearchIndex(context)
    private val directory = File(context.filesDir, "conversations").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "folder-index.json")
    data class FolderFile(val path: String, val uri: Uri, val modified: Long, val isDirectory: Boolean = false, val title: String = path.substringAfterLast('/'), val words: Int? = null, val size: Long? = null, val error: String? = null)
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
            FolderFile(f.getString("path"), Uri.parse(f.getString("uri")), f.getLong("modified"), f.getBoolean("directory"), f.getString("title"), if (f.isNull("words")) null else f.getInt("words"), if (f.isNull("size")) null else f.getLong("size"), if (f.isNull("error")) null else f.optString("error"))
        } }
    }.getOrDefault(emptyList())
    suspend fun scanFolder(onProgress: (List<FolderFile>, Int, Int) -> Unit = { _, _, _ -> }): List<FolderFile> = coroutineScope {
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
        val provisional = result.map { f -> cached[f.uri.toString()]?.takeIf { reusableMetadata(it.modified, it.size, f.modified, f.size) }?.let { f.copy(title = it.title, words = it.words, error = it.error) } ?: f }.toMutableList()
        val totalFiles = result.count { !it.isDirectory }
        onProgress(provisional.toList(), 0, totalFiles)
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val limiter = Semaphore(4)
        val updated = result.mapIndexed { index, file -> async {
            val enriched =
            if (file.isDirectory) file else {
                val old = cached[file.uri.toString()]
                if (old != null && reusableMetadata(old.modified, old.size, file.modified, file.size) && old.words != null && searchIndex.contains(linkedId(file.uri))) file.copy(title = old.title, words = old.words)
                else limiter.withPermit {
                    runCatching {
                        val raw = bytes(file.uri).toString(Charsets.UTF_8).removePrefix("\uFEFF")
                        val title = (JSONObject(raw).opt("name") as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: file.title
                        val conversation = ConversationParser.parse(raw)
                        searchIndex.put(linkedId(file.uri), conversation)
                        file.copy(title = title, words = conversation.wordCount())
                    }.getOrElse { searchIndex.remove(linkedId(file.uri)); file.copy(error = it.message ?: "Unreadable JSON") }
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
        updated.forEach { f -> json.put(JSONObject().put("path", f.path).put("uri", f.uri.toString()).put("modified", f.modified).put("directory", f.isDirectory).put("title", f.title).put("words", f.words ?: JSONObject.NULL).put("size", f.size ?: JSONObject.NULL).put("error", f.error ?: JSONObject.NULL)) }
        val temp = File(context.filesDir, "folder-index.tmp")
        temp.writeText(JSONObject().put("tree", tree.toString()).put("files", json).toString())
        check(temp.renameTo(indexFile)) { "Unable to save folder index." }
        updated
    }
    fun openLinked(file: FolderFile): LibraryEntry {
        val raw = bytes(file.uri)
        val id = linkedId(file.uri)
        return LibraryEntry(id, ConversationParser.parse(raw.toString(Charsets.UTF_8)), file.modified, file.path.substringAfterLast('/'), raw.size.toLong(), file.uri)
    }
    fun link(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.edit().putString("linkedFolder", uri.toString()).apply()
    }
    fun load(): Pair<List<LibraryEntry>, Int> {
        var unreadable = 0
        // Older releases created snapshots merely by reading linked files. Hide those copies, without deleting user data.
        val sources = preferences.all.filterKeys { it.startsWith("source_") }
        val automatic = sources.filterKeys { it.contains("/tree/") }.values.filterIsInstance<String>().toSet()
        val previousManual = sources.filterKeys { !it.contains("/tree/") }.values.filterIsInstance<String>().toSet()
        val entries = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
            val id = file.nameWithoutExtension
            if (id in automatic && id !in previousManual && !preferences.getBoolean("manual_$id", false)) return@mapNotNull null
            runCatching { LibraryEntry(id, ConversationParser.parse(file.readText()).also { if (!searchIndex.contains(id)) searchIndex.put(id, it) }, file.lastModified(), preferences.getString("filename_$id", null) ?: file.name, file.length(), preferences.getString("uri_$id", null)?.let { Uri.parse(it) }, androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
                .getOrElse { unreadable++; null }
        }
        return entries to unreadable
    }
    fun import(uri: Uri): Boolean {
        val raw = bytes(uri)
        val conversation = ConversationParser.parse(raw.toString(Charsets.UTF_8))
        val id = digest(raw)
        searchIndex.put(id, conversation)
        var name = uri.lastPathSegment ?: "Conversation JSON"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        preferences.edit().putBoolean("manual_$id", true).putString("filename_$id", name).putString("uri_$id", uri.toString()).apply()
        val destination = File(directory, "$id.json")
        if (destination.exists()) return false
        val temporary = File(directory, "$id.tmp")
        temporary.writeBytes(raw)
        check(temporary.renameTo(destination)) { "Unable to save imported conversation." }
        return true
    }
    private val linkedIds = java.util.concurrent.ConcurrentHashMap<Uri, String>()
    fun linkedId(uri: Uri): String = linkedIds.getOrPut(uri) { "linked_" + digest(uri.toString().toByteArray()) }
    fun reference(entry: LibraryEntry): String = JSONObject().put("id", entry.id).put("uri", entry.sourceUri?.toString().orEmpty()).put("name", entry.fileName).put("modified", entry.importedAt).toString()
    fun referenceId(reference: String): String? = runCatching { JSONObject(reference).getString("id") }.getOrNull()
    fun openReference(reference: String): LibraryEntry {
        val r = JSONObject(reference)
        val id = r.getString("id")
        if (id.startsWith("linked_")) {
            val uri = Uri.parse(r.getString("uri"))
            return openLinked(FolderFile(r.optString("name"), uri, r.optLong("modified")))
        }
        return load().first.firstOrNull { it.id == id } ?: error("This imported conversation is no longer available.")
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun remove(id: String) { check(File(directory, "$id.json").delete()) { "Unable to remove conversation." }; searchIndex.remove(id) }
    fun opened(id: String) { preferences.edit().putLong("recent_$id", System.currentTimeMillis()).apply() }
    fun recent(id: String) = preferences.getLong("recent_$id", 0)
}
