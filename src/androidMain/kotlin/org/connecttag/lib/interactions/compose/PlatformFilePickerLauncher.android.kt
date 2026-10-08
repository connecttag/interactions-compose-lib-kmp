package org.connecttag.lib.interactions.compose

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import org.connecttag.lib.filestorage.PlatformFilePickerRequest
import org.connecttag.lib.filestorage.PlatformFilePickerResult
import org.connecttag.lib.filestorage.PlatformFileReference
import org.connecttag.lib.filestorage.PlatformPickedFile

@Composable
actual fun rememberPlatformFilePickerLauncher(
    onResult: (PlatformFilePickerResult) -> Unit,
): PlatformFilePickerLauncher {
    val currentOnResult = rememberUpdatedState(onResult)
    val contentResolver = LocalContext.current.contentResolver
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            runCatching {
                contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        currentOnResult.value(
            uri?.let { selected ->
                PlatformFilePickerResult.Selected(
                    listOf(PlatformPickedFile(PlatformFileReference(selected.toString()))),
                )
            } ?: PlatformFilePickerResult.Cancelled,
        )
    }
    val multipleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) {
            currentOnResult.value(PlatformFilePickerResult.Cancelled)
        } else {
            uris.forEach { selected ->
                runCatching {
                    contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            currentOnResult.value(
                PlatformFilePickerResult.Selected(
                    uris.map { uri -> PlatformPickedFile(PlatformFileReference(uri.toString())) },
                ),
            )
        }
    }
    return remember(singleLauncher, multipleLauncher) {
        PlatformFilePickerLauncher { request: PlatformFilePickerRequest ->
            val mimeTypes = request.mimeTypes.ifEmpty { setOf("*/*") }.toTypedArray()
            if (request.allowMultiple) multipleLauncher.launch(mimeTypes) else singleLauncher.launch(mimeTypes)
        }
    }
}
