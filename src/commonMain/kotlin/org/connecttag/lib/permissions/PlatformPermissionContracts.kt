package org.connecttag.lib.permissions

enum class PlatformPermission {
    CAMERA,
    GALLERY,
    LOCATION,
    COARSE_LOCATION,
    BACKGROUND_LOCATION,
    MICROPHONE,
    REMOTE_NOTIFICATIONS,
}

enum class PlatformPermissionStatus {
    Granted,
    Denied,
    PermanentlyDenied,
    NotDetermined,
    NotRequired,
    Unavailable,
}

enum class PlatformPermissionUnavailableReason {
    RuntimeNotInstalled,
    UnsupportedPlatform,
    ProviderFailure,
    TimedOut,
}

enum class PlatformSettingsOpenResult {
    Opened,
    Unsupported,
    Failed,
}

fun PlatformPermissionStatus.isGrantedOrNotRequired(): Boolean {
    return this == PlatformPermissionStatus.Granted ||
        this == PlatformPermissionStatus.NotRequired
}

data class PermissionRequestResult(
    val permission: PlatformPermission,
    val status: PlatformPermissionStatus,
    val unavailableReason: PlatformPermissionUnavailableReason? = null,
) {
    init {
        require(
            unavailableReason == null || status == PlatformPermissionStatus.Unavailable,
        ) {
            "An unavailable reason requires PlatformPermissionStatus.Unavailable."
        }
    }
}

interface PlatformPermissionService {
    suspend fun status(permission: PlatformPermission): PlatformPermissionStatus

    suspend fun current(permission: PlatformPermission): PermissionRequestResult {
        return PermissionRequestResult(permission, status(permission))
    }

    suspend fun isGranted(permission: PlatformPermission): Boolean {
        return status(permission).isGrantedOrNotRequired()
    }

    suspend fun request(permission: PlatformPermission): PermissionRequestResult

    fun openSettings(): PlatformSettingsOpenResult
}

/** UI-lifecycle adapter for platforms whose permission request is launcher based. */
fun interface PlatformPermissionRequestLauncher {
    fun request(permission: PlatformPermission)
}
