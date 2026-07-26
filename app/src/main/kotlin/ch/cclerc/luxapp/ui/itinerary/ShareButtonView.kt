package ch.cclerc.luxapp.ui.itinerary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.CalendarManager
import ch.cclerc.luxapp.core.CalendarResult
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.SavedItineraryStorage
import ch.cclerc.luxapp.data.SavedItineraryStorageError
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.luxtrip.ItinerarySharer
import ch.cclerc.luxcom.model.trip.Itinerary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val shareBaseUrl = "https://lux.cclerc.ch/share.html#"
private val ShareButtonSize = 45.dp
private val CompactSpacing = 10.dp
private const val StateAnimationMillis = 200
private const val CheckmarkVisibleMillis = 2000L

@Composable
fun ShareButtonView(
    itinerary: Itinerary,
    compact: Boolean,
    modifier: Modifier = Modifier,
    showCompactSaveAction: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isUploading by remember { mutableStateOf(false) }
    var isSavingItinerary by remember { mutableStateOf(false) }
    var showCheckmark by remember { mutableStateOf(false) }
    var isItinerarySavedLocally by remember { mutableStateOf(false) }
    var didSaveInCurrentSession by remember { mutableStateOf(false) }
    var hasResolvedSavedState by remember { mutableStateOf(false) }
    var showingShareDialog by remember { mutableStateOf(false) }
    var showingMenu by remember { mutableStateOf(false) }
    var saveAlertMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itinerary) {
        hasResolvedSavedState = false
        isItinerarySavedLocally = SavedItineraryStorage.isSavedAsync(itinerary)
        hasResolvedSavedState = true
    }

    LaunchedEffect(itinerary) {
        SavedItineraryStorage.changes.collect {
            isItinerarySavedLocally = SavedItineraryStorage.isSavedAsync(itinerary)
            hasResolvedSavedState = true
        }
    }

    fun flashCheckmark() {
        showCheckmark = true
        scope.launch {
            delay(CheckmarkVisibleMillis)
            showCheckmark = false
        }
    }

    fun uploadAndShare(copyOnly: Boolean) {
        if (isUploading) return
        isUploading = true
        showingShareDialog = false
        showingMenu = false
        scope.launch {
            val identifier = runCatching {
                ItinerarySharer.uploadItinerary(itinerary, Settings.luxTripShareExpiryTimeH)
            }.getOrNull()
            isUploading = false
            if (identifier == null) {
                HapticFeedback.error()
                saveAlertMessage = "Le partage de l'itinéraire a échoué. Assurez vous d'avoir une connexion stable."
                return@launch
            }
            val url = shareBaseUrl + identifier
            if (copyOnly) {
                copyToClipboard(context, url)
                HapticFeedback.success()
                flashCheckmark()
            } else {
                HapticFeedback.lightImpact()
                shareText(context, url)
            }
        }
    }

    fun saveItinerary(addToCalendar: Boolean) {
        if (isSavingItinerary) return
        isSavingItinerary = true
        showingShareDialog = false
        showingMenu = false

        val localSaveResult = SavedItineraryStorage.save(itinerary)

        scope.launch {
            val calendarResult = if (addToCalendar) {
                CalendarManager.saveItineraryToCalendar(context, itinerary)
            } else {
                null
            }

            isSavingItinerary = false

            val localSaved = localSaveResult.isSuccess
            val localError = localSaveResult.exceptionOrNull() as? SavedItineraryStorageError
            val calendarSaved = calendarResult is CalendarResult.Launched
            val calendarError = (calendarResult as? CalendarResult.Failed)?.error

            if (localSaved) {
                isItinerarySavedLocally = true
                didSaveInCurrentSession = true
            }

            if (localSaved || calendarSaved) {
                HapticFeedback.success()
                flashCheckmark()
            } else {
                HapticFeedback.error()
            }

            saveAlertMessage = when {
                localSaved && calendarSaved ->
                    if (compact) {
                        null
                    } else {
                        "L'itinéraire a bien été enregistré dans l'app et dans votre calendrier."
                    }

                localSaved && calendarResult == null -> null

                localSaved && calendarError != null ->
                    "L'itinéraire a été enregistré dans l'app, mais pas dans le calendrier : ${calendarError.description}"

                calendarSaved && localError != null ->
                    "L'itinéraire a été enregistré dans le calendrier, mais pas localement : ${localError.description}"

                else -> "Erreur lors de l'enregistrement : " +
                    (localError?.description ?: calendarError?.description ?: "Une erreur inconnue est survenue.")
            }
        }
    }

    val shouldShowCompactSaveButton = when {
        isSavingItinerary || showCheckmark || didSaveInCurrentSession -> true
        !hasResolvedSavedState -> false
        else -> !isItinerarySavedLocally
    }

    val compactSaveSymbolName = when {
        showCheckmark -> "checkmark"
        didSaveInCurrentSession && isItinerarySavedLocally -> "bookmark.fill"
        else -> "bookmark"
    }

    val compactSaveSymbolColor = if (showCheckmark || (didSaveInCurrentSession && isItinerarySavedLocally)) {
        LuxTheme.colors.systemGreen
    } else {
        LuxTheme.accent
    }

    if (compact) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(CompactSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                FloatingButtonContainer(
                    enabled = !isUploading && !isSavingItinerary,
                    onClick = {
                        HapticFeedback.lightImpact()
                        showingMenu = true
                    }
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = LuxTheme.accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        SFSymbol(
                            name = "square.and.arrow.up",
                            size = 17.sp,
                            color = LuxTheme.accent
                        )
                    }
                }

                DropdownMenu(expanded = showingMenu, onDismissRequest = { showingMenu = false }) {
                    ShareMenuItem("Partager l'entiereté", "link") { uploadAndShare(copyOnly = false) }
                    ShareMenuItem("Copier le lien", "doc.on.doc") { uploadAndShare(copyOnly = true) }
                    ShareMenuItem("Sauvegarder l'itinéraire", "bookmark") { saveItinerary(addToCalendar = false) }
                    ShareMenuItem("Ajouter au calendrier", "calendar") { saveItinerary(addToCalendar = true) }
                }
            }

            AnimatedVisibility(
                visible = showCompactSaveAction && shouldShowCompactSaveButton,
                enter = fadeIn(tween(StateAnimationMillis)),
                exit = fadeOut(tween(StateAnimationMillis))
            ) {
                FloatingButtonContainer(
                    enabled = !isUploading && !isSavingItinerary && !didSaveInCurrentSession,
                    onClick = {
                        HapticFeedback.lightImpact()
                        saveItinerary(addToCalendar = false)
                    }
                ) {
                    if (isSavingItinerary) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = LuxTheme.accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        SFSymbol(
                            name = compactSaveSymbolName,
                            size = 17.sp,
                            color = compactSaveSymbolColor
                        )
                    }
                }
            }
        }
    } else {
        val colors = LuxTheme.colors
        val shape = RoundedCornerShape(percent = 50)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .iosShadow(
                    color = Color.Black.copy(alpha = 0.1f),
                    blurRadius = 2.dp,
                    offsetY = 1.dp,
                    shape = shape
                )
                .clip(shape)
                .background(LuxMaterials.ultraThin(), shape)
                .border(0.5.dp, colors.label.copy(alpha = 0.1f), shape)
                .clickable(enabled = !isUploading && !isSavingItinerary) {
                    HapticFeedback.lightImpact()
                    showingShareDialog = true
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (isUploading || isSavingItinerary) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colors.label,
                    strokeWidth = 2.dp
                )
            } else {
                SFSymbol(name = "square.and.arrow.up", size = 16.sp, color = colors.label)
            }
            Text(
                text = when {
                    isUploading -> "Partage..."
                    isSavingItinerary -> "Enregistrement..."
                    else -> "Partager"
                },
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.label
            )
        }

        if (showingShareDialog) {
            ShareActionsDialog(
                onDismiss = { showingShareDialog = false },
                onShare = { uploadAndShare(copyOnly = false) },
                onCopy = { uploadAndShare(copyOnly = true) },
                onSave = { saveItinerary(addToCalendar = false) },
                onCalendar = { saveItinerary(addToCalendar = true) }
            )
        }
    }

    saveAlertMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { saveAlertMessage = null },
            title = { Text(text = "Sauvegarder l'itinéraire", style = LuxTheme.type.headline) },
            text = { Text(text = message, style = LuxTheme.type.subheadline) },
            confirmButton = {
                TextButton(onClick = { saveAlertMessage = null }) {
                    Text(text = "OK", color = LuxTheme.accent)
                }
            }
        )
    }
}

@Composable
private fun ShareActionsDialog(
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onCalendar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Partager l'itinéraire", style = LuxTheme.type.headline) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Choisissez comment vous souhaitez partager cet itinéraire.\n" +
                        "Le partage de l'ensemble de l'itinéraire requiert que son receveur ait l'app.",
                    style = LuxTheme.type.subheadline,
                    color = LuxTheme.colors.secondaryLabel
                )
                ShareDialogAction("Partager l'entiereté", onShare)
                ShareDialogAction("Copier le lien", onCopy)
                ShareDialogAction("Sauvegarder l'itinéraire", onSave)
                ShareDialogAction("Ajouter au calendrier", onCalendar)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Annuler", color = LuxTheme.accent)
            }
        }
    )
}

@Composable
private fun ShareDialogAction(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = LuxTheme.type.body,
        color = LuxTheme.accent,
        modifier = Modifier
            .clickable {
                HapticFeedback.lightImpact()
                onClick()
            }
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun ShareMenuItem(title: String, symbol: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = title, style = LuxTheme.type.body, color = LuxTheme.colors.label) },
        leadingIcon = { SFSymbol(name = symbol, size = 17.sp, color = LuxTheme.accent) },
        onClick = onClick
    )
}

@Composable
private fun FloatingButtonContainer(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(ShareButtonSize)
            .iosShadow(
                color = Color.Black.copy(alpha = 0.18f),
                blurRadius = 2.dp,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(LuxMaterials.ultraThick(), CircleShape)
            .border(0.5.dp, LuxTheme.colors.label.copy(alpha = 0.1f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun shareText(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "Partager l'itinéraire"))
}

private fun copyToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Lux", url))
}
