package cz.jan.conversationreader

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun seedIsolatedLibrary() {
        check(context.packageName == "cz.jan.conversationreader.quality") { "Device tests require the isolated quality application" }
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().clear().putInt("lastSeenVersionCode", BuildConfig.VERSION_CODE)
            .putBoolean("collapsedOpeningDefaultApplied", true).putBoolean("collapsePrompts", false).commit()
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        directory.listFiles().orEmpty().forEach { it.delete() }
        val messages = JSONArray()
        repeat(6) { index ->
            messages.put(JSONObject().put("versions", JSONArray().put(JSONObject().put("role", "user").put("content", "Prompt $index. " + "A neutral long prompt for expansion. ".repeat(12)))))
            messages.put(JSONObject().put("versions", JSONArray().put(JSONObject().put("role", "assistant").put("senderInfo", JSONObject().put("senderName", "Synthetic/Quality-Model")).put("content", "<think>**Plan** the answer.\n\n- Check the details.</think>\n\n# Reply $index\n\n" + "A readable synthetic paragraph with a searchmarker.\n\n".repeat(50)))))
        }
        directory.resolve("device-quality.json").writeText(JSONObject().put("name", "Device quality conversation").put("messages", messages).toString())
        check(LibraryStore(context).load().first.any { it.id == "device-quality" })
    }

    private fun openConversation() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Device quality conversation").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Device quality conversation").onLast().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Expand prompt").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun promptJumpsThinkingSearchAndRecreationRemainUsable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openConversation()
            compose.onNodeWithContentDescription("Expand prompt").performClick()
            compose.onNodeWithContentDescription("Collapse prompt").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Show thinking").performClick()
            compose.onNodeWithText("↑ Collapse thinking").performScrollTo().performClick()
            repeat(2) {
                repeat(5) { compose.onNodeWithText("Next ↓").assertIsEnabled().performClick(); compose.waitForIdle() }
                compose.onNodeWithText("Next ↓").assertIsNotEnabled()
                repeat(5) { compose.onNodeWithText("↑ Previous").assertIsEnabled().performClick(); compose.waitForIdle() }
            }
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("searchmarker")
            compose.waitUntil(20_000) { compose.onAllNodesWithText("1 of 300").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Next result").performClick()
            compose.onNodeWithContentDescription("Exit search").performClick()
            scenario.recreate()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Aa").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Aa").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Conversation Reader").assertIsDisplayed()
        }
    }

    @Test fun atomicStorageReplacesExistingFileOnAndroid() {
        val file = File.createTempFile("quality-atomic-", ".json", context.cacheDir)
        try {
            atomicWrite(file, "first complete value".toByteArray())
            atomicWrite(file, "second complete value".toByteArray())
            assertEquals("second complete value", file.readText())
        } finally { file.delete() }
    }

    @Test fun modelSelectionSurvivesSwitchingViewsAndOpeningReader() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Device quality conversation").fetchSemanticsNodes().isNotEmpty() }
            fun menu(view: String) {
                compose.onNodeWithContentDescription("Open navigation menu").performClick()
                compose.onNode(hasText(view) and hasAnyAncestor(isPopup())).performClick()
            }
            menu("Models")
            compose.onNodeWithText("Synthetic/Quality-Model").performClick()
            compose.onNodeWithContentDescription("Sort Conversations").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Ascending").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onAllNodes(isPopup()).assertCountEquals(0)
            menu("Imported")
            menu("Models")
            compose.onNodeWithContentDescription("Sort Conversations").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Sort Models").assertCountEquals(0)
            compose.onAllNodesWithText("Device quality conversation").onLast().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Aa").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Synthetic/Quality-Model", substring = true).assertIsDisplayed()
            compose.onNode(hasText("Models") and isSelected()).performClick()
            compose.onNodeWithContentDescription("Sort Models").assertIsDisplayed()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val node = if (name == "settings" || name == "reading-preferences" || name == "appearance") compose.onNode(isDialog()) else compose.onAllNodes(isRoot()).onLast()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "quality-screenshots").apply { mkdirs() }
        directory.resolve("$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun redesignedLibraryReaderSettingsAndSearch() {
        val directory = File(context.filesDir, "conversations")
        val extra = directory.resolve("device-quality.json")
        val extraJson = JSONObject(extra.readText()).put("name", "An idea worth keeping").put("createdAt", System.currentTimeMillis() - 5 * 86_400_000L)
        val extraMessages = extraJson.getJSONArray("messages")
        for (i in 0 until extraMessages.length()) {
            val version = extraMessages.getJSONObject(i).getJSONArray("versions").getJSONObject(0)
            if (version.optString("role") == "assistant") version.put("senderInfo", JSONObject().put("senderName", "Llama-3.3"))
        }
        extra.writeText(extraJson.toString())
        listOf("Fairytale", "A quiet evening in Prague", "Notes on a new beginning", "A weekend in Vienna").forEachIndexed { i, title ->
            val model = if (i < 2) "Gemma-4-31B" else "Qwen-3.6"
            val messages = JSONArray()
                .put(JSONObject().put("versions", JSONArray().put(JSONObject().put("role", "user").put("content", "Create a story about a boy named Jan who discovers an old garden. " + "Make kindness the heart of his adventure. ".repeat(5)))))
                .put(JSONObject().put("versions", JSONArray().put(JSONObject().put("role", "assistant").put("senderInfo", JSONObject().put("senderName", model)).put("content", "<think>**Story direction**\n\nUse a gentle voice and a hopeful ending.</think>\n\n# Jan and the Glass Slipper\n\nLong ago, in a kingdom nestled between emerald hills and a river that shimmered like liquid silver, there lived a young boy named Jan.\n\nJan had hair the colour of autumn wheat and eyes as soft as morning rain. His hands were clever: he could mend a broken cart wheel and coax flowers from stubborn soil.\n\n## A small act of kindness\n\nOne morning, a stranger arrived at the garden gate. While others hurried past, Jan stopped to listen."))))
            directory.resolve("design-$i.json").writeText(JSONObject().put("name", title).put("createdAt", System.currentTimeMillis() - i * 86_400_000L).put("messages", messages).toString())
        }
        check(LibraryStore(context).load().first.any { it.id == "design-0" })
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Fairytale").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Library").assertIsSelected()
            capture("library")
            compose.onNodeWithContentDescription("Search").performClick()
            for (query in listOf("F", "Fa", "Fai", "Fairy")) {
                compose.onNode(hasSetTextAction()).performTextReplacement(query)
                compose.onNode(hasSetTextAction()).assertIsFocused()
            }
            compose.onNodeWithContentDescription("Exit search").performClick()
            compose.onNodeWithContentDescription("Open navigation menu").performClick()
            compose.onNode(hasText("Models") and hasAnyAncestor(isPopup())).performClick()
            capture("models")
            compose.onNodeWithText("Gemma-4-31B").performClick()
            capture("model-conversations")
            compose.onAllNodesWithText("Fairytale").onLast().performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Expand prompt").fetchSemanticsNodes().isNotEmpty() }
            capture("reader")
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("Reading preferences").assertIsDisplayed()
            capture("reading-preferences")
            compose.onNodeWithContentDescription("Close reading preferences").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
            capture("settings")
            compose.onNodeWithText("System").performScrollTo().assertIsDisplayed()
            capture("appearance")
            compose.onNodeWithText("About").performScrollTo().assertIsDisplayed()
        }
    }

}
