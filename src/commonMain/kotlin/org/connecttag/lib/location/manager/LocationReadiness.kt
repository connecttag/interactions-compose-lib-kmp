package org.connecttag.lib.location.manager

/**
 * Canonical, portable result for the prerequisites of a foreground location request.
 * Permission and service-state inspection remain platform implementation details.
 */
sealed interface LocationReadiness {
    data object Ready : LocationReadiness
    data class PermissionRequired(
        val requirement: LocationAccuracyRequirement,
        val grantedAccuracy: GrantedLocationAccuracy,
    ) : LocationReadiness
    data object LocationServiceDisabled : LocationReadiness
}

fun LocationCapabilityChecker.resolveLocationReadiness(
    requirement: LocationAccuracyRequirement = LocationAccuracyRequirement.Approximate,
): LocationReadiness {
    val grantedAccuracy = grantedLocationAccuracy()
    return when {
        !grantedAccuracy.satisfies(requirement) -> LocationReadiness.PermissionRequired(
            requirement = requirement,
            grantedAccuracy = grantedAccuracy,
        )
        !isLocationServiceEnabled() -> LocationReadiness.LocationServiceDisabled
        else -> LocationReadiness.Ready
    }
}
