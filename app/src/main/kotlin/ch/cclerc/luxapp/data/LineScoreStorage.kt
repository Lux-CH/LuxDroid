package ch.cclerc.luxapp.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class LineScore(
    val id: String = UUID.randomUUID().toString(),
    val routeShortName: String,
    val totalScore: Double,
    val usageCount: Int
) {
    constructor(routeShortName: String, initialScore: Double = 1.0) : this(
        id = UUID.randomUUID().toString(),
        routeShortName = routeShortName,
        totalScore = initialScore,
        usageCount = 1
    )

    fun addingScore(points: Double = 0.1): LineScore =
        copy(totalScore = totalScore + points, usageCount = usageCount + 1)
}

interface LineScoreStorageProtocol {
    val lineScores: StateFlow<List<LineScore>>

    fun loadLineScores(): List<LineScore>
    fun addLineScore(score: LineScore)
    fun updateLineScore(score: LineScore)
    fun deleteLineScore(routeShortName: String)
    fun saveLineScores(scores: List<LineScore>)
}

class LineScoreStorage(
    private val file: File = File(AppDirectories.base(), "lineScores.json")
) : LineScoreStorageProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serializer = ListSerializer(LineScore.serializer())

    private val _lineScores = MutableStateFlow(loadLineScoresFromDisk())
    override val lineScores: StateFlow<List<LineScore>> = _lineScores.asStateFlow()

    private fun loadLineScoresFromDisk(): List<LineScore> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    private fun persistToDisk(scores: List<LineScore>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(serializer, scores))
            if (!temporary.renameTo(file)) {
                file.writeText(json.encodeToString(serializer, scores))
                temporary.delete()
            }
        }
    }

    override fun loadLineScores(): List<LineScore> = _lineScores.value

    override fun saveLineScores(scores: List<LineScore>) {
        _lineScores.value = scores
        persistToDisk(scores)
    }

    override fun addLineScore(score: LineScore) {
        saveLineScores(_lineScores.value + score)
    }

    override fun updateLineScore(score: LineScore) {
        val current = _lineScores.value
        val index = current.indexOfFirst { it.routeShortName == score.routeShortName }
        if (index < 0) return
        val updated = current.toMutableList()
        updated[index] = score
        saveLineScores(updated)
    }

    override fun deleteLineScore(routeShortName: String) {
        saveLineScores(_lineScores.value.filter { it.routeShortName != routeShortName })
    }
}
