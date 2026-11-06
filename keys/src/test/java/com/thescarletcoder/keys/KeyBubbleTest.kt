package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyBubbleTest {
    @Test fun theBubbleIsWiderThanItsKey() {
        assertEquals(89f to 211f, KeyBubble.span(100f, 200f, 11f, 1080f))
    }

    @Test fun edgeKeysKeepTheirBubbleOnScreen() {
        // "q" at the left edge and "p" at the right: slid in, same width.
        assertEquals(0f to 122f, KeyBubble.span(5f, 105f, 11f, 1080f))
        assertEquals(958f to 1080f, KeyBubble.span(975f, 1075f, 11f, 1080f))
    }

    @Test fun aBubbleWiderThanTheScreenIsLeftAsIs() {
        assertEquals(-11f to 111f, KeyBubble.span(0f, 100f, 11f, 50f))
    }
}
