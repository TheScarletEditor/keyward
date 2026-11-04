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
     * The character right before the word [before] ends with (or right before the cursor when it ends with no word);
     * null at the start of the text.
     */
    fun charBeforeWord(before: CharSequence): Char? {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        return if (i > 0) before[i - 1] else null
    }

    /**
     * A word starting right after [c] is joined to the text before it, so it's part of something that isn't a plain
     * word: a number ("2nd", "4th"), a handle or tag ("@sam", "#tbt"), an email, a web address or a path ("site.com",
     * "a/b"), a name_like_this. Such a word is never autocorrected. Not after a space, an opening bracket or quote, or a dash.
     */
    fun joinedAfter(c: Char?): Boolean = c != null && !c.isWhitespace() && c !in OPENERS
    private const val OPENERS = "([{\"'“‘«¿¡-–—*"

    /**
     * Whether [sep], ending a word, may autocorrect it: a space or Enter, or punctuation that ends a word in a sentence.
     * Not ":" ("https:" became "http:"), "@", "/", "_", "#" and the like, which join the word to more.
     */
    fun correctsOn(sep: String): Boolean = sep == " " || sep == "\n" || (sep.length == 1 && sep[0] in ENDERS)
    private const val ENDERS = ".,?!;)]}\"”’…-"

    /** Two space taps closer together than this make ". " (Android's own keyboard uses the same 1.1 seconds). */
    const val DOUBLE_SPACE_MS = 1100L

    /** Punctuation that goes before the space a picked suggestion added: "word " then "." is "word. ", not "word .". */
    fun swapsWithSpace(sep: String) = sep.length == 1 && sep[0] in ".,?!"

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
