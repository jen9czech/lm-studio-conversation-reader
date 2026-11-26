package cz.jan.conversationreader

import android.app.UiAutomation
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderAccessibilityDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun androidAccessibilityTreeAndKeyboardExposeReaderActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "cz.jan.conversationreader.quality")
        val store = LibraryStore(context)
        store.preferences.edit().clear().putInt("lastSeenVersionCode", BuildConfig.VERSION_CODE).putBoolean("collapsedOpeningDefaultApplied", true)
            .putBoolean("collapsePrompts", true).commit()
        val directory = File(context.filesDir, "conversations").apply { mkdirs() }
        directory.resolve("accessibility-fixture.json").writeText(
            """{"name":"Accessibility fixture","messages":[{"versions":[{"role":"user","content":"Plan a garden"}]},{"versions":[{"role":"assistant","content":"Start with native plants."}]},{"versions":[{"role":"user","content":"Plan an orchard"}]}]}"""
        )
        check(store.load().first.any { it.id == "accessibility-fixture" })

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Accessibility fixture").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Accessibility fixture").onLast().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Plan a garden").fetchSemanticsNodes().isNotEmpty() }

            val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            fun nodes(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
                if (node != null) {
                    yield(node)
                    for (index in 0 until node.childCount) yieldAll(nodes(node.getChild(index)))
                }
            }
            fun bookmarkNode() = nodes(automation.rootInActiveWindow).firstOrNull { it.contentDescription?.toString() == "Add bookmark" }
            compose.waitUntil(10_000) { bookmarkNode() != null }
            if (Build.VERSION.SDK_INT >= 30) assertEquals("Not bookmarked", bookmarkNode()?.stateDescription?.toString())
            fun androidDisclosure(label: String) = nodes(automation.rootInActiveWindow).firstOrNull { node ->
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label?.toString() == label }
            }
            val collapsedNode = checkNotNull(androidDisclosure("Expand prompt and replies"))
            if (Build.VERSION.SDK_INT >= 30) assertEquals("Collapsed", collapsedNode.stateDescription?.toString())

            val preview = compose.onNode(hasText("Plan a garden") and SemanticsMatcher("Prompt expand action") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Expand prompt and replies"
            })
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_TAB)
            preview.performSemanticsAction(SemanticsActions.RequestFocus)
            preview.assertIsFocused().performClick()
            val collapse = compose.onNode(SemanticsMatcher("Collapse prompt") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Collapse prompt and replies"
            })
            compose.waitUntil(5_000) { androidDisclosure("Collapse prompt and replies") != null }
            compose.onNodeWithContentDescription("Next prompt").assertIsDisplayed().assertHasClickAction()
            collapse.performClick()
            preview.assertIsDisplayed()

            val search = compose.onNodeWithContentDescription("Search")
            search.performClick()
            val field = compose.onNode(hasSetTextAction()).assertIsFocused()
            field.performTextInput("native plants")
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Match 1 of 1").fetchSemanticsNodes().isNotEmpty() }
            field.assertIsFocused().performKeyInput { pressKey(Key.Escape) }
            search.assertIsFocused()
        }
    }
}
