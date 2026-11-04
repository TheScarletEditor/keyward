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

    @Test fun learnedDictionaryWordsKeepTheirCapital() {
        // Learned words are held in lowercase; the dictionary's spelling wins.
        val learned = listOf("london", "i'm")
        assertEquals("London", dict.candidates("lond", { 0 }, learned).first { it.word.equals("london", ignoreCase = true) }.word)
        assertEquals("I'm", dict.candidates("i'", { 0 }, learned).first { it.word.equals("i'm", ignoreCase = true) }.word)
        assertEquals("London", dict.autocorrect("lomdon", { dict.isKnown(it) }, { 0 }, learned))
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

    private fun top(w: String, learned: List<String> = emptyList()) =
        dict.candidates(w, { if (it in learned) 80 else 0 }, learned).first().word

    /** Learned words one slip from the contractions below, each at the most boost a learned word gets. */
    private val nearMisses = listOf("all", "will", "it", "is", "want", "went", "here", "where", "help", "hello", "we", "can", "i", "hell", "well")

    @Test fun contractionsWithoutTheApostrophe() {
        // The word with its apostrophe left out finds the contraction, first in line after the typed word, even when
        // learned words are a slip away (they used to tie with it, so it could drop off the strip).
        mapOf(
            "ill" to "I'll", "im" to "I'm", "ive" to "I've", "id" to "I'd", "dont" to "don't", "youre" to "you're",
            "wont" to "won't", "cant" to "can't", "its" to "it's", "well" to "we'll", "were" to "we're", "wed" to "we'd",
            "hell" to "he'll", "shell" to "she'll", "theyre" to "they're", "didnt" to "didn't", "isnt" to "isn't",
            "lets" to "let's", "whats" to "what's", "shed" to "she'd",
        ).forEach { (typed, want) ->
            assertEquals(typed, want, top(typed))
            assertEquals("$typed with learned words", want, top(typed, nearMisses))
        }
        // Built from the dictionary, not a list: rarer ones and names come too.
        assertEquals("o'clock", top("oclock"))
        assertEquals("O'Brien", top("obrien"))
    }

    @Test fun contractionsKeepTheCapitalI() {
        listOf("ill", "im", "ive", "id").forEach { assertTrue(it, top(it).startsWith("I'")) }
        assertEquals("I'll", Words.matchCase("ill", top("ill")))
        assertEquals("I'll", Words.matchCase("Ill", top("Ill")))
    }

    @Test fun realWordsAreNeverReplacedByTheirContraction() {
        // Suggested only: each is a word of its own, so space keeps what was typed.
        listOf("ill", "well", "were", "wed", "its", "id", "hell", "shell", "cant", "wont", "lets", "shed").forEach {
            assertNull(it, fix(it))
            assertNull("$it with learned words", dict.autocorrect(it, { w -> dict.isKnown(w) }, { 80 }, nearMisses))
        }
        assertNull("well on its own", fix("well"))
    }

    @Test fun unambiguousContractionsAutocorrect() {
        mapOf("dont" to "don't", "youre" to "you're", "didnt" to "didn't", "isnt" to "isn't", "theyre" to "they're", "im" to "I'm").forEach { (typed, want) ->
            assertEquals(typed, want, fix(typed))
            // A learned "won't" or "want" a slip from "dont" used to tie with "don't" and could win the fix.
            assertEquals("$typed with learned words", want, dict.autocorrect(typed, { dict.isKnown(it) }, { 80 }, nearMisses + "won't"))
        }
    }

    @Test fun possessivesDontCrowdPlurals() {
        assertFalse(Dictionary.isContraction("band's")); assertFalse(Dictionary.isContraction("girls'"))
        assertTrue(Dictionary.isContraction("it's")); assertTrue(Dictionary.isContraction("don't")); assertTrue(Dictionary.isContraction("o'clock"))
        assertFalse(Dictionary.isContraction("'tis")) // leading apostrophe: nothing typed leaves it out
        assertEquals("bands", top("bands"))
    }

    @Test fun typedWithTheApostropheIsUnchanged() {
        assertEquals("I'll", fix("i'll"))
        assertEquals("don't", top("don't"))
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
