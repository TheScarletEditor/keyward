package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The shipped binary dictionary (built by :keys:buildDict) behaves exactly like the text source. */
class BinaryDictTest {
    @Test fun binaryMatchesText() {
        val text = Dictionary.load(File("../tools/dict/en.dict").inputStream())
        val bin = Dictionary.loadBinary(File("build/generated/assets/buildDict/en.bin").inputStream())
        assertEquals(text.size, bin.size)
        listOf("teh", "helo", "becuase", "dont", "tomorow", "the", "us", "i", "nausea").forEach { w ->
            assertEquals(w, text.isKnown(w), bin.isKnown(w))
            assertEquals(w, text.autocorrect(w, { text.isKnown(it) }, { 0 }, emptyList()), bin.autocorrect(w, { bin.isKnown(it) }, { 0 }, emptyList()))
            assertEquals(w, text.candidates(w, { 0 }, emptyList()).map { it.word }, bin.candidates(w, { 0 }, emptyList()).map { it.word })
        }
    }
}
