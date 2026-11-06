package com.thescarletcoder.keys

/** Plain text rules the keyboard uses around the cursor. No Android types, so the JVM tests cover them. */
object Words {
    /** A letter or an apostrophe: what a word is made of, for typing and for learned words. */
    fun isWordChar(c: Char) = c.isLetter() || c == '\''

    /** Longest word kept or saved. */
    const val MAX_LETTERS = 30

    /** Something that can be learned or saved: 2 to [MAX_LETTERS] word characters, nothing else. */
    fun isWord(w: String) = w.length in 2..MAX_LETTERS && w.all(::isWordChar)

    /**
     * A suggestion in the user's capitalisation: "Hello" -> "Hello", "HELLO" -> "HELLO", "Dont" -> "Don't". A capital
     * the word has of its own stays ("ill" -> "I'll").
     */
    fun matchCase(typed: String, word: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

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

    /**
     * A word typed in capitals (two letters or more, none lowercase) without caps lock: an acronym or a name like
     * "CINC" (it became "CIBC"), left as typed. With caps lock on it's shouting, corrected like any word ("TEH" to "THE").
     */
    fun isAcronym(word: String, capsLock: Boolean) = !capsLock && word.count { it.isLetter() } >= 2 && word.none { it.isLowerCase() }

    /**
     * How many characters before the cursor one word-at-a-time backspace removes: the spaces right before the cursor,
     * then the word (everything up to the next space) before them. A surrogate pair cut by the read isn't split.
     */
    fun wordDeleteLength(before: CharSequence): Int {
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        if (i == 0 && before.isNotEmpty() && before[0].isLowSurrogate()) i = 1
        return before.length - i
    }

    /**
     * A period typed instead of a space (they sit side by side): "something.thit" is "something" and "thit" run
     * together (KB-17). [beforeWord] is the text before the word being typed, [word]; [known] says whether a word is
     * a real one and [fix] is autocorrect's fix for one that isn't. Returns the two words to put in with a space,
     * or null to leave it all as typed. Never for:
     * - a web address, domain, email, handle, tag or path: the word before the period starts right after a space
     *   (not after "@", "#", "/", ":" or another "."), and the word after it isn't a domain ending or file type,
     *   including the endings that are everyday words ("my.blog", "about.me", "the.shop"; review 2026-11-05);
     * - abbreviations: one letter either side ("e.g", "i.e", "a.m", "U.S") or a known short form ("etc", "Mr");
     * - numbers (decimals, versions, times): only letters either side;
     * - a word in capitals after the period ("ASP.NET"), an acronym. A capitalised word splits like any other:
     *   "end.The" is "end The", and a fix keeps the capital ("end.Thit" is "end This"), as the owner chose (2026-11-04).
     */
    fun missedSpace(beforeWord: CharSequence, word: String, known: (String) -> Boolean, fix: (String) -> String?): Pair<String, String>? {
        if (word.length < 2 || !word.all { it.isLetter() } || word.none { it.isLowerCase() }) return null
        if (word.lowercase() in NOT_AFTER_DOT || word.lowercase() in WORD_ENDINGS) return null
        val n = beforeWord.length
        if (n < 3 || beforeWord[n - 1] != '.') return null
        var i = n - 1
        while (i > 0 && beforeWord[i - 1].isLetter()) i--
        val left = beforeWord.subSequence(i, n - 1).toString()
        if (left.length < 2) return null
        val c = if (i > 0) beforeWord[i - 1] else null
        if (c != null && !c.isWhitespace() && c !in LEFT_OPENERS) return null
        if (left.lowercase() in SHORT_FORMS || left.lowercase() in NOT_AFTER_DOT || !known(left)) return null
        val right = if (known(word)) word else fix(word)?.let { if (word[0].isUpperCase()) it.replaceFirstChar(Char::uppercaseChar) else it } ?: return null
        return left to right
    }

    /**
     * Domain endings and file types: "site.com", "notes.txt", "Node.js" keep their period. Not endings that are
     * everyday words ("it", "me", "us"): "do.it" is a missed space far more often than an Italian web address.
     */
    private val NOT_AFTER_DOT = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int", "io", "app", "dev", "ai", "co", "uk", "ca", "au", "de",
        "fr", "es", "nl", "eu", "tv", "ly", "gg", "info", "biz", "xyz", "html", "htm", "php", "asp", "aspx", "js",
        "ts", "kt", "py", "java", "json", "xml", "css", "md", "txt", "csv", "pdf", "doc", "docx", "xls", "xlsx",
        "ppt", "pptx", "png", "jpg", "jpeg", "gif", "webp", "svg", "heic", "mov", "avi", "wav", "zip", "rar", "exe",
        "apk", "dmg", "iso", "ini", "cfg", "yaml", "yml", "sh", "bat", "sql", "db", "bak", "tmp", "lrc", "srt",
        "epub", "mobi", "ics",
    )

    /**
     * Domain endings that are also everyday words (review 2026-11-05): "my.blog", "tea.shop" and "about.me" keep their
     * period. Checked only after the period, never before it ("call me.thit" still splits). The cost: a real missed
     * space before one of these words ("read.news") is left as typed, the safer way to be wrong. "it" and "us" stay
     * out: "do.it" is a missed space far more often than an address.
     */
    private val WORD_ENDINGS = setOf(
        "blog", "shop", "store", "site", "online", "news", "tech", "cloud", "me", "live", "page", "link", "club",
        "space", "website", "studio",
    )

    private const val LEFT_OPENERS = "([{\"“‘«"

    /** Short forms that take a period: "etc.", "Mr.", "Jan.". The word after them stays joined. */
    private val SHORT_FORMS = setOf(
        "mr", "mrs", "ms", "dr", "st", "jr", "sr", "vs", "etc", "inc", "ltd", "corp", "prof", "gen", "sgt", "capt",
        "lt", "col", "rev", "hon", "approx", "dept", "est", "fig", "vol", "no", "ft", "oz", "lb", "lbs", "min",
        "max", "misc", "ave", "blvd", "rd", "mt", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec", "mon", "tue", "tues", "wed", "thu", "thur", "thurs", "fri", "sat", "sun", "www", "ie", "eg",
    )

    /** Two space taps than this make ". " (Android's own keyboard uses the same 1.1 seconds). */
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
