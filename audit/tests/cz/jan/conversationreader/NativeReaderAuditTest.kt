package cz.jan.conversationreader

import android.content.Context
import android.content.pm.ProviderInfo
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.getOrNull
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
        // Most reader checks explicitly exercise the expanded reading mode.
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().clear()
            .putInt("lastSeenVersionCode", BuildConfig.VERSION_CODE)
            .putBoolean("collapsedOpeningDefaultApplied", true).putBoolean("collapsePrompts", false).commit()
        File(context.filesDir, "conversations").listFiles().orEmpty().forEach { it.delete() }
        val local = AuditLocalFilesProvider()
        local.attachInfo(context, ProviderInfo().apply { authority = "audit.local" })
        ShadowContentResolver.registerProviderInternal("audit.local", local)
    }

    private fun openAuditConversation() {
        compose.onAllNodesWithText("Audit conversation").onLast().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Aa").fetchSemanticsNodes().isNotEmpty() }
    }

    private val collapsedPrompt = SemanticsMatcher("Prompt preview with an expand action") {
        it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == "Expand prompt and replies"
    }

    private fun seed() {
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        File(directory, "audit.json").writeText("""{"name":"Audit conversation","createdAt":1794739516542,"messages":[{"versions":[{"role":"user","content":[{"type":"text","text":"A test prompt"}]}]},{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","content":[{"type":"text","text":"A formatted **answer** with a needle."}]}]}]}]}""")
    }

    private fun withReaderPresentation(store: LibraryStore, collapsed: Boolean, checkReader: () -> Unit) {
        val entry = store.openReference("""{"id":"audit"}""")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    var preferences by remember { mutableStateOf(false) }
                    var size by remember { mutableFloatStateOf(17f) }
                    var theme by remember { mutableStateOf("Dark") }
                    var style by remember { mutableStateOf(ReaderStyle()) }
                    MaterialTheme(colorScheme = if (theme == "Light") Day else Night, typography = readerInterfaceTypography((size / 17f).coerceIn(.85f, 1.30f))) {
                        ConversationReader(entry, store, size, style, collapsed, 0, false, {}, { preferences = true })
                        if (preferences) PreferencesScreen(false, null, size, theme, collapsed, { preferences = false }, {},
                            { size = it }, { theme = it }, {}, style, { style = it })
                    }
                }
            }
            checkReader()
        }
    }

    @Test fun readingPreferencesResizeCollapsedPreviewsInPlace() {
        seed()
        val store = LibraryStore(context)
        withReaderPresentation(store, collapsed = true) {
            val preview = compose.onNodeWithText("A test prompt", useUnmergedTree = true)
            val before = preview.fetchSemanticsNode().boundsInRoot.height
            compose.onNodeWithContentDescription("Reading preferences").performClick()
            compose.onNodeWithText("Reading preferences").assertIsDisplayed()
            compose.onNodeWithContentDescription("Text size").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(26f) }
            compose.onNodeWithText("26 sp").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close reading preferences").performClick()
            assertTrue(preview.fetchSemanticsNode().boundsInRoot.height > before)
            compose.onNode(collapsedPrompt).assertIsDisplayed()
            compose.onAllNodesWithText("↑ Previous").assertCountEquals(0)
        }
    }

    @Test fun contentsOpensAtTheCurrentHeading() {
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        val messages = org.json.JSONArray()
        fun add(role: String, text: String) {
            messages.put(org.json.JSONObject().put("versions", org.json.JSONArray().put(
                org.json.JSONObject().put("role", role).put("content", text))))
        }
        add("user", "Outline fixture prompt")
        (1..24).forEach { section -> add("assistant", "# Section $section\n\n" + "A neutral paragraph for this section. ".repeat(25)) }
        directory.resolve("audit.json").writeText(org.json.JSONObject().put("name", "Audit conversation").put("messages", messages).toString())
        val store = LibraryStore(context)
        withReaderPresentation(store, collapsed = false) {
            val list = compose.onNode(hasScrollToIndexAction())
            val key = "text:12:0:0:0"
            compose.waitUntil(10_000) {
                val indexForKey = list.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.IndexForKey)
                (indexForKey?.invoke(key) ?: -1) >= 0
            }
            list.performScrollToKey(key)
            compose.waitUntil(10_000) { store.reading.anchor("audit")?.message == 12 }
            compose.onNodeWithContentDescription("Contents").performClick()
            compose.onNodeWithText("Current section").assertIsDisplayed()
            compose.onNodeWithText("Section 12").assertIsDisplayed()
            compose.onNodeWithText("Section 13").performClick()
            compose.waitUntil(10_000) { store.reading.anchor("audit")?.message == 13 }
        }
    }

    @Test @Config(qualifiers = "w840dp-h500dp-mdpi")
    fun wideReaderCentersTextAndShortWindowsKeepScrollablePreferences() {
        seed()
        withReaderPresentation(LibraryStore(context), collapsed = false) {
            val column = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("Column width was ${column.width}", column.width in 540f..700f)
            assertEquals(420.0, column.center.x.toDouble(), 2.0)
            compose.onNodeWithContentDescription("Reading preferences").performClick()
            compose.onNodeWithText("Reading preferences").assertIsDisplayed()
            compose.onNodeWithText("Courier\nPrime").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Close reading preferences").assertIsDisplayed()
            compose.onAllNodesWithText("Done").assertCountEquals(0)
        }
    }

    @Test fun readingProgressOffsetsIncludeCollapsedReplies() {
        fun rendered(text: String) = RenderedVersion(
            listOf(RenderedPart(android.text.SpannableString(text), emptyList(), emptyList()))
        )
        val document = mapOf(0 to rendered("abc"), 1 to rendered("defghij"), 2 to rendered("kl"), 3 to rendered("mnopqrst"))
        val firstGroupOpen = listOf(ReaderRow("footer", 1, 0, "footer"), ReaderRow("preview", 2, 0, "preview"))
        val secondGroupOpen = listOf(ReaderRow("preview", 0, 0, "preview"), ReaderRow("header", 2, 0, "header"))

        assertEquals(listOf(10L..10L, 10L..10L) to 20L, readingOffsets(firstGroupOpen, document, 4))
        assertEquals(listOf(0L..0L, 10L..10L) to 20L, readingOffsets(secondGroupOpen, document, 4))
    }

    @Test fun mainSearchBackExitsAndSettingsShowsVersion() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Conversation Reader").assertIsDisplayed()
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("absent")
            compose.onAllNodesWithText("0 results").assertCountEquals(0)
            compose.onNodeWithContentDescription("Exit search").performClick()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
            compose.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("System").onLast().performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun navigationAndSystemThemeRemainReadableAtDoubleFontScale() {
        seed()
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty() }
                fun assertUnclipped(text: String) {
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
                    assertTrue("Expected measured text for $text", results.isNotEmpty())
                    // Fractional glyph metrics can exceed the integer layout box by <1 px.
                    results.forEach { result ->
                        repeat(result.lineCount) { line ->
                            assertFalse("$text must not be ellipsized", result.isLineEllipsized(line))
                            assertTrue("$text must fit its width", result.getLineRight(line) <= result.size.width + 1f)
                            assertTrue("$text must fit its height", result.getLineBottom(line) <= result.size.height + 1f)
                        }
                    }
                }
                listOf("Folders", "All files", "Models", "Imported", "Bookmarked").forEach { assertUnclipped(it) }
                compose.onNodeWithContentDescription("Settings").performClick()
                compose.onNodeWithText("System").performScrollTo().assertIsDisplayed()
                assertUnclipped("System")
            }
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
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
                compose.onNode(hasText(view) and isSelected()).assertIsSelected().assertIsDisplayed()
            }
            compose.onNodeWithContentDescription("Open navigation menu").performClick()
            compose.onNode(hasText("Settings") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
            compose.onAllNodes(isPopup()).assertCountEquals(0)
            compose.onNodeWithText("Collapse by prompts").performScrollTo().assertIsDisplayed()
            val description = compose.onNodeWithText("Show prompt previews when opening a conversation").fetchSemanticsNode().boundsInRoot
            val toggle = compose.onNodeWithContentDescription("Collapse by prompts").fetchSemanticsNode().boundsInRoot
            assertTrue(description.right <= toggle.left)
            compose.onAllNodesWithText("Conversation navigation").assertCountEquals(0)
            compose.onAllNodesWithText("The quick brown fox").assertCountEquals(0)
        }
    }

    @Test fun foldersExcludeBodyMatchesAndAllFilesIncludesThem() {
        val provider = AuditDocumentsProvider().apply {
            raw = """{"name":"Archive title","messages":[{"versions":[{"role":"user","content":"bodyonlymarker"}]}]}"""
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = "audit.documents" })
        ShadowContentResolver.registerProviderInternal("audit.documents", provider)
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().putString("linkedFolder", "content://audit.documents/tree/root").commit()
        File(context.filesDir, "folder-index.json").delete()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Archive title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("bodyonlymarker")
            compose.onAllNodesWithText("Archive title").assertCountEquals(0)
            compose.onNodeWithText("All files").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("bodyonlymarker")
            compose.onNodeWithText("Include conversation text").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Archive title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Folders") and hasClickAction()).performClick()
            compose.onNode(hasSetTextAction()).performTextInput("bodyonlymarker")
            compose.onAllNodesWithText("Archive title").assertCountEquals(0)
            compose.onNode(hasSetTextAction()).performTextReplacement("Archive title")
            compose.onNode(hasText("Archive title") and !hasSetTextAction()).assertExists().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithContentDescription("Exit search").assertCountEquals(0)
        }
    }

    @Test fun thinkingIsClosedByDefaultAndSearchCanOpenIt() {
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        File(directory, "thinking.json").writeText("""{"name":"Thinking example","messages":[{"versions":[{"role":"user","content":"Please compare the choices."}]},{"versions":[{"role":"assistant","steps":[{"type":"contentBlock","style":{"type":"thinking"},"content":"<think>A plan.\n\n        * **Reason** about the secretmarker.\n\n    * Another idea.</think>"},{"type":"contentBlock","content":"The visible final answer.","genInfo":{"stats":{"predictedTokensCount":42}}}]}]}]}""")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Thinking example").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Thinking example").onLast().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Show thinking").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithContentDescription("Hide thinking").assertCountEquals(0)
            // Reader restoration can scroll between frames. Measure both controls only after
            // they share a stable layout; use unclipped bounds for partially visible rows.
            compose.waitUntil(10_000) {
                val copyBounds = compose.onNodeWithContentDescription("Copy prompt").getUnclippedBoundsInRoot()
                val thinkingBounds = compose.onNodeWithContentDescription("Show thinking").getUnclippedBoundsInRoot()
                kotlin.math.abs(((copyBounds.top.value + copyBounds.bottom.value) / 2f) - ((thinkingBounds.top.value + thinkingBounds.bottom.value) / 2f)) <= 1f
            }
            val copy = compose.onNodeWithContentDescription("Copy prompt").getUnclippedBoundsInRoot()
            val thinking = compose.onNodeWithContentDescription("Show thinking").getUnclippedBoundsInRoot()
            assertEquals(((copy.top.value + copy.bottom.value) / 2f), ((thinking.top.value + thinking.bottom.value) / 2f), 1f)
            assertTrue(thinking.right <= copy.left)
            compose.onAllNodesWithText("Select text").assertCountEquals(0)
            compose.onNodeWithContentDescription("Show thinking").performClick()
            compose.onNodeWithContentDescription("Hide thinking").assertExists()
            compose.onAllNodesWithText("Copy thinking").assertCountEquals(0)
            scenario.onActivity { activity ->
                fun texts(view: android.view.View): List<TextView> = when (view) {
                    is android.view.ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                    is TextView -> listOf(view)
                    else -> emptyList()
                }
                val thought = texts(activity.window.decorView).first { it.text.contains("secretmarker") }
                assertFalse(thought.text.contains("**"))
                assertFalse(thought.text.contains("##"))
                assertTrue((thought.text as android.text.Spanned).getSpans(0, thought.text.length, android.text.style.MetricAffectingSpan::class.java).isNotEmpty())
            }
            compose.onNodeWithText("↑ Collapse thinking").performClick()
            compose.onNodeWithContentDescription("Show thinking").assertExists()
            val details = compose.onNodeWithText("Generation info ↓").getUnclippedBoundsInRoot()
            val replyCopy = compose.onNodeWithContentDescription("Copy reply").getUnclippedBoundsInRoot()
            assertEquals(((details.top.value + details.bottom.value) / 2f), ((replyCopy.top.value + replyCopy.bottom.value) / 2f), 1f)
            assertTrue(details.right <= replyCopy.left)
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("secretmarker")
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1 of 1").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Hide thinking").assertExists()
            compose.onNodeWithContentDescription("Exit search").performClick()
            // Back returns to the library directly after leaving search.
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onAllNodesWithText("Thinking example").onLast().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Show thinking").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithContentDescription("Hide thinking").assertCountEquals(0)
        }
    }

    @Test fun importedConversationOpensAndSearchFindsRenderedReply() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            openAuditConversation()
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
        val store = LibraryStore(context)
        val entry = store.openReference(store.reference(store.load().first.single()))
        assertNotNull(entry.textUri)
        val text = context.contentResolver.openInputStream(entry.textUri!!)!!.bufferedReader().use { it.readText() }
        assertTrue(text.contains("Audit conversation"))
    }

    @Test fun openingAnImportedReferenceLeavesOtherImportsUnindexed() {
        seed()
        val store = LibraryStore(context)
        val entry = store.load().first.single()
        val other = File(context.filesDir, "conversations/unopened.json")
        other.writeText("""{"name":"Unopened synthetic conversation","messages":[]}""")

        assertEquals("Audit conversation", store.openReference(store.reference(entry)).conversation.title)
        assertFalse(store.searchIndex.contains("unopened"))
    }

    @Test fun continueReadingOpensSavedReplyAndPreferencesUseSharedTextSize() {
        seed()
        val store = LibraryStore(context)
        val entry = store.openReference(store.reference(store.load().first.single()))
        store.preferences.edit().putBoolean("collapsePrompts", true).putFloat("font", 21f).commit()
        store.reading.record(entry, store.reference(entry))
        store.reading.saveAnchor(entry.id, ReadingAnchor(0))
        store.reading.saveResumeAnchor(entry.id, ReadingAnchor(1, character = 12))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Text size").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("21 sp").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Back").onLast().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Continue reading").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Continue reading").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("↑ Previous").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(collapsedPrompt).assertCountEquals(0)
            compose.onNodeWithText("Aa").performClick()
            compose.onNodeWithText("Reading preferences").assertIsDisplayed()
        }
    }

    @Test fun continueReadingRestoresActualScrolledViewport() {
        seed()
        File(context.filesDir, "conversations/audit.json").writeText(
            """{"name":"Audit conversation","messages":[{"versions":[{"role":"user","content":"${"A neutral long passage for this resume test. ".repeat(600)}"}]}]}"""
        )
        val store = LibraryStore(context)
        val entry = store.load().first.single()
        store.preferences.edit().putBoolean("collapsePrompts", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            openAuditConversation()
            compose.onNode(collapsedPrompt).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Expand prompt").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Expand prompt").performClick()
            val list = compose.onNode(hasScrollToIndexAction())
            list.performScrollToIndex(6)
            compose.waitUntil(10_000) { (store.reading.resumeAnchor(entry.id)?.character ?: 0) > 5000 }
            fun scrollPosition() = list.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            val before = scrollPosition()
            val beforeAnchor = store.reading.resumeAnchor(entry.id)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            val leftAnchor = store.reading.resumeAnchor(entry.id)
            compose.onNodeWithText("Continue reading").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("↑ Previous").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            val after = scrollPosition()
            assertEquals("Before anchor: $beforeAnchor; left: $leftAnchor; after: ${store.reading.resumeAnchor(entry.id)}",
                before.toDouble(), after.toDouble(), .001)
            compose.onNodeWithText("↑ Previous").assertExists()
        }
    }

    @Test fun reopeningSavedReplyStartsWithCollapsedPrompts() {
        seed()
        val store = LibraryStore(context)
        val entry = store.load().first.single()
        store.reading.saveAnchor(entry.id, ReadingAnchor(1, character = 12))
        store.preferences.edit().remove("collapsedOpeningDefaultApplied").putBoolean("collapsePrompts", false).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            repeat(2) {
                openAuditConversation()
                compose.onNode(collapsedPrompt).assertIsDisplayed().performClick()
                compose.onNodeWithText("↑ Previous").assertExists()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onAllNodesWithText("↑ Previous").assertCountEquals(0)
                compose.onNodeWithContentDescription("Back").performClick()
            }
        }
        assertTrue(store.preferences.getBoolean("collapsePrompts", false))
        // The upgrade changes the default once, without overriding a subsequent explicit choice.
        store.preferences.edit().putBoolean("collapsePrompts", false).commit()
        assertFalse(store.reading.collapsePrompts())
    }

    @Test fun collapseBackKeepsReaderBeforeReturningToLibrary() {
        seed()
        context.getSharedPreferences("reader", Context.MODE_PRIVATE).edit().putBoolean("collapsePrompts", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            openAuditConversation()
            compose.onAllNodesWithText("↑ Previous").assertCountEquals(0)
            compose.onNode(collapsedPrompt).performClick()
            compose.onNodeWithText("↑ Previous").assertExists()
            compose.onNodeWithText("1 prompt ·", substring = true).assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onAllNodesWithText("↑ Previous").assertCountEquals(0)
            compose.onNodeWithText("1 prompt ·", substring = true).assertExists()
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
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Cached title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Cached title").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Expand prompt").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Expand prompt").performClick()
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
            compose.onNodeWithText("Liberation\nSerif").performScrollTo().performClick()
            compose.onAllNodesWithContentDescription("Back").onLast().performClick()
            compose.waitForIdle()
            assertTrue("Font changes must keep the same passage", kotlin.math.abs(before.character - store.reading.anchor(id)!!.character) < 100)
        }
    }

    @Test fun oneStarTogglesConsistentlyAcrossReaderAndBookmarkedView() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Add bookmark").performClick()
            compose.onNodeWithContentDescription("Remove bookmark").assertExists()
            compose.onNodeWithText("Bookmarked").performClick()
            openAuditConversation()
            compose.onNodeWithContentDescription("Remove bookmark").performClick()
            compose.onNodeWithContentDescription("Add bookmark").assertExists()
            compose.onAllNodesWithText("Bookmark", substring = false).assertCountEquals(0)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onAllNodesWithText("Audit conversation").assertCountEquals(0)
            compose.onNodeWithText("Tap a conversation’s star to add it to Bookmarked.").assertExists()
            compose.onAllNodesWithText("Favorites").assertCountEquals(0)
            compose.onNodeWithText("Library").performClick()
            compose.onNode(hasText("Imported") and isSelected()).assertIsDisplayed()
            openAuditConversation()
            compose.onNodeWithContentDescription("Add bookmark").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Bookmarked").performClick()
            compose.onNodeWithContentDescription("Remove bookmark").performClick()
            compose.onAllNodesWithText("Audit conversation").assertCountEquals(0)
        }
    }

    @Test fun importedFilterUsesTitlesAndOpensReaderWithNormalControls() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Search").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("needle")
            compose.onNodeWithText("No matching conversations.").assertExists()
            compose.onNode(hasSetTextAction()).performTextReplacement("Audit conversation")
            compose.onAllNodesWithContentDescription("Next result").assertCountEquals(0)
            compose.onAllNodesWithText("1 of 1").assertCountEquals(0)
            openAuditConversation()
            compose.onNodeWithText("Aa").assertExists()
            compose.onAllNodesWithContentDescription("Exit search").assertCountEquals(0)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(hasSetTextAction()).assertExists()
        }
    }

    @Test fun searchKeepsKeyboardAndFocusWhileTypingAndNavigatingMatches() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            openAuditConversation()
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

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun repeatedNextAndPreviousJumpsKeepReaderAliveAcrossChangingPromptRows() {
        val messages = org.json.JSONArray()
        repeat(8) { index ->
            messages.put(org.json.JSONObject().put("versions", org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("content", "Prompt $index. " + "A long neutral prompt for wrapping. ".repeat(12)))))
            messages.put(org.json.JSONObject().put("versions", org.json.JSONArray().put(org.json.JSONObject().put("role", "assistant").put("content", "Reply $index. " + "A neutral paragraph for scroll navigation.\n\n".repeat(70)))))
        }
        File(context.filesDir, "conversations").apply { mkdirs() }.resolve("next-test.json").writeText(org.json.JSONObject().put("name", "Navigation regression").put("messages", messages).toString())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Navigation regression").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Navigation regression").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Expand prompt").fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { assertFalse(androidx.compose.ui.ComposeUiFlags.isSemanticAutofillEnabled) }
            repeat(3) {
                repeat(7) { compose.onNodeWithText("Next ↓").assertIsEnabled().performClick(); compose.waitForIdle() }
                compose.onNodeWithText("Next ↓").assertIsNotEnabled()
                repeat(7) { compose.onNodeWithText("↑ Previous").assertIsEnabled().performClick(); compose.waitForIdle() }
                compose.onNodeWithText("Next ↓").assertIsEnabled()
                compose.onNodeWithText("Aa").assertIsDisplayed()
            }
        }
    }

    @Test fun longPromptHasThreeLinePreviewAndCountsHideAfterScrolling() {
        val prompt = (1..12).joinToString("\n") { "Prompt line $it with several words." }
        val reply = (1..70).joinToString("\n\n") { "Reply paragraph $it for scrolling." }
        val raw = org.json.JSONObject().put("name", "Long prompt example").put("messages", org.json.JSONArray()
            .put(org.json.JSONObject().put("versions", org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("content", prompt))))
            .put(org.json.JSONObject().put("versions", org.json.JSONArray().put(org.json.JSONObject().put("role", "assistant").put("content", reply)))))
        File(context.filesDir, "conversations").apply { mkdirs() }.resolve("preview.json").writeText(raw.toString())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Long prompt example").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Long prompt example").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Expand prompt").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 prompt ·", substring = true).assertExists()
            val expandBounds = compose.onNodeWithContentDescription("Expand prompt").getUnclippedBoundsInRoot()
            val copyBounds = compose.onNodeWithContentDescription("Copy prompt").getUnclippedBoundsInRoot()
            assertEquals((expandBounds.top.value + expandBounds.bottom.value) / 2f, (copyBounds.top.value + copyBounds.bottom.value) / 2f, 1f)
            assertTrue(expandBounds.right <= copyBounds.left)
            fun blocks(view: android.view.View): List<TextView> = when (view) {
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { blocks(view.getChildAt(it)) }
                is TextView -> listOf(view)
                else -> emptyList()
            }
            scenario.onActivity { activity ->
                val preview = blocks(activity.window.decorView).first { it.text.startsWith("Prompt line") }
                assertEquals(3, preview.maxLines)
                assertTrue(preview.layout.lineCount > 3 || preview.layout.getEllipsisCount(preview.layout.lineCount - 1) > 0)
                assertTrue(preview.height <= preview.lineHeight * 3 + preview.compoundPaddingTop + preview.compoundPaddingBottom + 4)
            }
            compose.onNodeWithContentDescription("Expand prompt").performClick()
            compose.onNodeWithContentDescription("Collapse prompt").performScrollTo().assertExists()
            scenario.onActivity { activity ->
                assertEquals(Int.MAX_VALUE, blocks(activity.window.decorView).first { it.text.startsWith("Prompt line") }.maxLines)
            }
            compose.onNodeWithContentDescription("Collapse prompt").performClick()
            compose.onNodeWithContentDescription("Expand prompt").assertExists()
            compose.onNodeWithText("1 prompt ·", substring = true).assertExists()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
            compose.onAllNodesWithText("1 prompt ·", substring = true).assertCountEquals(0)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithText("1 prompt ·", substring = true).assertExists()
        }
    }

    @Test fun modelsViewGroupsFilesAndReturnsToSelectedView() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Open navigation menu").performClick()
            compose.onNode(hasText("Models") and hasAnyAncestor(isPopup())).performClick()
            compose.onNode(hasText("Models") and isSelected()).assertIsSelected().assertIsDisplayed()
            compose.onNodeWithContentDescription("Sort Models").performClick()
            compose.onAllNodesWithText("Date").assertCountEquals(0)
            compose.onAllNodesWithText("Length").assertCountEquals(0)
            compose.onNodeWithText("Count").assertIsDisplayed()
            compose.onNodeWithContentDescription("Descending").assertIsNotSelected().performClick().assertIsSelected()
            compose.onNodeWithText("Your library").performTouchInput { click() }
            compose.onNodeWithText("Name ↓").assertIsDisplayed()
            compose.onNodeWithText("Unknown model").performClick()
            compose.onAllNodesWithContentDescription("Sort Models").assertCountEquals(0)
            compose.onNodeWithContentDescription("Sort Conversations").performClick()
            compose.onAllNodesWithText("Count").assertCountEquals(0)
            compose.onNodeWithText("Length").performClick()
            compose.onNodeWithText("Length").assertIsSelected()
            compose.onNodeWithText("Date").assertIsNotSelected()
            compose.onNodeWithContentDescription("Ascending").assertIsNotSelected().performClick().assertIsSelected()
            compose.onNodeWithText("Your library").performTouchInput { click() }
            compose.onNodeWithText("Length ↑").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Sort Models").assertCountEquals(0)
            openAuditConversation()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Aa").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(hasText("Models") and isSelected()).assertIsSelected()
            compose.onNodeWithText("Unknown model").assertIsDisplayed()
            compose.onNodeWithContentDescription("Sort Conversations").assertIsDisplayed()
            compose.onNodeWithContentDescription("Open navigation menu").performClick()
            compose.onNode(hasText("Imported") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithContentDescription("Open navigation menu").performClick()
            compose.onNode(hasText("Models") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithContentDescription("Sort Conversations").assertIsDisplayed()
            compose.onNodeWithText("Unknown model").assertIsDisplayed()
            compose.onNode(hasText("Models") and isSelected()).performClick()
            compose.onNodeWithContentDescription("Sort Models").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Sort Conversations").assertCountEquals(0)
            compose.onNodeWithText("Name ↓").assertIsDisplayed()
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
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            openAuditConversation()
            for (font in listOf("Serif", "Monospaced", "Sans-serif")) {
                compose.onNodeWithText("Aa").performClick()
                val label = when (font) { "Sans-serif" -> "Sans-\nserif"; "Serif" -> "Liberation\nSerif"; else -> "Courier\nPrime" }
                compose.onNodeWithText(label).performScrollTo().performClick()
                compose.onNodeWithContentDescription("Close reading preferences").performClick()
                compose.waitForIdle()
                scenario.onActivity { activity ->
                    val blocks = textViews(activity.window.decorView).filter { it.javaClass.simpleName == "BlockTextView" }
                    assertTrue("Reader should contain native body text", blocks.isNotEmpty())
                    blocks.forEach {
                        val base = readerTypeface(context, font)
                        val expected = if (it.text.contains("A test prompt")) android.graphics.Typeface.create(base, android.graphics.Typeface.ITALIC) else base
                        assertEquals("Font must update the actual text view", expected, it.typeface)
                    }
                }
            }
        }
    }

    @Test fun readerActionsShareToolbarAndProgressSharesBottomNavigationRow() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Audit conversation").fetchSemanticsNodes().isNotEmpty() }
            openAuditConversation()
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
            compose.onAllNodesWithText("End ↓").assertCountEquals(0)
            val progress = compose.onNodeWithContentDescription("Reading progress:", substring = true).fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithText("Next ↓").fetchSemanticsNode().boundsInRoot
            assertEquals(previous.center.y, progress.center.y, 1f)
            assertEquals(previous.center.y, next.center.y, 1f)
            assertTrue(previous.right <= progress.left)
            assertTrue(progress.right <= next.left)
            assertEquals((previous.left + next.right) / 2f, progress.center.x, 1f)
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
