package ch.cclerc.luxapp.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.settings.SectionHeader
import ch.cclerc.luxapp.ui.components.settings.SettingsCard
import ch.cclerc.luxapp.ui.theme.LuxTheme
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CreditItem(
    val name: String,
    val credit: String,
    val imageURL: String,
    val url: String
)

private data class PackageItem(
    val name: String,
    val credit: String,
    val url: String
)

private val creditItems = listOf(
    CreditItem(
        name = "Constantin Clerc",
        credit = "Développeur Principal",
        imageURL = "https://avatars.githubusercontent.com/u/102235607?v=4",
        url = "https://github.com/c22dev"
    ),
    CreditItem(
        name = "Valentin Busi Dias",
        credit = "Conseiller intuitivité et design",
        imageURL = "https://cclerc.ch/lux-assets/credits/val.png",
        url = "https://cclerc.ch/val"
    ),
    CreditItem(
        name = "Michail Kiourkos",
        credit = "Conseiller intuitivité et design",
        imageURL = "https://cclerc.ch/lux-assets/credits/michail.jpeg",
        url = "https://www.linkedin.com/in/michail-kiourkos-42025338a/"
    )
)

private val packageItems = listOf(
    PackageItem("MapLibre", "Rendu cartographique", "https://maplibre.org"),
    PackageItem("OpenFreeMap", "Tuiles vectorielles", "https://openfreemap.org"),
    PackageItem("Photon / OpenStreetMap", "Recherche de lieux", "https://photon.komoot.io"),
    PackageItem("Inter", "Police d'interface", "https://rsms.me/inter/"),
    PackageItem("Material Symbols", "Jeu d'icônes", "https://fonts.google.com/icons"),
    PackageItem("OkHttp", "Client réseau", "https://square.github.io/okhttp/"),
    PackageItem("kotlinx", "Coroutines et sérialisation", "https://github.com/Kotlin/kotlinx.serialization")
)

@Composable
fun CreditsView(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val context = LocalContext.current

    SettingsSubScreen(title = "Crédits", onBack = onBack, modifier = modifier) {
        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "person.3.fill",
                iconColor = colors.systemBlue,
                title = "Crédits",
                subtitle = "Ci-dessous une liste des crédits de l'application."
            )
            creditItems.forEachIndexed { index, item ->
                WebCreditCell(
                    name = item.name,
                    credit = item.credit,
                    imageURL = item.imageURL,
                    onClick = { openCustomTab(context, item.url) }
                )
                if (index < creditItems.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 82.dp),
                        thickness = 0.5.dp,
                        color = colors.separator
                    )
                }
            }
        }

        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "shippingbox.fill",
                iconColor = colors.systemGreen,
                title = "Bibliothèques",
                subtitle = "Projets open source utilisés par Lux."
            )
            packageItems.forEachIndexed { index, item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scaleClickable(haptic = false) { openCustomTab(context, item.url) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.name,
                            style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                            color = colors.label
                        )
                        Text(
                            text = item.credit,
                            style = LuxTheme.type.caption,
                            color = colors.secondaryLabel
                        )
                    }
                    SFSymbol(name = "arrow.up.right.square", size = 13.sp, color = colors.secondaryLabel)
                }
                if (index < packageItems.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 20.dp),
                        thickness = 0.5.dp,
                        color = colors.separator
                    )
                }
            }
        }
    }
}

@Composable
private fun WebCreditCell(
    name: String,
    credit: String,
    imageURL: String,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scaleClickable(haptic = false, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        RemoteAvatar(url = imageURL, size = 50.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.label
            )
            Text(
                text = credit,
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
        Spacer(Modifier.size(0.dp))
        SFSymbol(name = "arrow.up.right.square", size = 13.sp, color = colors.secondaryLabel)
    }
}

private val avatarCache = mutableMapOf<String, ImageBitmap>()

@Composable
private fun RemoteAvatar(url: String, size: Dp) {
    val colors = LuxTheme.colors
    var bitmap by remember(url) { mutableStateOf(avatarCache[url]) }

    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
        if (loaded != null) {
            avatarCache[url] = loaded
            bitmap = loaded
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.tertiarySystemFill)
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SFSymbol(name = "person.crop.circle.fill", size = 22.sp, color = colors.secondaryLabel)
        }
    }
}
