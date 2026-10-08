package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import org.connecttag.lib.permissions.PermissionRequestResult
import org.connecttag.lib.permissions.PlatformPermission
import org.connecttag.lib.permissions.PlatformPermissionRuntimeConfig
import org.connecttag.lib.permissions.PlatformPermissionService
import org.connecttag.lib.permissions.PlatformPermissionStatus
import org.connecttag.lib.permissions.PlatformPermissionUnavailableReason
import org.connecttag.lib.permissions.PlatformSettingsOpenResult
import org.connecttag.lib.permissions.ReactivePlatformPermissionStore

internal val LocalPlatformPermissionService = staticCompositionLocalOf<PlatformPermissionService> {
    MissingPlatformPermissionService
}
internal val LocalPlatformPermissionStore = staticCompositionLocalOf {
    MissingPlatformPermissionStore
}
internal val LocalPlatformPermissionRuntimeConfig = staticCompositionLocalOf {
    PlatformPermissionRuntimeConfig()
}

/**
 * Installs one lifecycle-bound permission service for the current UI host.
 * Mobile hosts create and bind one Moko controller; other hosts expose an
 * explicit unavailable service until they gain a native permission adapter.
 */
@Composable
expect fun ProvidePlatformPermissionRuntime(
    config: PlatformPermissionRuntimeConfig,
    onServiceAvailable: (PlatformPermissionService) -> Unit,
    content: @Composable () -> Unit,
)

@Composable
fun ProvidePlatformPermissionRuntime(
    config: PlatformPermissionRuntimeConfig = PlatformPermissionRuntimeConfig(),
    content: @Composable () -> Unit,
) {
    ProvidePlatformPermissionRuntime(
        config = config,
        onServiceAvailable = {},
        content = content,
    )
}

@Composable
fun ProvidePlatformPermissionRuntime(
    onServiceAvailable: (PlatformPermissionService) -> Unit,
    content: @Composable () -> Unit,
) {
    ProvidePlatformPermissionRuntime(
        config = PlatformPermissionRuntimeConfig(),
        onServiceAvailable = onServiceAvailable,
        content = content,
    )
}

@Composable
fun rememberPlatformPermissionStore(): ReactivePlatformPermissionStore {
    return LocalPlatformPermissionStore.current
}

@Composable
fun rememberPlatformPermissionRuntimeConfig(): PlatformPermissionRuntimeConfig {
    return LocalPlatformPermissionRuntimeConfig.current
}

@Composable
internal fun ProvideUnsupportedPlatformPermissionRuntime(
    config: PlatformPermissionRuntimeConfig,
    onServiceAvailable: (PlatformPermissionService) -> Unit,
    content: @Composable () -> Unit,
) {
    val store = remember(config.trackedPermissions) {
        ReactivePlatformPermissionStore(
            service = UnsupportedPlatformPermissionService,
            permissions = config.trackedPermissions,
            initialStatus = PlatformPermissionStatus.Unavailable,
            initialUnavailableReason = PlatformPermissionUnavailableReason.UnsupportedPlatform,
        )
    }
    val currentOnServiceAvailable = rememberUpdatedState(onServiceAvailable)
    LaunchedEffect(Unit) {
        currentOnServiceAvailable.value(UnsupportedPlatformPermissionService)
    }
    CompositionLocalProvider(
        LocalPlatformPermissionService provides UnsupportedPlatformPermissionService,
        LocalPlatformPermissionStore provides store,
        LocalPlatformPermissionRuntimeConfig provides config,
        content = content,
    )
}

@Composable
internal expect fun ObservePlatformPermissionRuntimeResume(
    onResume: () -> Unit,
)

private class UnavailablePlatformPermissionService(
    private val reason: PlatformPermissionUnavailableReason,
) : PlatformPermissionService {
    override suspend fun status(permission: PlatformPermission): PlatformPermissionStatus {
        return PlatformPermissionStatus.Unavailable
    }

    override suspend fun current(permission: PlatformPermission): PermissionRequestResult {
        return permissionResult(permission)
    }

    override suspend fun request(permission: PlatformPermission): PermissionRequestResult {
        return permissionResult(permission)
    }

    private fun permissionResult(permission: PlatformPermission): PermissionRequestResult {
        return PermissionRequestResult(
            permission = permission,
            status = PlatformPermissionStatus.Unavailable,
            unavailableReason = reason,
        )
    }

    override fun openSettings(): PlatformSettingsOpenResult {
        return when (reason) {
            PlatformPermissionUnavailableReason.UnsupportedPlatform -> {
                PlatformSettingsOpenResult.Unsupported
            }
            PlatformPermissionUnavailableReason.RuntimeNotInstalled,
            PlatformPermissionUnavailableReason.ProviderFailure,
            PlatformPermissionUnavailableReason.TimedOut -> {
                PlatformSettingsOpenResult.Failed
            }
        }
    }
}

private val MissingPlatformPermissionService = UnavailablePlatformPermissionService(
    PlatformPermissionUnavailableReason.RuntimeNotInstalled,
)
private val UnsupportedPlatformPermissionService = UnavailablePlatformPermissionService(
    PlatformPermissionUnavailableReason.UnsupportedPlatform,
)
private val MissingPlatformPermissionStore = ReactivePlatformPermissionStore(
    service = MissingPlatformPermissionService,
    initialStatus = PlatformPermissionStatus.Unavailable,
    initialUnavailableReason = PlatformPermissionUnavailableReason.RuntimeNotInstalled,
)
