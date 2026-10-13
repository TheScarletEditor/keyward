package com.thescarletcoder.keys

/**
 * What TalkBack says for each key (K4). Plain Kotlin so the JVM tests can check every key in every layout.
 * Symbols are named, because TalkBack may skip punctuation on its own.
 */
object KeyLabels {
    private val symbols = mapOf(
        "," to "Comma", "." to "Period", "?" to "Question mark", "!" to "Exclamation mark", "'" to "Apostrophe",
        "\"" to "Quote", ":" to "Colon", ";" to "Semicolon", "-" to "Dash", "_" to "Underscore", "@" to "At sign",
        "#" to "Hash", "$" to "Dollar", "&" to "Ampersand", "+" to "Plus", "(" to "Left parenthesis",
        ")" to "Right parenthesis", "/" to "Slash", "*" to "Asterisk", "~" to "Tilde", "`" to "Backtick",
        "|" to "Vertical bar", "•" to "Bullet", "√" to "Square root", "π" to "Pi", "÷" to "Divide",
        "×" to "Multiply", "¶" to "Paragraph sign", "∆" to "Delta", "£" to "Pound", "€" to "Euro",
        "¥" to "Yen", "^" to "Caret", "°" to "Degree", "=" to "Equals", "{" to "Left brace",
        "}" to "Right brace", "\\" to "Backslash", "%" to "Percent", "©" to "Copyright", "®" to "Registered",
        "™" to "Trademark", "✓" to "Check mark", "[" to "Left bracket", "]" to "Right bracket",
        "<" to "Less than", ">" to "Greater than",
    )

    /** Spoken label for [key] with the keyboard's current state. Never empty for a key that can be pressed. */
    fun spoken(key: Key, shift: Shift, enterKind: EnterKind, enterWord: String): String = when (key.type) {
        KeyType.CHAR -> {
            val l = key.label
            when {
                l.length == 1 && l[0].isLetter() -> if (shift == Shift.OFF) l else "Capital ${l.uppercase()}"
                else -> symbols[l] ?: l
            }
        }
        KeyType.SHIFT -> when (shift) {
            Shift.OFF -> "Shift"
            Shift.ONE -> "Shift, on"
            Shift.LOCKED -> "Caps lock, on"
        }
        KeyType.DELETE -> "Backspace" // the owner's word (Q15)
        KeyType.SPACE -> "Space"
        KeyType.ENTER -> when (enterKind) {
            EnterKind.RETURN -> "Return"
            EnterKind.SEND -> "Send"
            EnterKind.SEARCH -> "Search"
            EnterKind.GO -> "Go"
            EnterKind.WORD -> enterWord.ifEmpty { "Return" }
        }
        KeyType.SYMBOLS -> "Symbols and numbers"
        KeyType.SYMBOLS2 -> "More symbols"
        KeyType.LETTERS -> "Letters"
        KeyType.HIDE -> "Hide keyboard"
        KeyType.SPACER -> ""
    }

    /** For a suggestion slot; [replaces]: the highlighted word, the one space will put in (KB-6). */
    fun suggestion(word: String, replaces: Boolean = false) = if (replaces) "Suggestion: $word, will replace" else "Suggestion: $word"

    /** An accent as typed and as TalkBack's action name for it: the character itself, capital with Shift on (KB-5). */
    fun accent(ch: Char, shift: Shift): String = if (shift == Shift.OFF) ch.toString() else ch.uppercaseChar().toString()
}
