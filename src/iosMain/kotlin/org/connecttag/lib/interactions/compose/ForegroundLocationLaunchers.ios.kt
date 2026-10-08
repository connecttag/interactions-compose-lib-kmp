@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.connecttag.lib.interactions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.connecttag.lib.location.manager.ForegroundLocationPurpose
import org.connecttag.lib.location.manager.LocationAccuracyRequirement
import org.connecttag.lib.permissions.PermissionRequestResult
import org.connecttag.lib.permissions.PlatformPermissionStatus
import org.connecttag.lib.permissions.PlatformPermissionUnavailableReason
import org.connecttag.lib.permissions.PlatformSettingsOpenResult
import org.connecttag.lib.permissions.PreciseLocationPurpose
import org.connecttag.lib.permissions.PreciseLocationUpgradeStatus
import org.connecttag.lib.permissions.createIosPreciseLocationUpgradeRequester
import org.connecttag.lib.permissions.isGrantedOrNotRequired
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification

@Composable
actual fun rememberForegroundLocationPermissionLauncher(
    enabled: Boolean,
    onResult: (PermissionRequestResult) -> Unit,
): LocationPermissionLauncher {
    val currentOnResult = rememberUpdatedState(onResult)
    val coroutineScope = rememberCoroutineScope()
    val permissionStore = rememberPlatformPermissionStore()
    val preciseRequester = remember(enabled) {
        createIosPreciseLocationUpgradeRequester(enabled)
    }
    var pendingPrecisePurpose by remember {
        mutableStateOf<PreciseLocationPurpose?>(null)
    }
    val permissionLauncher = rememberPlatformPermissionRequestLauncher { result ->
        val precisePurpose = pendingPrecisePurpose
        if (precisePurpose != null && result.status.isGrantedOrNotRequired()) {
            coroutineScope.launch {
                val upgradeStatus = preciseRequester.requestTemporaryFullAccuracy(
                    precisePurpose,
                )
                pendingPrecisePurpose = null
                val upgradeResult = PermissionRequestResult(
                    permission = result.permission,
                    status = upgradeStatus.toPlatformPermissionStatus(),
                    unavailableReason = upgradeStatus.toUnavailableReason(),
                )
                permissionStore.updateResult(upgradeResult)
                currentOnResult.value(upgradeResult)
            }
        } else {
            pendingPrecisePurpose = null
            currentOnResult.value(result)
        }
    }

    return remember(enabled, permissionLauncher) {
        object : LocationPermissionLauncher {
            override fun launch(requirement: LocationAccuracyRequirement) {
                request(
                    requirement = requirement,
                    precisePurpose = PreciseLocationPurpose.CurrentPosition,
                )
            }

            override fun launch(purpose: ForegroundLocationPurpose) {
                request(
                    requirement = purpose.accuracyRequirement,
                    precisePurpose = purpose.toPreciseLocationPurpose(),
                )
            }

            private fun request(
                requirement: LocationAccuracyRequirement,
                precisePurpose: PreciseLocationPurpose,
            ) {
                if (enabled) {
                    pendingPrecisePurpose = precisePurpose.takeIf {
                        requirement == LocationAccuracyRequirement.Precise
                    }
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
}

private fun ForegroundLocationPurpose.toPreciseLocationPurpose(): PreciseLocationPurpose {
    return when (this) {
        ForegroundLocationPurpose.MapRecenter -> PreciseLocationPurpose.CurrentPosition
        ForegroundLocationPurpose.CurrentPosition -> PreciseLocationPurpose.CurrentPosition
        ForegroundLocationPurpose.LiveTracking -> PreciseLocationPurpose.LiveTracking
    }
}

private fun PreciseLocationUpgradeStatus.toPlatformPermissionStatus(): PlatformPermissionStatus {
    return when (this) {
        PreciseLocationUpgradeStatus.FullAccuracy -> PlatformPermissionStatus.Granted
        PreciseLocationUpgradeStatus.ReducedAccuracy -> PlatformPermissionStatus.Denied
        PreciseLocationUpgradeStatus.Unavailable -> PlatformPermissionStatus.Unavailable
        PreciseLocationUpgradeStatus.TimedOut -> PlatformPermissionStatus.Unavailable
    }
}

private fun PreciseLocationUpgradeStatus.toUnavailableReason(): PlatformPermissionUnavailableReason? {
    return when (this) {
        PreciseLocationUpgradeStatus.FullAccuracy,
        PreciseLocationUpgradeStatus.ReducedAccuracy -> null
        PreciseLocationUpgradeStatus.Unavailable -> {
            PlatformPermissionUnavailableReason.ProviderFailure
        }
        PreciseLocationUpgradeStatus.TimedOut -> {
            PlatformPermissionUnavailableReason.TimedOut
        }
    }
}

@Composable
internal actual fun rememberForegroundLocationSettingsLauncher(
    onResult: (LocationSettingsResolutionResult) -> Unit,
): ForegroundLocationSettingsLauncher {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        ForegroundLocationSettingsLauncher {
            currentOnResult.value(LocationSettingsResolutionResult.Unavailable)
        }
    }
}

@Composable
internal actual fun ObserveLocationCapabilityChanges(onChanged: () -> Unit) = Unit

@Composable
actual fun rememberPlatformSettingsLauncher(
    onReturned: (PlatformSettingsDestination) -> Unit,
): PlatformSettingsLauncher {
    val service = LocalPlatformPermissionService.current
    val currentOnReturned = rememberUpdatedState(onReturned)
    var pendingDestination by remember {
        mutableStateOf<PlatformSettingsDestination?>(null)
    }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            pendingDestination?.let { destination ->
                pendingDestination = null
                currentOnReturned.value(destination)
            }
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    return remember(service) {
        PlatformSettingsLauncher { destination ->
            pendingDestination = destination
            when (service.openSettings()) {
                PlatformSettingsOpenResult.Opened -> Unit
                PlatformSettingsOpenResult.Unsupported,
                PlatformSettingsOpenResult.Failed -> {
                    pendingDestination = null
                    currentOnReturned.value(destination)
                }
            }
        }
    }
}
