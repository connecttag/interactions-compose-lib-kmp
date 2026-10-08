package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import org.connecttag.lib.filestorage.PlatformFilePickerRequest
import org.connecttag.lib.filestorage.PlatformFilePickerResult

fun interface PlatformFilePickerLauncher {
    fun launch(request: PlatformFilePickerRequest)
}

@Composable
expect fun rememberPlatformFilePickerLauncher(
    onResult: (PlatformFilePickerResult) -> Unit,
): PlatformFilePickerLauncher
