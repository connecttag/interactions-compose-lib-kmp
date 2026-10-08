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
    val permissionLauncher = rememberPlatformPermissionRequestLauncher { result ->
        currentOnResult.value(result)
    }
    return remember(enabled, permissionLauncher) {
        LocationPermissionLauncher { requirement ->
            if (enabled) {
                permissionLauncher.request(requirement.toPlatformPermission())
            } else {
                currentOnResult.value(
                    PermissionRequestResult(
                        permission = requirement.toPlatformPermission(),
                        status = PlatformPermissionStatus.NotRequired,
                    ),
                )
            }
        }
    }
}
