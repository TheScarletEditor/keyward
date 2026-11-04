package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a word is corrected, and what spaces and punctuation do around it (Words). Synthetic text only. */
class TypingRulesTest {
    @Test fun aWordJoinedToTheTextBeforeItIsNotAPlainWord() {
        // The character autocorrect looks at: right before the word, apostrophes counted as part of it.
        assertEquals('2', Words.charBeforeWord("the 2nd"))
        assertEquals('@', Words.charBeforeWord("hi @sam"))
        assertEquals(' ', Words.charBeforeWord("say 'teh"))
        assertEquals('.', Words.charBeforeWord("site."))
        assertNull(Words.charBeforeWord("teh"))
        assertNull(Words.charBeforeWord(""))
        // Joined: numbers ("2nd" became "2HD"), handles and tags ("#tbt" became "#TNT"), web addresses, paths, names_like_this.
        "2@#./:_=&+$%".forEach { assertTrue("$it", Words.joinedAfter(it)) }
        // Not joined: the start of the text, a space or new line, an opening bracket or quote, a dash.
        assertFalse(Words.joinedAfter(null))
        " \n\t(\"“'‘-—*¿".forEach { assertFalse("$it", Words.joinedAfter(it)) }
    }

    @Test fun onlyWordEndingPunctuationAutocorrects() {
        listOf(" ", "\n", ".", ",", "?", "!", ";", ")", "\"", "”", "-").forEach { assertTrue(it, Words.correctsOn(it)) }
        // "https:" became "http:"; "@", "/" and "_" join a word to more.
        listOf(":", "@", "/", "_", "#", "=", "&", "+", "*", "\\").forEach { assertFalse(it, Words.correctsOn(it)) }
    }
}
