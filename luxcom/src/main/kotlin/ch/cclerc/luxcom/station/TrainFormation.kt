package ch.cclerc.luxcom.station

import kotlinx.serialization.Serializable

@Serializable
data class TrainFormation(
    val train: String,
    val track: String? = null,
    val sectors: Sectors,
    val coaches: List<Coach>
) {
    @Serializable
    data class Sectors(
        val first: List<String> = emptyList(),
        val second: List<String> = emptyList(),
        val restaurant: List<String> = emptyList(),
        val bike: List<String> = emptyList(),
        val wheelchair: List<String> = emptyList(),
        val family: List<String> = emptyList(),
        val business: List<String> = emptyList()
    )

    @Serializable
    data class Coach(
        val s: String? = null,
        val t: String,
        val n: String? = null,
        val o: List<String> = emptyList(),
        val closed: Boolean = false
    ) {
        val isFirstClass: Boolean get() = t in setOf("1", "12", "W1")
        val isRestaurant: Boolean get() = t in setOf("WR", "W1", "W2")
        val isLocomotive: Boolean get() = t == "LK" || t == "D"
    }

    val coveredSectors: Set<String>
        get() = coaches.filter { !it.isLocomotive }.mapNotNull { it.s }.toSet()

    companion object {
        fun sectorText(sectors: List<String>): String {
            val letters = sectors.mapNotNull { it.firstOrNull()?.code }.sorted()
            val runs = mutableListOf<IntArray>()
            for (letter in letters) {
                val last = runs.lastOrNull()
                if (last != null && letter == last[1] + 1) {
                    last[1] = letter
                } else {
                    runs.add(intArrayOf(letter, letter))
                }
            }
            return runs.joinToString(", ") { run ->
                val from = run[0].toChar().toString()
                val to = run[1].toChar().toString()
                if (run[0] == run[1]) from else "$from–$to"
            }
        }
    }
}
