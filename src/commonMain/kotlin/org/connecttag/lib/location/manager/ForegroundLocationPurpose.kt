package org.connecttag.lib.location.manager

/**
 * Product-neutral purposes for user-initiated foreground location access.
 * Each purpose owns its least-privileged accuracy requirement.
 */
enum class ForegroundLocationPurpose(
    val accuracyRequirement: LocationAccuracyRequirement,
) {
    MapRecenter(LocationAccuracyRequirement.Approximate),
    CurrentPosition(LocationAccuracyRequirement.Precise),
    LiveTracking(LocationAccuracyRequirement.Precise),
}
