package ch.cclerc.luxapp.ui.stops

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.ui.components.HintIndicator
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.SearchResult
import kotlinx.coroutines.delay

@Composable
fun SectionTitleView(
    isSearchMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 17.dp, start = 25.dp, end = 25.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(
            name = if (isSearchMode) "magnifyingglass" else "location.fill",
            size = 17.sp,
            color = colors.label
        )
        Text(
            text = if (isSearchMode) "Résultats de recherche" else "À proximité",
            style = LuxTheme.type.headline,
            color = colors.label
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun StopsContentView(
    isSearchMode: Boolean,
    isLoading: Boolean,
    searchResults: List<SearchResult>,
    showMinCharactersMessage: Boolean,
    onOpenStop: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val isDark = LuxTheme.isDark
    var isShowingTip by remember { mutableStateOf(false) }
    val showsHint = remember { Progress.numOfTimesStopViewWasOpened == 1 }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start
        ) {
            SectionTitleView(isSearchMode = isSearchMode)

            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                thickness = 0.5.dp,
                color = colors.separator
            )

            StopsStatusMessageView(
                showMinCharactersMessage = showMinCharactersMessage,
                isLoading = isLoading,
                isEmpty = searchResults.isEmpty(),
                isSearchMode = isSearchMode
            )

            if (searchResults.isNotEmpty()) {
                StopsList(
                    stops = searchResults,
                    isSearching = isSearchMode,
                    onOpenStop = onOpenStop
                )
            }
        }

        if (showsHint) {
            val capsuleAlpha by animateFloatAsState(
                targetValue = if (isShowingTip) 1f else 0f,
                animationSpec = tween(durationMillis = 750, easing = EaseOut),
                label = "stopsHintCapsule"
            )

            LaunchedEffect(Unit) {
                delay(2000)
                isShowingTip = true
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 375.dp),
                contentAlignment = Alignment.Center
            ) {
                val shape = RoundedCornerShape(percent = 50)
                Box(
                    modifier = Modifier
                        .size(350.dp, 65.dp)
                        .graphicsLayer { alpha = capsuleAlpha }
                        .iosShadow(
                            color = Color.Black.copy(alpha = if (isDark) 0.3f else 0.15f),
                            blurRadius = 7.5.dp,
                            offsetY = 5.dp,
                            shape = shape
                        )
                        .background(colors.secondarySystemBackground, shape)
                        .background(
                            if (isDark) {
                                colors.secondarySystemBackground.copy(alpha = 0.7f)
                            } else {
                                Color.White
                            },
                            shape
                        )
                        .border(
                            0.75.dp,
                            if (isDark) {
                                colors.label.copy(alpha = 0.1f)
                            } else {
                                colors.systemGray.copy(alpha = 0.1f)
                            },
                            shape
                        )
                )
                HintIndicator(
                    icon = "chevron.compact.down",
                    message = "Glissez vers le bas pour retourner sur l'écran d'accueil",
                    delayMs = 2000,
                    durationMs = 10000,
                    onDismiss = { isShowingTip = false }
                )
            }
        }
    }
}
