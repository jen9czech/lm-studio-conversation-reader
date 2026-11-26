package cz.jan.conversationreader

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val LibraryViews = listOf("Folders", "All files", "Models", "Imported", "Recent", "Bookmarked")

@Composable
fun ReaderToolbar(searching: Boolean, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onCloseSearch: () -> Unit, onPreferences: () -> Unit, title: String? = null, subtitle: String? = null, onBack: (() -> Unit)? = null, extraActions: @Composable RowScope.() -> Unit = {}, currentView: String? = null, onMenuView: ((String) -> Unit)? = null, searchHint: String = "Search") {
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val searchButton = remember { FocusRequester() }
    var returnToSearchButton by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    fun exitSearch() {
        returnToSearchButton = true
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        onCloseSearch()
    }
    LaunchedEffect(searching) {
        if (searching) {
            focus.requestFocus()
            keyboard?.show()
        } else if (returnToSearchButton) {
            searchButton.requestFocus()
            returnToSearchButton = false
        }
    }
    val window = LocalWindowInfo.current.containerSize
    val titleSize = MaterialTheme.typography.titleLarge.fontSize
    val stackTitle = title != null && with(LocalDensity.current) {
        window.width.toDp() < 340.dp * (titleSize.toDp() / 22.dp)
    }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = if (stackTitle && !searching) 48.dp else 64.dp).padding(horizontal = 8.dp)
            .onPreviewKeyEvent { event ->
                if (searching && event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    inputMode.requestInputMode(InputMode.Keyboard)
                    exitSearch()
                    true
                } else false
            }, verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                IconButton(onClick = ::exitSearch) { Icon(painterResource(R.drawable.ic_back), "Exit search") }
                BasicTextField(query, onQuery, singleLine = true, textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = searchHint }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { if (query.isEmpty()) Text(searchHint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); inner() }
                })
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(painterResource(R.drawable.ic_close), "Clear search") }
            } else {
                if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") }
                else Box(Modifier.padding(end = 8.dp)) {
                    if (onMenuView != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_reader), "Open navigation menu", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(36.dp))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            LibraryViews.forEach { view ->
                                DropdownMenuItem(text = { Text(view) }, leadingIcon = { Text(if (view == currentView) "✓" else "", Modifier.width(24.dp)) }, modifier = Modifier.semantics { selected = view == currentView }, onClick = { menuOpen = false; onMenuView(view) })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(painterResource(R.drawable.ic_settings), null) }, onClick = { menuOpen = false; onPreferences() })
                            HorizontalDivider()
                            Text("Version ${BuildConfig.VERSION_NAME}", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else Icon(painterResource(R.drawable.ic_reader), "LM Conversation Reader", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(36.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (!stackTitle) ToolbarTitle(title, subtitle)
                }
                IconButton(onClick = onSearch, modifier = Modifier.focusRequester(searchButton)) { Icon(painterResource(R.drawable.ic_search), "Search") }
                extraActions()
            }
            if (!searching) IconButton(onClick = onPreferences, modifier = if (title == null) Modifier.semantics { contentDescription = "Reading preferences" } else Modifier) {
                if (title != null) Icon(painterResource(R.drawable.ic_settings), "Settings") else Text("Aa", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (stackTitle && !searching) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            ToolbarTitle(title, subtitle)
        }
    }
}

@Composable
private fun ToolbarTitle(title: String?, subtitle: String?) {
    if (title != null) Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SearchResultsBar(current: Int, total: Int, complete: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val status = when {
            !complete -> "Searching…"
            total == 0 -> "No matches"
            else -> "${current + 1} of $total"
        }
        Text(status, Modifier.weight(1f).padding(vertical = 8.dp).semantics {
            if (complete) liveRegion = LiveRegionMode.Polite
            if (total > 0) contentDescription = "Match ${current + 1} of $total"
        }, style = MaterialTheme.typography.labelSmall)
        if (total > 0) {
            IconButton(onClick = onPrevious) { Icon(painterResource(R.drawable.ic_up), "Previous result") }
            IconButton(onClick = onNext) { Icon(painterResource(R.drawable.ic_down), "Next result") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(main: Boolean, folder: String?, fontSize: Float, theme: String, collapse: Boolean, onClose: () -> Unit, onFolder: () -> Unit, onFont: (Float) -> Unit, onTheme: (String) -> Unit, onCollapse: (Boolean) -> Unit, style: ReaderStyle = ReaderStyle(), onStyle: (ReaderStyle) -> Unit = {}) {
    val context = LocalContext.current
    if (main) {
        ReaderDetailScreen("Settings", onClose, compact = true) {
            Text("Linked folder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onFolder, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (folder == null) "Link a folder" else "Change folder", style = MaterialTheme.typography.labelLarge)
                    folder?.let { Text(Uri.decode(it).substringAfterLast(":"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Collapse by prompts", style = MaterialTheme.typography.titleMedium)
                    Text("Show prompt previews when opening a conversation", style = MaterialTheme.typography.bodySmall)
                }
                Switch(collapse, onCollapse, modifier = Modifier.semantics { contentDescription = "Collapse by prompts" })
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Text size", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.labelLarge)
            }
            Slider(fontSize, onFont, valueRange = 14f..26f, steps = 11, modifier = Modifier.semantics {
                contentDescription = "Text size"; stateDescription = "${fontSize.toInt()} sp"
            })
            Spacer(Modifier.height(8.dp))
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Light", "Dark", "System").forEach { choice -> ThemeChoice(choice, theme, onTheme, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jen9czech/lm-studio-conversation-reader"))) },
                contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.semantics { onClick(label = "Open GitHub project", action = null) }) {
                Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.UPDATED_DATE} ↗", style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { UpdateCheckButton() }
        }
        return
    }
    val controls: @Composable ColumnScope.() -> Unit = {
        DetailSection("Text size", compact = true, largerHeading = true) {
            Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.titleMedium)
            Slider(fontSize, onFont, valueRange = 14f..26f, steps = 11, modifier = Modifier.semantics {
                contentDescription = "Text size"
                stateDescription = "${fontSize.toInt()} sp"
            })
        }
        DetailSection("Font", compact = true, largerHeading = true) {
            FontChoices(style, onStyle)
        }
        DetailSection("Theme", compact = true, largerHeading = true) {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Light", "Dark", "System").forEach { choice -> ThemeChoice(choice, theme, onTheme, Modifier.weight(1f)) }
            }
        }

    }
    ControlsSheet("Reading preferences", onClose, allowPartial = true, content = controls)
}

@Composable
private fun FontChoices(style: ReaderStyle, onStyle: (ReaderStyle) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Sans-serif", "Serif", "Monospaced").forEach { font ->
            val selected = style.font == font
            Surface(modifier = Modifier.weight(1f).heightIn(min = 64.dp).selectable(selected, role = Role.RadioButton) { onStyle(style.copy(font = font)) },
                shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    val label = when (font) { "Sans-serif" -> "Sans-\nserif"; "Serif" -> "Liberation\nSerif"; else -> "Courier\nPrime" }
                    Text(label, fontFamily = readerFontFamily(font), style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlsSheet(title: String, onClose: () -> Unit, allowPartial: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val height = with(density) { window.height.toDp() }
    val expanded = !allowPartial || height < 600.dp || window.width > window.height || density.fontScale >= 1.5f
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded)) {
        Column(Modifier.fillMaxWidth().heightIn(max = height * .82f).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_back), "Close ${title.lowercase()}") }
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
            val scroll = rememberScrollState()
            Box(Modifier.weight(1f, fill = false)) {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (allowPartial) 6.dp else 12.dp), content = content)
                PlainScrollSlider(scroll)
            }
        }
    }
}

@Composable
internal fun ReaderDetailScreen(title: String, onClose: () -> Unit, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_back), "Back") }
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                }
                val scroll = rememberScrollState()
                Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 20.dp), content = content)
                    PlainScrollSlider(scroll)
                }
            }
        }
    }
}

@Composable
internal fun DetailSection(title: String, compact: Boolean = false, largerHeading: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
        Text(title, Modifier.padding(start = 4.dp).semantics { heading() }, style = if (largerHeading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp), content = content)
        }
    }
}

@Composable
private fun ThemeChoice(choice: String, theme: String, onTheme: (String) -> Unit, modifier: Modifier) {
    val selected = choice == theme
    Surface(modifier = modifier.heightIn(min = 52.dp).selectable(selected = selected, role = Role.RadioButton, onClick = { onTheme(choice) }), shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            if (choice == "System") Text("System", style = MaterialTheme.typography.labelMedium)
            else Icon(painterResource(if (choice == "Light") R.drawable.ic_sun else R.drawable.ic_moon), "$choice theme", Modifier.size(25.dp))
        }
    }
}
