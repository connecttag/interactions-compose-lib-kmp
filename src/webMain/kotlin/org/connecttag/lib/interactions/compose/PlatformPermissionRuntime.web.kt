package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import org.connecttag.lib.permissions.PlatformPermissionRuntimeConfig
import org.connecttag.lib.permissions.PlatformPermissionService

@Composable
actual fun ProvidePlatformPermissionRuntime(
    config: PlatformPermissionRuntimeConfig,
    onServiceAvailable: (PlatformPermissionService) -> Unit,
    content: @Composable () -> Unit,
) {
    ProvideUnsupportedPlatformPermissionRuntime(config, onServiceAvailable, content)
}
