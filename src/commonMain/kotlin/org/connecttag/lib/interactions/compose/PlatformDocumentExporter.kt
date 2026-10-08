package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable

data class DocumentExportRequest(
    val text: String,
    val fileName: String,
    val mimeType: String = "application/json",
)

fun interface PlatformDocumentExporter {
    fun export(request: DocumentExportRequest)
}

@Composable
expect fun rememberPlatformDocumentExporter(
    onResult: (Boolean) -> Unit,
): PlatformDocumentExporter
