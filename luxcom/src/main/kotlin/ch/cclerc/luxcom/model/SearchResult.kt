package ch.cclerc.luxcom.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val type: LocationType,
    val tokens: List<List<Int>>,
    val name: String,
    val id: String,
    val lat: Double,
    val lon: Double,
    val level: Double? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zip: String? = null,
    val areas: List<Area> = emptyList(),
    val score: Double,
    val modes: List<TransportationMode> = emptyList(),
    val groupedStopIds: List<String> = emptyList(),
    val hasRailNeighbour: Boolean? = null
) {
    val servesRail: Boolean
        get() = modes.any { it.isRail }

    @Serializable
    data class Area(
        val name: String,
        val adminLevel: Int,
        val matched: Boolean,
        val default: Boolean? = null
    )
}
