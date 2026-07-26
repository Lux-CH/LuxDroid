package ch.cclerc.luxcom.colors

data class LineColor(val line: String, val color: Long, val textColor: Long = 0xFFFFFFFF)

object LineColors {
    const val squaredPillFallback: Long = 0xFFEA0706L

    private const val BLACK: Long = 0xFF000000

    private val tpgLinesColor: Map<String, LineColor> = listOf(
        LineColor("1", 0xFF5A1E82),
        LineColor("2", 0xFFD2DB4A, BLACK),
        LineColor("3", 0xFFB82F89),
        LineColor("5", 0xFF00ACE7),
        LineColor("6", 0xFF008CBE),
        LineColor("7", 0xFF00A828),
        LineColor("8", 0xFF84471C),
        LineColor("9", 0xFFE2001D),
        LineColor("10", 0xFF006E3D),
        LineColor("11", 0xFF82419E),
        LineColor("12", 0xFFF5A300, BLACK),
        LineColor("13", 0xFF4CB847),
        LineColor("14", 0xFF5A1E82),
        LineColor("15", 0xFF84471C),
        LineColor("16", 0xFFF387B7),
        LineColor("17", 0xFF00ACE7, BLACK),
        LineColor("18", 0xFFB82F89),
        LineColor("19", 0xFFA05909),
        LineColor("20", 0xFF00A828),
        LineColor("21", 0xFF78003C),
        LineColor("22", 0xFF5A1E82),
        LineColor("23", 0xFFB82F89),
        LineColor("25", 0xFFA05909),
        LineColor("28", 0xFF82419E),
        LineColor("29", 0xFFE91E76, 0xFFFFE8BC),
        LineColor("31", 0xFF00B0A4),
        LineColor("32", 0xFF89CBBE, BLACK),
        LineColor("33", 0xFF00B0A4),
        LineColor("34", 0xFF89CBBE, BLACK),
        LineColor("35", 0xFF666666),
        LineColor("36", 0xFF666666),
        LineColor("37", 0xFF005F61),
        LineColor("38", 0xFF005F61),
        LineColor("39", 0xFF00B0A4),
        LineColor("40", 0xFF89CBBE, BLACK),
        LineColor("41", 0xFF00B0A4),
        LineColor("42", 0xFF00B0A4),
        LineColor("43", 0xFF89CBBE, BLACK),
        LineColor("44", 0xFF00B0A4),
        LineColor("45", 0xFF89CBBE, BLACK),
        LineColor("46", 0xFF00B0A4),
        LineColor("47", 0xFF00B0A4),
        LineColor("48", 0xFF89CBBE, BLACK),
        LineColor("49", 0xFF005E63),
        LineColor("50", 0xFF00B0A4),
        LineColor("51", 0xFF00B0A4),
        LineColor("52", 0xFF89CBBE, BLACK),
        LineColor("53", 0xFF89CBBE, BLACK),
        LineColor("54", 0xFF89CBBE, BLACK),
        LineColor("55", 0xFF005F61),
        LineColor("56", 0xFF009999),
        LineColor("57", 0xFF89CBBE, BLACK),
        LineColor("58", 0xFF02B1B0),
        LineColor("59", 0xFF005F61),
        LineColor("60", 0xFFEC619F),
        LineColor("61", 0xFFF5B5D2, BLACK),
        LineColor("62", 0xFFEC619F),
        LineColor("63", 0xFFF5B5D2),
        LineColor("64", 0xFFEC619F),
        LineColor("66", 0xFFF5B5D2, BLACK),
        LineColor("67", 0xFFF5B5D2, BLACK),
        LineColor("68", 0xFFEC619F),
        LineColor("69", 0xFFF5B5D2, BLACK),
        LineColor("70", 0xFF00B0A4),
        LineColor("71", 0xFF005F61),
        LineColor("72", 0xFF89CBBE, BLACK),
        LineColor("73", 0xFF005F61),
        LineColor("74", 0xFF89CBBE, BLACK),
        LineColor("75", 0xFF005F61),
        LineColor("76", 0xFF70C4B4),
        LineColor("77", 0xFF70C4B4),
        LineColor("78", 0xFFF5B5D2),
        LineColor("80", 0xFFFF9BAA, BLACK),
        LineColor("82", 0xFFEC619F),
        LineColor("83", 0xFFEC619F),
        LineColor("91", 0xFF005F61),
        LineColor("92", 0xFF89CBBE, BLACK),
        LineColor("E", 0xFFFF7E00),
        LineColor("G", 0xFFFF9BAA),
        LineColor("L", 0xFFFF7E00),
        LineColor("M", 0xFF00A828, BLACK),
        LineColor("N", 0xFF008CBE, BLACK),
        LineColor("271", 0xFFFFDC00),
        LineColor("272", 0xFF00B0A4),
        LineColor("274", 0xFFEC619F),
        LineColor("C1", 0xFF000000),
        LineColor("C3", 0xFF000000),
        LineColor("C4", 0xFF000000),
        LineColor("C5", 0xFF000000),
        LineColor("C6", 0xFF000000),
        LineColor("C7", 0xFF000000),
        LineColor("C8", 0xFF000000),
        LineColor("C9", 0xFF000000),
        LineColor("E+", 0xFF000000),
        LineColor("G+", 0xFF000000),
        LineColor("94", 0xFF000000),
        LineColor("96", 0xFF000000),
        LineColor("97", 0xFF6CB43F),
        LineColor("RL1", 0xFFE4023A),
        LineColor("RL2", 0xFF0385CD),
        LineColor("RL3", 0xFF64B32E),
        LineColor("RL4", 0xFFF8B003),
        LineColor("RL5", 0xFFC0096F),
        LineColor("RL6", 0xFF019AAA),
        LineColor("RL7", 0xFF27451F),
        LineColor("M1", 0xFF0076BA),
        LineColor("M2", 0xFFFF6600),
        LineColor("M3", 0xFF068A33),
        LineColor("M4", 0xFFB10D28),
        LineColor("A", 0xFFF58428),
        LineColor("A1", 0xFF7D78B7),
        LineColor("A2", 0xFF00744A),
        LineColor("A3", 0xFF0DAE4B),
        LineColor("A4", 0xFF009FDF),
        LineColor("A5", 0xFFF167A7),
        LineColor("A6", 0xFFDA2031),
        LineColor("803", 0xFFE63023),
        LineColor("804", 0xFF009640),
        LineColor("805", 0xFFF07D00),
        LineColor("810", 0xFFC693C2),
        LineColor("811", 0xFF96358C),
        LineColor("813", 0xFFF39DA9),
        LineColor("814", 0xFF00437A),
        LineColor("815", 0xFFAD222B),
        LineColor("818", 0xFF85431D),
        LineColor("891", 0xFFFFE52D),
        LineColor("R55", 0xFFEE2720)
    ).associateBy { it.line }

    private val tlLinesColor: Map<String, LineColor> = listOf(
        LineColor("m1", 0xFFEC008C),
        LineColor("m2", 0xFFEC008C),
        LineColor("R20", 0xFF58B947),
        LineColor("N1", 0xFFDF0915),
        LineColor("N2", 0xFFA46E5D),
        LineColor("N3", 0xFFF19203),
        LineColor("N4", 0xFF217A98),
        LineColor("N5", 0xFF8FBF1F),
        LineColor("N6", 0xFF9E456F),
        LineColor("1", 0xFFF2665E),
        LineColor("2", 0xFFFCAF17),
        LineColor("3", 0xFF939598),
        LineColor("4", 0xFF00A651),
        LineColor("6", 0xFF00AEEF),
        LineColor("7", 0xFF2E3192),
        LineColor("8", 0xFF8F53A1),
        LineColor("9", 0xFFB41E8E),
        LineColor("13", 0xFF43978D),
        LineColor("16", 0xFFC1B400),
        LineColor("17", 0xFFED1C24),
        LineColor("18", 0xFF44C8F5),
        LineColor("19", 0xFF2268B8),
        LineColor("20", 0xFFDE761C),
        LineColor("21", 0xFFC95789),
        LineColor("24", 0xFF91B672),
        LineColor("25", 0xFF4F78A8),
        LineColor("31", 0xFF529DBA),
        LineColor("32", 0xFF9D85BE),
        LineColor("33", 0xFF4C7520),
        LineColor("35", 0xFFFAA22B),
        LineColor("36", 0xFF9CB4BE),
        LineColor("38", 0xFFBB8732),
        LineColor("41", 0xFFB41E8E),
        LineColor("42", 0xFFCB8E96),
        LineColor("44", 0xFFF599B1),
        LineColor("45", 0xFF008C94),
        LineColor("46", 0xFFB49C00),
        LineColor("47", 0xFFCF9C51),
        LineColor("48", 0xFF80A0D3),
        LineColor("49", 0xFFC192C2),
        LineColor("54", 0xFFB9684A),
        LineColor("56", 0xFF0054A6),
        LineColor("58", 0xFF00BAD0),
        LineColor("60", 0xFF8DC63F),
        LineColor("64", 0xFFC1353C),
        LineColor("68", 0xFF605EA9),
        LineColor("69", 0xFF9B95C9),
        LineColor("Bus-LEB", 0xFF5BB134),
        LineColor("2003", 0xFFA0A0A0),
        LineColor("R56", 0xFF02B646),
        LineColor("R57", 0xFF006A28),
        LineColor("701", 0xFF004D21),
        LineColor("702", 0xFFF58F00),
        LineColor("703", 0xFF611E83),
        LineColor("704", 0xFF8CBE0D),
        LineColor("705", 0xFF643900),
        LineColor("706", 0xFFDECF37),
        LineColor("724", 0xFFAD559C),
        LineColor("726", 0xFF00A2E6),
        LineColor("730", 0xFF888A89),
        LineColor("735", 0xFFE7007C),
        LineColor("736", 0xFF8882BD),
        LineColor("740", 0xFFEC4A11),
        LineColor("742", 0xFFC0101E),
        LineColor("750", 0xFF23328B),
        LineColor("752", 0xFFD781B5),
        LineColor("760", 0xFF009894)
    ).associateBy { it.line }

    private val tacLinesColors: Map<String, LineColor> = listOf(
        LineColor("TANGO", 0xFF009CDB),
        LineColor("3", 0xFF00A786),
        LineColor("4", 0xFFF7931D, BLACK),
        LineColor("5", 0xFFA1318B),
        LineColor("6", 0xFFD6DF21, BLACK),
        LineColor("7", 0xFFBA7731, BLACK),
        LineColor("8", 0xFFE99FC4, BLACK),
        LineColor("TAD CHAL", 0xFF388EC8)
    ).associateBy { it.line }

    fun color(line: String): Long? = tpgLinesColor[line]?.color

    fun textColor(line: String): Long? = tpgLinesColor[line]?.textColor

    fun tlColor(line: String): Long? = tlLinesColor[line]?.color

    fun tlTextColor(line: String): Long? = tlLinesColor[line]?.textColor

    fun tacColors(line: String): Long? = tacLinesColors[line]?.color

    fun tacTextColor(line: String): Long? = tacLinesColors[line]?.textColor
}

fun parseHexColor(hex: String): Long? {
    val cleaned = hex.trim { !it.isLetterOrDigit() }
    val int = cleaned.toLongOrNull(16) ?: return null
    return when (cleaned.length) {
        3 -> {
            val r = (int shr 8) * 17
            val g = (int shr 4 and 0xF) * 17
            val b = (int and 0xF) * 17
            0xFF000000 or (r shl 16) or (g shl 8) or b
        }
        6 -> 0xFF000000 or int
        8 -> int
        else -> null
    }
}
