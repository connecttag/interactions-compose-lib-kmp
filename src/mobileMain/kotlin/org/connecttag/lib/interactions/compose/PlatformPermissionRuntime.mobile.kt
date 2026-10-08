package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import org.connecttag.lib.permissions.PlatformPermissionRuntimeConfig
import org.connecttag.lib.permissions.PlatformPermissionService
import org.connecttag.lib.permissions.ReactivePlatformPermissionStore
import org.connecttag.lib.permissions.createMokoPlatformPermissionService
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import kotlinx.coroutines.launch

@Composable
actual fun ProvidePlatformPermissionRuntime(
    config: PlatformPermissionRuntimeConfig,
    onServiceAvailable: (PlatformPermissionService) -> Unit,
    content: @Composable () -> Unit,
) {
    val controllerFactory = rememberPermissionsControllerFactory()
    val controller = remember(controllerFactory) {
        controllerFactory.createPermissionsController()
    }
    BindEffect(controller)

    val service = remember(controller, config.promptQueueWaitTimeoutMillis) {
        createMokoPlatformPermissionService(
            permissionsController = controller,
            promptQueueWaitTimeoutMillis = config.promptQueueWaitTimeoutMillis,
        )
    }
    val store = remember(service, config.trackedPermissions) {
        ReactivePlatformPermissionStore(
            service = service,
            permissions = config.trackedPermissions,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val currentOnServiceAvailable = rememberUpdatedState(onServiceAvailable)
    LaunchedEffect(service, store) {
        currentOnServiceAvailable.value(service)
        store.refreshAll()
    }
    ObservePlatformPermissionRuntimeResume {
        coroutineScope.launch {
            store.refreshAll()
        }
    }

    CompositionLocalProvider(
        LocalPlatformPermissionService provides service,
        LocalPlatformPermissionStore provides store,
        LocalPlatformPermissionRuntimeConfig provides config,
        content = content,
    )
}
