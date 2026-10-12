package com.thescarletcoder.keys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.thescarletcoder.scarlet.ScarletTokens
import com.thescarletcoder.scarlet.Tunables

/**
 * Draws the letter bubble (phones) and the accent row over the whole keyboard panel, including the transparent
 * room above the keys. Never takes touches: the keys underneath keep them.
 */
@SuppressLint("ViewConstructor")
class KeyOverlay(context: Context) : View(context), KeyPopups {
    var colors: KeyColors = KeyColors(ScarletTokens.DARK)
        set(v) { field = v; invalidate() }
    /** Where the keyboard view sits inside this overlay, in px. */
    var offsetX = 0f
    var offsetY = 0f
    var animScale = 1f

    private val density = resources.displayMetrics.density
    private val inter = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT

    // Bubble
    private val bubble = RectF()
    private var bubbleText = ""
    private var bubbleShown = false
    private var bubbleHeld = false
    private var bubbleUpAt = 0L

    // Accent row
    private var accents: Array<String> = emptyArray()
    private val row = RectF()
    private val cell = RectF()
    private var cellW = 0f
    private var highlight = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowed = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = inter }
    private val bubbleTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, resources.displayMetrics)
    private val accentTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics)

    init {
        isClickable = false
        isFocusable = false
    }

    /**
     * Never ask for height of its own. A plain View takes all the height it's offered, which made the keyboard
     * window as tall as the screen with the keys at the top (the owner, 2026-10-09). The panel's second measure pass
     * then gives this overlay exactly the keyboard's height.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun bubbleDown(key: RectF, label: String) {
        val h = Tunables[KeysTuning.BUBBLE_H_DP] * density
        val bottom = offsetY + key.top - 6 * density
        bubble.set(offsetX + key.left - 7 * density, bottom - h, offsetX + key.right + 7 * density, bottom)
        bubbleText = label
        bubbleShown = true; bubbleHeld = true
        invalidate()
    }

    override fun bubbleUp() {
        if (!bubbleShown || !bubbleHeld) return
        bubbleHeld = false
        bubbleUpAt = SystemClock.uptimeMillis()
        if (animScale <= 0f) bubbleShown = false
        invalidate()
    }

    override fun accentsOpen(left: Float, top: Float, cellW: Float, cellH: Float, options: Array<String>, highlight: Int) {
        bubbleShown = false
        accents = options
        this.cellW = cellW
        this.highlight = highlight
        row.set(offsetX + left, offsetY + top, offsetX + left + cellW * options.size, offsetY + top + cellH)
        invalidate()
    }

    override fun accentsMove(highlight: Int) { this.highlight = highlight; invalidate() }

    override fun accentsClose() { accents = emptyArray(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val c = colors
        val r = 12 * density
        if (bubbleShown) {
            var alpha = 1f
            if (!bubbleHeld) {
                val t = SystemClock.uptimeMillis() - bubbleUpAt
                val hold = Tunables[KeysTuning.BUBBLE_HOLD_MS] * animScale
                val fade = Tunables[KeysTuning.BUBBLE_FADE_MS] * animScale
                alpha = when {
                    t < hold -> 1f
                    fade <= 0f -> 0f
                    else -> 1f - (t - hold) / fade
                }
                if (alpha <= 0f) bubbleShown = false else postInvalidateOnAnimation()
            }
            if (bubbleShown) {
                shade(alpha)
                canvas.drawRoundRect(bubble, r, r, shadowed)
                label.textSize = bubbleTextSize
                label.color = c.text
                label.alpha = (alpha * 255).toInt()
                canvas.drawText(bubbleText, bubble.centerX(), bubble.centerY() - (label.descent() + label.ascent()) / 2, label)
            }
        }
        if (accents.isNotEmpty()) {
            shade(1f)
            canvas.drawRoundRect(row, r, r, shadowed)
            val inset = 3 * density
            cell.set(row.left + highlight * cellW + inset, row.top + inset, row.left + (highlight + 1) * cellW - inset, row.bottom - inset)
            fill.color = c.capsLock
            canvas.drawRoundRect(cell, r - inset, r - inset, fill)
            label.textSize = accentTextSize
            label.color = c.text
            for (i in accents.indices) {
                canvas.drawText(accents[i], row.left + (i + 0.5f) * cellW, row.centerY() - (label.descent() + label.ascent()) / 2, label)
            }
        }
    }

    /** The bubble/row fill with a soft shadow, at [alpha]. */
    private fun shade(alpha: Float) {
        shadowed.color = colors.bubble
        shadowed.alpha = (alpha * 255).toInt()
        shadowed.setShadowLayer(8 * density, 0f, 2 * density, ((if (colors.dark) 0x66 else 0x33) * alpha).toInt() shl 24)
    }
}
