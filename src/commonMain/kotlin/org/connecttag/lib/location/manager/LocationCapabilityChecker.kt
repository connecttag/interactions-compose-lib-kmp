package org.connecttag.lib.location.manager

/**
 * Portable boundary for deciding whether a location request may be started.
 * Platform implementations own permissions and service-state inspection.
 */
interface LocationCapabilityChecker {
    fun grantedLocationAccuracy(): GrantedLocationAccuracy

    fun isLocationServiceEnabled(): Boolean

    fun hasLocationPermission(
        requirement: LocationAccuracyRequirement = LocationAccuracyRequirement.Approximate,
    ): Boolean = grantedLocationAccuracy().satisfies(requirement)
}

enum class LocationAccuracyRequirement {
    Approximate,
    Precise,
}

enum class GrantedLocationAccuracy {
    None,
    Approximate,
    Precise,
    ;

    fun satisfies(requirement: LocationAccuracyRequirement): Boolean {
        return when (requirement) {
            LocationAccuracyRequirement.Approximate -> this != None
            LocationAccuracyRequirement.Precise -> this == Precise
        }
    }
}
