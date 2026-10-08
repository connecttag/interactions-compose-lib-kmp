package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformDocumentExporter(
    onResult: (Boolean) -> Unit,
): PlatformDocumentExporter = remember(onResult) {
    PlatformDocumentExporter { onResult(false) }
}
