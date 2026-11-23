package cz.jan.conversationreader

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageAuditTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var provider: AuditDocumentsProvider
    @Before fun setup() {
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().clear().putString("linkedFolder", "content://audit.documents/tree/root").commit()
        File(context.filesDir, "folder-index.json").delete()
        File(context.filesDir, "conversations").listFiles().orEmpty().forEach { it.delete() }
        provider = AuditDocumentsProvider()
        provider.attachInfo(context, ProviderInfo().apply { authority = "audit.documents" })
        ShadowContentResolver.registerProviderInternal("audit.documents", provider)
    }
    @Test fun unchangedScanDoesNotReopenJsonAndLinkedReadCreatesNoImport() = runBlocking {
        val store = LibraryStore(context)
        val first = store.scanFolder()
        assertEquals(1, provider.opens)
        assertEquals("Cached title", first.single().title)
        assertEquals(1, store.cachedFolder().size)
        store.scanFolder()
        assertEquals(1, provider.opens)
        val entry = store.openLinked(first.single())
        assertEquals("Cached title", entry.conversation.title)
        assertTrue(store.load().first.isEmpty())
    }
    @Test fun changedSizeAndTimestampInvalidateIndex() = runBlocking {
        val store = LibraryStore(context)
        store.scanFolder()
        provider.raw = provider.raw.replace("Cached title", "A changed conversation title")
        provider.modified++
        val updated = store.scanFolder()
        assertEquals(2, provider.opens)
        assertEquals("A changed conversation title", updated.single().title)
    }
    @Test fun failedFolderRefreshRetainsLastCompleteIndex() = runBlocking {
        val store = LibraryStore(context)
        store.scanFolder()
        val index = File(context.filesDir, "folder-index.json")
        val before = index.readBytes()
        provider.failQuery = true
        assertTrue(runCatching { store.scanFolder() }.isFailure)
        assertArrayEquals(before, index.readBytes())
        assertEquals("Cached title", store.cachedFolder().single().title)
    }
    @Test fun corruptFileKeepsFilenameAndDoesNotCrashWholeScan() = runBlocking {
        provider.raw = "not json"
        val files = LibraryStore(context).scanFolder()
        assertEquals("sample.conversation.json", files.single().title)
        assertNull(files.single().words)
    }

    @Test fun savedImportsReuseMetadataAndReindexAfterChange() {
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        val file = File(directory, "cached.json").apply { writeText(provider.raw) }
        val store = LibraryStore(context)
        val conversation = ConversationParser.parse(provider.raw)
        store.searchIndex.putImport("cached", conversation, ImportMetadata(
            file.lastModified(), file.length(), conversation.title, conversation.createdAt,
            conversation.wordCount(), conversation.models))
        assertEquals("Cached title", store.load().first.single().metadata.title)
        assertEquals("Cached title", LibraryStore(context).load().first.single().metadata.title)

        file.writeText(provider.raw.replace("Cached title", "Changed synthetic title"))
        val updated = store.load().first.single().metadata
        assertEquals("Changed synthetic title", updated.title)
        assertTrue(store.searchIndex.search("Changed synthetic title").containsKey("cached"))
        assertFalse(store.searchIndex.search("Cached title").containsKey("cached"))
    }

    @Test fun successfulFolderChangePrunesOldLinkedSearchRowsWithoutRemovingImports() = runBlocking {
        val store = LibraryStore(context)
        val old = store.scanFolder().single()
        val oldId = store.linkedId(old.uri)
        File(context.filesDir, "conversations/manual.json").writeText(provider.raw.replace("Cached title", "Manual example"))
        store.load()

        store.preferences.edit().putString("linkedFolder", "content://audit.documents/tree/replacement").commit()
        provider.failQuery = true
        assertTrue(runCatching { store.scanFolder() }.isFailure)
        assertTrue(store.searchIndex.contains(oldId))

        provider.failQuery = false
        provider.raw = provider.raw.replace("Hello world", "Replacement passage")
        val current = store.scanFolder().single()
        assertFalse(store.searchIndex.contains(oldId))
        assertTrue(store.searchIndex.search("Replacement passage").containsKey(store.linkedId(current.uri)))
        assertTrue(store.searchIndex.search("Manual example").containsKey("manual"))
    }

    @Test fun removedImportLeavesTheNextReadingAndClearsItsOwnState() {
        val store = LibraryStore(context)
        val kept = LibraryEntry("sample-other", ConversationParser.parse(provider.raw), 1)
        val removed = kept.copy(id = "sample")
        val file = File(context.filesDir, "conversations/sample.json").apply { writeText(provider.raw) }
        store.load()
        for (item in listOf(kept, removed)) {
            store.reading.record(item, store.reference(item))
            store.reading.setBookmarked(item.id, true)
            store.reading.saveResumeAnchor(item.id, ReadingAnchor(0, character = 4))
            store.opened(item.id)
        }
        store.preferences.edit().putInt("version_sample_0", 1).putInt("version_sample-other_0", 2)
            .putBoolean("favorite_sample", true).putInt("readingProgress_sample", 42).commit()

        store.remove(removed.id)
        assertFalse(file.exists())
        assertFalse(store.searchIndex.contains(removed.id))
        assertTrue(store.searchIndex.importedMetadata().isEmpty())
        assertEquals(kept.id, store.referenceId(store.reading.recent().single().reference))
        assertEquals(setOf(kept.id), store.reading.bookmarkedIds())
        assertNull(store.reading.resumeAnchor(removed.id))
        assertEquals(0L, store.recent(removed.id))
        assertFalse(store.preferences.contains("version_sample_0"))
        assertFalse(store.preferences.contains("readingProgress_sample"))
        assertEquals(2, store.preferences.getInt("version_sample-other_0", 0))
    }
}

class AuditDocumentsProvider : ContentProvider() {
    var opens = 0
    var failQuery = false
    var modified = 100L
    var raw = """{"name":"Cached title","messages":[{"versions":[{"role":"user","content":[{"type":"text","text":"Hello world"}]}]}]}"""
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor {
        if (failQuery) throw java.io.IOException("Synthetic unavailable folder")
        val columns = projection ?: arrayOf("document_id", "_display_name", "mime_type", "last_modified", "_size")
        return MatrixCursor(columns).apply { addRow(columns.map { when (it) {
            "document_id" -> "root/sample"; "_display_name" -> "sample.conversation.json"; "mime_type" -> "application/json"; "last_modified" -> modified; "_size" -> raw.toByteArray().size.toLong(); else -> null
        } }) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        opens++
        val file = File(context!!.cacheDir, "audit-provider.json").apply { writeText(raw) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
