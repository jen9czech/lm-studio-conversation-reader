package cz.jan.conversationreader

import android.content.Context
import android.content.pm.ProviderInfo
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.noties.markwon.Markwon
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp-mdpi", shadows = [WindowsFileProviderShadow::class], instrumentedPackages = ["androidx.core.content"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeReaderAuditTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearTestLibrary() {
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "conversations").listFiles().orEmpty().forEach { it.delete() }
        val local = AuditLocalFilesProvider()
        local.attachInfo(context, ProviderInfo().apply { authority = "audit.local" })
        ShadowContentResolver.registerProviderInternal("audit.local", local)
    }

    private fun seed() {
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        File(directory, "audit.json").writeText("""{"name":"Audit conversation","createdAt":1794739516542,"messages":[{"versions":[{"role":"user","content":[{"type":"text","text":"A test prompt"}]}]},{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","content":[{"type":"text","text":"A formatted **answer** with a needle."}]}]}]}]}""")
    }

    @Test fun mainSearchBackExitsAndSettingsShowsVersion() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Conversation Reader").assertIsDisplayed()
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("absent")
            compose.onNodeWithText("0 results").assertExists()
            compose.onNodeWithContentDescription("Exit search").performClick()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
            compose.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("System").onLast().performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun mainIconMenuListsViewsOpensSettingsAndShowsVersionLast() {
        ActivityScenario.launch(MainActivity::class.java).use {
            for (view in LibraryViews) {
                compose.onNodeWithContentDescription("Open navigation menu").performClick()
                LibraryViews.forEach { item -> compose.onNode(hasText(item) and hasAnyAncestor(isPopup())).assertIsDisplayed() }
                val version = compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val settings = compose.onNode(hasText("Settings") and hasAnyAncestor(isPopup())).fetchSemanticsNode().boundsInRoot
                assertTrue("Version belongs below Settings", version.top >= settings.bottom)
                compose.onNode(hasText(view) and hasAnyAncestor(isPopup())).performClick()
                compose.onAllNodes(isPopup()).assertCountEquals(0)
                compose.onNodeWithText(view).assertIsSelected()
            }
            compose.onNodeWithContentDescription("Open navigation menu").performClick()
            compose.onNode(hasText("Settings") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
            compose.onAllNodes(isPopup()).assertCountEquals(0)
        }
    }

    @Test fun importedConversationOpensAndSearchFindsRenderedReply() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Audit conversation").performClick()
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("needle")
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1 of 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 of 1").assertExists()
            compose.onAllNodesWithText("Aa").assertCountEquals(0)
            compose.onNodeWithContentDescription("Exit search").performClick()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        }
    }

    @Test fun importedLibraryCopyReadableThroughHostProviderAdapter() {
        seed()
        val entry = LibraryStore(context).load().first.single()
        assertNotNull(entry.textUri)
        val text = context.contentResolver.openInputStream(entry.textUri!!)!!.bufferedReader().use { it.readText() }
        assertTrue(text.contains("Audit conversation"))
    }

    @Test fun collapseBackKeepsReaderBeforeReturningToLibrary() {
        seed()
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().putBoolean("collapsePrompts", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Audit conversation").performClick()
            compose.onAllNodesWithText("End ↓").assertCountEquals(0)
            compose.onNodeWithText("▸ Expand prompt and replies").performClick()
            compose.onNodeWithText("End ↓").assertExists()
            compose.onAllNodesWithText("2 messages ·", substring = true).assertCountEquals(0)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onAllNodesWithText("End ↓").assertCountEquals(0)
            compose.onNodeWithText("2 messages ·", substring = true).assertExists()
            compose.onNodeWithContentDescription("Back").assertExists().performClick()
            compose.onNodeWithText("Conversation Reader").assertIsDisplayed()
        }
    }

    /** Regression for the linked-reader lifecycle defect found in the 0.7.4 audit. */
    @Test fun linkedReaderSurvivesActivityRecreation() {
        val provider = AuditDocumentsProvider()
        provider.raw = """{"name":"Cached title","messages":[{"versions":[{"role":"user","content":[{"type":"text","text":"${"A synthetic passage for the lifecycle regression. ".repeat(600)}"}]}]}]}"""
        provider.attachInfo(context, ProviderInfo().apply { authority = "audit.documents" })
        ShadowContentResolver.registerProviderInternal("audit.documents", provider)
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().putString("linkedFolder", "content://audit.documents/tree/root").commit()
        File(context.filesDir, "folder-index.json").delete()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Cached title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Cached title").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
            val store = LibraryStore(context)
            val id = store.linkedId(android.net.Uri.parse("content://audit.documents/tree/root/document/root%2Fsample"))
            compose.waitUntil(10_000) {
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
                (store.reading.anchor(id)?.character ?: 0) > 5000
            }
            val before = store.reading.anchor(id)!!
            scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Back").assertExists()
            val after = store.reading.anchor(id)!!
            assertEquals(before.message, after.message)
            assertTrue("Restore should stay within one line of the saved passage: $before -> $after", kotlin.math.abs(before.character - after.character) < 100)
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("Liberation Serif").performScrollTo().performClick()
            compose.onAllNodesWithContentDescription("Back").onLast().performClick()
            compose.waitForIdle()
            assertTrue("Font changes must keep the same passage", kotlin.math.abs(before.character - store.reading.anchor(id)!!.character) < 100)
        }
    }

    @Test fun oneStarTogglesConsistentlyAcrossReaderAndBookmarkedView() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Add bookmark").performClick()
            compose.onNodeWithContentDescription("Remove bookmark").assertExists()
            compose.onNodeWithText("Bookmarked").performScrollTo().performClick()
            compose.onNodeWithText("Audit conversation").performClick()
            compose.onNodeWithContentDescription("Remove bookmark").performClick()
            compose.onNodeWithContentDescription("Add bookmark").assertExists()
            compose.onAllNodesWithText("Bookmark", substring = false).assertCountEquals(0)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onAllNodesWithText("Audit conversation").assertCountEquals(0)
            compose.onNodeWithText("Tap a conversation’s star to add it to Bookmarked.").assertExists()
            compose.onAllNodesWithText("Favorites").assertCountEquals(0)
            compose.onNodeWithText("Imported").performScrollTo().performClick()
            compose.onNodeWithText("Audit conversation").performClick()
            compose.onNodeWithContentDescription("Add bookmark").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Bookmarked").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Remove bookmark").performClick()
            compose.onAllNodesWithText("Audit conversation").assertCountEquals(0)
        }
    }

    @Test fun libraryFullTextSearchOpensTheMatchingReaderHit() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("needle")
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1 of 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Audit conversation").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Exit search").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1 of 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Aa").assertCountEquals(0)
            compose.onNodeWithContentDescription("Exit search").performClick()
            compose.onNodeWithText("Return to reading").assertExists()
        }
    }

    @Test fun searchKeepsKeyboardAndFocusWhileTypingAndNavigatingMatches() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Audit conversation").performClick()
            compose.onNodeWithContentDescription("Search").performClick()
            val input = org.robolectric.Shadows.shadowOf(context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            for (query in listOf("ne", "nee", "need", "needle")) {
                compose.onNode(hasSetTextAction()).performTextReplacement(query)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("1 of 1").fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(hasSetTextAction()).assertIsFocused()
                assertTrue("Search jumps must not dismiss the keyboard", input.isSoftInputVisible)
            }
            compose.onNodeWithContentDescription("Next result").performClick()
            compose.onNodeWithContentDescription("Previous result").performClick()
            compose.onNode(hasSetTextAction()).assertIsFocused()
            assertTrue(input.isSoftInputVisible)
        }
    }

    @Test fun readingFontChangesActualNativeTextViews() {
        seed()
        fun textViews(view: android.view.View): List<TextView> = when (view) {
            is android.view.ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
            is TextView -> listOf(view)
            else -> emptyList()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Audit conversation").performClick()
            for (font in listOf("Serif", "Monospaced", "Sans-serif")) {
                compose.onNodeWithText("Aa").performClick()
                compose.onNodeWithText(readerFontName(font)).performScrollTo().performClick()
                compose.onAllNodesWithContentDescription("Back").onLast().performClick()
                compose.waitForIdle()
                scenario.onActivity { activity ->
                    val blocks = textViews(activity.window.decorView).filter { it.javaClass.simpleName == "BlockTextView" }
                    assertTrue("Reader should contain native body text", blocks.isNotEmpty())
                    blocks.forEach { assertEquals("Font must update the actual body view", readerTypeface(context, font), it.typeface) }
                }
            }
        }
    }

    @Test fun readerActionsShareToolbarAndProgressSharesBottomNavigationRow() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Audit conversation").performClick()
            val search = compose.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
            val contents = compose.onNodeWithContentDescription("Contents").fetchSemanticsNode().boundsInRoot
            val star = compose.onNodeWithContentDescription("Add bookmark").fetchSemanticsNode().boundsInRoot
            val preferences = compose.onNodeWithText("Aa").fetchSemanticsNode().boundsInRoot
            assertTrue(search.right <= contents.left)
            assertTrue(contents.right <= star.left)
            assertTrue(star.right <= preferences.left)
            assertEquals(search.center.y, contents.center.y, 1f)
            assertEquals(search.center.y, star.center.y, 1f)
            assertEquals(search.center.y, preferences.center.y, 1f)
            val previous = compose.onNodeWithText("↑ Previous").fetchSemanticsNode().boundsInRoot
            val end = compose.onNodeWithText("End ↓").fetchSemanticsNode().boundsInRoot
            val progress = compose.onNodeWithContentDescription("Reading progress:", substring = true).fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithText("Next ↓").fetchSemanticsNode().boundsInRoot
            assertEquals(previous.center.y, end.center.y, 1f)
            assertEquals(previous.center.y, progress.center.y, 1f)
            assertEquals(previous.center.y, next.center.y, 1f)
            assertTrue(progress.right <= previous.left)
            assertTrue(previous.right <= end.left)
            assertTrue(end.right <= next.left)
            assertTrue(progress.top > preferences.bottom)
        }
    }

    @Test fun nativeMarkdownPreservesCzechTextAndFormatting() {
        val markwon = Markwon.create(context)
        val view = TextView(context)
        markwon.setMarkdown(view, "# Příběh\n\nA **bold** reply and `code`.\n\nŽluťoučký kůň.")
        assertTrue(view.text.toString().contains("Příběh"))
        assertTrue(view.text.toString().contains("Žluťoučký kůň"))
        assertFalse(view.text.toString().contains("**"))
    }

    @Test fun nativeLongReplyLayoutReportsHostCost() {
        val renderer = Markwon.create(context)
        listOf(50_000, 200_000).forEach { length ->
            val text = "# Sample heading\n\n" + "A **synthetic** paragraph for reading.\n\n".repeat(length / 38)
            val start = System.nanoTime()
            val rendered = renderer.toMarkdown(text)
            val parsed = System.nanoTime()
            val view = TextView(context).apply { textSize = 17f; this.text = rendered }
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val laidOut = System.nanoTime()
            assertTrue(view.measuredHeight > 850)
            println("AUDIT Markwon chars=${text.length} renderMs=${(parsed-start)/1_000_000.0} layoutMs=${(laidOut-parsed)/1_000_000.0} height=${view.measuredHeight}; simulated Android on host, not phone frames")
        }
    }
}
