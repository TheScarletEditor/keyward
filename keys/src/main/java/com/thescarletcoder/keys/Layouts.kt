package com.thescarletcoder.keys

enum class KeyType { CHAR, SHIFT, DELETE, SYMBOLS, SYMBOLS2, LETTERS, SPACE, ENTER, HIDE, SPACER }

/** One key. [alt] is typed on a long press (top-row digits, like most keyboards). */
data class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val weight: Float = 1f,
    val alt: String? = null,
)

/** Standard layouts. Sam tweaks from here (docs/KEYS-SPEC.md). */
object Layouts {
    /** Long-press accents (KEYS-POLISH-SPEC section 9). Keys not listed keep long press = digit. */
    val accents: Map<Char, String> = mapOf(
        'e' to "éèêë", 'a' to "áàâä", 'o' to "óòôö", 'u' to "úùûü", 'i' to "íìîï", 'n' to "ñ", 'c' to "ç",
    )

    private fun chars(s: String, alts: String? = null) =
        s.mapIndexed { i, c -> Key(c.toString(), alt = alts?.getOrNull(i)?.toString()) }

    private fun syms(vararg s: String) = s.map { Key(it) }

    private val bottomLetters = listOf(
        Key("?123", KeyType.SYMBOLS, 1.5f), Key(","), Key("space", KeyType.SPACE, 4.5f), Key("."), Key("enter", KeyType.ENTER, 1.5f), Key("hide", KeyType.HIDE),
    )
    private val bottomSymbols = listOf(
        Key("ABC", KeyType.LETTERS, 1.5f), Key(","), Key("space", KeyType.SPACE, 4.5f), Key("."), Key("enter", KeyType.ENTER, 1.5f), Key("hide", KeyType.HIDE),
    )

    val letters: List<List<Key>> = listOf(
        chars("qwertyuiop", "1234567890"),
        listOf(Key("", KeyType.SPACER, 0.5f)) + chars("asdfghjkl") + Key("", KeyType.SPACER, 0.5f),
        listOf(Key("shift", KeyType.SHIFT, 1.5f)) + chars("zxcvbnm") + Key("delete", KeyType.DELETE, 1.5f),
        bottomLetters,
    )

    val symbols: List<List<Key>> = listOf(
        chars("1234567890"),
        syms("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
        listOf(Key("=\\<", KeyType.SYMBOLS2, 1.5f)) + syms("*", "\"", "'", ":", ";", "!", "?") + Key("delete", KeyType.DELETE, 1.5f),
        bottomSymbols,
    )

    val symbols2: List<List<Key>> = listOf(
        syms("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
        syms("£", "€", "¥", "^", "°", "=", "{", "}", "\\", "%"),
        listOf(Key("?123", KeyType.SYMBOLS, 1.5f)) + syms("©", "®", "™", "✓", "[", "]", "<", ">") + Key("delete", KeyType.DELETE, 1.5f),
        bottomSymbols,
    )

    /** Number and phone fields. */
    val numpad: List<List<Key>> = listOf(
        syms("1", "2", "3", "-"),
        syms("4", "5", "6", "+"),
        syms("7", "8", "9") + Key("delete", KeyType.DELETE),
        listOf(Key("ABC", KeyType.LETTERS), Key("0"), Key("."), Key("enter", KeyType.ENTER), Key("hide", KeyType.HIDE)),
    )
}
