package ch.cclerc.luxapp.ui.crowdback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.api.sendLCBReport
import ch.cclerc.luxcom.model.feedback.Report
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.trip.Leg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val wizardAttributes = listOf(
    ReportAttribute.CROWD,
    ReportAttribute.CLEAN,
    ReportAttribute.HEAT,
    ReportAttribute.NOISE
)

@Composable
fun ReportWizard(
    leg: Leg,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val scope = rememberCoroutineScope()
    val location by LocationService.location.collectAsStateWithLifecycle()

    var currentStep by remember { mutableIntStateOf(0) }
    val attributeValues = remember {
        mutableStateMapOf<ReportAttribute, Int>().apply {
            wizardAttributes.forEach { put(it, 3) }
        }
    }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var submittingAttributeIndex by remember { mutableIntStateOf(0) }
    var failedReportsCount by remember { mutableIntStateOf(0) }

    val overlayVisible = showSuccess || showError || isSubmitting
    val blurRadius by animateDpAsState(if (overlayVisible) 3.dp else 0.dp, tween(300))

    fun submitAllReports() {
        val tripId = leg.tripId ?: return
        val routeShortName = leg.routeShortName ?: return
        val current = location ?: LocationService.location.value ?: return

        isSubmitting = true
        submittingAttributeIndex = 0
        failedReportsCount = 0

        scope.launch {
            var successCount = 0

            wizardAttributes.forEachIndexed { index, attribute ->
                val level = attributeValues[attribute] ?: return@forEachIndexed
                submittingAttributeIndex = index

                val report = Report(
                    tripId = tripId,
                    routeShortName = routeShortName,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    attribute = attribute,
                    level = level
                )

                try {
                    sendLCBReport(report)
                    successCount += 1
                    delay(200)
                } catch (error: Exception) {
                    failedReportsCount += 1
                }
            }

            isSubmitting = false

            if (successCount > 0) {
                HapticFeedback.success()
                showSuccess = true
                delay(2500)
                onDismiss()
            } else {
                HapticFeedback.error()
                showError = true
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Signaler une situation",
                        style = LuxTheme.type.title3.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.label
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Annuler",
                        style = LuxTheme.type.body,
                        color = colors.secondaryLabel,
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = PlainIndication,
                            onClick = onDismiss
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinePill(
                        line = leg.routeShortName ?: "",
                        agencyId = leg.agencyId,
                        mode = leg.mode,
                        width = 35.dp,
                        height = 22.dp,
                        fontSize = 11.sp
                    )
                    Text(
                        text = leg.headsign ?: "",
                        style = LuxTheme.type.subheadline,
                        color = colors.secondaryLabel
                    )
                    Spacer(Modifier.weight(1f))
                }

                ReportProgressIndicator(
                    currentStep = currentStep,
                    totalSteps = wizardAttributes.size,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (currentStep < wizardAttributes.size) {
                val attribute = wizardAttributes[currentStep]

                AttributeStepView(
                    attribute = attribute,
                    selectedLevel = attributeValues[attribute] ?: 3,
                    onLevelChange = { level ->
                        HapticFeedback.selectionChanged()
                        attributeValues[attribute] = level
                    },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(colors.systemGray6, RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = null,
                                    indication = PlainIndication,
                                    onClick = {
                                        HapticFeedback.lightImpact()
                                        currentStep -= 1
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Précédent",
                                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                                color = colors.secondaryLabel
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(accent, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = null,
                                indication = PlainIndication,
                                enabled = !isSubmitting,
                                onClick = {
                                    if (currentStep == wizardAttributes.size - 1) {
                                        HapticFeedback.mediumImpact()
                                        submitAllReports()
                                    } else {
                                        HapticFeedback.lightImpact()
                                        currentStep += 1
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentStep == wizardAttributes.size - 1) "Terminer" else "Suivant",
                            style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                            color = Color.White
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSubmitting,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            SubmittingOverlay(
                currentAttribute = wizardAttributes.getOrElse(submittingAttributeIndex) {
                    wizardAttributes.first()
                },
                progress = submittingAttributeIndex.toFloat() / wizardAttributes.size.toFloat(),
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showSuccess,
            enter = scaleIn(LuxSprings.springFor(0.5, 0.8)) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            SuccessOverlay(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = showError,
            enter = scaleIn(LuxSprings.springFor(0.5, 0.8)) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            ErrorOverlay(
                failedReports = failedReportsCount,
                onDismiss = {
                    showError = false
                    onDismiss()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
