package ch.cclerc.luxapp.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.BlinkManager
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.displayBufferTime
import ch.cclerc.luxapp.domain.symbolName
import ch.cclerc.luxapp.ui.anim.NumericText
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.stop.StopTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.delay

private val hourFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private val blinkEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private fun StopTime.eventInstant(): Instant? = place.departure ?: place.arrival

private fun StopTime.scheduledInstant(): Instant? =
    place.scheduledDeparture ?: place.scheduledArrival

private fun StopTime.refreshIdentity(): String {
    val event = eventInstant()
    val scheduled = scheduledInstant()
    return listOf(
        tripId,
        mode.name,
        (event?.toEpochMilli()?.div(1000.0) ?: 0.0).toString(),
        (scheduled?.toEpochMilli()?.div(1000.0) ?: 0.0).toString(),
        if (cancelled) "1" else "0",
        if (realTime) "1" else "0"
    ).joinToString("|")
}

@Composable
fun ArrivalMinuteView(
    incomingStop: StopTime,
    shouldAutoRefresh: Boolean,
    fontSize: TextUnit = 19.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    var now by remember { mutableStateOf(Instant.now()) }
    val identity = incomingStop.refreshIdentity()

    DisposableEffect(Unit) {
        BlinkManager.register()
        onDispose { BlinkManager.unregister() }
    }

    LaunchedEffect(identity) { now = Instant.now() }

    LaunchedEffect(shouldAutoRefresh) {
        if (!shouldAutoRefresh) return@LaunchedEffect
        while (true) {
            now = Instant.now()
            delay(5000)
        }
    }

    val zone = ZoneId.systemDefault()
    val bufferTime = incomingStop.displayBufferTime
    val eventTime = incomingStop.eventInstant() ?: now
    val secondsDifference = ((eventTime.toEpochMilli() - now.toEpochMilli()) / 1000L).toInt()

    val scheduledTime = incomingStop.scheduledInstant() ?: now
    val scheduledDifference =
        ((eventTime.toEpochMilli() - scheduledTime.toEpochMilli()) / 60000L).toInt()

    val latenessColor: Color = when {
        incomingStop.cancelled -> colors.systemRed
        !incomingStop.realTime -> colors.label
        scheduledDifference < 2 && scheduledDifference >= -1 -> colors.systemGreen
        else -> colors.systemYellow
    }

    val secondsUntilCleanup =
        (((eventTime.toEpochMilli() + (bufferTime * 1000.0).toLong()) - now.toEpochMilli()) / 1000L)
            .toInt()
    val shouldBlink =
        secondsUntilCleanup <= bufferTime.toInt() && secondsUntilCleanup >= -bufferTime.toInt()

    if (shouldBlink) {
        val visible by BlinkManager.isVisible
        val blinkAlpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = blinkEasing),
            label = "blink-alpha"
        )
        val blinkScale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.88f,
            animationSpec = tween(durationMillis = 350, easing = blinkEasing),
            label = "blink-scale"
        )
        SFSymbol(
            name = incomingStop.mode.symbolName,
            size = 15.sp,
            color = latenessColor,
            modifier = modifier
                .alpha(blinkAlpha)
                .scale(blinkScale)
        )
    } else {
        val isNextDay =
            eventTime.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()
        val clockText = hourFormatter.format(eventTime.atZone(zone)) + if (isNextDay) "*" else ""

        val displayText = when {
            secondsDifference < 0 -> {
                val minutesAgo = ceil(abs(secondsDifference).toDouble() / 60.0).toInt()
                if (minutesAgo <= 1) "-1'" else clockText
            }

            secondsDifference <= 60 -> when {
                secondsDifference <= 30 -> "0'"
                secondsDifference <= 45 -> "<1'"
                else -> "1'"
            }

            else -> {
                val minutes = ceil(secondsDifference.toDouble() / 60.0).toInt()
                if (minutes < 100) "$minutes'" else clockText
            }
        }

        NumericText(
            text = displayText,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFeatureSettings = "tnum",
                textDecoration = if (incomingStop.cancelled) TextDecoration.LineThrough else null
            ),
            color = latenessColor,
            modifier = modifier
        )
    }
}
