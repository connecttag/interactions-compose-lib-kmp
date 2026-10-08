package org.connecttag.lib.permissions

import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.RequestCanceledException
import dev.icerock.moko.permissions.camera.CAMERA
import dev.icerock.moko.permissions.location.BACKGROUND_LOCATION
import dev.icerock.moko.permissions.location.COARSE_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
import dev.icerock.moko.permissions.microphone.RECORD_AUDIO
import dev.icerock.moko.permissions.notifications.REMOTE_NOTIFICATION
import kotlinx.coroutines.CancellationException

/** Private Android/iOS adapter for the portable permission contract. */
internal fun createMokoPlatformPermissionService(
    permissionsController: PermissionsController,
    promptQueueWaitTimeoutMillis: Long = DEFAULT_PERMISSION_PROMPT_QUEUE_WAIT_TIMEOUT_MILLIS,
): PlatformPermissionService = createCoordinatedPlatformPermissionService(
    delegate = MokoPlatformPermissionService(permissionsController),
    promptQueueWaitTimeoutMillis = promptQueueWaitTimeoutMillis,
)

internal class MokoPlatformPermissionService(
    private val permissionsController: PermissionsController,
) : PlatformPermissionService {
    override suspend fun status(permission: PlatformPermission): PlatformPermissionStatus {
        return current(permission).status
    }

    override suspend fun current(permission: PlatformPermission): PermissionRequestResult {
        val mokoPermission = permission.toMokoPermissionOrNull()
            ?: return PermissionRequestResult(permission, PlatformPermissionStatus.NotRequired)
        return try {
            PermissionRequestResult(
                permission = permission,
                status = permissionsController
                    .getPermissionState(mokoPermission)
                    .toPlatformPermissionStatus(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            PermissionRequestResult(
                permission = permission,
                status = PlatformPermissionStatus.Unavailable,
                unavailableReason = PlatformPermissionUnavailableReason.ProviderFailure,
            )
        }
    }

    override suspend fun request(permission: PlatformPermission): PermissionRequestResult {
        val mokoPermission = permission.toMokoPermissionOrNull()
            ?: return PermissionRequestResult(
                permission,
                PlatformPermissionStatus.NotRequired,
            )
        val status = try {
            permissionsController.providePermission(mokoPermission)
            PlatformPermissionStatus.Granted
        } catch (_: DeniedAlwaysException) {
            PlatformPermissionStatus.PermanentlyDenied
        } catch (_: DeniedException) {
            PlatformPermissionStatus.Denied
        } catch (_: RequestCanceledException) {
            PlatformPermissionStatus.NotDetermined
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            PlatformPermissionStatus.Unavailable
        }

        return PermissionRequestResult(
            permission = permission,
            status = status,
            unavailableReason = if (status == PlatformPermissionStatus.Unavailable) {
                PlatformPermissionUnavailableReason.ProviderFailure
            } else {
                null
            },
        )
    }

    override fun openSettings(): PlatformSettingsOpenResult {
        return try {
            permissionsController.openAppSettings()
            PlatformSettingsOpenResult.Opened
        } catch (_: Throwable) {
            PlatformSettingsOpenResult.Failed
        }
    }
}

internal fun PlatformPermission.toMokoPermissionOrNull(): Permission? {
    return when (this) {
        PlatformPermission.CAMERA -> Permission.CAMERA
        PlatformPermission.GALLERY -> null
        PlatformPermission.LOCATION -> Permission.LOCATION
        PlatformPermission.COARSE_LOCATION -> Permission.COARSE_LOCATION
        PlatformPermission.BACKGROUND_LOCATION -> Permission.BACKGROUND_LOCATION
        PlatformPermission.MICROPHONE -> Permission.RECORD_AUDIO
        PlatformPermission.REMOTE_NOTIFICATIONS -> Permission.REMOTE_NOTIFICATION
    }
}

internal fun PermissionState.toPlatformPermissionStatus(): PlatformPermissionStatus {
    return when (this) {
        PermissionState.Granted -> PlatformPermissionStatus.Granted
        PermissionState.Denied -> PlatformPermissionStatus.Denied
        PermissionState.DeniedAlways -> PlatformPermissionStatus.PermanentlyDenied
        PermissionState.NotDetermined,
        PermissionState.NotGranted -> PlatformPermissionStatus.NotDetermined
    }
}
