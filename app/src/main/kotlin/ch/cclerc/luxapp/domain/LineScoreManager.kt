package ch.cclerc.luxapp.domain

import ch.cclerc.luxapp.data.LineScore
import ch.cclerc.luxapp.data.LineScoreStorage
import ch.cclerc.luxapp.data.LineScoreStorageProtocol
import kotlinx.coroutines.flow.StateFlow

class LineScoreManager internal constructor(
    private val storage: LineScoreStorageProtocol
) {
    companion object {
        val shared: LineScoreManager by lazy { LineScoreManager(LineScoreStorage()) }
    }

    val allScores: StateFlow<List<LineScore>>
        get() = storage.lineScores

    private val lineScores: List<LineScore>
        get() = storage.lineScores.value

    fun addScore(to: String, points: Double = 0.1) {
        val routeShortName = to.trim().uppercase()
        val existing = lineScores.firstOrNull { it.routeShortName == routeShortName }
        if (existing != null) {
            storage.updateLineScore(existing.addingScore(points))
        } else {
            storage.addLineScore(LineScore(routeShortName = routeShortName, initialScore = points))
        }
    }

    fun getScore(routeShortName: String): Double =
        lineScores.firstOrNull { it.routeShortName == routeShortName }?.totalScore ?: 0.0

    fun getSortedRouteNames(routeNames: List<String>): List<String> =
        routeNames.sortedWith(compareByDescending<String> { getScore(it) }.thenBy { it })

    fun setScore(routeShortName: String, score: Double) {
        val existing = lineScores.firstOrNull { it.routeShortName == routeShortName }
        if (existing != null) {
            storage.updateLineScore(existing.copy(totalScore = score))
        } else {
            storage.addLineScore(LineScore(routeShortName = routeShortName, initialScore = score))
        }
    }

    fun removeScore(routeShortName: String) {
        storage.deleteLineScore(routeShortName)
    }
}
