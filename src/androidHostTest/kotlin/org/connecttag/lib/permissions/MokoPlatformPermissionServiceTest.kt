package org.connecttag.lib.permissions

import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.RequestCanceledException
import dev.icerock.moko.permissions.camera.CAMERA
import dev.icerock.moko.permissions.test.PermissionsControllerMock
import dev.icerock.moko.permissions.test.createPermissionControllerMock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MokoPlatformPermissionServiceTest {
    @Test
    fun requestReturnsGrantedWhenMokoAllowsPermission() = runBlocking {
        val service = createMokoPlatformPermissionService(
            createPermissionControllerMock(allow = setOf(Permission.CAMERA)),
        )

        assertEquals(
            PermissionRequestResult(PlatformPermission.CAMERA, PlatformPermissionStatus.Granted),
            service.request(PlatformPermission.CAMERA),
        )
    }

    @Test
    fun requestReturnsDeniedWhenMokoRejectsPermission() = runBlocking {
        val service = createMokoPlatformPermissionService(createPermissionControllerMock())

        assertEquals(
            PermissionRequestResult(PlatformPermission.CAMERA, PlatformPermissionStatus.Denied),
            service.request(PlatformPermission.CAMERA),
        )
    }

    @Test
    fun requestDistinguishesPermanentDenial() = runBlocking {
        val service = createMokoPlatformPermissionService(
            throwingController { permission ->
                DeniedAlwaysException(permission, "permanently denied in test")
            },
        )

        assertEquals(
            PermissionRequestResult(
                PlatformPermission.CAMERA,
                PlatformPermissionStatus.PermanentlyDenied,
            ),
            service.request(PlatformPermission.CAMERA),
        )
    }

    @Test
    fun canceledRequestRemainsUndetermined() = runBlocking {
        val service = createMokoPlatformPermissionService(
            throwingController { permission ->
                RequestCanceledException(permission, "request canceled in test")
            },
        )

        assertEquals(
            PermissionRequestResult(
                PlatformPermission.CAMERA,
                PlatformPermissionStatus.NotDetermined,
            ),
            service.request(PlatformPermission.CAMERA),
        )
    }

    @Test
    fun statusReadsMokoControllerState() = runBlocking {
        val service = createMokoPlatformPermissionService(
            createPermissionControllerMock(granted = setOf(Permission.CAMERA)),
        )

        assertEquals(
            PlatformPermissionStatus.Granted,
            service.status(PlatformPermission.CAMERA),
        )
    }

    @Test
    fun galleryUsesScopedPickerWithoutRuntimeMediaPermission() = runBlocking {
        val service = createMokoPlatformPermissionService(createPermissionControllerMock())

        assertEquals(
            PermissionRequestResult(
                PlatformPermission.GALLERY,
                PlatformPermissionStatus.NotRequired,
            ),
            service.request(PlatformPermission.GALLERY),
        )
        assertEquals(
            PlatformPermissionStatus.NotRequired,
            service.status(PlatformPermission.GALLERY),
        )
    }

    @Test
    fun settingsDispatchReportsOpened() {
        val service = createMokoPlatformPermissionService(
            throwingController { IllegalStateException("unused request path") },
        )

        assertEquals(PlatformSettingsOpenResult.Opened, service.openSettings())
    }
}

private fun throwingController(
    failure: (Permission) -> Exception,
): PermissionsControllerMock = object : PermissionsControllerMock() {
    override suspend fun providePermission(permission: Permission) {
        throw failure(permission)
    }

    override suspend fun isPermissionGranted(permission: Permission): Boolean = false

    override suspend fun getPermissionState(permission: Permission): PermissionState {
        return PermissionState.NotDetermined
    }

    override fun openAppSettings() = Unit
}
