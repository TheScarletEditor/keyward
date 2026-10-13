package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The text rules around the cursor (Words). Synthetic text only. */
class WordsTest {
    @Test fun wordAtEnd() {
        assertEquals("hello", Words.wordAtEnd("say hello", 48))
        assertEquals("", Words.wordAtEnd("say hello ", 48))
        assertEquals("don't", Words.wordAtEnd("I don't", 48))
        assertEquals("quote", Words.wordAtEnd("a 'quote", 48))   // leading apostrophe stays as text
        assertEquals("", Words.wordAtEnd("", 48))
        assertNull(Words.wordAtEnd("abcd", 4))                     // could be longer than what was read
        assertEquals("bc", Words.wordAtEnd("a bc", 4))
    }
}
