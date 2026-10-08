package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import org.connecttag.lib.location.manager.ForegroundLocationPurpose

enum class LocationSettingsResolutionResult {
    Satisfied,
    Changed,
    Declined,
    ReturnedFromSettings,
    Unavailable,
}

fun interface ForegroundLocationSettingsLauncher {
    fun launch(purpose: ForegroundLocationPurpose)
}

@Composable
internal expect fun rememberForegroundLocationSettingsLauncher(
    onResult: (LocationSettingsResolutionResult) -> Unit,
): ForegroundLocationSettingsLauncher
