package com.thescarletcoder.keys

/** Plain text rules the keyboard uses around the cursor. No Android types, so the JVM tests cover them. */
object Words {
    /** Closing and opening quotes and brackets: "He left." teh and . (teh still start a sentence. */
    private const val WRAPPERS = "\"'()[]{}“”‘’«»"

    /** A letter or an apostrophe: what a word is made of, for typing and for learned words. */
    fun isWordChar(c: Char) = c.isLetter() || c == '\''

    /**
     * The word [before] ends with (the text right before the cursor), leading apostrophes dropped; "" when it doesn't
     * end with one. Null when the whole of [before] is word characters and it is [max] long: the word may be longer
     * than anything worth picking up.
     */
    fun wordAtEnd(before: CharSequence, max: Int): String? {
        var n = 0
        while (n < before.length && isWordChar(before[before.length - 1 - n])) n++
        if (n == before.length && before.length >= max) return null
        return before.subSequence(before.length - n, before.length).toString().trimStart('\'')
    }

    /**
     * True when a word typed after [before] starts a sentence: nothing before it, or the last thing before it
     * (spaces, quotes and brackets aside) ends a sentence or a line. Unknown (null) counts as a start.
     */
    fun startsSentence(before: CharSequence?): Boolean {
        var i = (before ?: return true).length - 1
        while (i >= 0 && (before[i] == ' ' || before[i] == '\t' || before[i] in WRAPPERS)) i--
        return i < 0 || before[i] in ".!?\n"
    }
}
