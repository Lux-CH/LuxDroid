package ch.cclerc.luxapp.ui.map

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.ui.theme.legWalkColor
import ch.cclerc.luxapp.viewmodel.WalkingAnnotation

val WalkingDotSize = 12.dp

private const val WALKING_MOVE_DURATION_MS = 1000

val WalkingMoveSpec: AnimationSpec<LatLng> =
    tween(durationMillis = WALKING_MOVE_DURATION_MS, easing = LinearEasing)

@Composable
fun rememberAnimatedWalkingAnnotations(
    walking: List<WalkingAnnotation>
): List<WalkingAnnotation> = rememberAnimatedCoordinates(
    items = walking,
    spec = WalkingMoveSpec,
    idOf = { it.id },
    coordinateOf = { it.coordinate },
    withCoordinate = { annotation, coordinate -> annotation.copy(coordinate = coordinate) }
)

@Composable
fun WalkingDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(WalkingDotSize)
            .background(legWalkColor, CircleShape)
    )
}
