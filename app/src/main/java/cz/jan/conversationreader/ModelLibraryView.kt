package cz.jan.conversationreader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ModelConversation(val id: String, val reference: String, val title: String, val models: List<String>, val words: Int?, val modified: Long = 0, val prompts: Int? = null)

internal fun modelGroups(items: List<ModelConversation>, query: String, selectedModel: String? = null, sortField: String = "Name", descending: Boolean = false, groupDescending: Boolean = false, groupField: String = "Name"): Map<String, List<ModelConversation>> {
    val groups = sortedMapOf<String, MutableList<ModelConversation>>(String.CASE_INSENSITIVE_ORDER)
    items.distinctBy { it.id }.forEach { item ->
        val seenModels = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        item.models.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf("Unknown model") }.forEach { model ->
            val matches = if (selectedModel == null) matchesLibraryName(model, query)
            else model.equals(selectedModel, ignoreCase = true) && matchesLibraryName(item.title, query)
            if (matches && seenModels.add(model))
                groups.getOrPut(model) { mutableListOf() }.add(item)
        }
    }
    val itemOrder = when (sortField) {
        "Date" -> compareBy<ModelConversation> { it.modified }
        "Length" -> compareBy { it.words ?: -1 }
        else -> compareBy { it.title.lowercase() }
    }
    val comparator = if (groupField == "Count") compareBy<String> { groups.getValue(it).size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it } else String.CASE_INSENSITIVE_ORDER
    val orderedNames = groups.keys.sortedWith(if (groupDescending) comparator.reversed() else comparator)
    return orderedNames.associateWith { model ->
        groups.getValue(model).sortedWith((if (descending) itemOrder.reversed() else itemOrder).thenBy { it.title.lowercase() }.thenBy { it.id })
    }
}

@Composable
internal fun ColumnScope.ModelLibraryView(header: @Composable () -> Unit, items: List<ModelConversation>, query: String, searching: Boolean, onClearSearch: () -> Unit, bookmarked: Set<String>, sortField: String, descending: Boolean, onSort: (String, Boolean) -> Unit, groupField: String, groupDescending: Boolean, onGroupSort: (String, Boolean) -> Unit, active: Boolean, selectedModel: String?, onSelectModel: (String?) -> Unit, groupList: androidx.compose.foundation.lazy.LazyListState, conversationList: androidx.compose.foundation.lazy.LazyListState, onOpen: (String) -> Unit, onBookmark: (String) -> Unit) {
    val groups = remember(items, query, selectedModel, sortField, descending, groupDescending, groupField) { modelGroups(items, query, selectedModel, sortField, descending, groupDescending, groupField) }
    val list = if (selectedModel == null) groupList else conversationList
    val conversations = groups.entries.firstOrNull { it.key.equals(selectedModel, ignoreCase = true) }?.value.orEmpty()
    val total = if (selectedModel == null) groups.size else conversations.size
    androidx.activity.compose.BackHandler(active && selectedModel != null && !searching) { onSelectModel(null) }
    PinnedLibraryPane(Modifier.weight(1f), controls = {
                header()
                if (selectedModel == null) {
                    val summary = if (searching) null else "${groups.size} ${if (groups.size == 1) "model" else "models"}"
                    LibraryGroupSortControl("Models", groupField, groupDescending, summary, onSort = onGroupSort)
                } else {
                    Column {
                        SelectedGroupHeader(onBack = { onSelectModel(null) }) {
                            ModelSourceButton(selectedModel, Modifier.weight(1f), showModelName = true)
                        }
                        val summary = if (searching) null else "${conversations.size} conversations"
                        LibrarySortControl("Conversations", listOf("Date", "Name", "Length"), sortField, descending, summary, onSort = onSort)
                    }
                }
    }) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 12.dp)) {
            if (total == 0) item(key = "library:empty") {
                if (query.isNotBlank()) LibrarySearchEmpty(true, onClearSearch, if (selectedModel == null) "No matching models." else "No matching conversations.")
                else LibraryEmptyState(if (selectedModel != null) "No conversations for this model." else "No conversations yet. Link a folder or import files to browse by model.")
            }
            if (selectedModel == null) items(groups.keys.toList(), key = { "model:$it" }) { model ->
                LibraryGroupRow(model, groups.getValue(model).size, model = true, onClick = { onSelectModel(model) })
            } else items(conversations, key = { it.id }) { item ->
                LibraryConversationCard(item.title, item.words, item.modified.takeIf { it > 0 },
                    item.id in bookmarked, onOpen = { onOpen(item.reference) }, onBookmark = { onBookmark(item.id) }, prompts = item.prompts)
            }
        }
        LazyScrollSlider(list, listOf(selectedModel, groups.keys.toList(), conversations.map { it.id }))
    }
}
