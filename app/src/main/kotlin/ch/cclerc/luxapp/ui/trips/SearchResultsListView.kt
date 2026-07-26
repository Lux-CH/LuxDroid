package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.pulse
import ch.cclerc.luxapp.ui.anim.pulseScale
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxapp.viewmodel.TripsSearchViewModel
import kotlinx.coroutines.delay

internal fun Modifier.bottomFadeMask(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - height.toPx(),
                endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = LuxTheme.colors.secondaryLabel,
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun cardSurface(modifier: Modifier = Modifier): Modifier {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(LuxShapes.r20)
    return modifier
        .iosShadow(
            color = Color.Black.copy(alpha = if (colors.isDark) 0.25f else 0.1f),
            blurRadius = 12.dp,
            offsetY = 4.dp,
            shape = shape
        )
        .background(
            if (colors.isDark) colors.tertiarySystemBackground else colors.secondarySystemBackground,
            shape
        )
        .border(0.5.dp, colors.label.copy(alpha = if (colors.isDark) 0.08f else 0.06f), shape)
        .clip(shape)
}

@Composable
fun SearchResultsContent(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier
) {
    val activeField by viewModel.activeField.collectAsState()
    val fromQuery by viewModel.fromQuery.collectAsState()
    val toQuery by viewModel.toQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val showMinCharacters by viewModel.showMinCharactersMessage.collectAsState()
    val currentPositionAvailable by LocationService.location.collectAsState()

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        when {
            activeField != SearchField.NONE && fromQuery.isEmpty() && toQuery.isEmpty() &&
                currentPositionAvailable != null -> CurrentLocationOption(viewModel)
            showMinCharacters -> MinCharactersView()
            results.isNotEmpty() -> SearchResultsListView(viewModel)
            else -> EmptySearchView()
        }
    }
}

@Composable
fun SearchResultsListView(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier
) {
    val results by viewModel.searchResults.collectAsState()
    var appear by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { appear = false }
    }
    LaunchedEffect(Unit) {
        delay(100)
        appear = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("Résultats")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bottomFadeMask(28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = cardSurface(Modifier.padding(horizontal = 16.dp))
            ) {
                results.forEachIndexed { index, result ->
                    SearchResultRow(
                        result = result,
                        modifier = Modifier
                            .staggeredEntrance(index = index, visible = appear)
                            .fillMaxWidth()
                            .scaleClickable { viewModel.selectLocation(result) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    if (index < results.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 70.dp),
                            thickness = 0.5.dp,
                            color = LuxTheme.colors.separator
                        )
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun CurrentLocationOption(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r20)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("Localisation")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .iosShadow(
                    color = Color.Black.copy(alpha = if (colors.isDark) 0.25f else 0.07f),
                    blurRadius = 14.dp,
                    offsetY = 4.dp,
                    shape = shape
                )
                .background(
                    if (colors.isDark) colors.tertiarySystemBackground else colors.systemBackground,
                    shape
                )
                .clip(shape)
                .scaleClickable { viewModel.selectCurrentPosition() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchResultIcon(symbolName = "location.fill", color = accent)
            Text(
                text = "Position Actuelle",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.label
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
            thickness = 0.5.dp,
            color = colors.separator
        )
    }
}

@Composable
fun EmptySearchView(modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SFSymbol(
            name = "magnifyingglass",
            size = 40.sp,
            color = colors.secondaryLabel.copy(alpha = 0.6f),
            weight = 400,
            modifier = Modifier
                .padding(top = 40.dp)
                .pulseScale(from = 1f, to = 1.05f, durationMs = 1200)
        )
        Text(
            text = "Recherchez un lieu ou une adresse",
            style = LuxTheme.type.body,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MinCharactersView(modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SFSymbol(
            name = "character.cursor.ibeam",
            size = 40.sp,
            color = colors.secondaryLabel.copy(alpha = 0.6f),
            weight = 400,
            modifier = Modifier.padding(top = 40.dp)
        )
        Text(
            text = "Entrez au moins 3 caractères pour rechercher",
            style = LuxTheme.type.body,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .pulse(fromAlpha = 1f, toAlpha = 0.7f, durationMs = 1500)
        )
    }
}

@Composable
fun NoResultsView(modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        BouncingSymbol(
            name = "calendar.badge.exclamationmark",
            size = 46.sp,
            color = colors.secondaryLabel
        )
        Text(
            text = "Aucun itinéraire trouvé",
            style = LuxTheme.type.headline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Essayez de modifier vos critères de recherche, vos options ou l'heure de départ.",
            style = LuxTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun BouncingSymbol(
    name: String,
    size: TextUnit,
    color: Color
) {
    val transition = rememberInfiniteTransition(label = "bounce")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                0f at 0
                0f at 1200
                -6f at 1400
                0f at 1600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bounceOffset"
    )
    SFSymbol(
        name = name,
        size = size,
        color = color,
        weight = 500,
        modifier = Modifier.graphicsLayer { translationY = offset }
    )
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val capsule = RoundedCornerShape(LuxShapes.r50)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SFSymbol(
            name = "exclamationmark.triangle.fill",
            size = 46.sp,
            color = colors.systemOrange,
            weight = 500,
            modifier = Modifier.pulse(fromAlpha = 1f, toAlpha = 0.4f, durationMs = 1143)
        )
        Text(
            text = message,
            style = LuxTheme.type.headline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = "Il est possible que le serveur soit actuellement indisponible.",
            style = LuxTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(capsule)
                .background(
                    if (colors.isDark) colors.tertiarySystemBackground else colors.systemBackground,
                    capsule
                )
                .scaleClickable(haptic = false) {
                    HapticFeedback.mediumImpact()
                    onRetry()
                }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Réessayer",
                style = LuxTheme.type.body,
                color = LuxTheme.accent
            )
        }
    }
}
