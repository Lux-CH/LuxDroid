package ch.cclerc.luxapp.ui.stop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.SearchResult

@Composable
fun IndividualStopView(
    stop: SearchResult,
    onPlanTrip: (SearchResult) -> Unit,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val colors = LuxTheme.colors
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.secondarySystemBackground.copy(alpha = 0.8f)),
        contentAlignment = Alignment.TopStart
    ) {
        Column(Modifier.fillMaxSize()) {
            StopHeaderView(
                stop = stop,
                onPlanTrip = onPlanTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 52.dp)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            )

            HorizontalDivider(thickness = 0.5.dp, color = colors.separator)

            ExpandedStopView(
                stop = stop,
                fromStops = true,
                maxGroupsToShow = 50,
                onOpenTrip = onOpenTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-5).dp)
            )
        }

        if (onBack != null) {
            BackButton(
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = topInset + 6.dp, start = 8.dp)
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LuxTheme.accent
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .scaleClickable(haptic = false, onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(name = "chevron.backward", size = 18.sp, color = accent, weight = 600)
        Text(
            text = "Retour",
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = accent
        )
    }
}
