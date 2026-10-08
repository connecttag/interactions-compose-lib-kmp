package org.connecttag.lib.permissions

import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.camera.CAMERA
import dev.icerock.moko.permissions.location.BACKGROUND_LOCATION
import dev.icerock.moko.permissions.location.COARSE_LOCATION
import dev.icerock.moko.permissions.location.LOCATION
import dev.icerock.moko.permissions.microphone.RECORD_AUDIO
import dev.icerock.moko.permissions.notifications.REMOTE_NOTIFICATION
import kotlin.test.Test
import kotlin.test.assertEquals

class MokoPlatformPermissionMappingTest {
    @Test
    fun mapsEveryPlatformPermissionToMoko() {
        val expected = mapOf(
            PlatformPermission.CAMERA to Permission.CAMERA,
            PlatformPermission.LOCATION to Permission.LOCATION,
            PlatformPermission.COARSE_LOCATION to Permission.COARSE_LOCATION,
            PlatformPermission.BACKGROUND_LOCATION to Permission.BACKGROUND_LOCATION,
            PlatformPermission.MICROPHONE to Permission.RECORD_AUDIO,
            PlatformPermission.REMOTE_NOTIFICATIONS to Permission.REMOTE_NOTIFICATION,
        )

        assertEquals(
            expected,
            PlatformPermission.entries
                .mapNotNull { permission ->
                    permission.toMokoPermissionOrNull()?.let { permission to it }
                }
                .toMap(),
        )
        assertEquals(null, PlatformPermission.GALLERY.toMokoPermissionOrNull())
    }

    @Test
    fun mapsMokoPermissionStatesWithoutTreatingUnknownStateAsGranted() {
        val expected = mapOf(
            PermissionState.Granted to PlatformPermissionStatus.Granted,
            PermissionState.Denied to PlatformPermissionStatus.Denied,
            PermissionState.DeniedAlways to PlatformPermissionStatus.PermanentlyDenied,
            PermissionState.NotDetermined to PlatformPermissionStatus.NotDetermined,
            PermissionState.NotGranted to PlatformPermissionStatus.NotDetermined,
        )

        assertEquals(
            expected,
            PermissionState.entries.associateWith(PermissionState::toPlatformPermissionStatus),
        )
    }
}
