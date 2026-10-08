package org.connecttag.lib.interactions.compose

import org.connecttag.lib.location.manager.ForegroundLocationPurpose
import org.connecttag.lib.location.manager.GrantedLocationAccuracy
import org.connecttag.lib.location.manager.LocationAccuracyRequirement
import org.connecttag.lib.location.manager.LocationReadiness
import org.connecttag.lib.location.model.LocationError
import org.connecttag.lib.permissions.PermissionRequestResult
import org.connecttag.lib.permissions.PlatformPermission
import org.connecttag.lib.permissions.PlatformPermissionStatus
import org.connecttag.lib.permissions.PlatformPermissionUnavailableReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForegroundLocationAccessStateTest {
    @Test
    fun accuracyMapsToLeastPrivilegedMokoPermission() {
        assertEquals(
            PlatformPermission.COARSE_LOCATION,
            LocationAccuracyRequirement.Approximate.toPlatformPermission(),
        )
        assertEquals(
            PlatformPermission.LOCATION,
            LocationAccuracyRequirement.Precise.toPlatformPermission(),
        )
    }

    @Test
    fun purposeAwareLaunchPreservesTheExistingRequirementContract() {
        var launchedRequirement: LocationAccuracyRequirement? = null
        val launcher = LocationPermissionLauncher { requirement ->
            launchedRequirement = requirement
        }

        launcher.launch(ForegroundLocationPurpose.LiveTracking)

        assertEquals(LocationAccuracyRequirement.Precise, launchedRequirement)
    }

    @Test
    fun requestWaitsForPermissionThenForLocationService() {
        val state = ForegroundLocationAccessState()
        var executionCount = 0

        state.request(
            purpose = ForegroundLocationPurpose.MapRecenter,
            readiness = permissionRequired(LocationAccuracyRequirement.Approximate),
        ) { executionCount += 1 }

        assertEquals(ForegroundLocationPrompt.PermissionRequired, state.prompt)
        assertEquals(0, executionCount)

        state.beginPermissionRequest()
        state.handlePermissionResult(
            status = PlatformPermissionStatus.Granted,
            readiness = LocationReadiness.LocationServiceDisabled,
        )

        assertEquals(ForegroundLocationPrompt.LocationServiceDisabled, state.prompt)
        assertEquals(0, executionCount)

        state.beginLocationResolution()
        state.handleLocationSettingsResult(
            result = LocationSettingsResolutionResult.Changed,
            readiness = LocationReadiness.Ready,
        )

        assertEquals(null, state.prompt)
        assertEquals(1, executionCount)
        assertEquals(ForegroundLocationProgress.Executing, state.progress)
    }

    @Test
    fun approximateGrantShowsPreciseUpgradeForTracking() {
        val state = ForegroundLocationAccessState()

        state.request(
            purpose = ForegroundLocationPurpose.LiveTracking,
            readiness = LocationReadiness.PermissionRequired(
                requirement = LocationAccuracyRequirement.Precise,
                grantedAccuracy = GrantedLocationAccuracy.Approximate,
            ),
        ) {}

        assertEquals(ForegroundLocationPrompt.PrecisePermissionRequired, state.prompt)
    }

    @Test
    fun providerErrorRetainsActionForOneRetry() {
        val state = ForegroundLocationAccessState()
        var executionCount = 0

        state.request(ForegroundLocationPurpose.CurrentPosition, LocationReadiness.Ready) {
            executionCount += 1
        }
        state.handle(LocationError.GpsDisabled)
        state.retry(LocationReadiness.Ready)

        assertEquals(2, executionCount)
    }

    @Test
    fun unsupportedLocationProviderDoesNotOfferGpsSettings() {
        val events = mutableListOf<ForegroundLocationDiagnosticEvent>()
        val state = ForegroundLocationAccessState(events::add)

        state.request(ForegroundLocationPurpose.MapRecenter, LocationReadiness.Ready) {}
        state.handle(LocationError.Unsupported)

        assertEquals(ForegroundLocationPrompt.LocationUnavailable, state.prompt)
        assertEquals(
            ForegroundLocationDiagnosticStage.ActionFailedUnsupported,
            events.last().stage,
        )
    }

    @Test
    fun unavailablePermissionRuntimeDoesNotLookLikeUserDenial() {
        val events = mutableListOf<ForegroundLocationDiagnosticEvent>()
        val state = ForegroundLocationAccessState(events::add)

        state.request(
            ForegroundLocationPurpose.MapRecenter,
            permissionRequired(LocationAccuracyRequirement.Approximate),
        ) {}
        state.beginPermissionRequest()
        state.handlePermissionResult(
            result = PermissionRequestResult(
                permission = PlatformPermission.COARSE_LOCATION,
                status = PlatformPermissionStatus.Unavailable,
                unavailableReason = PlatformPermissionUnavailableReason.RuntimeNotInstalled,
            ),
            readiness = permissionRequired(LocationAccuracyRequirement.Approximate),
        )

        assertEquals(ForegroundLocationPrompt.LocationUnavailable, state.prompt)
        assertEquals(ForegroundLocationProgress.Idle, state.progress)
        assertEquals(
            ForegroundLocationDiagnosticStage.PermissionUnavailable,
            events.last().stage,
        )
        assertEquals(
            PlatformPermissionUnavailableReason.RuntimeNotInstalled,
            events.last().unavailableReason,
        )
    }

    @Test
    fun concurrentRequestDoesNotReplacePendingAction() {
        val state = ForegroundLocationAccessState()
        var firstExecutionCount = 0
        var secondExecutionCount = 0

        val firstAccepted = state.request(
            ForegroundLocationPurpose.MapRecenter,
            permissionRequired(LocationAccuracyRequirement.Approximate),
        ) { firstExecutionCount += 1 }
        val secondAccepted = state.request(
            ForegroundLocationPurpose.LiveTracking,
            LocationReadiness.Ready,
        ) { secondExecutionCount += 1 }
        state.handlePermissionResult(PlatformPermissionStatus.Granted, LocationReadiness.Ready)

        assertTrue(firstAccepted)
        assertFalse(secondAccepted)
        assertEquals(1, firstExecutionCount)
        assertEquals(0, secondExecutionCount)
    }

    @Test
    fun returningWithoutAppPermissionKeepsSettingsRecovery() {
        val state = ForegroundLocationAccessState()
        val missing = permissionRequired(LocationAccuracyRequirement.Precise)

        state.request(ForegroundLocationPurpose.CurrentPosition, missing) {}
        state.handlePermissionResult(
            status = PlatformPermissionStatus.PermanentlyDenied,
            readiness = missing,
        )
        state.beginSettingsResolution()
        state.resumeApplicationSettings(missing)

        assertEquals(ForegroundLocationPrompt.PermissionPermanentlyDenied, state.prompt)
    }

    @Test
    fun runtimeCapabilityChangeResumesPendingAction() {
        val state = ForegroundLocationAccessState()
        var executionCount = 0

        state.request(
            ForegroundLocationPurpose.MapRecenter,
            LocationReadiness.LocationServiceDisabled,
        ) { executionCount += 1 }
        state.reconcile(LocationReadiness.Ready)

        assertEquals(1, executionCount)
        assertEquals(ForegroundLocationProgress.Executing, state.progress)
    }

    @Test
    fun cancelInvokesRegisteredCancellationAndClearsRequest() {
        val events = mutableListOf<ForegroundLocationDiagnosticEvent>()
        val state = ForegroundLocationAccessState(events::add)
        var cancelCount = 0

        state.request(ForegroundLocationPurpose.CurrentPosition, LocationReadiness.Ready) {}
        state.registerCancellation { cancelCount += 1 }
        state.cancel()

        assertEquals(1, cancelCount)
        assertEquals(ForegroundLocationProgress.Idle, state.progress)
        assertEquals(null, state.pendingPurpose)
        assertEquals(
            ForegroundLocationDiagnosticStage.Cancelled,
            events.last().stage,
        )
    }
}

private fun permissionRequired(
    requirement: LocationAccuracyRequirement,
): LocationReadiness.PermissionRequired {
    return LocationReadiness.PermissionRequired(
        requirement = requirement,
        grantedAccuracy = GrantedLocationAccuracy.None,
    )
}
