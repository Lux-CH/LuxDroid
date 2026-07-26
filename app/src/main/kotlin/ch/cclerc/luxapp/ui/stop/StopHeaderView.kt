package ch.cclerc.luxapp.ui.stop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.rememberConnections
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import java.net.URLEncoder

@Composable
fun StopHeaderView(
    stop: SearchResult,
    onPlanTrip: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val clipboard = LocalClipboardManager.current
    val connections by rememberConnections(stop.id)
    var showAlert by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SFSymbol(name = "signpost.right", size = 20.sp, color = colors.secondaryLabel)
                Text(
                    text = stop.name,
                    style = LuxTheme.type.title3,
                    fontWeight = FontWeight.Bold,
                    color = colors.label
                )
            }

            if (connections.size > 1) {
                Row(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = size.width - 30.dp.toPx(),
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    connections.forEach { connection ->
                        LinePill(line = connection, agencyId = null, mode = TransportationMode.BUS)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (Settings.showDebug) {
                Box(
                    modifier = Modifier
                        .size(40.5.dp, 35.dp)
                        .clip(CircleShape)
                        .background(colors.secondarySystemFill.copy(alpha = colors.secondarySystemFill.alpha * 0.5f))
                        .border(0.5.dp, colors.label.copy(alpha = 0.1f), CircleShape)
                        .scaleClickable { showAlert = true },
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(name = "link", size = 13.3.sp, color = accent)
                }
            }

            Box(
                modifier = Modifier
                    .size(61.dp, 52.5.dp)
                    .clip(CircleShape)
                    .background(colors.secondarySystemFill.copy(alpha = colors.secondarySystemFill.alpha * 0.5f))
                    .border(0.5.dp, colors.label.copy(alpha = 0.1f), CircleShape)
                    .scaleClickable { onPlanTrip(stop) },
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = "arrow.triangle.turn.up.right.circle", size = 20.sp, color = accent)
            }
        }
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text("Entrez le nom du lieu", style = LuxTheme.type.headline, color = colors.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Afin de partager un lieu via Lux, vous devez entrer son nom. Le lieu sera partagé pour l'arrêt ouvert.",
                        style = LuxTheme.type.footnote,
                        color = colors.secondaryLabel
                    )
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        cursorBrush = SolidColor(accent),
                        textStyle = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            color = colors.label
                        ),
                        modifier = Modifier
                            .background(colors.secondarySystemFill, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(placeShareLink(name, stop.id)))
                    showAlert = false
                }) {
                    Text("Copier le lien dans la presse-papier", color = accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlert = false }) {
                    Text("Annuler", color = accent)
                }
            },
            containerColor = colors.secondarySystemBackgroundElevated
        )
    }
}

private fun placeShareLink(name: String, stopId: String): String {
    val encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    val suffix = stopId.replace("ch_Parent", "").replace("ch_", "")
    return "https://lux.cclerc.ch/place.html#$encodedName-$suffix"
}
