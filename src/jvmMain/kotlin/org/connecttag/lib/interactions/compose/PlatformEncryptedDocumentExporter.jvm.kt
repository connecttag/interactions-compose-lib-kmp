package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformEncryptedDocumentExporter(
    onResult: (Boolean) -> Unit,
): PlatformEncryptedDocumentExporter = remember { PlatformEncryptedDocumentExporter { onResult(false) } }
