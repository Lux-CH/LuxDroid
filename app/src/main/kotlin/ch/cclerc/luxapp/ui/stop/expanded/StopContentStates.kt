package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.components.IosActivityIndicator
import ch.cclerc.luxapp.ui.anim.IosTransitions
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme

@Composable
fun StopContentEmptyView(
    animateIn: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Aucun départ à venir.",
            style = LuxTheme.type.body,
            color = colors.systemGray,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .staggeredEntrance(
                    index = 0,
                    visible = animateIn,
                    delayPerItemMs = 0,
                    baseDelayMs = 100,
                    fromOffsetY = 0.dp,
                    fromScale = 0.7f,
                    spec = LuxSprings.SlowEntrance
                )
                .padding(16.dp)
        )

        ErrorText(errorMessage)
    }
}

@Composable
fun StopContentLoadingView(
    animateIn: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .staggeredEntrance(
                    index = 0,
                    visible = animateIn,
                    delayPerItemMs = 0,
                    baseDelayMs = 100,
                    fromOffsetY = 0.dp,
                    fromScale = 0.5f,
                    spec = LuxSprings.SlowEntrance
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosActivityIndicator(
                size = 18.dp,
                color = colors.secondaryLabel
            )
            Text(
                text = "Chargement des départs...",
                style = LuxTheme.type.footnote,
                color = colors.secondaryLabel
            )
        }

        ErrorText(errorMessage)
    }
}

@Composable
private fun ErrorText(errorMessage: String?) {
    val colors = LuxTheme.colors
    AnimatedVisibility(
        visible = errorMessage != null,
        enter = IosTransitions.scaleFadeEnter(),
        exit = IosTransitions.scaleFadeExit()
    ) {
        Text(
            text = errorMessage.orEmpty(),
            style = LuxTheme.type.body,
            color = colors.systemRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}
