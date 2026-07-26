package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.search.RouteOptionsStore
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.ModernCard
import ch.cclerc.luxapp.ui.components.ModernCardStyle
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.PedestrianProfile
import ch.cclerc.luxcom.model.trip.RouteOptions
import kotlin.math.ceil

private val TRANSFER_TIME_OPTIONS = listOf(0, 2, 5, 7, 10)
private val WALKING_TIME_OPTIONS = listOf(300, 900, 1200, 1800, 5400)
private val PEDESTRIAN_SPEED_OPTIONS = listOf(1.0, 1.2, 1.7, 2.0, 3.0)
private val LEGACY_SPEED_REMAP = mapOf(4.4 to 3.0, 1.9 to 1.75)

@Composable
fun RouteOptionsView(
    routeOptions: RouteOptions,
    onDismiss: () -> Unit,
    onSave: (RouteOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent

    var maxTransfers by remember { mutableIntStateOf(routeOptions.maxTransfers) }
    var minTransferTime by remember { mutableIntStateOf(routeOptions.minTransferTime) }
    var pedestrianProfile by remember { mutableStateOf(routeOptions.pedestrianProfile) }
    var maxWalkingTime by remember { mutableIntStateOf(RouteOptionsStore.maxWalkingTime(routeOptions)) }
    var pedestrianSpeed by remember { mutableDoubleStateOf(RouteOptionsStore.pedestrianSpeed(routeOptions)) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    fun buildOptions(): RouteOptions = RouteOptionsStore.apply(
        options = routeOptions,
        maxTransfers = maxTransfers,
        minTransferTime = minTransferTime,
        pedestrianProfile = pedestrianProfile,
        maxWalkingTime = maxWalkingTime,
        pedestrianSpeed = pedestrianSpeed
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.systemBackgroundElevated,
                        colors.systemGroupedBackgroundElevated.copy(alpha = 0.3f),
                        colors.systemGroupedBackgroundElevated
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Annuler",
                style = LuxTheme.type.body,
                color = colors.secondaryLabel,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = PlainIndication) { onDismiss() }
                    .padding(vertical = 4.dp)
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "Appliquer",
                style = LuxTheme.type.body,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = PlainIndication) {
                        onSave(buildOptions())
                        HapticFeedback.mediumImpact()
                        onDismiss()
                    }
                    .padding(vertical = 4.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            RouteOptionsHeader(modifier = Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 22.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TransfersSection(
                    maxTransfers = maxTransfers,
                    onMaxTransfersChange = { maxTransfers = it },
                    minTransferTime = minTransferTime,
                    onMinTransferTimeChange = { minTransferTime = it }
                )

                WalkOptionsSection(
                    maxWalkingTime = maxWalkingTime,
                    onMaxWalkingTimeChange = { maxWalkingTime = it },
                    pedestrianSpeed = pedestrianSpeed,
                    onPedestrianSpeedChange = { pedestrianSpeed = it }
                )

                AccessibilitySection(
                    pedestrianProfile = pedestrianProfile,
                    onPedestrianProfileChange = { pedestrianProfile = it }
                )

                Box(
                    Modifier.scaleClickable(haptic = false) {
                        showResetConfirmation = true
                        HapticFeedback.lightImpact()
                    }
                ) {
                    ModernCard(style = ModernCardStyle.Accent, optionalColor = accent) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SFSymbol(name = "arrow.clockwise", size = 17.sp, color = accent)
                            Text(
                                text = "Rétablir les valeurs par défaut",
                                style = LuxTheme.type.subheadline,
                                fontWeight = FontWeight.Medium,
                                color = accent
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = {
                Text(
                    text = "Rétablir les valeurs par défaut",
                    style = LuxTheme.type.headline,
                    color = colors.label
                )
            },
            text = {
                Text(
                    text = "Toutes les options seront réinitialisées aux paramètres par défaut. Cette action est irréversible.",
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    maxTransfers = RouteOptionsStore.DEFAULT_MAX_TRANSFERS
                    minTransferTime = RouteOptionsStore.DEFAULT_MIN_TRANSFER_TIME
                    pedestrianProfile = RouteOptionsStore.DEFAULT_PEDESTRIAN_PROFILE
                    maxWalkingTime = RouteOptionsStore.DEFAULT_MAX_WALKING_TIME
                    pedestrianSpeed = RouteOptionsStore.DEFAULT_PEDESTRIAN_SPEED
                    showResetConfirmation = false
                    onSave(
                        RouteOptionsStore.apply(
                            options = routeOptions,
                            maxTransfers = RouteOptionsStore.DEFAULT_MAX_TRANSFERS,
                            minTransferTime = RouteOptionsStore.DEFAULT_MIN_TRANSFER_TIME,
                            pedestrianProfile = RouteOptionsStore.DEFAULT_PEDESTRIAN_PROFILE,
                            maxWalkingTime = RouteOptionsStore.DEFAULT_MAX_WALKING_TIME,
                            pedestrianSpeed = RouteOptionsStore.DEFAULT_PEDESTRIAN_SPEED,
                            transitModes = null
                        )
                    )
                    HapticFeedback.mediumImpact()
                }) {
                    Text("Rétablir", color = colors.systemRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Annuler", color = colors.secondaryLabel)
                }
            },
            containerColor = colors.secondarySystemBackgroundElevated
        )
    }
}

@Composable
private fun RouteOptionsHeader(modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Options d'itinéraire",
                style = LuxTheme.type.title2,
                fontWeight = FontWeight.Bold,
                color = colors.label
            )
            Text(
                text = "Ajustez votre recherche d'itinéraires",
                style = LuxTheme.type.subheadline,
                color = colors.secondaryLabel
            )
        }

        Box(
            modifier = Modifier
                .size(60.dp)
                .background(LuxMaterials.ultraThin(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "slider.horizontal.3", size = 28.sp, color = accent)
        }
    }
}

@Composable
private fun OptionHeader(title: String, icon: String) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(name = icon, size = 12.sp, color = accent)
        Text(
            text = title,
            style = LuxTheme.type.subheadline,
            fontWeight = FontWeight.SemiBold,
            color = colors.label
        )
    }
}

@Composable
private fun OptionTitle(title: String, description: String) {
    val colors = LuxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = LuxTheme.type.subheadline,
            fontWeight = FontWeight.Medium,
            color = colors.label
        )
        Text(
            text = description,
            style = LuxTheme.type.caption,
            color = colors.secondaryLabel
        )
    }
}

@Composable
private fun TransfersSection(
    maxTransfers: Int,
    onMaxTransfersChange: (Int) -> Unit,
    minTransferTime: Int,
    onMinTransferTimeChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionHeader(title = "Transferts", icon = "arrow.triangle.2.circlepath")

        ModernCard {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionTitle(
                        title = "Nombre maximal de correspondances",
                        description = "Fixez le nombre maximum de correspondances autorisées"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (0..5).forEach { number ->
                            TransferCountButton(
                                number = number,
                                isSelected = maxTransfers == number,
                                onTap = {
                                    onMaxTransfersChange(number)
                                    HapticFeedback.lightImpact()
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = LuxTheme.colors.separator.copy(alpha = 0.5f))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionTitle(
                        title = "Temps minimum entre correspondances",
                        description = "Fixez le temps d'attente minimal entre deux correspondances pour vous assurer de réaliser vos connections dans les temps"
                    )
                    TransferTimeSelector(
                        selectedTime = minTransferTime,
                        onSelect = onMinTransferTimeChange
                    )
                }
            }
        }
    }
}

@Composable
private fun WalkOptionsSection(
    maxWalkingTime: Int,
    onMaxWalkingTimeChange: (Int) -> Unit,
    pedestrianSpeed: Double,
    onPedestrianSpeedChange: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionHeader(title = "Options de marche", icon = "figure.walk")

        ModernCard {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionTitle(
                        title = "Durée maximale de marche",
                        description = "Fixez la durée maximale que vous acceptez de parcourir à pied"
                    )
                    WalkingTimeSelector(
                        selectedTime = maxWalkingTime,
                        onSelect = onMaxWalkingTimeChange
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = LuxTheme.colors.separator.copy(alpha = 0.5f))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionTitle(
                        title = "Rythme de marche",
                        description = "Choisissez votre rythme de marche habituel pour des estimations plus précises"
                    )
                    PedestrianSpeedSelector(
                        selectedSpeed = pedestrianSpeed,
                        onSelect = onPedestrianSpeedChange
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibilitySection(
    pedestrianProfile: PedestrianProfile,
    onPedestrianProfileChange: (PedestrianProfile) -> Unit
) {
    val colors = LuxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OptionHeader(title = "Accessibilité", icon = "figure.roll")

        ModernCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Mode de déplacement à pied",
                    style = LuxTheme.type.subheadline,
                    fontWeight = FontWeight.Medium,
                    color = colors.label
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccessibilityProfileButton(
                        title = "À pied",
                        iconName = "figure.walk",
                        isSelected = pedestrianProfile == PedestrianProfile.FOOT,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            onPedestrianProfileChange(PedestrianProfile.FOOT)
                            HapticFeedback.lightImpact()
                        }
                    )
                    AccessibilityProfileButton(
                        title = "En fauteuil roulant",
                        iconName = "figure.roll",
                        isSelected = pedestrianProfile == PedestrianProfile.WHEELCHAIR,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            onPedestrianProfileChange(PedestrianProfile.WHEELCHAIR)
                            HapticFeedback.lightImpact()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferCountButton(
    number: Int,
    isSelected: Boolean,
    onTap: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val fill by animateColorAsState(
        if (isSelected) accent else colors.tertiarySystemFill,
        LuxSprings.springFor(0.3, 0.7),
        label = "transferCountFill"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(fill, CircleShape)
            .border(0.5.dp, colors.hairline, CircleShape)
            .scaleClickable(haptic = false) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number",
            style = LuxTheme.type.callout,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color.White else colors.label
        )
    }
}

@Composable
private fun TransferTimeSelector(
    selectedTime: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r12)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TRANSFER_TIME_OPTIONS.forEach { minutes ->
            val isSelected = selectedTime == minutes
            val fill by animateColorAsState(
                if (isSelected) accent else colors.tertiarySystemFill,
                LuxSprings.springFor(0.3, 0.7),
                label = "transferTimeFill"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(fill, shape)
                    .border(0.5.dp, colors.hairline, shape)
                    .scaleClickable(haptic = false) {
                        onSelect(minutes)
                        HapticFeedback.lightImpact()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${minutes}m",
                    style = LuxTheme.type.subheadline,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else colors.label
                )
            }
        }
    }
}

@Composable
private fun WalkingTimeSelector(
    selectedTime: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r12)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WALKING_TIME_OPTIONS.forEach { seconds ->
            val isSelected = selectedTime == seconds
            val fill by animateColorAsState(
                if (isSelected) accent else colors.tertiarySystemFill,
                LuxSprings.springFor(0.3, 0.7),
                label = "walkingTimeFill"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(fill, shape)
                    .border(0.5.dp, colors.hairline, shape)
                    .scaleClickable(haptic = false) {
                        onSelect(seconds)
                        HapticFeedback.lightImpact()
                    }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${seconds / 60}m",
                    style = LuxTheme.type.subheadline,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else colors.label
                )
                Text(
                    text = walkingDescription(seconds),
                    style = LuxTheme.type.caption2,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PedestrianSpeedSelector(
    selectedSpeed: Double,
    onSelect: (Double) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val isSpeedInvalid = !PEDESTRIAN_SPEED_OPTIONS.contains(selectedSpeed)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = isSpeedInvalid,
            enter = scaleIn(LuxSprings.Snappy) + fadeIn(LuxSprings.Snappy),
            exit = scaleOut(LuxSprings.Snappy) + fadeOut(LuxSprings.Snappy)
        ) {
            ModernCard(style = ModernCardStyle.Accent, optionalColor = accent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SFSymbol(name = "exclamationmark.triangle.fill", size = 17.sp, color = accent)
                    Text(
                        text = "La vitesse choisie n'est plus sélectionnable.",
                        style = LuxTheme.type.subheadline,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .background(accent, RoundedCornerShape(LuxShapes.r12))
                            .scaleClickable(haptic = false) {
                                LEGACY_SPEED_REMAP[selectedSpeed]?.let { onSelect(it) }
                                HapticFeedback.lightImpact()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Changer",
                            style = LuxTheme.type.subheadline,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PEDESTRIAN_SPEED_OPTIONS.forEach { speed ->
                val isSelected = selectedSpeed == speed
                val fill by animateColorAsState(
                    if (isSelected) accent else colors.tertiarySystemFill,
                    LuxSprings.springFor(0.3, 0.7),
                    label = "speedFill"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .scaleClickable(haptic = false) {
                            onSelect(speed)
                            HapticFeedback.lightImpact()
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(fill, CircleShape)
                            .border(0.5.dp, colors.hairline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        SFSymbol(
                            name = speedIcon(speed),
                            size = 18.sp,
                            color = if (isSelected) Color.White else colors.label
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = speedDescription(speed),
                            style = LuxTheme.type.caption2,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) accent else colors.secondaryLabel,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = formatSpeedInKmh(speed),
                            style = LuxTheme.type.caption2.copy(fontSize = 9.sp),
                            color = if (isSelected) accent.copy(alpha = 0.8f) else colors.tertiaryLabel,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessibilityProfileButton(
    title: String,
    iconName: String,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r12)
    val fill by animateColorAsState(
        if (isSelected) accent else colors.tertiarySystemFill,
        LuxSprings.springFor(0.3, 0.7),
        label = "profileFill"
    )
    val outline by animateColorAsState(
        if (isSelected) accent else Color.Transparent,
        LuxSprings.springFor(0.2, 1.0),
        label = "profileOutline"
    )
    Column(
        modifier = modifier
            .border(2.dp, outline, shape)
            .scaleClickable(haptic = false) { onTap() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(fill, CircleShape)
                .border(0.5.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(
                name = iconName,
                size = 28.sp,
                color = if (isSelected) Color.White else colors.label
            )
        }

        Text(
            text = title,
            style = LuxTheme.type.subheadline.copy(fontSize = 14.sp),
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = if (isSelected) accent else colors.label,
            textAlign = TextAlign.Center
        )
    }
}

private fun walkingDescription(seconds: Int): String = when (seconds) {
    300 -> "Courte"
    900 -> "Standard"
    1200 -> "Modérée"
    1800 -> "Longue"
    5400 -> "Étendue"
    else -> "Standard"
}

private fun speedDescription(speed: Double): String = when (speed) {
    1.0 -> "Lent"
    1.2 -> "Normal"
    1.7 -> "Active"
    2.0 -> "Rapide"
    3.0 -> "Sprint"
    else -> "Normal"
}

private fun speedIcon(speed: Double): String = when (speed) {
    1.0 -> "tortoise.fill"
    1.2 -> "figure.walk"
    1.7 -> "figure.walk.motion"
    2.0 -> "figure.run"
    3.0 -> "hare.fill"
    else -> "figure.walk"
}

private fun formatSpeedInKmh(speed: Double): String = "${ceil(speed * 3.6).toInt()} km/h"
