package com.thescarletcoder.keys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat

enum class Shift { OFF, ONE, LOCKED }

interface KeyListener {
    fun onPressFeedback()
    fun onKey(key: Key)
    fun onAlt(text: String)
    fun onDeleteRepeat()
    fun onSpaceLongPress()
}

/** Draws the keys and turns touches into key presses. Plain Canvas drawing for the lowest typing latency. */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: KeyListener) : View(context) {

    var rows: List<List<Key>> = Layouts.letters
        set(v) { field = v; requestLayout(); invalidate() }
    var shift: Shift = Shift.OFF
        set(v) { field = v; invalidate() }
    var enterLabel: String = "return"
        set(v) { field = v; invalidate() }
    var keyHeightDp: Int = 64
        set(v) { field = v; requestLayout() }

    private val density = resources.displayMetrics.density
    private val size = Compact.sizes(context)
    private val gap = size.gap * density
    private val pad = size.pad * density
    private val rects = mutableListOf<Pair<RectF, Key>>()
    private val pressed = SparseArray<Pair<RectF, Key>>() // pointer id -> key
    private val consumed = mutableSetOf<Int>() // pointers whose long press already fired

    private val inter = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    private val interMedium = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1 * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = KeyColors.TEXT }
    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * density; strokeCap = Paint.Cap.ROUND }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; color = KeyColors.TEXT_FAINT; typeface = inter }

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val wanted = (rows.size * keyHeightDp * density + (rows.size - 1) * gap + 2 * pad).toInt()
        // Android caps the keyboard window's height (in landscape on the tablet it is less than the chosen key
        // height needs). Take what is offered and shrink the keys in onLayout, so the bottom row never gets cut off.
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) wanted
            else minOf(wanted, MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        rects.clear()
        val keyH = minOf(keyHeightDp * density, (height - 2 * pad - (rows.size - 1) * gap) / rows.size)
        val usable = width - 2 * pad
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (usable - gap * (row.size - 1)) / total
            var x = pad
            val y = pad + r * (keyH + gap)
            row.forEach { k ->
                val w = unit * k.weight
                if (k.type != KeyType.SPACER) rects += RectF(x, y, x + w, y + keyH) to k
                x += w + gap
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(KeyColors.BG)
        val r = size.corner * density
        val isPressed = { k: Key -> (0 until pressed.size()).any { pressed.valueAt(it).second === k } }
        rects.forEach { (rect, k) ->
            val down = isPressed(k)
            val (bg, edge) = when {
                k.type == KeyType.ENTER -> KeyColors.ACCENT to KeyColors.ACCENT_EDGE
                k.type == KeyType.SHIFT && shift == Shift.LOCKED -> KeyColors.ON to KeyColors.ON_EDGE
                k.type == KeyType.CHAR || k.type == KeyType.SPACE -> KeyColors.KEY to KeyColors.EDGE
                else -> KeyColors.FUNCTION to KeyColors.EDGE
            }
            fill.color = if (down) KeyColors.PRESSED else bg
            canvas.drawRoundRect(rect, r, r, fill)
            stroke.color = edge
            canvas.drawRoundRect(rect, r, r, stroke)

            if (k.type == KeyType.HIDE) {
                // Down chevron: close the keyboard. Drawn, so it never depends on a font having the symbol.
                chevron.color = KeyColors.TEXT
                val cx = rect.centerX(); val cy = rect.centerY(); val s = 9 * density
                canvas.drawLine(cx - s, cy - s / 2, cx, cy + s / 2, chevron)
                canvas.drawLine(cx, cy + s / 2, cx + s, cy - s / 2, chevron)
                return@forEach
            }
            val label = labelFor(k)
            val isWordLabel = label.length > 1
            text.typeface = if (isWordLabel) interMedium else inter
            text.textSize = if (isWordLabel) size.wordText * density else size.charText * density
            text.color = if (k.type == KeyType.SPACE) KeyColors.TEXT_FAINT else KeyColors.TEXT
            val ty = rect.centerY() - (text.descent() + text.ascent()) / 2
            canvas.drawText(label, rect.centerX(), ty, text)

            k.alt?.let {
                hint.textSize = size.hint * density
                canvas.drawText(it, rect.right - (size.hint * 0.6f) * density, rect.top + (size.hint * 1.35f) * density, hint)
            }
        }
    }

    private fun labelFor(k: Key): String = when (k.type) {
        KeyType.CHAR -> if (shift != Shift.OFF && k.label.length == 1 && k.label[0].isLetter()) k.label.uppercase() else k.label
        KeyType.SHIFT -> when (shift) { Shift.OFF -> "⇧"; Shift.ONE -> "⬆"; Shift.LOCKED -> "⇪" }
        KeyType.DELETE -> "⌫"
        KeyType.ENTER -> enterLabel
        KeyType.SPACE -> "Scarlet Keys"
        else -> k.label
    }

    private fun keyAt(x: Float, y: Float): Pair<RectF, Key>? {
        rects.firstOrNull { it.first.contains(x, y) }?.let { return it }
        // Between keys: nearest key in the same row, so gaps never swallow a tap.
        return rects.minByOrNull { (r, _) ->
            val dx = maxOf(r.left - x, 0f, x - r.right)
            val dy = maxOf(r.top - y, 0f, y - r.bottom)
            dx * dx + dy * dy * 4
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = e.actionIndex
        val id = e.getPointerId(i)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val hit = keyAt(e.getX(i), e.getY(i)) ?: return true
                pressed.put(id, hit)
                consumed.remove(id)
                listener.onPressFeedback()
                startTimers(id, hit.second)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (p in 0 until e.pointerCount) {
                    val pid = e.getPointerId(p)
                    val cur = pressed.get(pid) ?: continue
                    if (consumed.contains(pid) || cur.second.type == KeyType.DELETE) continue
                    val hit = keyAt(e.getX(p), e.getY(p)) ?: continue
                    if (hit.second !== cur.second) { pressed.put(pid, hit); cancelTimers(); startTimers(pid, hit.second); invalidate() }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val hit = pressed.get(id)
                pressed.remove(id)
                cancelTimers()
                if (hit != null && !consumed.contains(id) && hit.second.type != KeyType.DELETE) listener.onKey(hit.second)
                consumed.remove(id)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed.clear(); consumed.clear(); cancelTimers(); invalidate() }
        }
        return true
    }

    private fun startTimers(pointer: Int, key: Key) {
        when {
            key.type == KeyType.DELETE -> {
                listener.onKey(key) // delete fires on press, then repeats while held
                val r = object : Runnable {
                    override fun run() { listener.onDeleteRepeat(); handler.postDelayed(this, 50) }
                }
                repeatRunnable = r
                handler.postDelayed(r, 400)
            }
            key.alt != null -> {
                val r = Runnable { consumed += pointer; listener.onAlt(key.alt) }
                longPressRunnable = r
                handler.postDelayed(r, 350)
            }
            key.type == KeyType.SPACE -> {
                val r = Runnable { consumed += pointer; listener.onSpaceLongPress() }
                longPressRunnable = r
                handler.postDelayed(r, 600)
            }
        }
    }

    private fun cancelTimers() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
        longPressRunnable = null
    }

    override fun onDetachedFromWindow() {
        cancelTimers()
        super.onDetachedFromWindow()
    }
}

/** Scarlet Coder dark palette for the keyboard. Scarlet stays soft and only on Enter. */
object KeyColors {
    const val BG = 0xFF171614.toInt()
    const val KEY = 0xFF2A2825.toInt()
    const val FUNCTION = 0xFF211F1C.toInt()
    const val PRESSED = 0xFF3A3733.toInt()
    const val EDGE = 0x1FFAF9F6
    const val TEXT = 0xFFFAF9F6.toInt()
    const val TEXT_SOFT = 0xFF908E86.toInt()
    const val TEXT_FAINT = 0xFF65635E.toInt()
    const val ACCENT = 0x24E20514 // scarlet ~14%
    const val ACCENT_EDGE = 0x73E20514 // scarlet ~45%
    const val ON = 0x385E9B8C // teal wash
    const val ON_EDGE = 0x995E9B8C.toInt()
    const val TEAL = 0xFF5E9B8C.toInt()
}
