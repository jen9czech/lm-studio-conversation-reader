package cz.jan.conversationreader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ReaderToolbar(searching: Boolean, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onCloseSearch: () -> Unit, onPreferences: () -> Unit, title: String? = null, subtitle: String? = null, onBack: (() -> Unit)? = null, extraActions: @Composable RowScope.() -> Unit = {}) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searching) { if (searching) { focus.requestFocus(); keyboard?.show() } }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (searching) {
            IconButton(onClick = { focusManager.clearFocus(force = true); keyboard?.hide(); onCloseSearch() }) { Icon(painterResource(R.drawable.ic_back), "Exit search") }
            BasicTextField(query, onQuery, singleLine = true, textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f).focusRequester(focus), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), decorationBox = { inner ->
                Box { if (query.isEmpty()) Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge); inner() }
            })
            IconButton(onClick = { onQuery("") }) { Icon(painterResource(R.drawable.ic_close), "Clear search") }
        } else {
            if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") }
            else Icon(painterResource(R.drawable.ic_reader), "LM Conversation Reader", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.padding(end = 8.dp).size(48.dp))
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onSearch) { Icon(painterResource(R.drawable.ic_search), "Search") }
            extraActions()
        }
        if (!searching) IconButton(onClick = onPreferences) {
            if (title != null) Icon(painterResource(R.drawable.ic_settings), "Settings") else Text("Aa", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SearchResultsBar(current: Int, total: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (total == 0) "0 results" else "${current + 1} of $total", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onPrevious, enabled = total > 0) { Icon(painterResource(R.drawable.ic_up), "Previous result") }
        IconButton(onClick = onNext, enabled = total > 0) { Icon(painterResource(R.drawable.ic_down), "Next result") }
    }
}

@Composable
fun PreferencesScreen(main: Boolean, folder: String?, fontSize: Float, theme: String, collapse: Boolean, onClose: () -> Unit, onFolder: () -> Unit, onFont: (Float) -> Unit, onTheme: (String) -> Unit, onCollapse: (Boolean) -> Unit, style: ReaderStyle = ReaderStyle(), onStyle: (ReaderStyle) -> Unit = {}, onRescan: () -> Unit = {}) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_back), "Back") }
                    Text(if (main) "Settings" else "Reading preferences", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                }
                val preferenceScroll = rememberScrollState()
                Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(preferenceScroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (main) {
                        PreferenceSection("Linked folder") {
                            Text(folder?.let { Uri.decode(it).substringAfterLast(":") } ?: "No folder selected", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = onFolder) { Text(if (folder == null) "Link folder" else "Change folder") }
                            OutlinedButton(onClick = onRescan, enabled = folder != null) { Text("Update index now") }
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
                        Text("A comfortable size for reading", fontSize = fontSize.sp, fontFamily = readerFontFamily(style.font))
                    }
                    PreferenceSection("Font") {
                        Column(Modifier.selectableGroup()) {
                            listOf("Sans-serif", "Serif", "Monospaced").forEach { font ->
                                Row(Modifier.fillMaxWidth().selectable(selected = style.font == font, role = Role.RadioButton, onClick = { onStyle(style.copy(font = font)) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = style.font == font, onClick = null)
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(font, fontFamily = readerFontFamily(font), fontSize = 20.sp)
                                        Text("The quick brown fox", fontFamily = readerFontFamily(font), fontSize = 20.sp)
                                        Text("Aa Bb 012345 · Žluťoučký kůň", fontFamily = readerFontFamily(font), fontSize = 18.sp)
                                        Text(when (font) { "Serif" -> "Times New Roman style · Liberation Serif"; "Monospaced" -> "Courier style · Courier Prime"; else -> "Arial style · Liberation Sans" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    PreferenceSection("Theme") {
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Light", "Dark", "System").forEach { choice ->
                                val selected = choice == theme
                                Surface(modifier = Modifier.weight(1f).height(72.dp).selectable(selected = selected, role = Role.RadioButton, onClick = { onTheme(choice) }), shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
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
                PlainScrollSlider(preferenceScroll)
                }
            }
        }
    }
}

@Composable
private fun PreferenceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    } }
}
