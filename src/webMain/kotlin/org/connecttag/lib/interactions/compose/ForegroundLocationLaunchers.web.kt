package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.connecttag.lib.permissions.PermissionRequestResult
import org.connecttag.lib.permissions.PlatformPermissionStatus

@Composable
actual fun rememberForegroundLocationPermissionLauncher(
    enabled: Boolean,
    onResult: (PermissionRequestResult) -> Unit,
): LocationPermissionLauncher {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember(enabled) {
        LocationPermissionLauncher { requirement ->
            currentOnResult.value(
                PermissionRequestResult(
                    permission = requirement.toPlatformPermission(),
                    status = PlatformPermissionStatus.NotRequired,
                ),
            )
        }
    }
}

@Composable
internal actual fun rememberForegroundLocationSettingsLauncher(
    onResult: (LocationSettingsResolutionResult) -> Unit,
): ForegroundLocationSettingsLauncher {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        ForegroundLocationSettingsLauncher {
            currentOnResult.value(LocationSettingsResolutionResult.Unavailable)
        }
    }
}

@Composable
internal actual fun ObserveLocationCapabilityChanges(onChanged: () -> Unit) = Unit

@Composable
actual fun rememberPlatformSettingsLauncher(
    onReturned: (PlatformSettingsDestination) -> Unit,
): PlatformSettingsLauncher {
    val currentOnReturned = rememberUpdatedState(onReturned)
    return remember {
        PlatformSettingsLauncher { destination -> currentOnReturned.value(destination) }
    }
}
