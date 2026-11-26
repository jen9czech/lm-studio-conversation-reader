package cz.jan.conversationreader

import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h850dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScopeAuditTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun linkedViewsKeepSearchAndSortingScopedToTheDisplayedList() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore(context)
        val tree = "content://audit.documents/tree/root"
        val topUri = "$tree/document/1"
        store.preferences.edit().clear().putString("linkedFolder", tree)
            .putString("folderGroupSort:$tree:", "Conversation count").putString("modelGroupSort", "Conversation count").commit()
        val data = JSONArray()
            .put(JSONObject().put("path", "Archive").put("uri", "$tree/document/0").put("directory", true).put("modified", 0).put("title", "Archive"))
            .put(JSONObject().put("path", "hidden-filename.json").put("uri", topUri).put("directory", false).put("modified", 1000).put("title", "Gardening notes").put("words", 30))
            .put(JSONObject().put("path", "Archive/nested.json").put("uri", "$tree/document/2").put("directory", false).put("modified", 2000).put("title", "A nested conversation").put("words", 40))
        File(context.filesDir, "folder-index.json").writeText(JSONObject().put("tree", tree).put("files", data).toString())
        store.searchIndex.put(store.linkedId(Uri.parse(topUri)), ConversationParser.parse(
            """{"name":"Gardening notes","messages":[{"versions":[{"role":"user","content":"Woodland recipe"}]}]}"""
        ))
        val provider = AuditDocumentsProvider().apply { failQuery = true }
        provider.attachInfo(context, ProviderInfo().apply { authority = "audit.documents" })
        ShadowContentResolver.registerProviderInternal("audit.documents", provider)

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Gardening notes").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 folder").assertIsDisplayed()
            compose.onNodeWithText("Count ↑").assertIsDisplayed()
            compose.onAllNodesWithText("conversations", substring = true).assertCountEquals(0)
            compose.onNodeWithText("Models").performClick()
            compose.onNodeWithText("1 model").assertIsDisplayed()
            compose.onNodeWithText("Count ↑").assertIsDisplayed()
            compose.onAllNodesWithText("conversations", substring = true).assertCountEquals(0)
            compose.onNodeWithText("Folders").performClick()
            compose.onNodeWithContentDescription("Search").performClick()
            val search = compose.onNode(hasSetTextAction())
            search.performTextInput("nested")
            compose.onNodeWithText("No matches in this folder.").assertExists()
            compose.onNodeWithText("Archive").assertDoesNotExist()
            search.performTextReplacement("hidden-filename")
            compose.onNodeWithText("Gardening notes").assertDoesNotExist()

            compose.onNodeWithText("All files").performClick()
            search.performTextInput("Woodland recipe")
            compose.onNodeWithText("No matching conversations.").assertExists()
            compose.onNodeWithText("Include conversation text").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Gardening notes").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Woodland recipe", substring = true) and !hasSetTextAction()).assertExists()
            compose.onNodeWithText("Include conversation text").performClick()
            compose.onNodeWithText("No matching conversations.").assertExists()

            compose.onNodeWithContentDescription("Exit search").performClick()
            compose.onNodeWithContentDescription("Sort Conversations").performClick()
            compose.onNodeWithText("Length").performClick()
            assertEquals("Length", store.preferences.getString("sortField:All files", null))
            assertFalse(store.preferences.contains("sortField:Folders:$tree:"))
            assertFalse(store.preferences.contains("sortField"))
        }
    }
}
