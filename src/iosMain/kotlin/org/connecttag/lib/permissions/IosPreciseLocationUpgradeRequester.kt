@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.connecttag.lib.permissions

import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLLocationManager
import platform.Foundation.NSProcessInfo
import kotlin.coroutines.resume

fun createIosPreciseLocationUpgradeRequester(
    enabled: Boolean = true,
    timeoutMillis: Long = DEFAULT_PRECISE_LOCATION_UPGRADE_TIMEOUT_MILLIS,
): PreciseLocationUpgradeRequester = IosPreciseLocationUpgradeRequester(
    locationManager = CLLocationManager(),
    enabled = enabled,
    timeoutMillis = timeoutMillis,
)

private class IosPreciseLocationUpgradeRequester(
    private val locationManager: CLLocationManager,
    private val enabled: Boolean,
    private val timeoutMillis: Long,
) : PreciseLocationUpgradeRequester {
    override suspend fun requestTemporaryFullAccuracy(
        purpose: PreciseLocationPurpose,
    ): PreciseLocationUpgradeStatus {
        if (!enabled) return PreciseLocationUpgradeStatus.Unavailable
        if (!supportsReducedAccuracy()) return PreciseLocationUpgradeStatus.FullAccuracy
        if (hasFullAccuracy()) return PreciseLocationUpgradeStatus.FullAccuracy

        return withTimeoutOrNull(timeoutMillis.coerceAtLeast(1L)) {
            suspendCancellableCoroutine { continuation ->
                locationManager.requestTemporaryFullAccuracyAuthorizationWithPurposeKey(
                    purpose.infoPlistKey,
                ) { error ->
                    if (continuation.isActive) {
                        continuation.resume(
                            when {
                                hasFullAccuracy() -> PreciseLocationUpgradeStatus.FullAccuracy
                                error == null -> PreciseLocationUpgradeStatus.ReducedAccuracy
                                else -> PreciseLocationUpgradeStatus.Unavailable
                            },
                        )
                    }
                }
            }
        } ?: PreciseLocationUpgradeStatus.TimedOut
    }

    private fun supportsReducedAccuracy(): Boolean {
        val majorVersion = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
            majorVersion
        }
        return majorVersion >= IOS_REDUCED_ACCURACY_VERSION
    }

    private fun hasFullAccuracy(): Boolean {
        return locationManager.accuracyAuthorization == CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy
    }
}

private const val IOS_REDUCED_ACCURACY_VERSION = 14L
private const val DEFAULT_PRECISE_LOCATION_UPGRADE_TIMEOUT_MILLIS = 30_000L
