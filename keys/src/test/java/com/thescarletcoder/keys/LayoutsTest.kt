package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each kind of field gets the keys it needs without a trip to ?123. */
class LayoutsTest {
    private fun labels(rows: List<List<Key>>) = rows.flatten().map { it.label }

    @Test fun phoneFieldsHaveStarAndHash() {
        listOf("*", "#", "+", "0", "9").forEach { assertTrue(it, it in labels(Layouts.phonepad)) }
    }

    @Test fun dateAndTimeFieldsHaveSlashAndColon() {
        listOf("/", ":", "-", ".").forEach { assertTrue(it, it in labels(Layouts.datepad)) }
    }

    @Test fun emailAndWebFieldsPutTheirSymbolNextToSpace() {
        val bottom = { rows: List<List<Key>> -> rows.last().map { it.label } }
        assertEquals(listOf("?123", "@", "space", ".", "enter", "hide"), bottom(Layouts.lettersEmail))
        assertEquals(listOf("?123", "/", "space", ".", "enter", "hide"), bottom(Layouts.lettersUrl))
        // The rest of the keyboard is the same letters.
        assertEquals(Layouts.letters.dropLast(1), Layouts.lettersEmail.dropLast(1))
        assertEquals(Layouts.letters.last().map { it.label }, listOf("?123", ",", "space", ".", "enter", "hide"))
    }

    @Test fun everyPadCanGoToLettersAndBack() {
        listOf(Layouts.numpad, Layouts.phonepad, Layouts.datepad).forEach { pad ->
            val types = pad.flatten().map { it.type }
            listOf(KeyType.LETTERS, KeyType.DELETE, KeyType.ENTER, KeyType.HIDE).forEach { assertTrue("$it", it in types) }
        }
    }
}
