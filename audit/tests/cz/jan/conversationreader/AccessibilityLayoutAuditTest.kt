package cz.jan.conversationreader

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityLayoutAuditTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var inputMode: InputModeManager

    private fun render(fontScale: Float = 1f, content: @Composable () -> Unit, assertions: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    inputMode = LocalInputModeManager.current
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                        MaterialTheme(colorScheme = Day) { content() }
                    }
                }
            }
            assertions()
        }
    }

    @Test fun compactWindowKeepsLargeTitleTabsAndActionsReadable() {
        render(fontScale = 2f, content = {
            Column {
                ReaderToolbar(false, "", {}, {}, {}, {}, title = "Conversation Reader", onMenuView = {})
                LibrarySourceTabs("Folders") {}
                LibraryBottomNavigation("Folders") {}
            }
        }) {
            listOf("Conversation Reader", "Folders", "All files", "Models", "Imported", "Bookmarked").forEach { label ->
                val matches = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(matches) }
                assertTrue("Expected a measured layout for $label", matches.isNotEmpty())
                matches.forEach { layout ->
                    // Fractional glyph metrics may extend past the integer layout box by less than one pixel.
                    repeat(layout.lineCount) { line ->
                        assertTrue("$label must be fully readable", !layout.isLineEllipsized(line) &&
                            layout.getLineRight(line) <= layout.size.width + 1f && layout.getLineBottom(line) <= layout.size.height + 1f)
                    }
                }
            }
            compose.onNodeWithText("Folders").assertIsSelected()
            compose.onNodeWithContentDescription("Settings").assertHasClickAction()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun escapeReturnsKeyboardFocusToSearchAndCompletedMatchesAreAnnounced() {
        render(content = {
            var searching by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf("") }
            Column {
                ReaderToolbar(searching, query, { query = it }, { searching = true }, { searching = false }, {}, title = "Conversation Reader")
                if (searching) SearchResultsBar(1, 3, true, {}, {})
            }
        }) {
            compose.onNodeWithContentDescription("Search").performClick()
            val field = compose.onNode(hasSetTextAction()).assertIsFocused()
            compose.onNodeWithContentDescription("Match 2 of 3")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            field.performKeyInput { pressKey(Key.Escape) }
            compose.onNodeWithContentDescription("Search").assertIsFocused()
        }
    }

    @Test @Config(qualifiers = "w760dp-h320dp-land-mdpi")
    fun largeTextSettingsCanReachThemeInShortLandscape() {
        render(fontScale = 2f, content = {
            PreferencesScreen(true, null, 21f, "System", true, {}, {}, {}, {}, {})
        }) {
            compose.onNodeWithText("Settings").assertIsDisplayed()
            compose.onNodeWithContentDescription("Text size").performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "21 sp"))
            compose.onNodeWithText("System").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        }
    }

    @Test fun sortChoicesStackInEqualWidthAndApplyImmediately() {
        render(content = {
            var field by remember { mutableStateOf("Date") }
            var descending by remember { mutableStateOf(true) }
            LibrarySortControl("Conversations", listOf("Date", "Name", "Length"), field, descending) { choice, down ->
                field = choice
                descending = down
            }
        }) {
            compose.onNodeWithContentDescription("Sort Conversations").performClick()
            val date = compose.onNodeWithText("Date").assertIsSelected().fetchSemanticsNode().boundsInRoot
            val length = compose.onNodeWithText("Length").fetchSemanticsNode().boundsInRoot
            assertTrue(date.top < length.top && date.width == length.width && date.right == length.right)
            compose.onNodeWithText("Length").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Ascending").performClick().assertIsSelected()
            compose.onAllNodesWithText("Done").assertCountEquals(0)
            compose.onAllNodesWithContentDescription("Close sort conversations").assertCountEquals(0)
            compose.onNodeWithText("Length ↑").assertExists()
        }
    }

    @Test fun libraryControlsStayPinnedWhenRowsScroll() {
        render(content = {
            PinnedLibraryPane(Modifier.fillMaxSize(), controls = {
                Text("Library header")
                LibrarySortControl("Conversations", listOf("Date", "Name", "Length"), "Date", true, summary = "80 files") { _, _ -> }
            }) {
                LazyColumn(Modifier.fillMaxSize()) { items(80) { Text("Example $it") } }
            }
        }) {
            val sort = compose.onNodeWithContentDescription("Sort Conversations")
            val before = sort.fetchSemanticsNode().boundsInRoot
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(50)
            compose.onNodeWithText("Example 50").assertIsDisplayed()
            compose.onNodeWithText("Library header").assertIsDisplayed()
            compose.onNodeWithText("80 files").assertIsDisplayed()
            org.junit.Assert.assertEquals(before, sort.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun disclosureRetainsKeyboardFocusAndSmallReaderNavigationStaysLabeled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore(context)
        val conversation = ConversationParser.parse("""{"name":"Focus fixture","messages":[
            {"versions":[{"role":"user","content":"Describe the garden"}]},
            {"versions":[{"role":"assistant","content":"A neutral reply."}]},
            {"versions":[{"role":"user","content":"Describe the orchard"}]}]}""")
        val entry = LibraryEntry("accessibility-fixture", conversation, 1L)
        render(fontScale = 2f, content = {
            Column(Modifier.fillMaxSize()) { ConversationReader(entry, store, 17f, ReaderStyle(), true, 0, false, {}, {}) }
        }) {
            val preview = compose.onNode(hasText("Describe the garden") and SemanticsMatcher("Expandable prompt") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Expand prompt and replies"
            })
            compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
            preview.performSemanticsAction(SemanticsActions.RequestFocus)
            preview.assertIsFocused().performClick()
            val collapse = compose.onNodeWithText("Collapse prompt and replies").assertIsFocused()
            compose.onNodeWithContentDescription("Next prompt").assertIsDisplayed().assertHasClickAction()
            collapse.performClick()
            preview.assertIsFocused()
        }
    }

    @Test fun linksAndSelectedPromptsMeetTextContrastInBothThemes() {
        fun contrast(first: Color, second: Color): Double {
            val foreground = first.luminance().toDouble()
            val background = second.luminance().toDouble()
            return (maxOf(foreground, background) + .05) / (minOf(foreground, background) + .05)
        }
        for ((scheme, link) in listOf(Day to DayLink, Night to Night.primary)) {
            assertTrue(contrast(link, scheme.surface) >= 4.5)
            assertTrue(contrast(link, scheme.primaryContainer) >= 4.5)
            assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5)
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainer) >= 4.5)
        }
    }
}
