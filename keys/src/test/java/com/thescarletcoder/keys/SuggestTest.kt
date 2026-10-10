package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Runs the real dictionary on the laptop JVM. Synthetic words only. */
class SuggestTest {
    companion object {
        lateinit var dict: Dictionary
        @BeforeClass @JvmStatic fun load() { dict = Dictionary.load(File("../tools/dict/en.dict").inputStream()) }
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
