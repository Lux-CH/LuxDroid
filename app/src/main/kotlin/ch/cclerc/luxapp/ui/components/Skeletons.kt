package ch.cclerc.luxapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.anim.pulse
import ch.cclerc.luxapp.ui.theme.LuxTheme

private val skeletonPillWidths = listOf(42, 28, 54, 34, 46)

@Composable
fun TripResultSkeletonCard(modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val bone = colors.quaternaryLabel
    val shape = RoundedCornerShape(24.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(colors.secondarySystemGroupedBackground, shape)
            .border(0.5.dp, colors.hairline, shape)
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.size(128.dp, 20.dp).background(bone, RoundedCornerShape(6.dp)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(66.dp, 26.dp).background(bone, RoundedCornerShape(13.dp)))
        }
        Box(Modifier.size(86.dp, 14.dp).background(bone, RoundedCornerShape(5.dp)))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(48.dp)
        ) {
            skeletonPillWidths.forEach { width ->
                Box(Modifier.size(width.dp, 22.dp).background(bone, RoundedCornerShape(6.dp)))
            }
        }
    }
}

@Composable
fun TripResultsSkeletonList(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .pulse(fromAlpha = 1f, toAlpha = 0.55f, durationMs = 900)
    ) {
        repeat(4) {
            TripResultSkeletonCard()
        }
    }
}
