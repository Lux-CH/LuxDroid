package ch.cclerc.luxapp.ui.crowdback

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.api.getLCBInfo
import ch.cclerc.luxcom.model.feedback.InfoResponse
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.net.ApiError
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

val vividRed: Color = Color(red = 1.0f, green = 0.1f, blue = 0.0f)
val vividGreen: Color = Color(red = 0.0f, green = 0.9f, blue = 0.2f)

val ReportAttribute.rawKey: String
    get() = name.lowercase()

val ReportAttribute.iconName: String
    get() = when (this) {
        ReportAttribute.CROWD -> "person.3.fill"
        ReportAttribute.SMELL -> "nose.fill"
        ReportAttribute.CLEAN -> "sparkles"
        ReportAttribute.HEAT -> "thermometer.medium"
        ReportAttribute.NOISE -> "speaker.wave.2.fill"
    }

fun ReportAttribute.color(level: Double, colors: LuxColors): Color = when (this) {
    ReportAttribute.HEAT -> when {
        level < 1.5 -> colors.systemCyan
        level < 2.5 -> colors.systemBlue
        level < 3.5 -> colors.systemIndigo
        level < 4.5 -> colors.systemPurple
        level <= 5.0 -> colors.systemRed
        else -> colors.systemBlue
    }

    ReportAttribute.CLEAN -> when {
        level < 1.5 -> vividRed
        level < 2.5 -> colors.systemRed
        level < 3.5 -> colors.systemYellow
        level < 4.5 -> colors.systemGreen
        level <= 5.0 -> vividGreen
        else -> colors.systemRed
    }

    ReportAttribute.CROWD, ReportAttribute.NOISE, ReportAttribute.SMELL -> when {
        level < 1.5 -> vividGreen
        level < 2.5 -> colors.systemGreen
        level < 3.5 -> colors.systemYellow
        level < 4.5 -> colors.systemRed
        level <= 5.0 -> vividRed
        else -> colors.systemGreen
    }
}

suspend fun fetchLineInfo(
    tripId: String,
    routeShortName: String,
    latitude: Double,
    longitude: Double
): InfoResponse? = try {
    getLCBInfo(
        tripId = tripId,
        routeShortName = routeShortName,
        latitude = latitude,
        longitude = longitude
    )
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: ApiError.RequestFailed) {
    if (error.statusCode != 404) {
        Log.e("CrowdBack", "error loading line info : $error")
    }
    null
} catch (error: Exception) {
    Log.e("CrowdBack", "error loading line info : $error")
    null
}

@Composable
fun rememberLineInfo(
    leg: Leg,
    enabled: Boolean = true,
    reloadToken: Int = 0
): InfoResponse? {
    val location by LocationService.location.collectAsStateWithLifecycle()
    var info by remember(leg.tripId) { mutableStateOf<InfoResponse?>(null) }

    val tripId = leg.tripId
    val routeShortName = leg.routeShortName
    val hasLocation = location != null

    LaunchedEffect(tripId, routeShortName, hasLocation, enabled, reloadToken) {
        if (!enabled || tripId == null || routeShortName == null) return@LaunchedEffect
        val current = LocationService.location.value ?: return@LaunchedEffect
        info = fetchLineInfo(
            tripId = tripId,
            routeShortName = routeShortName,
            latitude = current.latitude,
            longitude = current.longitude
        )
    }

    return info
}

@Composable
fun LineInfoView(info: InfoResponse?, modifier: Modifier = Modifier) {
    if (info == null) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (info.rt != null) {
            SFSymbol(
                name = "wave.3.forward",
                size = 12.sp,
                color = LuxTheme.colors.systemGreen
            )
        }

        ReportAttribute.entries.forEach { attribute ->
            AttributeIndicator(
                attribute = attribute,
                info = info,
                isRealtime = info.rt?.get(attribute.rawKey) != null
            )
        }
    }
}

@Composable
private fun AttributeIndicator(
    attribute: ReportAttribute,
    info: InfoResponse,
    isRealtime: Boolean
) {
    val colors = LuxTheme.colors

    val realtime = info.rt?.get(attribute.rawKey)
    val average = info.average[attribute.rawKey]

    val level: Double
    val trustLevel: Double
    if (isRealtime && realtime != null) {
        level = realtime.level.toDouble()
        trustLevel = realtime.trustLevel.toDouble()
    } else if (average != null) {
        level = average.level
        trustLevel = average.trustLevel
    } else {
        return
    }

    if (trustLevel < 2.0) return

    val isPatternBased = !isRealtime && average?.reportCount == 0
    val tint = attribute.color(level, colors)
    val filled = level.roundToInt()

    Row(
        modifier = Modifier
            .alpha(if (isPatternBased) 0.6f else 1.0f)
            .background(
                colors.secondaryLabel.copy(alpha = 0.1f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(
            name = attribute.iconName,
            size = 11.sp,
            color = tint,
            modifier = Modifier.height(12.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..5).forEach { step ->
                Box(
                    Modifier
                        .size(3.dp)
                        .background(
                            if (step <= filled) tint else colors.secondaryLabel.copy(alpha = 0.3f),
                            CircleShape
                        )
                )
            }
        }
    }
}
