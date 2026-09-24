package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.punctuality
import ch.cclerc.luxapp.domain.symbolName
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.trip.Leg

@Composable
fun LegHeaderView(
    leg: Leg,
    legColor: Color,
    isSingle: Boolean,
    nextStop: Place?,
    modifier: Modifier = Modifier,
    onOpenSubLeg: (String) -> Unit = {}
) {
    val tripId = leg.tripId
    val tappable = !isSingle && tripId != null

    val rowModifier = if (tappable) {
        modifier.clickable(
            interactionSource = null,
            indication = PlainIndication,
            onClick = { onOpenSubLeg(tripId!!) }
        )
    } else {
        modifier
    }

    LegHeaderContent(
        leg = leg,
        legColor = legColor,
        nextStop = nextStop,
        modifier = rowModifier
    )
}

@Composable
private fun LegHeaderContent(
    leg: Leg,
    legColor: Color,
    nextStop: Place?,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val boardingTimeColor = leg.from.punctuality(leg.realTime, leg.cancelled).highlightColor(colors)
        ?: colors.secondaryLabel

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        val pillShape = if (leg.mode.usesSquaredPill) {
            RoundedCornerShape(2.dp)
        } else {
            RoundedCornerShape(50)
        }
        Box(
            Modifier.iosShadow(
                color = Color.Black.copy(alpha = 0.1f),
                blurRadius = 2.dp,
                offsetY = 1.dp,
                shape = pillShape
            )
        ) {
            LinePill(
                line = leg.routeShortName ?: "",
                agencyId = leg.agencyId,
                mode = leg.mode,
                width = 64.dp,
                height = 40.dp,
                fontSize = 19.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(
                    name = "arrow.right",
                    size = 15.sp,
                    color = legColor.copy(alpha = 0.7f)
                )
                Text(
                    text = leg.headsign ?: "",
                    style = LuxTheme.type.headline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label
                )
                Spacer(Modifier.weight(1f))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leg.cancelled) {
                    SFSymbol(
                        name = "xmark.octagon.fill",
                        size = 10.sp,
                        color = colors.systemRed
                    )
                    Text(
                        text = "Course supprimée",
                        style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.systemRed
                    )
                } else if (nextStop != null) {
                    SFSymbol(
                        name = "arrow.down",
                        size = 10.sp,
                        color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.6f)
                    )
                    Text(
                        text = "Prochain: ${nextStop.name}",
                        style = LuxTheme.type.caption,
                        color = colors.secondaryLabel
                    )
                } else {
                    SFSymbol(
                        name = leg.mode.symbolName,
                        size = 10.sp,
                        color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.6f)
                    )
                    Text(
                        text = "Montez à ${formatTime(leg.startTime)}",
                        style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = boardingTimeColor
                    )
                }
            }
        }
    }
}
