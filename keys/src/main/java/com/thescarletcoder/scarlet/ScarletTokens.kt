// ScarletTokens v1 (2026-10-09). Keep identical in Keys and Lyrics.
package com.thescarletcoder.scarlet

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Colours as ARGB ints, so both Canvas code (Keys) and Compose (`Color(int)`) can use them. */
data class ScarletPalette(
    val dark: Boolean,
    val bg: Int,
    val surface: Int,
    val raised: Int,
    val pressed: Int,
    val text: Int,
    val soft: Int,
    val faint: Int,
    val line: Int,
    val scarlet: Int,
    val scarletWash: Int,
    val scarletEdge: Int,
    val teal: Int,
    val tealWash: Int,
    val tealEdge: Int,
)

enum class ThemeChoice { SYSTEM, DARK, LIGHT }

object ScarletTokens {
    private const val BONE = 0xFAF9F6
    private const val CHARCOAL = 0x171614
    private const val SCARLET = 0xE20514
    private fun a(alpha: Float, rgb: Int) = ((alpha * 255 + 0.5f).toInt() shl 24) or rgb

    val DARK = ScarletPalette(
        dark = true,
        bg = a(1f, CHARCOAL), surface = 0xFF211F1C.toInt(), raised = 0xFF2A2825.toInt(), pressed = 0xFF3A3733.toInt(),
        text = a(1f, BONE), soft = 0xFF908E86.toInt(), faint = 0xFF65635E.toInt(), line = a(0.12f, BONE),
        scarlet = a(1f, SCARLET), scarletWash = a(0.14f, SCARLET), scarletEdge = a(0.45f, SCARLET),
        teal = 0xFF5E9B8C.toInt(), tealWash = a(0.22f, 0x5E9B8C), tealEdge = a(0.60f, 0x5E9B8C),
    )

    val LIGHT = ScarletPalette(
        dark = false,
        bg = a(1f, BONE), surface = 0xFFF2F0EB.toInt(), raised = 0xFFFFFEFC.toInt(), pressed = 0xFFE4E0D9.toInt(),
        text = a(1f, CHARCOAL), soft = 0xFF66635D.toInt(), faint = 0xFF85817A.toInt(), line = a(0.12f, CHARCOAL),
        scarlet = a(1f, SCARLET), scarletWash = a(0.10f, SCARLET), scarletEdge = a(0.40f, SCARLET),
        // Brand teal is 3.05:1 on bone; #44776A is 4.87:1.
        teal = 0xFF44776A.toInt(), tealWash = a(0.18f, 0x44776A), tealEdge = a(0.60f, 0x44776A),
    )

    fun parseChoice(s: String?): ThemeChoice = ThemeChoice.entries.firstOrNull { it.name == s } ?: ThemeChoice.SYSTEM

    /** Is the system itself in dark mode (ignores this app's own choice)? */
    fun systemIsDark(): Boolean =
        Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun resolve(choice: ThemeChoice): ScarletPalette = when (choice) {
        ThemeChoice.DARK -> DARK
        ThemeChoice.LIGHT -> LIGHT
        ThemeChoice.SYSTEM -> if (systemIsDark()) DARK else LIGHT
    }

    /**
     * Tell Android the app's own choice, so the window background and splash screen (values / values-night)
     * match from the first frame.
     */
    fun applyToSystem(context: Context, choice: ThemeChoice) {
        val mode = when (choice) {
            ThemeChoice.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeChoice.DARK -> UiModeManager.MODE_NIGHT_YES
            ThemeChoice.LIGHT -> UiModeManager.MODE_NIGHT_NO
        }
        runCatching { context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode) }
    }
}

/** The four motion voices. Nothing overshoots. */
object Motion {
    val NAV_EASE: Easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)
    val LUX_EASE: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    const val NAV_MS = 220
    const val NAV_SLOW_MS = 800
    const val SNAP_IN_MS = 0
    const val SNAP_OUT_MS = 120
    const val LUX_MS = 650

    /** Android's animator duration scale ("Remove animations" sets it to 0). Read fresh each time; it can change. */
    fun scale(context: Context): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    /** [ms] scaled by the system setting; 0 when animations are removed. */
    fun duration(context: Context, ms: Int): Int = (ms * scale(context)).toInt().coerceAtLeast(0)

    /** Decelerating ease-out for hand-driven Canvas animations (t in 0..1). */
    fun easeOut(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1f - u * u * u }
}
