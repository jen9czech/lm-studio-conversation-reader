package cz.jan.conversationreader

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderPresentationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun livePreferencesPreserveThePassageAndContentsStartsAtTheCurrentHeading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "cz.jan.conversationreader.quality")
        val store = LibraryStore(context)
        store.preferences.edit().clear().putBoolean("collapsedOpeningDefaultApplied", true)
            .putBoolean("collapsePrompts", true).commit()
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        val messages = JSONArray()
        fun add(role: String, text: String) {
            messages.put(JSONObject().put("versions", JSONArray().put(JSONObject().put("role", role).put("content", text))))
        }
        add("user", "Describe the seasons")
        (1..24).forEach { section -> add("assistant", "# Section $section\n\n" + "A neutral paragraph for this section. ".repeat(25)) }
        directory.resolve("reader-presentation.json").writeText(JSONObject()
            .put("name", "Reader presentation fixture").put("messages", messages).toString())
        check(store.load().first.any { it.id == "reader-presentation" })

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Reader presentation fixture").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Reader presentation fixture").onLast().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Describe the seasons").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 prompt ·", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Reader presentation fixture").performClick()
            compose.onNodeWithText("Prompts").assertIsDisplayed()
            compose.onNodeWithText("Replies").assertIsDisplayed()
            compose.onNodeWithText("Total messages").assertIsDisplayed()
            compose.onNodeWithText("25").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Back").onLast().performClick()
            val preview = compose.onNodeWithText("Describe the seasons", useUnmergedTree = true)
            val before = preview.fetchSemanticsNode().boundsInRoot.height
            val preferences = compose.onNodeWithContentDescription("Reading preferences")
            fun changeSize(size: Float) {
                preferences.performClick()
                compose.onNodeWithText("Reading preferences").assertIsDisplayed()
                compose.onNodeWithContentDescription("Text size").performSemanticsAction(SemanticsActions.SetProgress) { it(size) }
                compose.onNodeWithText("${size.toInt()} sp").assertIsDisplayed()
                compose.onNodeWithContentDescription("Close reading preferences").performClick()
            }
            changeSize(24f)
            assertTrue(preview.fetchSemanticsNode().boundsInRoot.height > before)

            val expand = SemanticsMatcher("Prompt preview with an expand action") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Expand prompt and replies"
            }
            compose.onNode(expand).performClick()
            compose.waitUntil(15_000) { store.reading.resumeAnchor("reader-presentation") != null }
            compose.onNode(hasScrollToIndexAction()).performScrollToKey("text:12:0:0:0")
            compose.waitUntil(15_000) { store.reading.resumeAnchor("reader-presentation")?.message == 12 }
            compose.onNodeWithContentDescription("Contents").performClick()
            compose.onNodeWithText("Current section").assertIsDisplayed()
            compose.onNodeWithText("Section 12").assertIsDisplayed()
            compose.onNodeWithText("Section 13").performClick()
            compose.waitUntil(15_000) { store.reading.resumeAnchor("reader-presentation")?.message == 13 }

            compose.onNode(hasScrollToIndexAction()).performTouchInput {
                swipeUp(startY = height * .72f, endY = height * .52f, durationMillis = 400)
            }
            compose.waitUntil(15_000) {
                store.reading.resumeAnchor("reader-presentation")?.let { it.message == 13 && it.character > 0 } == true
            }
            val saved = checkNotNull(store.reading.resumeAnchor("reader-presentation"))
            changeSize(18f)
            try {
                compose.waitUntil(5_000) {
                    store.reading.resumeAnchor("reader-presentation")?.let {
                        it.layoutKey != saved.layoutKey && it.message == saved.message && kotlin.math.abs(it.character - saved.character) < 80
                    } == true
                }
            } catch (failure: ComposeTimeoutException) {
                throw AssertionError("Expected reflow near $saved. Saved after resize: ${store.reading.resumeAnchor("reader-presentation")}", failure)
            }
        }
    }
}
