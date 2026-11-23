package cz.jan.conversationreader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import java.net.URI
import java.net.URLEncoder

data class ModelSource(val url: String, val direct: Boolean)

/** A local alias is not enough to claim a particular repository as the source. */
internal fun huggingFaceSource(model: String): ModelSource? {
    val original = model.trim()
    if (original.isBlank() || original == "Unknown model") return null
    val identifier = original.substringBefore('@').trim()
    val repositoryPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*")
    val savedUrl = runCatching { URI(original) }.getOrNull()?.takeIf { it.scheme == "https" && it.host == "huggingface.co" && it.userInfo == null && it.port == -1 }
    val repository = if (savedUrl != null) savedUrl.path.trim('/').split('/').take(2).joinToString("/") else identifier
    if (repositoryPattern.matches(repository) && !repository.endsWith(".gguf", true) && repository.split('/').all { it != "." && it != ".." })
        return ModelSource("https://huggingface.co/$repository", true)
    return ModelSource(huggingFaceSearchUrl(identifier), false)
}

private fun huggingFaceSearchUrl(identifier: String): String {
    val query = identifier.substringAfterLast('/').removeSuffix(".gguf").removeSuffix(".GGUF").take(200)
    return "https://huggingface.co/models?search=" + URLEncoder.encode(query, "UTF-8")
}

@Composable
internal fun ModelSourceButton(model: String, modifier: Modifier = Modifier, showModelName: Boolean = false) {
    val source = remember(model) { huggingFaceSource(model) }
    if (source == null) {
        if (showModelName) Text(model, modifier, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        return
    }
    val context = LocalContext.current
    var error by remember(model) { mutableStateOf(false) }
    Column(modifier) {
        TextButton(contentPadding = if (showModelName) PaddingValues(horizontal = 8.dp, vertical = 8.dp) else androidx.compose.material3.ButtonDefaults.TextButtonContentPadding, onClick = {
            error = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }.isFailure
        }) {
            Text(if (showModelName) "$model ↗" else if (source.direct) "Open on Hugging Face ↗" else "Find on Hugging Face ↗",
                style = if (showModelName) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
        }
        if (error) Text("No browser could open this link.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ConversationModelAction(model: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var browserError by remember(model) { mutableStateOf(false) }
    val source = remember(model) { huggingFaceSource(model) }
    val searchUrl = remember(model, source) {
        val identifier = if (source?.direct == true) source.url.removePrefix("https://huggingface.co/") else model.trim().substringBefore('@')
        huggingFaceSearchUrl(identifier)
    }

    Column {
        Text(
            text = "$model ↗",
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).combinedClickable(
                role = Role.Button,
                onClickLabel = "Search Hugging Face",
                onLongClickLabel = "Copy model name",
                onLongClick = { clipboard.setText(AnnotatedString(model)) },
                onClick = {
                    browserError = runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
                    }.isFailure
                }
            ).padding(vertical = 12.dp),
            color = readerLinkColor(),
            style = MaterialTheme.typography.bodyLarge
        )
        Text("Tap to search Hugging Face · Long press to copy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (browserError) Text("No browser could open this link.", color = MaterialTheme.colorScheme.error)
    }
}
