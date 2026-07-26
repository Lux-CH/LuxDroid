package ch.cclerc.luxapp.ui.stops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.theme.LuxTheme

@Composable
fun StopsStatusMessageView(
    showMinCharactersMessage: Boolean,
    isLoading: Boolean,
    isEmpty: Boolean,
    isSearchMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        when {
            showMinCharactersMessage -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SFSymbol(
                        name = "character.cursor.ibeam",
                        size = 36.sp,
                        color = colors.secondaryLabel.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 40.dp)
                    )
                    Text(
                        text = "Entrez au moins 3 caractères pour rechercher",
                        style = LuxTheme.type.body,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            isLoading && isEmpty -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.secondaryLabel
                    )
                    Text(
                        text = if (isSearchMode) {
                            "Recherche en cours..."
                        } else {
                            "Chargement des arrêts à proximité..."
                        },
                        style = LuxTheme.type.footnote,
                        color = colors.secondaryLabel
                    )
                }
            }

            isEmpty -> {
                Text(
                    text = if (isSearchMode) {
                        "Aucun résultat trouvé."
                    } else {
                        "Aucun arrêt à proximité trouvé."
                    },
                    style = LuxTheme.type.body,
                    color = colors.systemGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
    }
}
