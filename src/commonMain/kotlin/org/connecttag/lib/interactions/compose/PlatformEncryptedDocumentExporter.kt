package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable

data class EncryptedDocumentExportRequest(
    val plainText: String,
    val fileName: String,
    val keyAlias: String,
    val mimeType: String = "application/octet-stream",
)

fun interface PlatformEncryptedDocumentExporter {
    fun export(request: EncryptedDocumentExportRequest)
}

@Composable
expect fun rememberPlatformEncryptedDocumentExporter(
    onResult: (Boolean) -> Unit,
): PlatformEncryptedDocumentExporter
