package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable

enum class PlatformSettingsDestination {
    ApplicationDetails,
    LocationServices,
}

fun interface PlatformSettingsLauncher {
    fun launch(destination: PlatformSettingsDestination)
}

@Composable
expect fun rememberPlatformSettingsLauncher(
    onReturned: (PlatformSettingsDestination) -> Unit,
): PlatformSettingsLauncher
