package ch.cclerc.luxapp.ui.stops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.rememberConnections
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.components.MorePill
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.geo.calculateDistance
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun StopRowView(
    stop: SearchResult,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val location by LocationService.location.collectAsState()
    val heading by LocationService.heading.collectAsState()
    val connections = if (isSearching) {
        emptyList()
    } else {
        rememberConnections(stop.id).value
    }

    val userLocation = location
    val relativeAngle = if (!isSearching && userLocation != null && heading != null) {
        calculateRelativeAngle(
            userLat = userLocation.latitude,
            userLon = userLocation.longitude,
            stopLat = stop.lat,
            stopLon = stop.lon,
            deviceHeading = heading!!.toDouble()
        )
    } else {
        0.0
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol("signpost.right", 17.sp, colors.secondaryLabel)
                Text(
                    text = stop.name,
                    style = LuxTheme.type.body,
                    color = colors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isSearching) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    connections.take(5).forEach { routeName ->
                        LinePill(line = routeName, agencyId = null, mode = TransportationMode.BUS)
                    }
                    if (connections.size > 5) {
                        MorePill()
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (userLocation != null) {
                val distance = calculateDistance(
                    userLat = userLocation.latitude,
                    userLon = userLocation.longitude,
                    stopLat = stop.lat,
                    stopLon = stop.lon
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SFSymbol(
                        name = if (isSearching) "location.fill" else "location.north.fill",
                        size = 12.sp,
                        color = colors.secondaryLabel,
                        modifier = Modifier.graphicsLayer { rotationZ = relativeAngle.toFloat() }
                    )
                    Text(
                        text = formatDistance(distance),
                        style = LuxTheme.type.subheadline,
                        color = colors.secondaryLabel
                    )
                }
            }
            SFSymbol("chevron.right", 14.sp, colors.secondaryLabel, weight = 600)
        }
    }
}

private fun calculateRelativeAngle(
    userLat: Double,
    userLon: Double,
    stopLat: Double,
    stopLon: Double,
    deviceHeading: Double
): Double {
    val lat1 = userLat * PI / 180
    val lat2 = stopLat * PI / 180
    val deltaLon = (stopLon - userLon) * PI / 180

    val y = sin(deltaLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

    val bearing = atan2(y, x) * 180 / PI
    val normalizedBearing = if (bearing >= 0) bearing else bearing + 360

    val relativeAngle = normalizedBearing - deviceHeading
    return if (relativeAngle >= 0) relativeAngle else relativeAngle + 360
}

private val kmDistanceFormatter: NumberFormat = NumberFormat
    .getNumberInstance(Locale.getDefault())
    .apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

fun formatDistance(distance: Double): String {
    return if (distance >= 1000) {
        val km = distance / 1000.0
        val roundedKm = (km * 10).roundToInt() / 10.0
        "${kmDistanceFormatter.format(roundedKm)}km"
    } else {
        "${distance.roundToInt()}m"
    }
}
