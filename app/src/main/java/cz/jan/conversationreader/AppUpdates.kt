package cz.jan.conversationreader

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal fun versionParts(value: String): List<Int>? {
    val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value) ?: return null
    return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
}

internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val a = versionParts(candidate) ?: return false
    val b = versionParts(installed) ?: return false
    return a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x > y } ?: false
}

private fun latestRelease(): String {
    val connection = URL("https://api.github.com/repos/jen9czech/lm-studio-conversation-reader/releases/latest").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ConversationReader-Android")
        check(connection.responseCode == 200) { "GitHub returned HTTP ${connection.responseCode}" }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(response).getString("tag_name").also { require(versionParts(it) != null) { "Unrecognized release version" } }
    } finally { connection.disconnect() }
}

@Composable
internal fun UpdateCheckButton() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var updateTag by remember { mutableStateOf<String?>(null) }
    FilledTonalButton(enabled = !checking, onClick = {
        checking = true
        scope.launch {
            val check = runCatching { withContext(Dispatchers.IO) { latestRelease() } }
            updateTag = check.getOrNull()?.takeIf { isNewerVersion(it, BuildConfig.VERSION_NAME) }
            result = check.fold(
                onSuccess = { if (updateTag != null) "Version ${it.removePrefix("v")} is available. Open the release page to download its APK and install it using Android." else "You have the latest released version." },
                onFailure = { "Couldn’t check GitHub for updates. Check your Internet connection and try again." }
            )
            checking = false
        }
    }) { Text(if (checking) "Checking for updates…" else "Check for updates") }
    result?.let { message ->
        AlertDialog(onDismissRequest = { result = null }, title = { Text(if (updateTag != null) "Update available" else "App updates") },
            text = { Text(message) }, confirmButton = {
                TextButton(onClick = {
                    val tag = updateTag
                    result = null
                    if (tag != null) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jen9czech/lm-studio-conversation-reader/releases/tag/$tag"))) }
                }) { Text(if (updateTag != null) "Open download" else "OK") }
            }, dismissButton = if (updateTag != null) ({ TextButton(onClick = { result = null }) { Text("Later") } }) else null)
    }
}
