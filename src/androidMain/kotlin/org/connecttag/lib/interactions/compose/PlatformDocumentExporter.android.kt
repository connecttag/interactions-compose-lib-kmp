package org.connecttag.lib.interactions.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import java.nio.charset.StandardCharsets

@Composable
actual fun rememberPlatformDocumentExporter(
    onResult: (Boolean) -> Unit,
): PlatformDocumentExporter {
    val context = LocalView.current.context
    val currentOnResult by rememberUpdatedState(onResult)
    var pendingText by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val text = pendingText
        pendingText = null
        val saved = uri != null && text != null && runCatching {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(text.toByteArray(StandardCharsets.UTF_8))
            } ?: error("Unable to open document destination")
        }.isSuccess
        currentOnResult(saved)
    }
    return remember(launcher) {
        PlatformDocumentExporter { request ->
            pendingText = request.text
            launcher.launch(request.fileName)
        }
    }
}
