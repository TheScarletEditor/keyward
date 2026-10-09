package com.thescarletcoder.keys

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * The whole keyboard: suggestion strip and keys, with an overlay for the letter bubble and accent row.
 * On phones with the bubble on, a transparent band above the strip gives top-row bubbles room inside the
 * keyboard window; the service reports it in onComputeInsets so the app underneath stays visible and tappable.
 * The Settings preview uses the same panel.
 */
@SuppressLint("ViewConstructor")
class KeyboardPanel(context: Context, listener: KeyListener, onPick: (String) -> Unit) : FrameLayout(context) {
    val keyboard = KeyboardView(context, listener)
    val strip = SuggestionStrip(context, onPick)
    val overlay = KeyOverlay(context)
    private val room = View(context)
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val density = resources.displayMetrics.density

    /** Height of the transparent band above the strip, in px (0 when there's no bubble). */
    var extraTopPx = 0
        private set

    init {
        body.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body.addView(keyboard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(room, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(overlay, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        keyboard.popups = overlay
    }

    /** Apply the look and size. Cheap to call again; only re-lays out when the band or key height changes. */
    fun apply(colors: KeyColors, keyHeightDp: Int, bubble: Boolean, animScale: Float) {
        keyboard.colors = colors; strip.colors = colors; overlay.colors = colors
        keyboard.animScale = animScale; strip.animScale = animScale; overlay.animScale = animScale
        body.setBackgroundColor(colors.bg)
        keyboard.keyHeightDp = keyHeightDp
        keyboard.bubbleEnabled = bubble
        val size = Compact.sizes(context)
        val band = if (bubble) ((KeysTuning.BUBBLE_H_MAX + 6 - size.strip - size.pad) * density).toInt().coerceAtLeast(0) else 0
        if (band != extraTopPx) {
            extraTopPx = band
            room.layoutParams = room.layoutParams.apply { height = band }
        }
    }

    /** Keep the keys above the navigation bar (Android's own hide and switch buttons). */
    fun keepAboveNavigation() {
        setOnApplyWindowInsetsListener { _, insets ->
            // On ZUI with gesture navigation the IME's back/switch buttons are not reported as a navigation
            // bar, so take the largest of the bottom insets the system offers.
            val bottom = maxOf(
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsets.Type.tappableElement()).bottom,
                insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom,
                insets.getInsets(WindowInsets.Type.systemGestures()).bottom,
            )
            body.setPadding(0, 0, 0, bottom)
            insets
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        overlay.offsetX = keyboard.left.toFloat()
        overlay.offsetY = (body.top + keyboard.top).toFloat() // body.top is the transparent band's height
        // The accent row may rise over the strip and the transparent band.
        keyboard.popupRoom = (extraTopPx + strip.height).toFloat()
    }
}
