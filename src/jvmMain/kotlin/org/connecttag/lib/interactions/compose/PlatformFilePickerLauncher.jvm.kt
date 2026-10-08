package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.connecttag.lib.filestorage.PlatformFilePickerResult

@Composable
actual fun rememberPlatformFilePickerLauncher(
    onResult: (PlatformFilePickerResult) -> Unit,
): PlatformFilePickerLauncher = remember(onResult) {
    PlatformFilePickerLauncher { onResult(PlatformFilePickerResult.Unsupported) }
}
