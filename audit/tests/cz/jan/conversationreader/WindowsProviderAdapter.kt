package cz.jan.conversationreader

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File

/** Test-only adapter: Android FileProvider uses '/' checks that cannot work with Windows canonical paths.
 * This enables UI/library tests on Windows; it does NOT validate the production FileProvider itself.
 */
@Implements(value = FileProvider::class, isInAndroidSdk = false)
class WindowsFileProviderShadow {
    companion object {
        @JvmStatic @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri = Uri.parse("content://audit.local/${file.name}")
    }
}

class AuditLocalFilesProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(File(context!!.filesDir, "conversations/${uri.lastPathSegment}"), ParcelFileDescriptor.MODE_READ_ONLY)
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? = null
    override fun getType(uri: Uri) = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
