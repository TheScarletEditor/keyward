package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Runs the real dictionary on the laptop JVM. Synthetic words only. */
class SuggestTest {
    companion object {
        lateinit var dict: Dictionary
        @BeforeClass @JvmStatic fun load() { dict = Dictionary.load(TestDict.source()) }
    }

    private fun fix(w: String) = dict.autocorrect(w, { dict.isKnown(it) }, { 0 }, emptyList())

    @Test fun commonTyposGetFixed() {
        assertEquals("the", fix("teh"))
        assertEquals("hello", fix("helo"))
        assertEquals("because", fix("becuase"))
        assertEquals("don't", fix("dont"))
        assertEquals("tomorrow", fix("tomorow"))
    }

    @Test fun realWordsAreLeftAlone() {
        listOf("the", "walk", "dog", "nausea", "ibuprofen", "I", "ok").forEach { assertNull(it, fix(it)) }
    }

    @Test fun iIsAlwaysCapital() {
        assertEquals("I", fix("i"))
        assertEquals("I'm", fix("im"))
        assertEquals("I'm", fix("i'm"))
        assertEquals("I've", fix("ive"))
        assertEquals("I'll", fix("i'll"))
        assertEquals("I'd", fix("i'd"))
        listOf("I", "I'm", "I'M", "id", "ill").forEach { assertNull(it, fix(it)) }
        assertNull("acronym in caps", fix("IM"))
        // Forms the rule fixes (R12: never learned just by typing them where autocorrect is off).
        assertTrue(Dictionary.fixesToI("im")); assertTrue(Dictionary.fixesToI("ive")); assertTrue(Dictionary.fixesToI("Im"))
        assertFalse(Dictionary.fixesToI("i'm")); assertFalse(Dictionary.fixesToI("I'm")); assertFalse(Dictionary.fixesToI("ill"))
        // A word the user taught Keys by taking the fix back is left alone.
        assertNull(dict.autocorrect("im", { dict.isKnown(it) }, { 0 }, listOf("im")))
        // "i'm" learned from every fix still gets its capital.
        assertEquals("I'm", dict.autocorrect("i'm", { dict.isKnown(it) }, { 0 }, listOf("i'm")))
        assertEquals("I'm", dict.autocorrect("im", { dict.isKnown(it) }, { 0 }, listOf("i'm")))
    }

    @Test fun modernNamesAreKnown() {
        listOf("macbook", "Macbook", "MacBook", "airpods", "uber", "venmo", "TikTok", "Keyward", "keyward").forEach { assertNull(it, fix(it)) }
        val c = dict.candidates("macbook", { 0 }, emptyList()).map { it.word }
        assertTrue(c.toString(), "MacBook" in c)
    }

    @Test fun aCapitalIsCorrectedUnlessSaved() {
        // Q28: a capital alone doesn't protect a word; a saved (known) word is never corrected.
        assertEquals("the", fix("teh"))
        assertEquals("the", dict.autocorrect("Teh", { dict.isKnown(it) }, { 0 }, emptyList()))
        assertNull(dict.autocorrect("Teh", { dict.isKnown(it) || it == "teh" }, { 0 }, listOf("teh")))
    }

    @Test fun completionsOffered() {
        val c = dict.candidates("tomo", { 0 }, emptyList()).map { it.word.lowercase() }
        assertTrue(c.take(3).toString(), "tomorrow" in c.take(3))
    }

    @Test fun learnedWordsStopCorrection() {
        val learned = listOf("zorbex")
        assertNull(dict.autocorrect("zorbex", { dict.isKnown(it) || it in learned }, { 0 }, learned))
    }

    @Test fun sharedCandidatesGiveTheSameFix() {
        // The keyboard hands autocorrect the candidates it already found for the strip; the answer must not change.
        listOf("teh", "helo", "becuase", "dont", "tomorow", "the", "zorbex", "wierd").forEach { w ->
            val ranked = dict.candidates(w, { 0 }, emptyList())
            assertEquals(w, fix(w), dict.autocorrect(w, { dict.isKnown(it) }, { 0 }, emptyList(), ranked))
        }
    }

    @Test fun fastEnoughPerKeystroke() {
        val words = listOf("becuase", "tomorow", "recieve", "seperate", "definately", "accomodate", "wierd", "goverment")
        val t0 = System.nanoTime()
        repeat(5) { words.forEach { fix(it) } }
        val perCall = (System.nanoTime() - t0) / 1_000_000.0 / (5 * words.size)
        println("autocorrect avg ms per call on laptop: $perCall")
        assertTrue("too slow: $perCall ms", perCall < 50)
    }
}
