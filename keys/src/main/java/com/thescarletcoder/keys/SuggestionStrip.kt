package com.thescarletcoder.keys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.ScarletTokens
import com.thescarletcoder.scarlet.Tunables

/**
 * Three suggestion slots above the keys. Always the same height, so the keyboard never jumps between fields.
 * [highlight] marks the word Enter/space would autocorrect to. In a private field with nothing to show, a lock
 * and "Private field. Nothing learned."
 */
@SuppressLint("ViewConstructor")
class SuggestionStrip(context: Context, private val onPick: (String) -> Unit) : View(context) {
    var colors: KeyColors = KeyColors(ScarletTokens.DARK)
        set(v) { field = v; invalidate() }
    var privateField = false
        set(v) { field = v; invalidate(); contentDescription = if (v) "Private field. Nothing learned." else null }
    var animScale = 1f

    private val density = resources.displayMetrics.density
    private val words = arrayOfNulls<String>(3)
    private val shown = arrayOfNulls<String>(3)
    private val slotStart = LongArray(3)
    private var highlight = -1
    private var replacing = false
    private var pressedSlot = -1

    private val inter = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    private val interMedium = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
    private val size = Compact.sizes(context)
    // Not `density` inside apply: TextPaint has its own density field (1.0), which made suggestions a third of their size.
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = size.stripText * this@SuggestionStrip.density }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divider = Paint().apply { strokeWidth = 1 * density }
    private val pill = RectF()

    private val note = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = inter; textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }
    private val lockPx = 16 * density
    private val lockBody = KeyIcons.roundRect(5.5f, 10.5f, 13f, 9.5f, 2f, lockPx)
    private val lockShackle = KeyIcons.path(KeyIcons.LOCK_SHACKLE, lockPx)
    private val lockLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = KeyIcons.STROKE * lockPx / KeyIcons.GRID; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** [replaces]: the highlighted word is a fix space will swap in (not just the word as typed), for TalkBack (Q25). */
    fun show(list: List<String>, highlightIndex: Int, replaces: Boolean = false) {
        replacing = replaces
        val now = SystemClock.uptimeMillis()
        for (i in 0 until 3) {
            val w = list.getOrNull(i)
            if (w != words[i]) { words[i] = w; slotStart[i] = if (w == null) 0L else now }
        }
        highlight = highlightIndex
        ellipsize()
        invalidate()
        a11y.invalidateRoot()
    }

    /** Each suggestion is its own item for TalkBack ("Suggestion: hello"); a private field says so (K4). */
    private val a11y = object : ExploreByTouchHelper(this) {
        private val bounds = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val slot = (x / (width / 3f)).toInt().coerceIn(0, 2)
            return if (words[slot] != null) slot else INVALID_ID
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) { for (i in 0 until 3) if (words[i] != null) ids += i }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val w = words.getOrNull(id)
            val slotW = width / 3
            bounds.set(id * slotW, 0, (id + 1) * slotW, height)
            node.setBoundsInParent(bounds)
            if (w == null) { node.contentDescription = ""; return }
            node.contentDescription = KeyLabels.suggestion(w, replaces = replacing && id == highlight)
            node.className = "android.widget.Button"
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
            val w = words.getOrNull(id) ?: return false
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            onPick(w); return true
        }
    }

    init { ViewCompat.setAccessibilityDelegate(this, a11y) }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    fun clear() = show(emptyList(), -1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (size.strip * density).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { ellipsize() }

    /** Fit each word to its slot once, when words change (never while drawing). */
    private fun ellipsize() {
        val slotW = width / 3f
        for (i in 0 until 3) {
            val w = words[i]
            shown[i] = if (w == null || slotW <= 0f) w else {
                paint.typeface = if (i == highlight) interMedium else inter
                TextUtils.ellipsize(w, paint, slotW - 24 * density, TextUtils.TruncateAt.END).toString()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val c = colors
        canvas.drawColor(c.bg)
        if (words[0] == null && words[1] == null && words[2] == null) {
            if (privateField) drawPrivate(canvas, c)
            return
        }
        val slotW = width / 3f
        val now = SystemClock.uptimeMillis()
        val fade = Tunables[KeysTuning.STRIP_FADE_MS] * animScale
        val cy = height / 2f
        val pillR = 15 * density
        var animating = false
        for (i in 0 until 3) {
            val w = shown[i] ?: continue
            val cx = i * slotW + slotW / 2
            if (i == pressedSlot) {
                fill.color = c.stripPressed
                pill.set(i * slotW + 6 * density, cy - 17 * density, (i + 1) * slotW - 6 * density, cy + 17 * density)
                canvas.drawRoundRect(pill, pillR, pillR, fill)
            }
            paint.typeface = if (i == highlight) interMedium else inter
            if (i == highlight) {
                val half = paint.measureText(w) / 2 + 12 * density
                fill.color = c.stripPick
                pill.set(cx - half, cy - pillR, cx + half, cy + pillR)
                canvas.drawRoundRect(pill, pillR, pillR, fill)
            }
            val t = if (fade <= 0f) 1f else (now - slotStart[i]) / fade
            if (t < 1f) animating = true
            paint.color = if (i == highlight) c.text else c.soft
            paint.alpha = (Motion.easeOut(t) * 255).toInt()
            canvas.drawText(w, cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
            // Dividers only between two slots that both have a word.
            if (i > 0 && shown[i - 1] != null) {
                divider.color = c.divider
                canvas.drawLine(i * slotW, height * 0.3f, i * slotW, height * 0.7f, divider)
            }
        }
        if (animating) postInvalidateOnAnimation()
    }

    private fun drawPrivate(canvas: Canvas, c: KeyColors) {
        val gap = 6 * density
        val textW = note.measureText(PRIVATE)
        val total = lockPx + gap + textW
        val x = (width - total) / 2
        canvas.save()
        canvas.translate(x, (height - lockPx) / 2)
        lockLine.color = c.faint
        canvas.drawPath(lockBody, lockLine)
        canvas.drawPath(lockShackle, lockLine)
        canvas.restore()
        note.color = c.faint
        canvas.drawText(PRIVATE, x + lockPx + gap, height / 2f - (note.descent() + note.ascent()) / 2, note)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val slot = (e.x / (width / 3f)).toInt().coerceIn(0, 2)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressedSlot = if (words[slot] != null) slot else -1; invalidate() }
            MotionEvent.ACTION_UP -> {
                if (slot == pressedSlot) words[slot]?.let(onPick)
                pressedSlot = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressedSlot = -1; invalidate() }
        }
        return true
    }

    private companion object { const val PRIVATE = "Private field. Nothing learned." }
}
