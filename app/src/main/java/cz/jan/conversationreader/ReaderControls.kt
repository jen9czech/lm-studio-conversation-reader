package cz.jan.conversationreader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ReaderToolbar(searching: Boolean, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onCloseSearch: () -> Unit, onPreferences: () -> Unit, title: String? = null, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) { if (searching) { focus.requestFocus(); keyboard?.show() } }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (searching) {
            IconButton(onClick = { keyboard?.hide(); onCloseSearch() }) { Icon(painterResource(R.drawable.ic_back), "Exit search") }
            BasicTextField(query, onQuery, singleLine = true, textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f).focusRequester(focus), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), decorationBox = { inner ->
                Box { if (query.isEmpty()) Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge); inner() }
            })
            IconButton(onClick = { onQuery("") }) { Icon(painterResource(R.drawable.ic_close), "Clear search") }
        } else {
            if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") }
            else Icon(painterResource(R.drawable.ic_reader), "LM Conversation Reader", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.padding(end = 8.dp).size(36.dp))
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onSearch) { Icon(painterResource(R.drawable.ic_search), "Search") }
        }
        IconButton(onClick = onPreferences) {
            if (title != null) Icon(painterResource(R.drawable.ic_settings), "Settings") else Text("Aa", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PreferencesScreen(main: Boolean, folder: String?, fontSize: Float, theme: String, collapse: Boolean, onClose: () -> Unit, onFolder: () -> Unit, onFont: (Float) -> Unit, onTheme: (String) -> Unit, onCollapse: (Boolean) -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_back), "Back") }
                    Text(if (main) "Settings" else "Reading preferences", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (main) {
                        PreferenceSection("Linked folder") {
                            Text(folder?.let { Uri.decode(it).substringAfterLast(":") } ?: "No folder selected", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = onFolder) { Text(if (folder == null) "Link folder" else "Change folder") }
                        }
                        PreferenceSection("Conversation navigation") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Collapse by prompts", Modifier.weight(1f))
                                Switch(collapse, onCollapse)
                            }
                            Text("Show two-line prompt previews. Tap to read the full prompt and replies.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    PreferenceSection("Text size") {
                        Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.titleMedium)
                        Slider(fontSize, onFont, valueRange = 14f..26f, steps = 11)
                        Text("A comfortable size for reading", fontSize = fontSize.sp)
                    }
                    PreferenceSection("Theme") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Light", "Dark", "System").forEach { choice ->
                                val selected = choice == theme
                                Surface(onClick = { onTheme(choice) }, modifier = Modifier.weight(1f).height(72.dp), shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (choice == "System") Text("System", fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
                                        else Icon(painterResource(if (choice == "Light") R.drawable.ic_sun else R.drawable.ic_moon), "$choice theme", Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (main) PreferenceSection("About") {
                        Text("LM Studio Conversation Reader", style = MaterialTheme.typography.titleMedium)
                        Text("Version ${BuildConfig.VERSION_NAME}\nUpdated ${BuildConfig.UPDATED_DATE}")
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jen9czech/lm-studio-conversation-reader"))) }) { Text("GitHub project ↗") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    } }
}
