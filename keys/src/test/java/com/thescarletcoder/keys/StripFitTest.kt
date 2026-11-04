package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sizes roughly as on a phone, in px: a 1080 px strip, 63 px around each word, 150 px for "+ Save". */
class StripFitTest {
    private val total = 1080f
    private val pad = 63f
    private val chip = 150f
    private fun w(chars: Int) = chars * 22f // about 22 px a letter at the usual size

    private fun widths(f: StripFit.Fit) = (0 until 3).map { f.edges[it + 1] - f.edges[it] }

    @Test fun shortWordsKeepThreeEqualSlots() {
        val f = StripFit.fit(total, listOf(w(4), w(5), w(3)), -1, pad, chip)
        widths(f).forEach { assertEquals(360f, it, 0.01f) }
        assertTrue(f.whole.all { it })
        assertEquals(1f, f.scale[0], 0f)
    }

    @Test fun aLongWordTakesRoomFromShortOnes() {
        val f = StripFit.fit(total, listOf(w(4), w(18), w(3)), -1, pad, chip)
        assertTrue(widths(f)[1] > 360f)
        assertTrue(f.whole.all { it })
        assertEquals(1f, f.scale[1], 0f)
        assertEquals(total, f.edges[3], 0.01f)
    }

    @Test fun aVeryLongWordShrinksButStaysWhole() {
        val f = StripFit.fit(total, listOf(w(12), w(26), w(12)), -1, pad, chip)
        assertTrue(f.whole.all { it })
        assertTrue(f.scale[1] < 1f && f.scale[1] >= StripFit.MIN_SCALE)
    }

    @Test fun theSaveWordIsShownWholeEvenWhenTheOthersAreCut() {
        // "accomodation" style: a long typed word with "+ Save", beside two long suggestions.
        val f = StripFit.fit(total, listOf(w(22), w(20), w(20)), 0, pad, chip)
        assertEquals(0, f.saveSlot)
        assertTrue(f.whole[0])
        assertFalse(f.whole[1] && f.whole[2])
    }

    @Test fun aSaveWordThatCantBeShownWholeLosesSave() {
        // A narrow strip (split screen): the word can't be read in full, so it isn't offered for saving.
        val f = StripFit.fit(400f, listOf(w(30), w(10), w(10)), 0, pad, chip)
        assertEquals(-1, f.saveSlot)
    }

    @Test fun emptySlotsGiveTheirRoomAndAreNeverHit() {
        val f = StripFit.fit(total, listOf(w(30), null, null), -1, pad, chip)
        assertTrue(f.whole[0])
        assertEquals(0, StripFit.slotAt(f.edges, 1000f))
        assertEquals(0, StripFit.slotAt(f.edges, 10f))
    }

    @Test fun tapsLandInTheSlotTheyHit() {
        val f = StripFit.fit(total, listOf(w(4), w(18), w(3)), -1, pad, chip)
        assertEquals(0, StripFit.slotAt(f.edges, f.edges[1] - 1))
        assertEquals(1, StripFit.slotAt(f.edges, f.edges[1] + 1))
        assertEquals(2, StripFit.slotAt(f.edges, total - 1))
        assertEquals(2, StripFit.slotAt(f.edges, total + 5))
    }
}
