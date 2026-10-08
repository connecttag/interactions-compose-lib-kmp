package org.connecttag.lib.permissions

/**
 * Stable, application-neutral purpose keys for Apple's temporary full-accuracy
 * request. The matching keys must exist in
 * `NSLocationTemporaryUsageDescriptionDictionary` in the iOS application.
 */
enum class PreciseLocationPurpose(
    internal val infoPlistKey: String,
) {
    CurrentPosition("LocationCurrentPosition"),
    LiveTracking("LocationLiveTracking"),
}

enum class PreciseLocationUpgradeStatus {
    FullAccuracy,
    ReducedAccuracy,
    Unavailable,
    TimedOut,
}

/** Requests only the iOS temporary accuracy upgrade; base location permission remains Moko-owned. */
fun interface PreciseLocationUpgradeRequester {
    suspend fun requestTemporaryFullAccuracy(
        purpose: PreciseLocationPurpose,
    ): PreciseLocationUpgradeStatus
}
