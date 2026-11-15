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
    @Test fun corruptFileKeepsFilenameAndDoesNotCrashWholeScan() = runBlocking {
        provider.raw = "not json"
        val files = LibraryStore(context).scanFolder()
        assertEquals("sample.conversation.json", files.single().title)
        assertNull(files.single().words)
    }
}

class AuditDocumentsProvider : ContentProvider() {
    var opens = 0
    var modified = 100L
    var raw = """{"name":"Cached title","messages":[{"versions":[{"role":"user","content":[{"type":"text","text":"Hello world"}]}]}]}"""
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor {
        // Robolectric uses the host File.renameTo implementation. Windows cannot replace an existing
        // destination like Android can. The app has already read the old index before this query.
        // Remove only this test sandbox's index so these tests exercise reuse/invalidation rather
        // than Windows rename semantics; Android atomic replacement still needs device testing.
        File(context!!.filesDir, "folder-index.json").delete()
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
