package com.thescarletcoder.keys

/** Plain text rules the keyboard uses around the cursor. No Android types, so the JVM tests cover them. */
object Words {
    /** A letter or an apostrophe: what a word is made of, for typing and for learned words. */
    fun isWordChar(c: Char) = c.isLetter() || c == '\''

    /** Longest word kept or saved. */
    const val MAX_LETTERS = 30

    /** Something that can be learned or saved: 2 to [MAX_LETTERS] word characters, nothing else. */
    fun isWord(w: String) = w.length in 2..MAX_LETTERS && w.all(::isWordChar)

    /** In an EditorInfo's privateImeOptions from Keyward's own screens: nothing typed there is learned or corrected (the words search). */
    const val NO_LEARN = "com.thescarletcoder.keys.noLearn"

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
}
