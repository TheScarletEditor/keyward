package com.thescarletcoder.keys

import android.graphics.Matrix
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.thescarletcoder.scarlet.ScarletPalette

/** Keyboard colours for one theme (KEYS-POLISH-SPEC section 1). Enter is teal; scarlet is kept for delete actions in Settings (Q30). */
class KeyColors(p: ScarletPalette) {
    val dark = p.dark
    val bg = if (p.dark) 0xFF171614.toInt() else 0xFFE9E6E0.toInt()
    val key = if (p.dark) 0xFF2A2825.toInt() else 0xFFFFFEFC.toInt()
    val function = if (p.dark) 0xFF211F1C.toInt() else 0xFFDCD7CF.toInt()
    val pressedKey = if (p.dark) 0xFF3A3733.toInt() else 0xFFE4E0D9.toInt()
    val pressedFunction = if (p.dark) 0xFF3A3733.toInt() else 0xFFCFC9C0.toInt()
    /** Hairline edge (dark only; in light the lip does the work). 0 = none. */
    val edge = if (p.dark) p.line else 0
    val text = p.text
    val soft = p.soft
    val faint = p.faint
    val enter = p.teal
    val enterPressed = if (p.dark) 0xFF4C7F72.toInt() else 0xFF365E54.toInt()
    /** The Enter icon: the page colour on teal (4.9:1 light, 5.6:1 dark). */
    val enterText = p.bg
    /** "+ Save" on the strip: teal, darkened in light so the small text reads on the keyboard (about 5:1). */
    val save = if (p.dark) p.teal else 0xFF3A6A5E.toInt()
    val capsLock = p.tealWash
    val capsLockEdge = p.tealEdge
    val lip = if (p.dark) 0x99000000.toInt() else 0x2E171614
    /** Dark only: a hairline highlight along the top of each key. */
    val highlight = if (p.dark) 0x12FAF9F6 else 0
    val bubble = if (p.dark) 0xFF3A3733.toInt() else 0xFFFFFEFC.toInt()
    val stripPick = if (p.dark) 0x14FAF9F6 else 0x0F171614
    val stripPressed = if (p.dark) 0x24FAF9F6 else 0x1A171614
    val divider = p.line
}

/** Key icons as paths on a 24-unit grid (KEYS-POLISH-SPEC section 2). Never font glyphs. */
object KeyIcons {
    const val GRID = 24f
    const val STROKE = 1.7f

    val SHIFT = "M12 4.5 L4.6 12 H8.8 V18.5 H15.2 V12 H19.4 Z"
    val CAPS = "M12 3.5 L4.6 11 H8.8 V16.5 H15.2 V11 H19.4 Z"
    val CAPS_BAR = "M8.8 20.5 H15.2"
    val DELETE = "M9.2 6 H19 a2 2 0 0 1 2 2 v8 a2 2 0 0 1 -2 2 H9.2 L3.5 12 Z"
    val DELETE_X = "M12.6 9.6 l4.8 4.8 M17.4 9.6 l-4.8 4.8"
    val SEND = "M4.5 11.6 L19.5 4.6 L15.6 19.2 L11.6 13 Z"
    val SEND_FOLD = "M11.6 13 L19.5 4.6"
    val SEARCH_HANDLE = "M15 15 L19.8 19.8"
    val GO = "M5 12 H18 M13 7 L18 12 L13 17"
    val RETURN = "M19 5.5 V12 a2 2 0 0 1 -2 2 H5.5"
    val RETURN_HEAD = "M9 10.5 L5.5 14 L9 17.5"
    val LOCK_SHACKLE = "M8.5 10.5 V8 a3.5 3.5 0 0 1 7 0 v2.5"

    /** [data] scaled from the 24 grid to [sizePx], origin at the icon's top-left. */
    fun path(data: String, sizePx: Float): Path = PathParser.createPathFromPathData(data).apply {
        transform(Matrix().apply { setScale(sizePx / GRID, sizePx / GRID) })
    }

    fun circle(cx: Float, cy: Float, r: Float, sizePx: Float): Path =
        Path().apply { val s = sizePx / GRID; addCircle(cx * s, cy * s, r * s, Path.Direction.CW) }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, sizePx: Float): Path = Path().apply {
        val s = sizePx / GRID
        addRoundRect(x * s, y * s, (x + w) * s, (y + h) * s, r * s, r * s, Path.Direction.CW)
    }
}

/** What Enter shows for the current field. */
enum class EnterKind { RETURN, SEND, SEARCH, GO, WORD }
