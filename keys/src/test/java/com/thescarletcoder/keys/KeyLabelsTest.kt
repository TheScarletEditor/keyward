package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every key TalkBack can land on says something, in every layout and state (K4). */
class KeyLabelsTest {
    private val layouts = mapOf(
        "letters" to Layouts.letters, "symbols" to Layouts.symbols, "symbols2" to Layouts.symbols2, "numpad" to Layouts.numpad,
    )

    @Test fun everyKeyHasASpokenLabel() {
        layouts.forEach { (name, rows) ->
            rows.flatten().filter { it.type != KeyType.SPACER }.forEach { key ->
                Shift.entries.forEach { shift ->
                    EnterKind.entries.forEach { enter ->
                        val s = KeyLabels.spoken(key, shift, enter, "Next")
                        assertTrue("$name '${key.label}' $shift $enter", s.isNotBlank())
                    }
                }
            }
        }
    }

    @Test fun punctuationIsNamedNotJustSymbols() {
        layouts.values.flatten().flatten().filter { it.type == KeyType.CHAR && !it.label[0].isLetterOrDigit() }.forEach { key ->
            assertFalse("'${key.label}' has no name", KeyLabels.spoken(key, Shift.OFF, EnterKind.RETURN, "") == key.label)
        }
    }

    @Test fun everyAccentHasAnActionName() {
        Layouts.accents.values.forEach { accents ->
            assertTrue("more accents than TalkBack actions: $accents", accents.length <= 4)
            accents.forEach { ch ->
                assertEquals(ch.toString(), KeyLabels.accent(ch, Shift.OFF))
                assertEquals(ch.uppercaseChar().toString(), KeyLabels.accent(ch, Shift.ONE))
            }
        }
    }

    @Test fun theHighlightedSuggestionSaysItWillReplace() {
        assertEquals("Suggestion: hello", KeyLabels.suggestion("hello"))
        assertEquals("Suggestion: hello, will replace", KeyLabels.suggestion("hello", replaces = true))
        assertEquals("Save Zorvex to your words", KeyLabels.suggestion("Zorvex", saves = true))
    }

    @Test fun iconKeysSayWhatTheyDo() {
        val enter = Key("enter", KeyType.ENTER)
        assertEquals("Send", KeyLabels.spoken(enter, Shift.OFF, EnterKind.SEND, ""))
        assertEquals("Next", KeyLabels.spoken(enter, Shift.OFF, EnterKind.WORD, "Next"))
        assertEquals("Caps lock, on", KeyLabels.spoken(Key("shift", KeyType.SHIFT), Shift.LOCKED, EnterKind.RETURN, ""))
        assertEquals("Capital Q", KeyLabels.spoken(Key("q"), Shift.ONE, EnterKind.RETURN, ""))
        assertEquals("q", KeyLabels.spoken(Key("q"), Shift.OFF, EnterKind.RETURN, ""))
        assertEquals("Backspace", KeyLabels.spoken(Key("delete", KeyType.DELETE), Shift.OFF, EnterKind.RETURN, ""))
    }
}
