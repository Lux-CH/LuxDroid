package ch.cclerc.luxapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed interface SheetDetent {
    data class Fraction(val f: Float) : SheetDetent
    data object Medium : SheetDetent
    data object Large : SheetDetent
    data object Dismissed : SheetDetent

    class Height(val key: String, val height: Dp) : SheetDetent {
        override fun equals(other: Any?): Boolean = other is Height && other.key == key
        override fun hashCode(): Int = key.hashCode()
    }
}

internal fun SheetDetent.sortFraction(): Float = when (this) {
    SheetDetent.Dismissed -> -1f
    is SheetDetent.Height -> 0f
    is SheetDetent.Fraction -> f
    SheetDetent.Medium -> 0.5f
    SheetDetent.Large -> 1f
}

internal fun detentHeightPx(
    detent: SheetDetent,
    containerHeightPx: Float,
    largeHeightPx: Float,
    density: Density? = null
): Float = when (detent) {
    SheetDetent.Dismissed -> 0f
    is SheetDetent.Height -> density?.run { detent.height.toPx() } ?: detent.height.value
    is SheetDetent.Fraction -> containerHeightPx * detent.f
    SheetDetent.Medium -> containerHeightPx * 0.5f
    SheetDetent.Large -> largeHeightPx
}

class LuxSheetRequest(
    val cornerRadius: Dp = 36.dp,
    val detents: List<SheetDetent> = listOf(SheetDetent.Large),
    val showDragIndicator: Boolean = true,
    val interactiveDismiss: Boolean = true,
    val content: @Composable () -> Unit
)

class SheetController {
    internal val requests: SnapshotStateList<LuxSheetRequest> = mutableStateListOf()

    val sheets: List<LuxSheetRequest> get() = requests

    fun present(sheet: LuxSheetRequest) {
        requests.add(sheet)
    }

    fun dismiss() {
        if (requests.isNotEmpty()) {
            requests.removeAt(requests.lastIndex)
        }
    }
}
