package com.thescarletcoder.keys

import android.content.Context
import android.media.AudioManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.thescarletcoder.scarlet.Tunables

/**
 * Key sound and haptics (KEYS-POLISH-SPEC section 10). Both stay off until the user turns them on.
 * System services and vibration effects are made once, not per key press.
 */
class Feedback(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val primitives = vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
    ) == true
    /** Touch usage: Android's own "touch feedback" setting still applies. */
    private val touch = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    private var letter: VibrationEffect? = null
    private var space: VibrationEffect? = null
    private var deleteTick: VibrationEffect? = null
    private val onTune = { build() }

    init { build(); Tunables.addListener(onTune) }

    private fun build() {
        if (!primitives) return
        fun one(p: Int, scale: Float) = VibrationEffect.startComposition().addPrimitive(p, scale.coerceIn(0f, 1f)).compose()
        letter = one(VibrationEffect.Composition.PRIMITIVE_TICK, Tunables[KeysTuning.HAP_LETTER])
        space = one(VibrationEffect.Composition.PRIMITIVE_CLICK, Tunables[KeysTuning.HAP_SPACE])
        deleteTick = one(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, Tunables[KeysTuning.HAP_DELETE])
    }

    fun press(view: View, key: Key, sound: Boolean, vibrate: Boolean) {
        if (sound) audio?.playSoundEffect(
            when (key.type) {
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }, -1f,
        )
        if (vibrate) {
            val effect = when (key.type) {
                KeyType.SPACE, KeyType.ENTER -> space
                else -> letter
            }
            if (effect != null) vibrator?.vibrate(effect, touch) else view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Each repeat while delete is held. */
    fun deleteRepeat(view: View, vibrate: Boolean) {
        if (!vibrate) return
        val effect = deleteTick
        if (effect != null) vibrator?.vibrate(effect, touch) else view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun release() { Tunables.removeListener(onTune) }
}
