package ch.cclerc.luxapp.domain.intelligence

import ch.cclerc.luxapp.data.Settings
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class IntelligenceProfile(
    val weather: Weather = Weather.WALK_LESS,
    val directness: Directness = Directness.FIVE,
    val walking: Walking = Walking.NEUTRAL,
    val crowd: Crowd = Crowd.AVOID,
    val margin: Margin = Margin.COMFORTABLE,
    val usesHabits: Boolean = true,
    val isConfigured: Boolean = false
) {
    enum class Weather { INDIFFERENT, WALK_LESS, AVOID_WALKING }

    enum class Directness(val minutes: Int) { NEVER(2), FIVE(5), TEN(10), FIFTEEN(15) }

    enum class Walking { ENJOYS, NEUTRAL, MINIMAL }

    enum class Crowd { INDIFFERENT, AVOID, AVOID_STRONGLY }

    enum class Margin(val minutes: Int) { TIGHT(0), COMFORTABLE(3), GENEROUS(6) }
}

@Serializable
data class IntelligenceLearning(
    val isEnabled: Boolean = true,
    val walk: Double = 0.0,
    val weather: Double = 0.0,
    val transfer: Double = 0.0,
    val margin: Double = 0.0,
    val crowd: Double = 0.0,
    val observations: Int = 0,
    val updatedAt: Long? = null
) {
    enum class Key(val lower: Double, val upper: Double) {
        WALK(-0.3, 0.7),
        WEATHER(-0.6, 1.2),
        TRANSFER(-1.5, 8.0),
        MARGIN(-2.0, 3.0),
        CROWD(-2.0, 3.0)
    }

    fun nudged(key: Key, delta: Double): IntelligenceLearning {
        fun clamp(value: Double) = min(key.upper, max(key.lower, value + delta))
        return when (key) {
            Key.WALK -> copy(walk = clamp(walk))
            Key.WEATHER -> copy(weather = clamp(weather))
            Key.TRANSFER -> copy(transfer = clamp(transfer))
            Key.MARGIN -> copy(margin = clamp(margin))
            Key.CROWD -> copy(crowd = clamp(crowd))
        }
    }

    fun erased(): IntelligenceLearning = IntelligenceLearning(isEnabled = isEnabled)
}

object IntelligenceStore {
    private const val PROFILE_KEY = "intelligenceProfile"
    private const val LEARNING_KEY = "intelligenceLearning"
    private const val ALERTS_KEY = "intelligenceDepartureAlerts"
    private const val ROUTE_PRESET_KEY = "routePreset"

    private val json = Json { ignoreUnknownKeys = true }

    private val _profile by lazy { MutableStateFlow(load(PROFILE_KEY, IntelligenceProfile.serializer()) ?: IntelligenceProfile()) }
    val profileFlow: StateFlow<IntelligenceProfile> get() = _profile.asStateFlow()
    var profile: IntelligenceProfile
        get() = _profile.value
        set(value) {
            _profile.value = value
            persist(PROFILE_KEY, IntelligenceProfile.serializer(), value)
        }

    private val _learning by lazy { MutableStateFlow(load(LEARNING_KEY, IntelligenceLearning.serializer()) ?: IntelligenceLearning()) }
    val learningFlow: StateFlow<IntelligenceLearning> get() = _learning.asStateFlow()
    var learning: IntelligenceLearning
        get() = _learning.value
        set(value) {
            _learning.value = value
            persist(LEARNING_KEY, IntelligenceLearning.serializer(), value)
        }

    private val _departureAlerts by lazy { MutableStateFlow(Settings.prefs.getBoolean(ALERTS_KEY, false)) }
    val departureAlertsFlow: StateFlow<Boolean> get() = _departureAlerts.asStateFlow()
    var departureAlerts: Boolean
        get() = _departureAlerts.value
        set(value) {
            _departureAlerts.value = value
            Settings.prefs.edit().putBoolean(ALERTS_KEY, value).apply()
        }

    val isIntelligentMode: Boolean
        get() = (Settings.prefs.getString(ROUTE_PRESET_KEY, null) ?: "INTELLIGENT") == "INTELLIGENT"

    fun reset() {
        profile = IntelligenceProfile()
    }

    private fun <T> load(key: String, serializer: KSerializer<T>): T? =
        Settings.prefs.getString(key, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> persist(key: String, serializer: KSerializer<T>, value: T) {
        Settings.prefs.edit().putString(key, json.encodeToString(serializer, value)).apply()
    }
}

class IntelligenceQuestion(
    val id: String,
    val label: String,
    val symbol: String,
    val title: String,
    val subtitle: String,
    val options: List<Option>
) {
    class Option(
        val id: String,
        val symbol: String,
        val title: String,
        val detail: String,
        val isSelected: (IntelligenceProfile) -> Boolean,
        val apply: (IntelligenceProfile) -> IntelligenceProfile
    )

    companion object {
        val all: List<IntelligenceQuestion> = listOf(
            IntelligenceQuestion(
                id = "weather",
                label = "Météo",
                symbol = "cloud.rain.fill",
                title = "Quand il pleut ou qu'il fait très froid",
                subtitle = "Lux consulte la météo au départ de chaque recherche.",
                options = listOf(
                    Option("indifferent", "figure.walk", "Je marche quand même", "La météo ne change rien",
                        { it.weather == IntelligenceProfile.Weather.INDIFFERENT }, { it.copy(weather = IntelligenceProfile.Weather.INDIFFERENT) }),
                    Option("walkLess", "umbrella.fill", "Je préfère marcher moins", "Quelques minutes de plus pour rester au sec",
                        { it.weather == IntelligenceProfile.Weather.WALK_LESS }, { it.copy(weather = IntelligenceProfile.Weather.WALK_LESS) }),
                    Option("avoidWalking", "cloud.heavyrain.fill", "J'évite de marcher au maximum", "Même si le trajet est plus long",
                        { it.weather == IntelligenceProfile.Weather.AVOID_WALKING }, { it.copy(weather = IntelligenceProfile.Weather.AVOID_WALKING) })
                )
            ),
            IntelligenceQuestion(
                id = "directness",
                label = "Correspondances",
                symbol = "arrow.triangle.swap",
                title = "Pour éviter une correspondance, vous attendriez",
                subtitle = "Un bus direct qui arrive un peu plus tard peut valoir le coup.",
                options = listOf(
                    Option("never", "bolt.fill", "Pas du tout", "Le plus rapide, peu importe les changements",
                        { it.directness == IntelligenceProfile.Directness.NEVER }, { it.copy(directness = IntelligenceProfile.Directness.NEVER) }),
                    Option("five", "clock", "Jusqu'à 5 minutes", "Un petit détour pour rester assis",
                        { it.directness == IntelligenceProfile.Directness.FIVE }, { it.copy(directness = IntelligenceProfile.Directness.FIVE) }),
                    Option("ten", "clock.fill", "Jusqu'à 10 minutes", "Je préfère nettement les trajets directs",
                        { it.directness == IntelligenceProfile.Directness.TEN }, { it.copy(directness = IntelligenceProfile.Directness.TEN) }),
                    Option("fifteen", "hourglass", "Jusqu'à 15 minutes", "Les correspondances, très peu pour moi",
                        { it.directness == IntelligenceProfile.Directness.FIFTEEN }, { it.copy(directness = IntelligenceProfile.Directness.FIFTEEN) })
                )
            ),
            IntelligenceQuestion(
                id = "walking",
                label = "Marche",
                symbol = "figure.walk",
                title = "La marche, pour vous, c'est",
                subtitle = "Par beau temps, sur les trajets à pied et les correspondances.",
                options = listOf(
                    Option("enjoys", "figure.hiking", "Un plaisir", "Marcher plutôt qu'attendre",
                        { it.walking == IntelligenceProfile.Walking.ENJOYS }, { it.copy(walking = IntelligenceProfile.Walking.ENJOYS) }),
                    Option("neutral", "figure.walk", "Un moyen comme un autre", "Tant que ça reste raisonnable",
                        { it.walking == IntelligenceProfile.Walking.NEUTRAL }, { it.copy(walking = IntelligenceProfile.Walking.NEUTRAL) }),
                    Option("minimal", "figure.stand", "À limiter", "Bagages, poussette ou simplement pas envie",
                        { it.walking == IntelligenceProfile.Walking.MINIMAL }, { it.copy(walking = IntelligenceProfile.Walking.MINIMAL) })
                )
            ),
            IntelligenceQuestion(
                id = "crowd",
                label = "Affluence",
                symbol = "person.3.fill",
                title = "Face à un véhicule bondé",
                subtitle = "D'après les signalements des autres voyageurs Lux.",
                options = listOf(
                    Option("indifferent", "person.3.fill", "Ça ne me dérange pas", "Je monte quand même",
                        { it.crowd == IntelligenceProfile.Crowd.INDIFFERENT }, { it.copy(crowd = IntelligenceProfile.Crowd.INDIFFERENT) }),
                    Option("avoid", "person.2.fill", "J'évite si possible", "À durée à peu près égale",
                        { it.crowd == IntelligenceProfile.Crowd.AVOID }, { it.copy(crowd = IntelligenceProfile.Crowd.AVOID) }),
                    Option("avoidStrongly", "person.fill", "J'attends le suivant", "Une place assise avant tout",
                        { it.crowd == IntelligenceProfile.Crowd.AVOID_STRONGLY }, { it.copy(crowd = IntelligenceProfile.Crowd.AVOID_STRONGLY) })
                )
            ),
            IntelligenceQuestion(
                id = "margin",
                label = "Marge",
                symbol = "arrow.triangle.2.circlepath",
                title = "En correspondance, il vous faut",
                subtitle = "Lux écarte les changements trop serrés pour vous.",
                options = listOf(
                    Option("tight", "hare.fill", "Peu de marge", "Je cours s'il le faut",
                        { it.margin == IntelligenceProfile.Margin.TIGHT }, { it.copy(margin = IntelligenceProfile.Margin.TIGHT) }),
                    Option("comfortable", "figure.walk", "Quelques minutes", "Environ 3 minutes",
                        { it.margin == IntelligenceProfile.Margin.COMFORTABLE }, { it.copy(margin = IntelligenceProfile.Margin.COMFORTABLE) }),
                    Option("generous", "tortoise.fill", "Une marge large", "Je ne cours jamais",
                        { it.margin == IntelligenceProfile.Margin.GENEROUS }, { it.copy(margin = IntelligenceProfile.Margin.GENEROUS) })
                )
            ),
            IntelligenceQuestion(
                id = "habits",
                label = "Habitudes",
                symbol = "heart.fill",
                title = "Vos lignes habituelles",
                subtitle = "Lux apprend les lignes et directions que vous consultez le plus.",
                options = listOf(
                    Option("yes", "heart.fill", "Les privilégier", "À temps de trajet comparable",
                        { it.usesHabits }, { it.copy(usesHabits = true) }),
                    Option("no", "circle.dashed", "Peu importe", "Seul le trajet compte",
                        { !it.usesHabits }, { it.copy(usesHabits = false) })
                )
            )
        )
    }
}
