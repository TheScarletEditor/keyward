package com.thescarletcoder.keys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat

/** Three suggestion slots above the keys. [highlight] marks the word Enter/space would autocorrect to. */
@SuppressLint("ViewConstructor")
class SuggestionStrip(context: Context, private val onPick: (String) -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private var words: List<String> = emptyList()
    private var highlight: Int = -1
    private var pressedSlot = -1

    private val inter = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    private val interMedium = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
    private val size = Compact.sizes(context)
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = size.stripText * density }
    private val divider = Paint().apply { color = KeyColors.EDGE; strokeWidth = 1 * density }
    private val pressedFill = Paint().apply { color = KeyColors.FUNCTION }

    fun show(list: List<String>, highlightIndex: Int) {
        words = list.take(3)
        highlight = highlightIndex
        invalidate()
    }

    fun clear() = show(emptyList(), -1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (size.strip * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(KeyColors.BG)
        if (words.isEmpty()) return
        val slotW = width / 3f
        for (i in 0 until 3) {
            val w = words.getOrNull(i) ?: continue
            if (i == pressedSlot) canvas.drawRect(i * slotW, 0f, (i + 1) * slotW, height.toFloat(), pressedFill)
            paint.typeface = if (i == highlight) interMedium else inter
            paint.color = if (i == highlight) KeyColors.TEXT else KeyColors.TEXT_SOFT
            val shown = TextUtils.ellipsize(w, paint, slotW - 24 * density, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, i * slotW + slotW / 2, height / 2f - (paint.descent() + paint.ascent()) / 2, paint)
            if (i > 0) canvas.drawLine(i * slotW, height * 0.25f, i * slotW, height * 0.75f, divider)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val slot = (e.x / (width / 3f)).toInt().coerceIn(0, 2)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressedSlot = slot; invalidate() }
            MotionEvent.ACTION_UP -> {
                if (slot == pressedSlot) words.getOrNull(slot)?.let(onPick)
                pressedSlot = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedSlot = -1; invalidate() }
        }
        return true
    }
}
