package cz.jan.conversationreader

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

data class LibraryEntry(val id: String, val conversation: Conversation, val importedAt: Long)

class LibraryStore(private val context: Context) {
    data class FolderFile(val path: String, val uri: Uri, val modified: Long, val isDirectory: Boolean = false, val title: String = path.substringAfterLast('/'), val words: Int? = null)
    private fun readFolderTitle(uri: Uri, fallback: String): Pair<String, Int?> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = stream.read(buffer)
            while (count != -1) {
                require(output.size() + count <= 20 * 1024 * 1024)
                output.write(buffer, 0, count)
                count = stream.read(buffer)
            }
            val bytes = output.toByteArray()
            val raw = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val root = org.json.JSONObject(raw)
            val title = (root.opt("name") as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
            title to runCatching { ConversationParser.parse(raw).wordCount() }.getOrNull()
        } ?: (fallback to null)
    }.getOrDefault(fallback to null)
    fun scanFolder(): List<FolderFile> {
        val tree = Uri.parse(preferences.getString("linkedFolder", null) ?: return emptyList())
        val result = mutableListOf<FolderFile>()
        val visited = mutableSetOf<String>()
        fun scan(id: String, path: String) {
            check(visited.size < 10000) { "Folder contains too many directories." }
            if (!visited.add(id)) return
            val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            val columns = arrayOf("document_id", "_display_name", "mime_type", "last_modified")
            val cursor = context.contentResolver.query(children, columns, null, null, null) ?: error("Cannot read linked folder. Choose it again.")
            cursor.use { c -> while (c.moveToNext()) {
                val child = c.getString(0); val name = c.getString(1) ?: "Untitled"
                val relative = if (path.isEmpty()) name else "$path/$name"
                if (c.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    result += FolderFile(relative, android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, child), c.getLong(3), true)
                    scan(child, relative)
                }
                else if (name.endsWith(".json", true) || name.endsWith(".conversation", true)) {
                    val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, child)
                    val metadata = readFolderTitle(uri, name)
                    result += FolderFile(relative, uri, c.getLong(3), title = metadata.first, words = metadata.second)
                }
            } }
        }
        scan(android.provider.DocumentsContract.getTreeDocumentId(tree), "")
        return result
    }
    fun link(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.edit().putString("linkedFolder", uri.toString()).apply()
    }
    private val directory = File(context.filesDir, "conversations").apply { mkdirs() }
    val preferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    fun load(): Pair<List<LibraryEntry>, Int> {
        var unreadable = 0
        val entries = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
            runCatching { LibraryEntry(file.nameWithoutExtension, ConversationParser.parse(file.readText()), file.lastModified()) }
                .getOrElse { unreadable++; null }
        }
        return entries to unreadable
    }
    fun import(uri: Uri): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = stream.read(buffer)
            while (count != -1) {
                require(result.size() + count <= 20 * 1024 * 1024) { "File exceeds the 20 MB import limit." }
                result.write(buffer, 0, count)
                count = stream.read(buffer)
            }
            result.toByteArray()
        } ?: error("Unable to open this file.")
        ConversationParser.parse(bytes.toString(Charsets.UTF_8))
        val id = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val destination = File(directory, "$id.json")
        preferences.edit().putString("source_$uri", id).apply()
        if (destination.exists()) return false
        val temporary = File(directory, "$id.tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(destination)) { "Unable to save imported conversation." }
        return true
    }
    fun remove(id: String) { check(File(directory, "$id.json").delete()) { "Unable to remove conversation." } }
    fun opened(id: String) { preferences.edit().putLong("recent_$id", System.currentTimeMillis()).apply() }
    fun recent(id: String) = preferences.getLong("recent_$id", 0)
}
