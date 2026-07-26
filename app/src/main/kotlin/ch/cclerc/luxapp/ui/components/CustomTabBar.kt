package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import kotlin.math.roundToInt

enum class LuxTab(val title: String, val icon: String) {
    Home("Accueil", "house.fill"),
    Stops("Arrêts", "signpost.right.fill")
}

@Composable
fun CustomTabBar(
    selectedTab: LuxTab,
    onModeChange: (LuxTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val isDark = LuxTheme.isDark
    val accent = LuxTheme.accent
    val density = LocalDensity.current
    val capsule = RoundedCornerShape(50)
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val slotRects = remember { mutableStateMapOf<LuxTab, Rect>() }

    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp)
            .height(54.dp)
            .iosShadow(
                color = Color.Black.copy(alpha = if (isDark) 0.3f else 0.15f),
                blurRadius = 7.5.dp,
                offsetY = 5.dp,
                shape = capsule
            )
            .background(colors.secondarySystemBackground, capsule)
            .background(
                if (isDark) colors.secondarySystemBackground.copy(alpha = 0.7f) else Color.White,
                capsule
            )
            .border(0.75.dp, if (isDark) colors.hairline else colors.hairlineGray, capsule)
            .onGloballyPositioned { containerCoords = it }
    ) {
        val target = slotRects[selectedTab]
        if (target != null) {
            val maxPillWidth = with(density) { 125.dp.toPx() }
            val indicatorX by animateFloatAsState(target.left, LuxSprings.Snappy)
            val indicatorWidth by animateFloatAsState(target.width, LuxSprings.Snappy)
            Box(
                Modifier
                    .offset { IntOffset(indicatorX.roundToInt(), target.top.roundToInt()) }
                    .size(
                        with(density) { indicatorWidth.coerceAtMost(maxPillWidth).toDp() },
                        with(density) { target.height.toDp() }
                    )
                    .background(accent.copy(alpha = if (isDark) 0.15f else 0.1f), capsule)
                    .border(0.25.dp, colors.hairline, capsule)
            )
        }
        Row(
            Modifier
                .fillMaxHeight()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            LuxTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Row(
                    Modifier
                        .width(125.dp)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            containerCoords?.let {
                                slotRects[tab] = it.localBoundingBoxOf(coords, clipBounds = false)
                            }
                        }
                        .clip(capsule)
                        .clickable(interactionSource = null, indication = PlainIndication) {
                            HapticFeedback.softImpact()
                            if (selectedTab != tab) onModeChange(tab)
                        },
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        SFSymbol(
                            name = tab.icon,
                            size = 16.sp,
                            color = if (selected) accent else colors.label.copy(alpha = 0.6f),
                            weight = if (selected) 700 else 500
                        )
                    }
                    BasicText(
                        text = tab.title,
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) accent else colors.label.copy(alpha = 0.6f)
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
