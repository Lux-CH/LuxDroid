package ch.cclerc.luxapp.ui.trips.intelligence

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.intelligence.IntelligenceProfile
import ch.cclerc.luxapp.domain.intelligence.IntelligenceQuestion
import ch.cclerc.luxapp.domain.intelligence.IntelligenceStore
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INTRO_STEP = -1

@Composable
fun IntelligenceSetupView(onDismiss: () -> Unit) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val questions = IntelligenceQuestion.all
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(IntelligenceStore.profile) }
    var step by remember { mutableIntStateOf(if (IntelligenceStore.profile.isConfigured) questions.size else INTRO_STEP) }
    var isForward by remember { mutableStateOf(true) }
    var isEditingFromSummary by remember { mutableStateOf(false) }
    val dragOffset = remember { Animatable(0f) }

    val isIntro = step == INTRO_STEP
    val isSummary = step >= questions.size
    val isQuestion = !isIntro && !isSummary

    fun go(target: Int) {
        val clamped = maxOf(INTRO_STEP, minOf(target, questions.size))
        if (clamped == step) return
        isForward = clamped > step
        step = clamped
    }

    fun nextStep(after: Int): Int = if (isEditingFromSummary) questions.size else after + 1

    fun goBack() {
        if (step >= questions.size) {
            isEditingFromSummary = false
            go(questions.size - 1)
        } else {
            go(step - 1)
        }
    }

    fun select(option: IntelligenceQuestion.Option) {
        HapticFeedback.lightImpact()
        draft = option.apply(draft)
        val current = step
        scope.launch {
            delay(320)
            if (step == current) go(nextStep(current))
        }
    }

    val canSwipeForward = !isSummary
    val canSwipeBack = !isIntro && !(isQuestion && step == 0 && draft.isConfigured)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.systemGroupedBackground)
    ) {
        val glow = accent.copy(alpha = if (colors.isDark) 0.28f else 0.18f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(glow, Color.Transparent),
                            center = Offset(size.width / 2, 0f),
                            radius = 420.dp.toPx()
                        )
                    )
                }
        )

        Column(Modifier.fillMaxSize()) {
            SetupTopBar(
                step = step,
                count = questions.size,
                isQuestion = isQuestion,
                onLeading = { if (isQuestion && step > 0) go(step - 1) else onDismiss() },
                onSkip = { go(nextStep(step)) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp)
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { translationX = dragOffset.value }
                    .pointerInput(step, canSwipeForward, canSwipeBack) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -70.dp.toPx() && canSwipeForward) {
                                    HapticFeedback.lightImpact()
                                    scope.launch { dragOffset.snapTo(0f) }
                                    go(nextStep(step))
                                } else if (total > 70.dp.toPx() && canSwipeBack) {
                                    HapticFeedback.lightImpact()
                                    scope.launch { dragOffset.snapTo(0f) }
                                    goBack()
                                } else {
                                    scope.launch { dragOffset.animateTo(0f, LuxSprings.springFor(0.35, 0.8)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { dragOffset.animateTo(0f, LuxSprings.springFor(0.35, 0.8)) }
                            }
                        ) { change, amount ->
                            change.consume()
                            total += amount
                            val allowed = if (total < 0) canSwipeForward else canSwipeBack
                            scope.launch { dragOffset.snapTo(if (allowed) total * 0.6f else total * 0.15f) }
                        }
                    }
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = isForward
                        val spec = LuxSprings.springFor<androidx.compose.ui.unit.IntOffset>(0.42, 0.88)
                        (slideInHorizontally(spec) { if (forward) it else -it } + fadeIn()) togetherWith
                            (slideOutHorizontally(spec) { if (forward) -it else it } + fadeOut())
                    },
                    label = "intelligenceStep",
                    modifier = Modifier.fillMaxSize()
                ) { shown ->
                    when {
                        shown == INTRO_STEP -> SetupIntro()
                        shown >= questions.size -> SetupSummary(draft, questions) { index ->
                            HapticFeedback.lightImpact()
                            isEditingFromSummary = true
                            go(index)
                        }
                        else -> QuestionPage(questions[shown], draft, ::select)
                    }
                }
            }

            when {
                isIntro -> Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PrimaryButton("Commencer") { go(0) }
                    Text("Moins d'une minute", style = LuxTheme.type.footnote, color = colors.secondaryLabel)
                }
                isSummary -> Column(
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                0f to colors.systemGroupedBackground.copy(alpha = 0f),
                                0.5f to colors.systemGroupedBackground
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrimaryButton("Enregistrer") {
                        IntelligenceStore.profile = draft.copy(isConfigured = true)
                        onDismiss()
                    }
                    Text(
                        "Recommencer",
                        style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                        color = accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .scaleClickable {
                                draft = IntelligenceProfile()
                                isEditingFromSummary = false
                                go(0)
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupTopBar(
    step: Int,
    count: Int,
    isQuestion: Boolean,
    onLeading: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(60.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .size(34.dp)
                        .scaleClickable(haptic = false, onClick = onLeading)
                        .background(colors.tertiarySystemFill, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(
                        name = if (isQuestion && step > 0) "chevron.left" else "xmark",
                        size = 14.sp,
                        color = colors.secondaryLabel,
                        weight = 700
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (isQuestion) {
                Text(
                    "${step + 1} sur $count",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.secondaryLabel
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(60.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "Passer",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.secondaryLabel,
                    modifier = Modifier
                        .alpha(if (isQuestion) 1f else 0f)
                        .scaleClickable(enabled = isQuestion, haptic = false, onClick = onSkip)
                )
            }
        }

        AnimatedVisibility(visible = isQuestion, enter = fadeIn(), exit = fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(count) { index ->
                    val fill by animateColorAsState(
                        targetValue = if (index <= step) accent else colors.label.copy(alpha = 0.1f),
                        animationSpec = LuxSprings.springFor(0.4, 0.85),
                        label = "setupProgress"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(fill, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(title: String, onClick: () -> Unit) {
    val accent = LuxTheme.accent
    Text(
        title,
        style = LuxTheme.type.headline,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .scaleClickable(haptic = false) {
                HapticFeedback.mediumImpact()
                onClick()
            }
            .iosShadow(accent.copy(alpha = 0.35f), 14.dp, 6.dp, CircleShape)
            .background(accent, CircleShape)
            .padding(vertical = 16.dp)
    )
}

@Composable
private fun Glyph(size: Dp) {
    val accent = LuxTheme.accent
    val bounce = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { bounce.animateTo(1f, LuxSprings.springFor(0.45, 0.45)) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .iosShadow(accent.copy(alpha = 0.4f), size / 5, size / 12, CircleShape)
                .background(
                    Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f))),
                    CircleShape
                )
        )
        SFSymbol(
            name = "sparkles",
            size = (size.value * 0.42f).sp,
            color = Color.White,
            weight = 600,
            modifier = Modifier.graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
        )
    }
}

@Composable
private fun SetupIntro() {
    val colors = LuxTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.padding(top = 24.dp)) { Glyph(92.dp) }
            Text("Intelligent", style = LuxTheme.type.largeTitle, color = colors.label)
            Text(
                "Le bon trajet, pas seulement le plus rapide. Quelques questions pour qu'il vous ressemble.",
                style = LuxTheme.type.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.secondarySystemGroupedBackground, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FeatureRow("cloud.sun.rain.fill", "Météo", "Moins de marche quand il pleut, fait froid ou trop chaud")
            FeatureRow("person.3.fill", "Affluence", "Évite les véhicules signalés bondés par la communauté")
            FeatureRow("heart.fill", "Habitudes", "Privilégie les lignes que vous prenez déjà")
            FeatureRow("arrow.triangle.swap", "Trajets directs", "Attend un bus direct quand ça vaut le coup")
        }
    }
}

@Composable
private fun FeatureRow(symbol: String, title: String, detail: String) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(40.dp)
                .background(accent.copy(alpha = 0.13f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = symbol, size = 17.sp, color = accent, weight = 600)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label)
            Text(detail, style = LuxTheme.type.footnote, color = colors.secondaryLabel)
        }
    }
}

@Composable
private fun QuestionPage(
    question: IntelligenceQuestion,
    draft: IntelligenceProfile,
    onSelect: (IntelligenceQuestion.Option) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 30.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(56.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = question.symbol, size = 24.sp, color = accent, weight = 600)
            }
            Text(
                question.label.uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = accent
            )
            Text(question.title, style = LuxTheme.type.title, color = colors.label)
            Text(question.subtitle, style = LuxTheme.type.subheadline, color = colors.secondaryLabel)
        }

        if (question.options.size == 4) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.chunked(2).forEach { pair ->
                    Row(
                        Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { option ->
                            OptionTile(option, option.isSelected(draft), Modifier.weight(1f).fillMaxHeight()) { onSelect(option) }
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEach { option ->
                    OptionRow(option, option.isSelected(draft)) { onSelect(option) }
                }
            }
        }
    }
}

@Composable
private fun Modifier.optionBackground(isSelected: Boolean): Modifier {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(20.dp)
    val fill by animateColorAsState(
        if (isSelected) accent.copy(alpha = 0.12f) else colors.secondarySystemGroupedBackground,
        LuxSprings.springFor(0.3, 0.8),
        label = "optionFill"
    )
    val stroke by animateColorAsState(
        if (isSelected) accent else colors.label.copy(alpha = 0.06f),
        LuxSprings.springFor(0.3, 0.8),
        label = "optionStroke"
    )
    return this
        .background(fill, shape)
        .border(if (isSelected) 1.5.dp else 0.5.dp, stroke, shape)
}

@Composable
private fun OptionIcon(symbol: String, isSelected: Boolean) {
    val accent = LuxTheme.accent
    Box(
        Modifier
            .size(44.dp)
            .background(if (isSelected) accent else accent.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        SFSymbol(name = symbol, size = 18.sp, color = if (isSelected) Color.White else accent, weight = 600)
    }
}

@Composable
private fun OptionRow(option: IntelligenceQuestion.Option, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Row(
        Modifier
            .fillMaxWidth()
            .scaleClickable(haptic = false, onClick = onClick)
            .optionBackground(isSelected)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptionIcon(option.symbol, isSelected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(option.title, style = LuxTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = colors.label)
            Text(option.detail, style = LuxTheme.type.footnote, color = colors.secondaryLabel)
        }
        SFSymbol(
            name = if (isSelected) "checkmark.circle.fill" else "circle",
            size = 22.sp,
            color = if (isSelected) accent else colors.label.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun OptionTile(
    option: IntelligenceQuestion.Option,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Column(
        modifier
            .heightIn(min = 160.dp)
            .scaleClickable(haptic = false, onClick = onClick)
            .optionBackground(isSelected)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            OptionIcon(option.symbol, isSelected)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                SFSymbol(name = "checkmark.circle.fill", size = 20.sp, color = accent)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(option.title, style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label)
            Text(option.detail, style = LuxTheme.type.caption, color = colors.secondaryLabel, maxLines = 3)
        }
    }
}

@Composable
private fun SetupSummary(
    draft: IntelligenceProfile,
    questions: List<IntelligenceQuestion>,
    onEdit: (Int) -> Unit
) {
    val colors = LuxTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.padding(top = 16.dp)) { Glyph(68.dp) }
            Text("Votre Intelligent", style = LuxTheme.type.title, color = colors.label)
            Text(
                "Combiné à la météo, à l'affluence signalée et à vos lignes habituelles. Touchez une carte pour la modifier.",
                style = LuxTheme.type.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            questions.withIndex().chunked(2).forEach { pair ->
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { (index, question) ->
                        SummaryTile(question, draft, Modifier.weight(1f).fillMaxHeight()) { onEdit(index) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(
    question: IntelligenceQuestion,
    draft: IntelligenceProfile,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val option = question.options.firstOrNull { it.isSelected(draft) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 116.dp)
            .scaleClickable(haptic = false, onClick = onClick)
            .background(colors.secondarySystemGroupedBackground, shape)
            .border(0.5.dp, colors.label.copy(alpha = 0.06f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = option?.symbol ?: question.symbol, size = 15.sp, color = accent, weight = 600)
            }
            Spacer(Modifier.weight(1f))
            SFSymbol(name = "pencil", size = 11.sp, color = colors.tertiaryLabel, weight = 700)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(question.label, style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Medium), color = colors.secondaryLabel)
            if (option != null) {
                Text(
                    option.title,
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label,
                    maxLines = 2
                )
            }
        }
    }
}
