package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import org.connecttag.lib.permissions.PermissionRequestResult
import org.connecttag.lib.permissions.PlatformPermissionRequestLauncher
import kotlinx.coroutines.launch

@Composable
fun rememberPlatformPermissionRequestLauncher(
    onResult: (PermissionRequestResult) -> Unit,
): PlatformPermissionRequestLauncher {
    val store = LocalPlatformPermissionStore.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnResult = rememberUpdatedState(onResult)

    return remember(store, coroutineScope) {
        PlatformPermissionRequestLauncher { permission ->
            coroutineScope.launch {
                currentOnResult.value(store.request(permission))
            }
        }
    }
}
