package org.connecttag.lib.location.model

import org.connecttag.lib.geo.MapPoint

data class UserLocation(
    val point: MapPoint,
    val accuracy: Float = 0f,
    val timestamp: Long,
    val elapsedRealtimeMillis: Long? = null,
    val receivedAtMillis: Long? = null,
    val ageAtReceiptMillis: Long? = null,
    val speedMetersPerSecond: Float? = null,
    /** A risk signal only. Consumers must not treat it as proof or auto-block by itself. */
    val isMock: Boolean = false,
)

sealed interface LocationState {
    object Idle : LocationState
    object Loading : LocationState
    data class Success(val location: UserLocation) : LocationState
    data class Error(val type: LocationError) : LocationState
}

sealed interface LocationError {
    object PermissionDenied : LocationError
    object GpsDisabled : LocationError
    object Unsupported : LocationError
    object Timeout : LocationError
    data class Unknown(val message: String?) : LocationError
}
