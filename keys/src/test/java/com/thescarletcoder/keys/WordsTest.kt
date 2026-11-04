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

    @Test fun matchCase() {
        assertEquals("don't", Words.matchCase("dont", "don't"))
        assertEquals("Don't", Words.matchCase("Dont", "don't"))
        assertEquals("DON'T", Words.matchCase("DONT", "don't"))
        assertEquals("I'll", Words.matchCase("ill", "I'll"))   // the word's own capital stays
        assertEquals("I'll", Words.matchCase("Ill", "I'll"))
        assertEquals("I", Words.matchCase("i", "I"))
    }
}
