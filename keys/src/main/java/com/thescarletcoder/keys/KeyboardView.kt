package com.thescarletcoder.keys

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
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

enum class Shift { OFF, ONE, LOCKED }

interface KeyListener {
    fun onPressFeedback(key: Key)
    fun onKey(key: Key)
    fun onAlt(text: String)
    fun onDeleteRepeat()
    fun onSpaceLongPress()
}

/** The letter bubble and the accent row, drawn by [KeyOverlay] above the keys. Coordinates are this view's. */
interface KeyPopups {
    fun bubbleDown(key: RectF, label: String)
    fun bubbleUp()
    fun accentsOpen(left: Float, top: Float, cellW: Float, cellH: Float, options: Array<String>, highlight: Int)
    fun accentsMove(highlight: Int)
    fun accentsClose()
}

/**
 * Draws the keys and turns touches into key presses. Plain Canvas drawing for the lowest typing latency.
 * Nothing is allocated while drawing or handling touches: keys live in parallel arrays built in onLayout.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: KeyListener) : View(context) {

    var rows: List<List<Key>> = Layouts.letters
        set(v) {
            // TalkBack's focus was on a key of the old layout: let it go, or it lands on a different key (L4-1).
            a11y.accessibilityFocusedVirtualViewId.takeIf { it != ExploreByTouchHelper.INVALID_ID }?.let {
                a11y.getAccessibilityNodeProvider(this)?.performAction(it, AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
            }
            field = v; closeAccents(); requestLayout(); invalidate()
        }
    var shift: Shift = Shift.OFF
        set(v) { if (field != v) { field = v; invalidate(); a11y.invalidateRoot() } }
    var enterKind: EnterKind = EnterKind.RETURN
        set(v) { field = v; invalidate(); a11y.invalidateRoot() }
    /** Shown on Enter when [enterKind] is WORD ("Next", "Done", "Back"). */
    var enterWord: String = ""
        set(v) { field = v; invalidate(); a11y.invalidateRoot() }
    var keyHeightDp: Int = 64
        set(v) { field = v; requestLayout() }
    var colors: KeyColors = KeyColors(ScarletTokens.DARK)
        set(v) { field = v; invalidate() }
    var popups: KeyPopups? = null
    var bubbleEnabled = false
    /** Room above this view's top (inside the keyboard window) that the accent row may use, in px. */
    var popupRoom = 0f
    /** Android's animator duration scale, refreshed by the service when a field starts. 0 = remove animations. */
    var animScale = 1f

    /** The field has text: the space bar wordmark dims. */
    var fieldHasText = false
        set(v) {
            if (field == v) return
            wordFrom = wordmarkAlpha(SystemClock.uptimeMillis()); wordStart = SystemClock.uptimeMillis()
            field = v; invalidate()
        }

    private val density = resources.displayMetrics.density
    private val size = Compact.sizes(context)
    private val gap = size.gap * density
    private val pad = size.pad * density

    // Laid-out keys (parallel arrays, rebuilt in onLayout).
    private var n = 0
    private var keyRect = arrayOf<RectF>()
    private var keyOf = arrayOf<Key>()
    private var upperLabel = arrayOf<String>()
    private var downCount = IntArray(0)
    private var releasedAt = LongArray(0)
    private var accentsFor = arrayOfNulls<String>(0)

    // Pointer id -> key index (-1 = none). Pointer ids are small in practice.
    private val pointerKey = IntArray(MAX_POINTERS) { -1 }
    private val consumed = BooleanArray(MAX_POINTERS)

    // Accent row
    private var popupPointer = -1
    private var popupOptions: Array<String> = emptyArray()
    private var popupLeft = 0f
    private var popupCellW = 1f
    private var popupHighlight = 0

    // Space bar wordmark fade
    private var wordFrom = 1f
    private var wordStart = 0L

    private val inter = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    private val interMedium = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1 * density }
    private val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1 * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; typeface = inter }
    private val wordmark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = interMedium; letterSpacing = 0.16f
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }
    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * density; strokeCap = Paint.Cap.ROUND }

    // Icons, built once at their drawn size (never in onDraw).
    private val iconPx = (if (Compact.isCompact(context)) 20f else 22f) * density
    private val iconLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = KeyIcons.STROKE * iconPx / KeyIcons.GRID
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shiftPath = KeyIcons.path(KeyIcons.SHIFT, iconPx)
    private val capsPath = KeyIcons.path(KeyIcons.CAPS, iconPx)
    private val capsBar = KeyIcons.path(KeyIcons.CAPS_BAR, iconPx)
    private val deletePath = KeyIcons.path(KeyIcons.DELETE, iconPx)
    private val deleteX = KeyIcons.path(KeyIcons.DELETE_X, iconPx)
    private val sendPath = KeyIcons.path(KeyIcons.SEND, iconPx)
    private val sendFold = KeyIcons.path(KeyIcons.SEND_FOLD, iconPx)
    private val searchCircle = KeyIcons.circle(10.5f, 10.5f, 5.8f, iconPx)
    private val searchHandle = KeyIcons.path(KeyIcons.SEARCH_HANDLE, iconPx)
    private val goPath = KeyIcons.path(KeyIcons.GO, iconPx)
    private val returnPath = KeyIcons.path(KeyIcons.RETURN, iconPx)
    private val returnHead = KeyIcons.path(KeyIcons.RETURN_HEAD, iconPx)

    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    private val repeatRunnable = object : Runnable {
        override fun run() { listener.onDeleteRepeat(); handler.postDelayed(this, 50) }
    }
    private var lpPointer = -1
    private var lpIndex = -1
    private val longPressRunnable = Runnable { onLongPress() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        var wanted = (rows.size * keyHeightDp * density + (rows.size - 1) * gap + 2 * pad).toInt()
        // In landscape the chosen key height would leave the app only a sliver (on a phone, about 100 dp), so the
        // keys take at most a share of the screen's height and shrink to fit.
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            wanted = minOf(wanted, (resources.displayMetrics.heightPixels * Tunables[KeysTuning.LANDSCAPE_KEYS]).toInt())
        }
        // Android caps the keyboard window's height (in landscape on a tablet it can be less than the chosen key
        // height needs). Take what is offered and shrink the keys in onLayout, so the bottom row never gets cut off.
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) wanted
            else minOf(wanted, MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val keyH = minOf(keyHeightDp * density, (height - 2 * pad - (rows.size - 1) * gap) / rows.size)
        val usable = width - 2 * pad
        val count = rows.sumOf { row -> row.count { it.type != KeyType.SPACER } }
        if (count != n) {
            n = count
            keyRect = Array(count) { RectF() }
            downCount = IntArray(count); releasedAt = LongArray(count)
        }
        val keys = ArrayList<Key>(count)
        var i = 0
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (usable - gap * (row.size - 1)) / total
            var x = pad
            val y = pad + r * (keyH + gap)
            row.forEach { k ->
                val w = unit * k.weight
                if (k.type != KeyType.SPACER) { keyRect[i++].set(x, y, x + w, y + keyH); keys += k }
                x += w + gap
            }
        }
        keyOf = keys.toTypedArray()
        upperLabel = Array(n) { keyOf[it].label.let { l -> if (l.length == 1 && l[0].isLetter()) l.uppercase() else l } }
        accentsFor = Array(n) { keyOf[it].let { k -> if (k.type == KeyType.CHAR && k.label.length == 1) Layouts.accents[k.label[0]] else null } }
        a11y.invalidateRoot()
    }

    // ---- TalkBack (K4) ----

    /**
     * Each key is its own item for TalkBack: explore by touch, then lift (or double tap) to type it. Node info is built
     * only when TalkBack asks, so drawing and touch handling allocate nothing more (D-013).
     */
    private val a11y = object : ExploreByTouchHelper(this) {
        private val bounds = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int = keyAt(x, y).takeIf { it >= 0 } ?: INVALID_ID

        override fun getVisibleVirtualViews(ids: MutableList<Int>) { for (i in 0 until n) ids += i }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            if (id !in 0 until n) { node.contentDescription = ""; node.setBoundsInParent(bounds.apply { setEmpty() }); return }
            val k = keyOf[id]
            node.contentDescription = KeyLabels.spoken(k, shift, enterKind, enterWord)
            node.className = "android.widget.Button"
            keyRect[id].roundOut(bounds)
            node.setBoundsInParent(bounds)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            if (k.alt != null || k.type == KeyType.SPACE) node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, args: Bundle?): Boolean {
            if (id !in 0 until n) return false
            val k = keyOf[id]
            return when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> { listener.onPressFeedback(k); listener.onKey(k); true }
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> when {
                    k.type == KeyType.SPACE -> { listener.onSpaceLongPress(); true }
                    k.alt != null -> { listener.onAlt(k.alt); true }
                    else -> false
                }
                else -> false
            }
        }
    }

    init { ViewCompat.setAccessibilityDelegate(this, a11y) }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun onDraw(canvas: Canvas) {
        val c = colors
        canvas.drawColor(c.bg)
        val now = SystemClock.uptimeMillis()
        val snap = Tunables[KeysTuning.SNAP_OUT_MS] * animScale
        val r = size.corner * density
        val lip = Tunables[KeysTuning.LIP_DP] * density
        var animating = false
        for (i in 0 until n) {
            val rect = keyRect[i]; val k = keyOf[i]
            val letterLike = k.type == KeyType.CHAR || k.type == KeyType.SPACE
            val base: Int; val edge: Int
            when {
                k.type == KeyType.ENTER -> { base = c.enter; edge = c.enterEdge }
                k.type == KeyType.SHIFT && shift == Shift.LOCKED -> { base = c.capsLock; edge = c.capsLockEdge }
                letterLike -> { base = c.key; edge = c.edge }
                else -> { base = c.function; edge = c.edge }
            }
            val pressedColor = if (letterLike) c.pressedKey else c.pressedFunction
            val color = when {
                downCount[i] > 0 -> pressedColor
                snap <= 0f -> base
                else -> {
                    val t = (now - releasedAt[i]) / snap
                    if (t < 1f) { animating = true; mix(pressedColor, base, Motion.easeOut(t)) } else base
                }
            }
            if (lip > 0f) {
                fill.color = c.lip
                canvas.drawRoundRect(rect.left, rect.top + lip, rect.right, rect.bottom + lip, r, r, fill)
            }
            fill.color = color
            canvas.drawRoundRect(rect, r, r, fill)
            if (edge != 0) { stroke.color = edge; canvas.drawRoundRect(rect, r, r, stroke) }
            if (c.highlight != 0) {
                hairline.color = c.highlight
                canvas.drawLine(rect.left + r, rect.top + 0.5f * density, rect.right - r, rect.top + 0.5f * density, hairline)
            }
            drawContent(canvas, i, rect, k, c, now)?.let { animating = true }
        }
        if (animating) postInvalidateOnAnimation()
    }

    /** Draws the key's label or icon. Returns non-null while the wordmark is still fading. */
    private fun drawContent(canvas: Canvas, i: Int, rect: RectF, k: Key, c: KeyColors, now: Long): Unit? {
        when (k.type) {
            KeyType.HIDE -> {
                // Down chevron: close the keyboard.
                chevron.color = c.text
                val cx = rect.centerX(); val cy = rect.centerY(); val s = 9 * density
                canvas.drawLine(cx - s, cy - s / 2, cx, cy + s / 2, chevron)
                canvas.drawLine(cx, cy + s / 2, cx + s, cy - s / 2, chevron)
            }
            KeyType.SHIFT -> when (shift) {
                Shift.OFF -> icon(canvas, rect, c.text, shiftPath, null, filled = false)
                Shift.ONE -> icon(canvas, rect, c.text, shiftPath, null, filled = true)
                Shift.LOCKED -> icon(canvas, rect, c.text, capsPath, capsBar, filled = true)
            }
            KeyType.DELETE -> icon(canvas, rect, c.text, deletePath, deleteX, filled = false)
            KeyType.ENTER -> when (enterKind) {
                EnterKind.SEND -> icon(canvas, rect, c.text, sendPath, sendFold, filled = false)
                EnterKind.SEARCH -> icon(canvas, rect, c.text, searchCircle, searchHandle, filled = false)
                EnterKind.GO -> icon(canvas, rect, c.text, goPath, null, filled = false)
                EnterKind.RETURN -> icon(canvas, rect, c.text, returnPath, returnHead, filled = false)
                EnterKind.WORD -> label(canvas, rect, enterWord, word = true, c.text)
            }
            KeyType.SPACE -> {
                val a = wordmarkAlpha(now)
                wordmark.color = c.faint
                wordmark.alpha = (a * (c.faint ushr 24)).toInt()
                canvas.drawText(WORDMARK, rect.centerX(), rect.centerY() - (wordmark.descent() + wordmark.ascent()) / 2, wordmark)
                if (wordmarkMoving(now)) return Unit
            }
            else -> {
                val l = if (k.type == KeyType.CHAR && shift != Shift.OFF) upperLabel[i] else k.label
                label(canvas, rect, l, word = l.length > 1, c.text)
                k.alt?.let {
                    hint.color = c.faint
                    hint.textSize = size.hint * density
                    canvas.drawText(it, rect.right - (size.hint * 0.6f) * density, rect.top + (size.hint * 1.35f) * density, hint)
                }
            }
        }
        return null
    }

    private fun label(canvas: Canvas, rect: RectF, s: String, word: Boolean, color: Int) {
        text.typeface = if (word) interMedium else inter
        text.textSize = (if (word) size.wordText else size.charText) * density
        text.color = color
        canvas.drawText(s, rect.centerX(), rect.centerY() - (text.descent() + text.ascent()) / 2, text)
    }

    private fun icon(canvas: Canvas, rect: RectF, color: Int, main: Path, extra: Path?, filled: Boolean) {
        canvas.save()
        canvas.translate(rect.centerX() - iconPx / 2, rect.centerY() - iconPx / 2)
        if (filled) { iconFill.color = color; canvas.drawPath(main, iconFill) }
        iconLine.color = color
        canvas.drawPath(main, iconLine) // the outline also rounds a filled shape's corners
        extra?.let { canvas.drawPath(it, iconLine) }
        canvas.restore()
    }

    private fun wordmarkTarget() = if (fieldHasText) Tunables[KeysTuning.WORDMARK_DIM] else 1f
    private fun wordmarkMoving(now: Long) = animScale > 0f && now - wordStart < WORDMARK_FADE_MS * animScale
    private fun wordmarkAlpha(now: Long): Float {
        val target = wordmarkTarget()
        if (!wordmarkMoving(now)) return target
        val t = (now - wordStart) / (WORDMARK_FADE_MS * animScale)
        return wordFrom + (target - wordFrom) * Motion.easeOut(t)
    }

    // ---- touch ----

    private fun keyAt(x: Float, y: Float): Int {
        for (i in 0 until n) if (keyRect[i].contains(x, y)) return i
        // Between keys: nearest key, so gaps never swallow a tap.
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in 0 until n) {
            val rc = keyRect[i]
            val dx = maxOf(rc.left - x, 0f, x - rc.right)
            val dy = maxOf(rc.top - y, 0f, y - rc.bottom)
            val d = dx * dx + dy * dy * 4
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun bubbleLabel(i: Int) = if (shift != Shift.OFF) upperLabel[i] else keyOf[i].label

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val ai = e.actionIndex
        val id = e.getPointerId(ai)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (id >= MAX_POINTERS) return true
                val hit = keyAt(e.getX(ai), e.getY(ai))
                if (hit < 0) return true
                pointerKey[id] = hit; consumed[id] = false
                downCount[hit]++
                listener.onPressFeedback(keyOf[hit])
                if (bubbleEnabled && keyOf[hit].type == KeyType.CHAR) popups?.bubbleDown(keyRect[hit], bubbleLabel(hit))
                startTimers(id, hit)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (p in 0 until e.pointerCount) {
                    val pid = e.getPointerId(p)
                    if (pid >= MAX_POINTERS) continue
                    val cur = pointerKey[pid]
                    if (cur < 0) continue
                    if (pid == popupPointer) {
                        val h = ((e.getX(p) - popupLeft) / popupCellW).toInt().coerceIn(0, popupOptions.size - 1)
                        if (h != popupHighlight) { popupHighlight = h; popups?.accentsMove(h) }
                        continue
                    }
                    if (consumed[pid] || keyOf[cur].type == KeyType.DELETE) continue
                    val hit = keyAt(e.getX(p), e.getY(p))
                    if (hit >= 0 && hit != cur) {
                        downCount[cur]--; releasedAt[cur] = SystemClock.uptimeMillis()
                        downCount[hit]++; pointerKey[pid] = hit
                        cancelTimers(); startTimers(pid, hit)
                        if (bubbleEnabled) {
                            if (keyOf[hit].type == KeyType.CHAR) popups?.bubbleDown(keyRect[hit], bubbleLabel(hit)) else popups?.bubbleUp()
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (id >= MAX_POINTERS) return true
                val idx = pointerKey[id]
                pointerKey[id] = -1
                cancelTimers()
                if (idx >= 0) { downCount[idx] = maxOf(0, downCount[idx] - 1); releasedAt[idx] = SystemClock.uptimeMillis() }
                if (id == popupPointer) {
                    val pick = popupOptions.getOrNull(popupHighlight)
                    closeAccents()
                    if (pick != null) listener.onAlt(pick)
                } else if (idx >= 0 && !consumed[id] && keyOf[idx].type != KeyType.DELETE) listener.onKey(keyOf[idx])
                consumed[id] = false
                if (bubbleEnabled) popups?.bubbleUp()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerKey.fill(-1); consumed.fill(false); downCount.fill(0)
                cancelTimers(); closeAccents(); popups?.bubbleUp(); invalidate()
            }
        }
        return true
    }

    private fun startTimers(pointer: Int, i: Int) {
        val key = keyOf[i]
        when {
            key.type == KeyType.DELETE -> {
                listener.onKey(key) // delete fires on press, then repeats while held
                repeating = true
                handler.postDelayed(repeatRunnable, 400)
            }
            key.alt != null || accentsFor[i] != null || key.type == KeyType.SPACE -> {
                lpPointer = pointer; lpIndex = i
                handler.postDelayed(longPressRunnable, if (key.type == KeyType.SPACE) 600L else Tunables.int(KeysTuning.LONGPRESS_MS).toLong())
            }
        }
    }

    private fun onLongPress() {
        val p = lpPointer; val i = lpIndex
        if (p < 0 || i < 0 || i >= n || pointerKey[p] != i) return
        consumed[p] = true
        val key = keyOf[i]
        val accents = accentsFor[i]
        when {
            key.type == KeyType.SPACE -> listener.onSpaceLongPress()
            accents != null -> openAccents(p, i, key.alt, accents)
            key.alt != null -> listener.onAlt(key.alt)
        }
    }

    /** Row above the key: the digit first (top row), then the accents; the first accent starts highlighted. */
    private fun openAccents(pointer: Int, i: Int, digit: String?, accents: String) {
        val upper = shift != Shift.OFF
        val list = ArrayList<String>(accents.length + 1)
        digit?.let { list += it }
        accents.forEach { ch -> list += if (upper) ch.uppercaseChar().toString() else ch.toString() }
        popupOptions = list.toTypedArray()
        popupHighlight = if (digit != null) 1 else 0
        val rect = keyRect[i]
        popupCellW = rect.width()
        val total = popupCellW * popupOptions.size
        // The highlighted option sits right above the finger, kept inside the keyboard's width.
        popupLeft = (rect.left - popupHighlight * popupCellW).coerceIn(pad, maxOf(pad, width - pad - total))
        val lift = 6 * density
        val cellH = minOf(rect.height(), rect.top - lift + popupRoom).coerceAtLeast(rect.height() * 0.6f)
        popupPointer = pointer
        popups?.bubbleUp()
        popups?.accentsOpen(popupLeft, rect.top - lift - cellH, popupCellW, cellH, popupOptions, popupHighlight)
    }

    private fun closeAccents() {
        if (popupPointer >= 0) { popupPointer = -1; popups?.accentsClose() }
    }

    private fun cancelTimers() {
        if (repeating) { handler.removeCallbacks(repeatRunnable); repeating = false }
        handler.removeCallbacks(longPressRunnable)
        lpPointer = -1; lpIndex = -1
    }

    override fun onDetachedFromWindow() {
        cancelTimers()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX_POINTERS = 16
        private const val WORDMARK = "SCARLET KEYS"
        private const val WORDMARK_FADE_MS = 400f

        /** ARGB blend from [a] to [b] (t 0..1), no allocation. */
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a ushr shift) and 0xFF) + (((b ushr shift) and 0xFF) - ((a ushr shift) and 0xFF)) * t).toInt() and 0xFF
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
