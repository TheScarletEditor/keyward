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
 * [highlight] marks the word Enter/space would autocorrect to; [saveSlot] is the typed word with "+ Save" (Q28).
 * In a private field with nothing to show, a lock and "Private field. Nothing learned."
 */
@SuppressLint("ViewConstructor")
class SuggestionStrip(context: Context, private val onPick: (String, Boolean) -> Unit) : View(context) {
    var colors: KeyColors = KeyColors(ScarletTokens.DARK)
        set(v) { field = v; invalidate() }
    var privateField = false
        set(v) { field = v; invalidate(); contentDescription = if (v) "Private field. Nothing learned." else null }
    var animScale = 1f
    /** Whether a long press on this word offers to forget it: a word Keyward learned or the user saved (KB-14). */
    var forgettable: (String) -> Boolean = { false }
    /** The user confirmed forgetting this word. */
    var onForget: (String) -> Unit = {}

    private val density = resources.displayMetrics.density
    private val words = arrayOfNulls<String>(3)
    private val shown = arrayOfNulls<String>(3)
    /** Where each slot starts and ends, and each word's text size, from [StripFit]: long words get the room (KB-16). */
    private var edges = floatArrayOf(0f, 0f, 0f, 0f)
    private val scale = FloatArray(3) { 1f }
    private val baseText get() = size.stripText * density
    private val slotStart = LongArray(3)
    private var highlight = -1
    private var replacing = false
    private var saveSlot = -1
    /** The slot the service offered "+ Save" for; [saveSlot] is where it still shows once the words are fitted. */
    private var offeredSave = -1
    private var pressedSlot = -1
    /** The word a long press offered to forget, while the strip asks "Remove ... from your words?" (KB-14). */
    private var forgetting: String? = null
    private var forgetShown = ""
    /** The finger that opened the question is still down: its lift neither picks nor answers. */
    private var longPressed = false
    private val longPress = Runnable {
        val w = words.getOrNull(pressedSlot) ?: return@Runnable
        if (pressedSlot != saveSlot && forgettable(w)) { longPressed = true; pressedSlot = -1; askForget(w) }
    }

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
    private val chip = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = interMedium; textAlign = Paint.Align.CENTER
        // In dp like the strip's words, so large text sizes can't push it out of its pill or squeeze the word out.
        textSize = size.stripText * 0.7f * this@SuggestionStrip.density
    }
    private val chipLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1 * density }
    private val chipPad = 7 * density
    private val chipGap = 6 * density
    private val chipW = chip.measureText(SAVE) + 2 * chipPad

    private val lockPx = 16 * density
    private val lockBody = KeyIcons.roundRect(5.5f, 10.5f, 13f, 9.5f, 2f, lockPx)
    private val lockShackle = KeyIcons.path(KeyIcons.LOCK_SHACKLE, lockPx)
    private val lockLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = KeyIcons.STROKE * lockPx / KeyIcons.GRID; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** [replaces]: the highlighted word is a fix space will swap in (not just the word as typed), for TalkBack (Q25). */
    fun show(list: List<String>, highlightIndex: Int, replaces: Boolean = false, saveSlot: Int = -1) {
        // New words (typing on, a new field, clear()) drop the question unanswered.
        if (forgetting != null && (0 until 3).any { list.getOrNull(it) != words[it] }) forgetting = null
        replacing = replaces
        offeredSave = if (list.getOrNull(saveSlot) != null) saveSlot else -1
        this.saveSlot = offeredSave
        val now = SystemClock.uptimeMillis()
        for (i in 0 until 3) {
            val w = list.getOrNull(i)
            // A finger already down on this slot doesn't pick the new word on lift (it never saw it).
            if (w != words[i]) { words[i] = w; slotStart[i] = if (w == null) 0L else now; if (i == pressedSlot) pressedSlot = -1 }
        }
        highlight = highlightIndex
        ellipsize()
        invalidate()
        a11y.invalidateRoot()
    }

    private fun askForget(w: String) {
        forgetting = w
        forgetShown = fitPrompt(w)
        invalidate()
        a11y.invalidateRoot()
        announceForAccessibility(KeyLabels.forgetPrompt(w))
    }

    private fun endForget(remove: Boolean) {
        val w = forgetting ?: return
        forgetting = null
        if (remove) onForget(w)
        invalidate()
        a11y.invalidateRoot()
    }

    /** Width of the "Remove" pill on the right, and the question fitted into the rest. */
    private val removeW get() = chip.measureText(REMOVE) + 4 * chipPad
    private fun fitPrompt(w: String): String {
        val room = width - removeW - 3 * 16 * density
        return TextUtils.ellipsize(KeyLabels.forgetPrompt(w), note, room.coerceAtLeast(0f), TextUtils.TruncateAt.MIDDLE).toString()
    }
    private fun onRemove(x: Float) = x >= width - removeW - 2 * 16 * density

    /** Each suggestion is its own item for TalkBack ("Suggestion: hello"); a private field says so (K4). */
    private val a11y = object : ExploreByTouchHelper(this) {
        private val bounds = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            if (forgetting != null) return if (onRemove(x)) REMOVE_ID else KEEP_ID
            val slot = StripFit.slotAt(edges, x)
            return if (words[slot] != null) slot else INVALID_ID
        }

        override fun getVisibleVirtualViews(ids: MutableList<Int>) {
            if (forgetting != null) { ids += KEEP_ID; ids += REMOVE_ID; return }
            for (i in 0 until 3) if (words[i] != null) ids += i
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val f = forgetting
            if (id == KEEP_ID || id == REMOVE_ID) {
                val split = (width - removeW - 2 * 16 * density).toInt()
                bounds.set(if (id == REMOVE_ID) split else 0, 0, if (id == REMOVE_ID) width else split, height)
                node.setBoundsInParent(bounds)
                if (f == null) { node.contentDescription = ""; return }
                node.contentDescription = if (id == REMOVE_ID) KeyLabels.forgetConfirm(f) else KeyLabels.forgetCancel(f)
                node.className = "android.widget.Button"
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                return
            }
            val w = words.getOrNull(id)
            bounds.set(edges[id].toInt(), 0, edges[id + 1].toInt(), height)
            node.setBoundsInParent(bounds)
            if (w == null) { node.contentDescription = ""; return }
            node.contentDescription = KeyLabels.suggestion(w, replaces = replacing && id == highlight, saves = id == saveSlot)
            node.className = "android.widget.Button"
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            if (id != saveSlot && forgettable(w)) node.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, KeyLabels.FORGET_ACTION))
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
            if (id == KEEP_ID || id == REMOVE_ID) {
                if (forgetting == null || action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
                endForget(remove = id == REMOVE_ID); return true
            }
            val w = words.getOrNull(id) ?: return false
            return when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> { onPick(w, id == saveSlot); true }
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> if (id != saveSlot && forgettable(w)) { askForget(w); true } else false
                else -> false
            }
        }
    }

    init { ViewCompat.setAccessibilityDelegate(this, a11y) }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    fun clear() = show(emptyList(), -1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (size.strip * density).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { ellipsize(); forgetting?.let { forgetShown = fitPrompt(it) } }

    /**
     * Fit the words to the strip once, when words change (never while drawing): a long word takes room from the
     * others, then shrinks, then is shortened in the middle so its ending still shows. A "+ Save" word that can't be
     * shown whole loses "+ Save", so a word is never saved unseen (KB-16).
     */
    private fun ellipsize() {
        if (width <= 0) { for (i in 0 until 3) shown[i] = words[i]; return }
        val pad = 24 * density
        val natural = (0 until 3).map { i ->
            words[i]?.let { paint.textSize = baseText; paint.typeface = if (i == highlight) interMedium else inter; paint.measureText(it) }
        }
        val fit = StripFit.fit(width.toFloat(), natural, offeredSave, pad, chipW + chipGap)
        edges = fit.edges
        saveSlot = fit.saveSlot
        for (i in 0 until 3) {
            scale[i] = fit.scale[i]
            val w = words[i]
            shown[i] = if (w == null || fit.whole[i]) w else {
                paint.textSize = baseText * scale[i]; paint.typeface = if (i == highlight) interMedium else inter
                val room = edges[i + 1] - edges[i] - pad - (if (i == saveSlot) chipW + chipGap else 0f)
                TextUtils.ellipsize(w, paint, room.coerceAtLeast(0f), TextUtils.TruncateAt.MIDDLE).toString()
            }
        }
        paint.textSize = baseText
    }

    override fun onDraw(canvas: Canvas) {
        val c = colors
        canvas.drawColor(c.bg)
        if (forgetting != null) { drawForget(canvas, c); return }
        if (words[0] == null && words[1] == null && words[2] == null) {
            if (privateField) drawPrivate(canvas, c)
            return
        }
        val now = SystemClock.uptimeMillis()
        val fade = Tunables[KeysTuning.STRIP_FADE_MS] * animScale
        val cy = height / 2f
        val pillR = 15 * density
        var animating = false
        for (i in 0 until 3) {
            val w = shown[i] ?: continue
            val cx = (edges[i] + edges[i + 1]) / 2
            paint.textSize = baseText * scale[i]
            if (i == pressedSlot) {
                fill.color = c.stripPressed
                pill.set(edges[i] + 6 * density, cy - 17 * density, edges[i + 1] - 6 * density, cy + 17 * density)
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
            paint.color = if (i == highlight || i == saveSlot) c.text else c.soft
            val alpha = (Motion.easeOut(t) * 255).toInt()
            paint.alpha = alpha
            val baseline = cy - (paint.descent() + paint.ascent()) / 2
            if (i == saveSlot) {
                // The word, then a small teal "+ Save" pill, centred together in the slot.
                val wordW = paint.measureText(w)
                val left = cx - (wordW + chipGap + chipW) / 2
                canvas.drawText(w, left + wordW / 2, baseline, paint)
                val cl = left + wordW + chipGap
                val ch = (chip.descent() - chip.ascent()) / 2 + 3 * density
                pill.set(cl, cy - ch, cl + chipW, cy + ch)
                chipLine.color = c.save; chipLine.alpha = alpha
                canvas.drawRoundRect(pill, ch, ch, chipLine)
                chip.color = c.save; chip.alpha = alpha
                canvas.drawText(SAVE, pill.centerX(), cy - (chip.descent() + chip.ascent()) / 2, chip)
            } else canvas.drawText(w, cx, baseline, paint)
            // Dividers only between two slots that both have a word.
            if (i > 0 && shown[i - 1] != null) {
                divider.color = c.divider
                canvas.drawLine(edges[i], height * 0.3f, edges[i], height * 0.7f, divider)
            }
        }
        paint.textSize = baseText
        if (animating) postInvalidateOnAnimation()
    }

    /** "Remove "word" from your words?" on the left, a scarlet "Remove" pill on the right. A tap anywhere else keeps it. */
    private fun drawForget(canvas: Canvas, c: KeyColors) {
        val pad = 16 * density
        note.color = c.text
        canvas.drawText(forgetShown, pad, height / 2f - (note.descent() + note.ascent()) / 2, note)
        val cy = height / 2f
        val ch = (chip.descent() - chip.ascent()) / 2 + 5 * density
        pill.set(width - pad - removeW, cy - ch, width - pad, cy + ch)
        chipLine.color = c.remove; chipLine.alpha = 255
        canvas.drawRoundRect(pill, ch, ch, chipLine)
        chip.color = c.remove; chip.alpha = 255
        canvas.drawText(REMOVE, pill.centerX(), cy - (chip.descent() + chip.ascent()) / 2, chip)
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
        val slot = StripFit.slotAt(edges, e.x)
        if (forgetting != null) {
            // The question is up: a tap on "Remove" forgets the word, a tap anywhere else keeps it.
            if (e.actionMasked == MotionEvent.ACTION_UP) { if (longPressed) longPressed = false else endForget(remove = onRemove(e.x)) }
            if (e.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedSlot = if (words[slot] != null) slot else -1; longPressed = false
                if (pressedSlot >= 0) postDelayed(longPress, LONG_PRESS_MS)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (slot != pressedSlot) removeCallbacks(longPress)
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (slot == pressedSlot) words[slot]?.let { onPick(it, slot == saveSlot) }
                pressedSlot = -1; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); pressedSlot = -1; invalidate() }
        }
        return true
    }

    private companion object {
        const val PRIVATE = "Private field. Nothing learned."
        const val SAVE = "+ Save"
        const val REMOVE = "Remove"
        /** Like space's long press: longer than a key's, so a slow tap on a word still picks it. */
        const val LONG_PRESS_MS = 600L
        /** TalkBack items while the forget question is up (suggestions are 0 to 2). */
        const val KEEP_ID = 10
        const val REMOVE_ID = 11
    }
}
