package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Itinerary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tripTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

internal fun formatTripDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun TripResultView(
    itinerary: Itinerary,
    modifier: Modifier = Modifier,
    destinationName: String? = null,
    onClick: ((Itinerary) -> Unit)? = null
) {
    val colors = LuxTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = LuxSprings.Snappy,
        label = "tripCardScale"
    )
    val shadowRadius by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 10.dp,
        animationSpec = LuxSprings.springFor(0.3, 0.7),
        label = "tripCardShadowRadius"
    )
    val shadowOffset by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 4.dp,
        animationSpec = LuxSprings.springFor(0.3, 0.7),
        label = "tripCardShadowOffset"
    )

    val shape = RoundedCornerShape(LuxShapes.r24)
    val fill = if (onClick == null) {
        if (colors.isDark) colors.systemFill.copy(alpha = 0.3f) else colors.systemBackground
    } else {
        colors.secondarySystemGroupedBackground
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .iosShadow(
            color = Color.Black.copy(alpha = if (colors.isDark) 0.3f else 0.1f),
            blurRadius = shadowRadius,
            offsetY = shadowOffset,
            shape = shape
        )
        .clip(shape)
        .background(fill, shape)
        .border(0.5.dp, colors.hairline, shape)
        .let {
            if (onClick == null) it
            else it.clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick(itinerary) }
        }
        .padding(18.dp)

    Column(cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicText(
                    text = tripTimeFormatter.format(
                        itinerary.startTime.atZone(ZoneId.systemDefault())
                    ),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.label
                    ),
                    maxLines = 1
                )
                SFSymbol(
                    name = "arrow.right",
                    size = 16.sp,
                    color = colors.secondaryLabel,
                    weight = 600
                )
                BasicText(
                    text = tripTimeFormatter.format(
                        itinerary.endTime.atZone(ZoneId.systemDefault())
                    ),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.label
                    ),
                    maxLines = 1
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .background(
                        if (colors.isDark) colors.systemGray5 else colors.systemGray6,
                        CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SFSymbol(
                    name = "clock.fill",
                    size = 12.sp,
                    color = colors.secondaryLabel,
                    weight = 500
                )
                BasicText(
                    text = formatTripDuration(itinerary.duration),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.label
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val direct = itinerary.transfers == 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SFSymbol(
                    name = "point.bottomleft.forward.to.point.topright.filled.scurvepath",
                    size = 13.sp,
                    color = if (direct) colors.systemGreen else colors.secondaryLabel,
                    weight = 500
                )
                BasicText(
                    text = if (direct) {
                        "Direct"
                    } else {
                        "${itinerary.transfers} transfer${if (itinerary.transfers > 1) "s" else ""}"
                    },
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (direct) colors.systemGreen else colors.secondaryLabel
                    ),
                    maxLines = 1
                )
            }

            val walkingLegs = itinerary.legs.filter { it.mode == TransportationMode.WALK }
            if (walkingLegs.isNotEmpty()) {
                SFSymbol(
                    name = "circle.fill",
                    size = 5.sp,
                    color = colors.secondaryLabel,
                    weight = 500
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SFSymbol(
                        name = "figure.walk",
                        size = 13.sp,
                        color = colors.secondaryLabel,
                        weight = 500
                    )
                    BasicText(
                        text = "${walkingLegs.sumOf { it.duration } / 60} min",
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.secondaryLabel
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            RouteVisualizationView(
                legs = itinerary.legs,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    }
}
