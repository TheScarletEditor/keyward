package com.thescarletcoder.keys

/**
 * The letter bubble's size and place: a little wider than the key and kept on screen at the edges (KB-18). Plain
 * arithmetic, no Android types, so the JVM tests cover it. Its height is the "Letter bubble" tunable.
 */
object KeyBubble {
    /** How far the bubble reaches past each side of its key, in dp (was 7). */
    const val OVERHANG_DP = 11f
    /** The letter's size in the bubble, in sp (was 30). */
    const val TEXT_SP = 34f

    /**
     * Left and right edges of the bubble over a key from [keyLeft] to [keyRight], reaching [overhang] past each side,
     * slid back inside 0 to [width] where an edge key ("q", "p") would push it off the screen.
     */
    fun span(keyLeft: Float, keyRight: Float, overhang: Float, width: Float): Pair<Float, Float> {
        var l = keyLeft - overhang
        var r = keyRight + overhang
        if (width <= 0f || r - l >= width) return l to r
        if (l < 0f) { r -= l; l = 0f }
        if (r > width) { l -= r - width; r = width }
        return l to r
    }
}
