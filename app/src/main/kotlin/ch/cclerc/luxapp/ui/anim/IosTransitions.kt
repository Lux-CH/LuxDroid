package ch.cclerc.luxapp.ui.anim

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import ch.cclerc.luxapp.ui.theme.LuxSprings

object IosTransitions {

    private val topOrigin = TransformOrigin(0.5f, 0f)

    fun searchModeEnter(
        spec: FiniteAnimationSpec<Float> = LuxSprings.SearchTransition,
        offsetSpec: FiniteAnimationSpec<IntOffset> = LuxSprings.springFor(0.45, 0.82),
        offsetYPx: Int = 10
    ): EnterTransition =
        fadeIn(spec) +
            scaleIn(spec, initialScale = 0.98f, transformOrigin = topOrigin) +
            slideInVertically(offsetSpec) { -offsetYPx }

    fun searchModeExit(
        spec: FiniteAnimationSpec<Float> = LuxSprings.SearchTransition,
        offsetSpec: FiniteAnimationSpec<IntOffset> = LuxSprings.springFor(0.45, 0.82),
        offsetYPx: Int = 10
    ): ExitTransition =
        fadeOut(spec) +
            scaleOut(spec, targetScale = 0.96f, transformOrigin = topOrigin) +
            slideOutVertically(offsetSpec) { offsetYPx }

    fun contentSwapEnter(
        spec: FiniteAnimationSpec<Float> = LuxSprings.ContentSpring,
        offsetSpec: FiniteAnimationSpec<IntOffset> = LuxSprings.springFor(0.5, 0.85)
    ): EnterTransition =
        fadeIn(spec) +
            slideInVertically(offsetSpec) { it } +
            scaleIn(spec, initialScale = 0.97f, transformOrigin = topOrigin)

    fun contentSwapExit(
        spec: FiniteAnimationSpec<Float> = LuxSprings.ContentSpring,
        offsetSpec: FiniteAnimationSpec<IntOffset> = LuxSprings.springFor(0.5, 0.85)
    ): ExitTransition =
        fadeOut(spec) +
            slideOutVertically(offsetSpec) { it } +
            scaleOut(spec, targetScale = 0.97f, transformOrigin = topOrigin)

    fun scaleFadeEnter(
        scale: Float = 0.8f,
        spec: FiniteAnimationSpec<Float> = LuxSprings.UltraSmooth
    ): EnterTransition =
        fadeIn(spec) + scaleIn(spec, initialScale = scale)

    fun scaleFadeExit(
        scale: Float = 0.8f,
        spec: FiniteAnimationSpec<Float> = LuxSprings.UltraSmooth
    ): ExitTransition =
        fadeOut(spec) + scaleOut(spec, targetScale = scale)

    fun moveEdgeBottomEnter(
        spec: FiniteAnimationSpec<Float> = LuxSprings.UltraSmooth,
        offsetSpec: FiniteAnimationSpec<IntOffset> = LuxSprings.springFor(0.4, 0.85)
    ): EnterTransition =
        slideInVertically(offsetSpec) { it } + fadeIn(spec)

    fun moveEdgeBottomExit(
        spec: FiniteAnimationSpec<Float> = LuxSprings.UltraSmooth,
        offsetSpec: FiniteAnimationSpec<IntOffset> = LuxSprings.springFor(0.4, 0.85)
    ): ExitTransition =
        slideOutVertically(offsetSpec) { it } + fadeOut(spec)
}
