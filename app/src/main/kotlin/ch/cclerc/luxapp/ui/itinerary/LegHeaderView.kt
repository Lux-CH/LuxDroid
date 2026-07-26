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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.symbolName
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.crowdback.LineInfoView
import ch.cclerc.luxapp.ui.crowdback.ReportWizard
import ch.cclerc.luxapp.ui.crowdback.rememberLineInfo
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.navigation.SheetDetent
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.feedback.InfoResponse
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
    val sheets = LocalSheetController.current
    val crowdbackAllowed = Settings.crowdbackAllowed

    var reloadToken by remember { mutableIntStateOf(0) }
    val lineInfo = rememberLineInfo(
        leg = leg,
        enabled = crowdbackAllowed,
        reloadToken = reloadToken
    )

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
        crowdbackAllowed = crowdbackAllowed,
        lineInfo = lineInfo,
        onReport = {
            sheets.present(
                LuxSheetRequest(
                    cornerRadius = LuxShapes.r38,
                    detents = listOf(SheetDetent.Medium)
                ) {
                    DisposableEffect(Unit) {
                        onDispose { reloadToken += 1 }
                    }
                    ReportWizard(leg = leg, onDismiss = { sheets.dismiss() })
                }
            )
        },
        modifier = rowModifier
    )
}

@Composable
private fun LegHeaderContent(
    leg: Leg,
    legColor: Color,
    nextStop: Place?,
    crowdbackAllowed: Boolean,
    lineInfo: InfoResponse?,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors

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

                if (crowdbackAllowed) {
                    SFSymbol(
                        name = "exclamationmark.bubble",
                        size = 16.sp,
                        color = colors.systemGray,
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = PlainIndication,
                            onClick = onReport
                        )
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nextStop != null) {
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
                        color = colors.secondaryLabel
                    )
                }
            }

            if (crowdbackAllowed) {
                LineInfoView(info = lineInfo)
            }
        }
    }
}
