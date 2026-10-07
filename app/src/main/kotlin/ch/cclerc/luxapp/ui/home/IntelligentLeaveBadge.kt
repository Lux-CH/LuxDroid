package ch.cclerc.luxapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.intelligence.NearbyIntelligence
import ch.cclerc.luxapp.ui.anim.NumericText
import ch.cclerc.luxapp.ui.onboard.color
import ch.cclerc.luxapp.ui.onboard.rememberNow
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.LuxTypography
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import kotlin.math.floor

@Composable
fun IntelligentLeaveBadge(pick: NearbyIntelligence.Pick, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val now = rememberNow(15_000)
    val leaveIn = floor((pick.leaveAt.toEpochMilli() - now.toEpochMilli()) / 60_000.0).toInt()
    val tint = if (leaveIn <= 0) colors.systemOrange else LuxTheme.accent
    val description = if (leaveIn <= 0) {
        "Magic : partez maintenant, ${pick.walkMinutes} min à pied"
    } else {
        "Magic : partez dans $leaveIn min, ${pick.walkMinutes} min à pied"
    }

    Row(
        modifier
            .wrapContentSize()
            .clearAndSetSemantics { contentDescription = description }
            .background(tint.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(name = "sparkles", size = 8.sp, color = tint, weight = 700)
        NumericText(
            text = if (leaveIn <= 0) "Partez" else "$leaveIn min",
            style = LuxTypography.timeVariant(TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)),
            color = tint
        )
        val weather = pick.weather
        if (weather != null && weather.isHarsh) {
            SFSymbol(name = weather.symbol, size = 9.sp, color = if (weather.isWet) colors.systemBlue else colors.systemGray)
        }
        val crowd = pick.crowd
        if (crowd != null && crowd >= 3.5) {
            SFSymbol(name = "person.3.fill", size = 8.sp, color = ReportAttribute.CROWD.color(crowd, colors))
        }
    }
}
