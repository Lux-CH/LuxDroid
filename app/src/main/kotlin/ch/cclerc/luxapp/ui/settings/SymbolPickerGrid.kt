package ch.cclerc.luxapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxTheme
import java.util.Locale

internal val placeSymbols: List<String> = listOf(
    "house", "house.fill", "building.fill", "building.2.fill", "building.columns.fill",
    "bed.double.fill", "book.fill", "fork.knife", "cross.case.fill", "cross.circle",
    "theatermasks.fill", "tree.fill", "leaf", "leaf.fill", "water.waves",
    "snowflake", "sun.max", "sun.max.fill", "moon", "moon.fill",
    "cloud", "cloud.fill", "sparkles", "star", "star.fill",
    "heart", "heart.fill", "heart.circle", "flag", "flag.fill",
    "flag.circle.fill", "flag.checkered", "bookmark", "bookmark.fill", "mappin",
    "mappin.circle.fill", "mappin.and.ellipse", "map", "map.fill", "location",
    "location.fill", "location.circle", "location.circle.fill", "location.north.fill",
    "signpost.right", "signpost.right.fill", "parkingsign.circle.fill", "fuelpump", "car.fill",
    "bus", "bus.fill", "bus.doubledecker", "tram", "tram.fill",
    "tram.tunnel.fill", "cablecar", "cablecar.fill", "ferry", "ferry.fill",
    "train.side.front.car", "airplane", "scooter", "figure.walk", "figure.walk.motion",
    "figure.run", "figure.outdoor.cycle", "figure.stairs", "figure.stand", "figure.roll",
    "figure.wave", "pawprint.fill", "hare", "hare.fill", "tortoise.fill",
    "ladybug", "ladybug.fill", "music.note", "camera", "camera.fill",
    "photo", "photo.fill", "printer", "phone", "phone.fill",
    "envelope", "envelope.fill", "paperplane", "paperplane.fill", "globe",
    "network", "wifi", "bolt", "bolt.fill", "lightbulb",
    "lightbulb.fill", "washer.fill", "shippingbox", "shippingbox.fill", "doc",
    "doc.fill", "doc.text", "doc.on.doc", "calendar", "calendar.badge.clock",
    "clock", "clock.fill", "clock.circle", "timer", "trophy",
    "trophy.fill", "ticket", "ticket.fill", "qrcode", "wrench",
    "wrench.fill", "gear", "gearshape", "gearshape.fill", "person",
    "person.fill", "person.2", "person.2.fill", "person.3", "person.3.fill",
    "person.crop.circle", "person.crop.circle.fill", "person.text.rectangle", "flask", "flask.fill",
    "chart.bar", "chart.bar.fill", "chart.line.uptrend.xyaxis", "list.bullet", "list.star",
    "bell", "bell.fill", "lock", "lock.fill", "lock.shield",
    "eye", "eye.fill", "magnifyingglass", "paintbrush", "paintbrush.fill",
    "paintpalette", "paintpalette.fill", "pencil", "square.and.pencil", "wand.and.stars",
    "shield.fill", "thermometer.medium", "speaker.wave.2", "speaker.wave.2.fill", "hand.wave",
    "hand.wave.fill", "hand.tap", "hand.tap.fill", "iphone", "battery.100",
    "link", "bookmark", "trash", "trash.fill", "checkmark.seal.fill"
).distinct()

private val symbolAliases: Map<String, List<String>> = mapOf(
    "house" to listOf("maison", "domicile", "home"),
    "house.fill" to listOf("maison", "domicile", "home"),
    "building.fill" to listOf("travail", "bureau", "immeuble"),
    "building.2.fill" to listOf("travail", "entreprise", "bureau"),
    "building.columns.fill" to listOf("banque", "musee", "universite", "ecole"),
    "book.fill" to listOf("ecole", "etude", "bibliotheque", "livre"),
    "fork.knife" to listOf("restaurant", "manger", "repas"),
    "cross.case.fill" to listOf("hopital", "medecin", "sante"),
    "bed.double.fill" to listOf("hotel", "dormir", "maison"),
    "tram" to listOf("tram", "transport"),
    "bus" to listOf("bus", "transport"),
    "train.side.front.car" to listOf("train", "gare"),
    "airplane" to listOf("avion", "aeroport"),
    "ferry" to listOf("mouette", "bateau"),
    "figure.walk" to listOf("marche", "pieton"),
    "figure.outdoor.cycle" to listOf("velo", "cycle"),
    "cart" to listOf("courses", "magasin"),
    "trophy" to listOf("sport", "salle"),
    "heart" to listOf("favori", "amour"),
    "star" to listOf("favori", "etoile"),
    "leaf" to listOf("parc", "nature"),
    "tree.fill" to listOf("parc", "nature", "foret"),
    "water.waves" to listOf("lac", "plage", "piscine"),
    "theatermasks.fill" to listOf("theatre", "cinema", "spectacle"),
    "music.note" to listOf("concert", "musique"),
    "flask.fill" to listOf("laboratoire", "science"),
    "person.3.fill" to listOf("amis", "famille"),
    "pawprint.fill" to listOf("animal", "veterinaire")
)

private fun matches(symbol: String, query: String): Boolean {
    if (query.isBlank()) return true
    val normalized = query.trim().lowercase(Locale.ROOT)
    if (symbol.replace('.', ' ').contains(normalized)) return true
    return symbolAliases[symbol]?.any { it.contains(normalized) } == true
}

@Composable
fun SymbolPickerGrid(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { placeSymbols.filter { matches(it, query) } }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.systemBackground)
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 26.dp, bottom = 12.dp)
        ) {
            Text(
                text = "Choisir un symbole",
                style = LuxTheme.type.headline,
                color = colors.label
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Fermer",
                style = LuxTheme.type.body,
                color = colors.secondaryLabel,
                modifier = Modifier.scaleClickable(haptic = false) { onDismiss() }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(colors.secondarySystemBackground, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            SFSymbol(name = "magnifyingglass", size = 15.sp, color = colors.secondaryLabel)
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = LuxTheme.type.body.copy(color = colors.label),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
                if (query.isEmpty()) {
                    Text(
                        text = "Rechercher un symbole",
                        style = LuxTheme.type.body,
                        color = colors.tertiaryLabel
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it }) { symbol ->
                val isSelected = symbol == selected
                val shape = RoundedCornerShape(16.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(
                            if (isSelected) accent.copy(alpha = 0.15f) else colors.secondarySystemGroupedBackground,
                            shape
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) accent else colors.hairline,
                            shape = shape
                        )
                        .scaleClickable { onSelect(symbol) }
                ) {
                    SFSymbol(
                        name = symbol,
                        size = 24.sp,
                        color = if (isSelected) accent else colors.label
                    )
                }
            }
        }
    }
}
