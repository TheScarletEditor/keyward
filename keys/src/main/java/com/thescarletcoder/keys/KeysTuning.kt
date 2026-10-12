package com.thescarletcoder.keys

import android.content.Context
import com.thescarletcoder.scarlet.Tunable
import com.thescarletcoder.scarlet.Tunables

/** Keys' feel values (KEYS-POLISH-SPEC section 13). Defaults are the spec's starting points. */
object KeysTuning {
    const val SNAP_OUT_MS = "snap_out_ms"
    const val BUBBLE_H_DP = "bubble_h_dp"
    const val BUBBLE_HOLD_MS = "bubble_hold_ms"
    const val BUBBLE_FADE_MS = "bubble_fade_ms"
    const val STRIP_FADE_MS = "strip_fade_ms"
    const val WORDMARK_DIM = "wordmark_dim"
    const val LIP_DP = "lip_dp"
    const val LONGPRESS_MS = "longpress_ms"
    const val HAP_LETTER = "hap_letter"
    const val HAP_SPACE = "hap_space"
    const val HAP_DELETE = "hap_delete"
    const val LANDSCAPE_KEYS = "landscape_keys"

    /** The tallest bubble the slider allows; the keyboard reserves room above itself for it on phones. */
    const val BUBBLE_H_MAX = 72f

    private val list = listOf(
        Tunable(SNAP_OUT_MS, "Press", 120f, 0f, 400f, 10f, isInt = true),
        Tunable(LIP_DP, "Press", 1.5f, 0f, 4f, 0.25f),
        Tunable(LONGPRESS_MS, "Press", 350f, 150f, 800f, 10f, isInt = true),
        Tunable(BUBBLE_H_DP, "Letter bubble", 54f, 40f, BUBBLE_H_MAX, 1f, isInt = true),
        Tunable(BUBBLE_HOLD_MS, "Letter bubble", 50f, 0f, 300f, 10f, isInt = true),
        Tunable(BUBBLE_FADE_MS, "Letter bubble", 110f, 0f, 400f, 10f, isInt = true),
        Tunable(STRIP_FADE_MS, "Suggestions", 90f, 0f, 300f, 10f, isInt = true),
        Tunable(WORDMARK_DIM, "Space bar", 0.22f, 0f, 1f, 0.01f),
        Tunable(HAP_LETTER, "Haptics", 0.5f, 0f, 1f, 0.05f),
        Tunable(HAP_SPACE, "Haptics", 0.6f, 0f, 1f, 0.05f),
        Tunable(HAP_DELETE, "Haptics", 0.4f, 0f, 1f, 0.05f),
        // Most of the screen's height the keys (not the suggestion strip) may take in landscape.
        Tunable(LANDSCAPE_KEYS, "Landscape", 0.4f, 0.25f, 0.6f, 0.01f),
    )

    /** Device-protected, like the rest of Keys' settings, so tuning works before first unlock. */
    fun init(context: Context) = Tunables.init(
        context.createDeviceProtectedStorageContext().getSharedPreferences("tuning", Context.MODE_PRIVATE), list,
    )
}
