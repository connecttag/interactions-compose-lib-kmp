package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import org.connecttag.lib.location.manager.ForegroundLocationPurpose
import org.connecttag.lib.location.manager.LocationAccuracyRequirement
import org.connecttag.lib.permissions.PermissionRequestResult
import org.connecttag.lib.permissions.PlatformPermission

fun interface LocationPermissionLauncher {
    fun launch(requirement: LocationAccuracyRequirement)

    fun launch(purpose: ForegroundLocationPurpose) {
        launch(purpose.accuracyRequirement)
    }
}

internal fun LocationAccuracyRequirement.toPlatformPermission(): PlatformPermission {
    return when (this) {
        LocationAccuracyRequirement.Approximate -> PlatformPermission.COARSE_LOCATION
        LocationAccuracyRequirement.Precise -> PlatformPermission.LOCATION
    }
}

@Composable
expect fun rememberForegroundLocationPermissionLauncher(
    enabled: Boolean,
    onResult: (PermissionRequestResult) -> Unit,
): LocationPermissionLauncher

@Composable
internal expect fun ObserveLocationCapabilityChanges(
    onChanged: () -> Unit,
)
