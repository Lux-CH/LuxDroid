package ch.cclerc.luxapp.core

import android.content.Context
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticFeedback {
    private var vibratorManager: VibratorManager? = null

    fun init(context: Context) {
        vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
    }

    private val vibrator: Vibrator?
        get() = vibratorManager?.defaultVibrator?.takeIf { it.hasVibrator() }

    private fun play(effect: VibrationEffect) {
        val manager = vibratorManager ?: return
        runCatching { manager.vibrate(CombinedVibration.createParallel(effect)) }
    }

    private fun primitive(primitiveId: Int, scale: Float, fallbackEffectId: Int) {
        val v = vibrator ?: return
        val effect = if (v.areAllPrimitivesSupported(primitiveId)) {
            VibrationEffect.startComposition()
                .addPrimitive(primitiveId, scale)
                .compose()
        } else {
            VibrationEffect.createPredefined(fallbackEffectId)
        }
        play(effect)
    }

    fun softImpact() {
        primitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f, VibrationEffect.EFFECT_TICK)
    }

    fun lightImpact() {
        primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1.0f, VibrationEffect.EFFECT_TICK)
    }

    fun mediumImpact() {
        primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, VibrationEffect.EFFECT_CLICK)
    }

    fun selectionChanged() {
        primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, VibrationEffect.EFFECT_TICK)
    }

    fun success() {
        if (vibrator == null) return
        play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }

    fun error() {
        if (vibrator == null) return
        play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }
}
