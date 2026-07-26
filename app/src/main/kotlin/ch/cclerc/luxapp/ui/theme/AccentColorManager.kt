package ch.cclerc.luxapp.ui.theme

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import ch.cclerc.luxapp.data.Settings

object AccentColorManager {

    data class AccentOption(
        val id: String,
        val displayName: String,
        val light: Color,
        val dark: Color
    ) {
        fun resolved(dark: Boolean): Color = if (dark) this.dark else this.light
    }

    private fun fixed(id: String, name: String, hex: Long): AccentOption =
        AccentOption(id, name, Color(hex), Color(hex))

    val availableColors: List<AccentOption> = listOf(
        fixed("sbb-red", "Rouge SBB CFF", 0xFFEB0000),
        fixed("garnet-red", "Rouge Grenat", 0xFF85142B),
        fixed("unige-pink", "Rose UNIGE", 0xFFD9005D),
        fixed("tpg-orange", "Orange TPG", 0xFFFC5412),
        AccentOption("lux-orange", "Orange Lux", Color(0xFFFF9500), Color(0xFFFF9F0A)),
        AccentOption("mouettes-yellow", "Jaune Mouettes", Color(0xFFFFCC00), Color(0xFFFFD60A)),
        AccentOption("vaudoise-green", "Vert Vaudoise", Color(0xFF34C759), Color(0xFF30D158)),
        fixed("cgte-green", "Vert CGTE", 0xFF2D8859),
        fixed("leman-blue", "Bleu Léman", 0xFF2EFEDA),
        fixed("ice-tea-blue", "Bleu Ice Tea", 0xFF3182DB),
        fixed("sbb-blue", "Bleu SBB CFF", 0xFF2D327D),
        AccentOption("sncf-violet", "Violet SCNF", Color(0xFF5856D6), Color(0xFF5E5CE6)),
        fixed("pastel-green", "Vert Pastel", 0xFFA8E6CF),
        fixed("pastel-blue", "Bleu Pastel", 0xFFA7D8FF),
        fixed("pastel-lavender", "Lavande Pastel", 0xFFCBB9F7),
        fixed("pastel-rose", "Rose Pastel", 0xFFF7BDD8),
        fixed("pastel-peach", "Pêche Pastel", 0xFFFFC8A8),
        fixed("pastel-mimosa", "Mimosa Pastel", 0xFFF8E7A2)
    )

    private val hiddenColors: List<AccentOption> = listOf(
        fixed("jura-brown", "Brun Jura", 0xFF6B4423),
        fixed("rust-brown", "Rouille Marronds", 0xFF954535),
        fixed("vignes-ocre", "Ocre Vignes", 0xFFB8733E),
        fixed("alps-gray", "Gris Alpes", 0xFF7C8B99),
        fixed("glacier-blue", "Bleu Glacier", 0xFF5B7C8D),
        fixed("snow-white", "Blanc Neige", 0xFFA5C9E1),
        fixed("festive-red", "Rouge Fête", 0xFF8B1E3F),
        fixed("fir-green", "Vert Sapin", 0xFF4A5D4F),
        fixed("escalade-gold", "Or Escalade", 0xFFC4983C)
    )

    val allColors: List<AccentOption> = availableColors + hiddenColors

    private val byId: Map<String, AccentOption> = allColors.associateBy { it.id }
    private val idByName: Map<String, String> = allColors.associate { it.displayName to it.id }
    private val defaultOption: AccentOption = byId.getValue("lux-orange")

    private const val PREF_KEY = "selectedAccentColor"

    private val selectedIdState by lazy {
        val raw = Settings.prefs.getString(PREF_KEY, null)
        val resolved = when {
            raw == null -> defaultOption.id
            byId.containsKey(raw) -> raw
            idByName.containsKey(raw) -> {
                val migrated = idByName.getValue(raw)
                Settings.prefs.edit().putString(PREF_KEY, migrated).apply()
                migrated
            }
            else -> defaultOption.id
        }
        mutableStateOf(resolved)
    }

    private val selectedOptionState by lazy {
        derivedStateOf { byId[selectedIdState.value] ?: defaultOption }
    }

    val selectedAccent: State<AccentOption>
        get() = selectedOptionState

    fun current(): AccentOption = selectedOptionState.value

    fun select(option: AccentOption) {
        selectedIdState.value = option.id
        Settings.prefs.edit().putString(PREF_KEY, option.id).apply()
    }
}
