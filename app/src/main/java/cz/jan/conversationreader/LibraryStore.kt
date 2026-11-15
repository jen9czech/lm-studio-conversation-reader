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

data class LibraryEntry(val id: String, val conversation: Conversation, val importedAt: Long, val fileName: String = "", val fileSize: Long? = null, val sourceUri: Uri? = null)

class LibraryStore(private val context: Context) {
    val preferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "conversations").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "folder-index.json")
    data class FolderFile(val path: String, val uri: Uri, val modified: Long, val isDirectory: Boolean = false, val title: String = path.substringAfterLast('/'), val words: Int? = null, val size: Long? = null)
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
            FolderFile(f.getString("path"), Uri.parse(f.getString("uri")), f.getLong("modified"), f.getBoolean("directory"), f.getString("title"), if (f.isNull("words")) null else f.getInt("words"), if (f.isNull("size")) null else f.getLong("size"))
        } }
    }.getOrDefault(emptyList())
    suspend fun scanFolder(): List<FolderFile> = coroutineScope {
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
        val limiter = Semaphore(4)
        val updated = result.map { file -> async {
            if (file.isDirectory) file else {
                val old = cached[file.uri.toString()]
                if (old != null && reusableMetadata(old.modified, old.size, file.modified, file.size) && old.words != null) file.copy(title = old.title, words = old.words)
                else limiter.withPermit {
                    runCatching {
                        val raw = bytes(file.uri).toString(Charsets.UTF_8).removePrefix("\uFEFF")
                        val title = (JSONObject(raw).opt("name") as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: file.title
                        file.copy(title = title, words = runCatching { ConversationParser.parse(raw).wordCount() }.getOrNull())
                    }.getOrDefault(file)
                }
            }
        } }.awaitAll()
        val json = JSONArray()
        updated.forEach { f -> json.put(JSONObject().put("path", f.path).put("uri", f.uri.toString()).put("modified", f.modified).put("directory", f.isDirectory).put("title", f.title).put("words", f.words ?: JSONObject.NULL).put("size", f.size ?: JSONObject.NULL)) }
        val temp = File(context.filesDir, "folder-index.tmp")
        temp.writeText(JSONObject().put("tree", tree.toString()).put("files", json).toString())
        check(temp.renameTo(indexFile)) { "Unable to save folder index." }
        updated
    }
    fun openLinked(file: FolderFile): LibraryEntry {
        val raw = bytes(file.uri)
        val id = "linked_" + digest(file.uri.toString().toByteArray())
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
            runCatching { LibraryEntry(id, ConversationParser.parse(file.readText()), file.lastModified(), preferences.getString("filename_$id", null) ?: file.name, file.length(), preferences.getString("uri_$id", null)?.let { Uri.parse(it) }) }
                .getOrElse { unreadable++; null }
        }
        return entries to unreadable
    }
    fun import(uri: Uri): Boolean {
        val raw = bytes(uri)
        ConversationParser.parse(raw.toString(Charsets.UTF_8))
        val id = digest(raw)
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
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun remove(id: String) { check(File(directory, "$id.json").delete()) { "Unable to remove conversation." } }
    fun opened(id: String) { preferences.edit().putLong("recent_$id", System.currentTimeMillis()).apply() }
    fun recent(id: String) = preferences.getLong("recent_$id", 0)
}
