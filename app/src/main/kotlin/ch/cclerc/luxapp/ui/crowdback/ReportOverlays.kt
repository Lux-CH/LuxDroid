package ch.cclerc.luxapp.ui.crowdback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.feedback.ReportAttribute

@Composable
private fun OverlayCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .padding(horizontal = 40.dp)
            .iosShadow(
                color = Color.Black.copy(alpha = 0.1f),
                blurRadius = 20.dp,
                offsetY = 10.dp,
                shape = shape
            )
            .background(LuxMaterials.regular(), shape)
            .padding(24.dp)
    ) {
        content()
    }
}

@Composable
fun SubmittingOverlay(
    currentAttribute: ReportAttribute,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val animatedProgress by animateFloatAsState(progress, tween(500))

    Box(modifier, contentAlignment = Alignment.Center) {
        OverlayCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(60.dp)) {
                        val strokeWidth = 6.dp.toPx()
                        val inset = strokeWidth / 2f
                        drawCircle(
                            color = colors.systemGray5,
                            radius = (size.minDimension - strokeWidth) / 2f,
                            style = Stroke(width = strokeWidth)
                        )
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - strokeWidth,
                                size.height - strokeWidth
                            ),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    SFSymbol(
                        name = currentAttribute.iconName,
                        size = 20.sp,
                        color = accent
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rapport en cours...",
                        style = LuxTheme.type.headline,
                        color = colors.label
                    )
                    Text(
                        text = "Rapport: ${currentAttribute.displayName}",
                        style = LuxTheme.type.subheadline,
                        color = colors.secondaryLabel
                    )
                }
            }
        }
    }
}

@Composable
fun SuccessOverlay(modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors

    Box(modifier, contentAlignment = Alignment.Center) {
        OverlayCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(colors.systemGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(
                        name = "checkmark",
                        size = 28.sp,
                        color = Color.White,
                        weight = 700
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rapports envoyés !",
                        style = LuxTheme.type.headline,
                        color = colors.label
                    )
                    Text(
                        text = "Vos rapports ont été transmis avec succès.\nMerci pour votre contribution !",
                        style = LuxTheme.type.subheadline,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorOverlay(
    failedReports: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors

    Box(modifier, contentAlignment = Alignment.Center) {
        OverlayCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(colors.systemRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(
                        name = "exclamationmark.triangle.fill",
                        size = 24.sp,
                        color = Color.White,
                        weight = 700
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (failedReports == 0) "Erreur de rapport" else "Rapport partiel",
                        style = LuxTheme.type.headline,
                        color = colors.label
                    )
                    Text(
                        text = if (failedReports == 0) {
                            "Impossible d'envoyer les rapports. Veuillez réessayer plus tard."
                        } else {
                            "$failedReports rapport(s) n'ont pas pu être envoyés. Les autres ont été transmis avec succès."
                        },
                        style = LuxTheme.type.subheadline,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center
                    )
                }

                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(36.dp)
                        .background(colors.systemRed, RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = null,
                            indication = PlainIndication,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        style = LuxTheme.type.headline,
                        color = Color.White
                    )
                }
            }
        }
    }
}
