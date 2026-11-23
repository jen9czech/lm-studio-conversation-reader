package cz.jan.conversationreader

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal fun cardSortDate(recentView: Boolean, item: RecentReading, displayedDate: Long?): Long =
    if (recentView) item.openedAt else displayedDate ?: 0L

@Composable
internal fun rememberLibrarySortField(preferences: SharedPreferences, key: String, scope: String, default: String) =
    remember(preferences, scope) { mutableStateOf(preferences.getString("$key:$scope", preferences.getString(key, default)) ?: default) }

@Composable
internal fun rememberLibrarySortDirection(preferences: SharedPreferences, key: String, scope: String, default: Boolean) =
    remember(preferences, scope) { mutableStateOf(preferences.getBoolean("$key:$scope", preferences.getBoolean(key, default))) }

@Composable
internal fun LibraryGroupSortControl(label: String, field: String, descending: Boolean, summary: String? = null, onSort: (String, Boolean) -> Unit) {
    LibrarySortControl(label, listOf("Name", "Count"), field, descending, summary, onSort = onSort)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySortControl(label: String, fields: List<String>, field: String, descending: Boolean, summary: String? = null, action: (@Composable () -> Unit)? = null, onSort: (String, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sortWidth = maxWidth * .62f
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val inlineAction = maxWidth >= 300.dp * fontScale * (MaterialTheme.typography.labelMedium.fontSize.value / 12f)
        Column {
            if (!inlineAction && action != null) action()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (summary != null) Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (inlineAction && action != null) action()
                Box(modifier = if (summary == null) Modifier else Modifier.widthIn(max = sortWidth), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("$field ${if (descending) "↓" else "↑"}",
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f, fill = false), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                        Icon(painterResource(R.drawable.ic_sort), "Sort $label", Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)) {
                        SortOptions(fields, field, descending, onSort)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortOptions(fields: List<String>, field: String, descending: Boolean, onSort: (String, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        fields.forEach { choice ->
            Surface(color = if (choice == field) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(choice == field, role = Role.RadioButton) { onSort(choice, descending) }) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(choice, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
            listOf(false, true).forEach { down ->
                val label = if (down) "Descending" else "Ascending"
                Surface(color = if (descending == down) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(descending == down, role = Role.RadioButton) { onSort(field, down) }
                        .semantics { contentDescription = label }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (down) R.drawable.ic_down else R.drawable.ic_up), null, Modifier.size(22.dp))
                    }
                }
            }
    }
}
