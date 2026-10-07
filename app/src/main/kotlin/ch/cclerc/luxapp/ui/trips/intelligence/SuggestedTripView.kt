package ch.cclerc.luxapp.ui.trips.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.intelligence.IntelligenceStore
import ch.cclerc.luxapp.domain.intelligence.TripSuggestion
import ch.cclerc.luxapp.domain.intelligence.WeatherSnapshot
import ch.cclerc.luxapp.ui.anim.pulse
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.trips.TripResultView
import ch.cclerc.luxcom.model.trip.Itinerary

@Composable
fun SuggestedTripView(
    suggestion: TripSuggestion,
    destinationName: String?,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: ((Itinerary) -> Unit)? = null
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.1f), shape)
            .border(0.5.dp, accent.copy(alpha = 0.2f), shape)
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IntelligenceHeader(weather = suggestion.weather, onCustomize = onCustomize)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestion.reasons.forEach { reason ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                            SFSymbol(name = reason.symbol, size = 12.sp, color = accent, weight = 600)
                        }
                        Text(reason.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.label)
                    }
                }
                if (suggestion.minutesLater > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                            SFSymbol(name = "clock", size = 12.sp, color = colors.secondaryLabel, weight = 600)
                        }
                        Text(
                            "${suggestion.minutesLater} min de plus que le plus rapide",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.secondaryLabel
                        )
                    }
                }
            }
        }

        TripResultView(
            itinerary = suggestion.itinerary,
            destinationName = destinationName,
            horizontalInset = 0.dp,
            onClick = onClick
        )
    }
}

@Composable
fun IntelligenceThinkingView(onCustomize: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.1f), shape)
            .border(0.5.dp, accent.copy(alpha = 0.2f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IntelligenceHeader(weather = null, onCustomize = onCustomize)
        Text(
            "Analyse de la météo, de l'affluence et de vos habitudes…",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.secondaryLabel
        )
    }
}

@Composable
private fun IntelligenceHeader(weather: WeatherSnapshot?, onCustomize: () -> Unit) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val profile by IntelligenceStore.profileFlow.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SFSymbol(
            name = "sparkles",
            size = 14.sp,
            color = accent,
            weight = 600,
            modifier = if (weather == null && !profile.isConfigured) Modifier.pulse(1f, 0.35f, 700) else Modifier
        )
        Text("Suggestion", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.label)

        if (weather != null) {
            Row(
                Modifier
                    .background(colors.secondarySystemFill.copy(alpha = colors.secondarySystemFill.alpha * 0.6f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = weather.symbol, size = 12.sp, color = weatherTint(weather))
                Text(weather.temperatureText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.secondaryLabel)
            }
        }

        Spacer(Modifier.weight(1f))

        val label = Modifier.semantics { contentDescription = "Personnaliser Magic" }
        if (profile.isConfigured) {
            Box(
                label
                    .size(30.dp)
                    .scaleClickable(haptic = false) {
                        HapticFeedback.lightImpact()
                        onCustomize()
                    },
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = "slider.horizontal.3", size = 14.sp, color = colors.secondaryLabel, weight = 600)
            }
        } else {
            Text(
                "Personnaliser",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = label
                    .scaleClickable(haptic = false) {
                        HapticFeedback.lightImpact()
                        onCustomize()
                    }
                    .background(accent.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun weatherTint(weather: WeatherSnapshot) = LuxTheme.colors.let { colors ->
    when {
        weather.symbol.startsWith("sun") -> colors.systemYellow
        weather.symbol == "cloud.sun.fill" -> colors.systemYellow
        weather.isWet -> colors.systemBlue
        else -> colors.systemGray
    }
}
