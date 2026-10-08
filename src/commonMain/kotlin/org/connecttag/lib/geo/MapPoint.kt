package org.connecttag.lib.geo

import kotlinx.serialization.Serializable

@Serializable
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
)
