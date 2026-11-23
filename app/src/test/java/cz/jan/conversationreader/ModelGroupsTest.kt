package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ModelGroupsTest {
    private fun item(id: String, models: List<String>) = ModelConversation(id, id, "Title $id", models, 20)

    @Test fun bookmarksUseDisplayedDatesAndRecentUsesLastOpened() {
        val openedLater = RecentReading("older", "Older conversation", "", openedAt = 20)
        val openedEarlier = RecentReading("newer", "Newer conversation", "", openedAt = 10)
        assertTrue(cardSortDate(false, openedEarlier, 200) > cardSortDate(false, openedLater, 100))
        assertTrue(cardSortDate(true, openedLater, 100) > cardSortDate(true, openedEarlier, 200))
    }

    @Test fun groupsAllModelsOnceAndRetainsUnknownModels() {
        val multiple = item("multi", listOf("Model B", "Model A", " model a ", "Model A"))
        val groups = modelGroups(listOf(multiple, multiple, item("unknown", emptyList())), "")
        assertEquals(listOf("Model A", "Model B", "Unknown model"), groups.keys.toList())
        assertEquals(listOf("multi"), groups.getValue("Model A").map { it.id })
        assertEquals(listOf("multi"), groups.getValue("Model B").map { it.id })
        assertEquals("unknown", groups.getValue("Unknown model").single().id)
    }

    @Test fun filtersOnlyModelNamesAtTheTopAndConversationMetadataInsideAModel() {
        val items = listOf(item("alpha", listOf("Model A")), item("beta", listOf("Model B")))
        assertEquals(setOf("Model A"), modelGroups(items, "model a").keys)
        assertTrue(modelGroups(items, "alpha").isEmpty())
        assertEquals("alpha", modelGroups(items, "Title alpha", "Model A").values.flatten().single().id)
        assertTrue(modelGroups(items, "alpha.json", "Model A").isEmpty())
        assertTrue(modelGroups(items, "beta", "Model A").isEmpty())
        assertTrue(modelGroups(items, "model a", "Model A").isEmpty())
    }
    @Test fun groupSortingAndConversationSortingAreIndependent() {
        val items = listOf(
            item("alpha", listOf("Model A")).copy(modified = 30, words = 10),
            item("beta", listOf("Model A")).copy(modified = 10, words = 20),
            item("gamma", listOf("Model B")).copy(modified = 20, words = 100))
        val byDate = modelGroups(items, "", sortField = "Date", descending = true, groupDescending = true)
        assertEquals(listOf("Model B", "Model A"), byDate.keys.toList())
        assertEquals(listOf("alpha", "beta"), byDate.getValue("Model A").map { it.id })
        val byLength = modelGroups(items, "", sortField = "Length", descending = true, groupDescending = true)
        assertEquals(byDate.keys, byLength.keys)
        assertEquals(listOf("beta", "alpha"), byLength.getValue("Model A").map { it.id })
        assertEquals(listOf("Model A", "Model B"), modelGroups(items, "", sortField = "Date", descending = true).keys.toList())
        assertEquals(listOf("Model B", "Model A"), modelGroups(items, "", groupField = "Count").keys.toList())
        assertEquals(listOf("Model A", "Model B"), modelGroups(items, "", groupField = "Count", groupDescending = true).keys.toList())
    }
}
