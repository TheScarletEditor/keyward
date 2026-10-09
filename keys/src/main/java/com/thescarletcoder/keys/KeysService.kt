package com.thescarletcoder.keys

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import java.util.concurrent.Executors

/**
 * Scarlet Keys (docs/KEYS-SPEC.md). No network permission. Never logs or stores what's typed;
 * only optional learned single words, encrypted, and never from password or no-learning fields.
 */
class KeysService : InputMethodService(), KeyListener {

    private lateinit var prefs: KeysPrefs
    private lateinit var keyboard: KeyboardView
    private lateinit var strip: SuggestionStrip
    private lateinit var userWords: UserWords
    @Volatile private var dict: Dictionary? = null
    private val worker = Executors.newSingleThreadExecutor()

    // Per-field state
    private var suggestOn = false
    private var autocorrectOn = false
    private var learnOn = false
    private var enterAction = EditorInfo.IME_ACTION_UNSPECIFIED
    private var useEnterAction = false
    private var numeric = false

    private val composing = StringBuilder()
    private var autocorrectPick: String? = null
    private var pickFor: String? = null
    private var seq = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastShiftTap = 0L

    /** Set right after an autocorrect; one backspace puts back what Sam actually typed. */
    private var undo: Undo? = null
    private class Undo(val original: String, val corrected: String, val separator: String)

    override fun onCreate() {
        super.onCreate()
        prefs = KeysPrefs(this)
        userWords = UserWords(this)
        worker.execute {
            dict = runCatching { Dictionary.load(this) }.getOrNull()
            userWords.load()
        }
    }

    override fun onCreateInputView(): View {
        keyboard = KeyboardView(this, this)
        strip = SuggestionStrip(this) { pick(it) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KeyColors.BG)
            addView(strip)
            addView(keyboard)
            // The keyboard window reaches behind the navigation bar; keep the keys above it so Android's
            // hide-keyboard and switch-keyboard buttons never sit on top of ?123 and Enter.
            setOnApplyWindowInsetsListener { v, insets ->
                // On ZUI with gesture navigation the IME's back/switch buttons are not reported as a navigation
                // bar, so take the largest of the bottom insets the system offers.
                val bottom = maxOf(
                    insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsets.Type.tappableElement()).bottom,
                    insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom,
                    insets.getInsets(WindowInsets.Type.systemGestures()).bottom,
                )
                v.setPadding(0, 0, 0, bottom)
                insets
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val password = isPassword(cls, variation)
        val noSuggestFlag = info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val addressy = cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_FILTER,
        )
        numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME

        suggestOn = prefs.suggestions && cls == InputType.TYPE_CLASS_TEXT && !password && !addressy
        autocorrectOn = suggestOn && prefs.autocorrect && !noSuggestFlag
        learnOn = suggestOn && prefs.learn && !noSuggestFlag &&
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        if (learnOn) worker.execute { userWords.load() }

        enterAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val multiline = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        useEnterAction = !multiline && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
            enterAction != EditorInfo.IME_ACTION_NONE && enterAction != EditorInfo.IME_ACTION_UNSPECIFIED

        keyboard.keyHeightDp = prefs.keyHeight
        keyboard.rows = if (numeric) Layouts.numpad else Layouts.letters
        keyboard.enterLabel = if (useEnterAction) when (enterAction) {
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Back"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "Return"
        } else "Return"

        composing.clear()
        undo = null
        strip.clear()
        strip.visibility = if (suggestOn) View.VISIBLE else View.GONE
        updateAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        finishComposing()
        if (learnOn) worker.execute { userWords.save() }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Sam tapped elsewhere in the text: stop working on the old word.
        if (composing.isNotEmpty() && (newSelStart != newSelEnd || candidatesEnd == -1 || newSelStart != candidatesEnd)) {
            composing.clear()
            currentInputConnection?.finishComposingText()
            strip.clear()
            undo = null
        }
        if (newSelStart != newSelEnd) undo = null
        if (composing.isEmpty()) updateAutoCaps()
    }

    // ---- KeyListener ----

    override fun onPressFeedback() {
        if (prefs.vibrate) keyboard.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (prefs.sound) (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
    }

    override fun onKey(key: Key) {
        when (key.type) {
            KeyType.CHAR -> typeChar(if (keyboard.shift != Shift.OFF && key.label[0].isLetter()) key.label.uppercase() else key.label)
            KeyType.SPACE -> separator(" ")
            KeyType.DELETE -> delete()
            KeyType.ENTER -> enter()
            KeyType.SHIFT -> shiftTap()
            KeyType.SYMBOLS -> { keyboard.rows = Layouts.symbols }
            KeyType.SYMBOLS2 -> { keyboard.rows = Layouts.symbols2 }
            KeyType.LETTERS -> { keyboard.rows = Layouts.letters; updateAutoCaps() }
            KeyType.HIDE -> { finishComposing(); requestHideSelf(0) }
            KeyType.SPACER -> Unit
        }
    }

    override fun onAlt(text: String) = typeChar(text)
    override fun onDeleteRepeat() = delete()
    override fun onSpaceLongPress() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---- typing ----

    private fun typeChar(s: String) {
        val c = s[0]
        val wordChar = c.isLetter() || (c == '\'' && composing.isNotEmpty())
        when {
            wordChar && suggestOn -> {
                composing.append(s)
                currentInputConnection?.setComposingText(composing, 1)
                refreshSuggestions()
            }
            c.isLetterOrDigit() -> { finishComposing(); currentInputConnection?.commitText(s, 1) }
            else -> separator(s)
        }
        undo = null
        if (keyboard.shift == Shift.ONE) keyboard.shift = Shift.OFF
    }

    /** Space or punctuation: the word is finished, so this is where autocorrect happens. */
    private fun separator(sep: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            val original = composing.toString()
            val final = fixFor(original) ?: original
            ic.commitText(final, 1)
            learn(final)
            composing.clear()
            strip.clear()
            undo = if (final != original) Undo(original, final, sep) else null
            ic.commitText(sep, 1)
        } else {
            // Double space after a word: ". " like most keyboards.
            if (sep == " " && lastWasSpaceAfterWord()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
            } else ic.commitText(sep, 1)
            undo = null
        }
        updateAutoCaps()
    }

    private fun lastWasSpaceAfterWord(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString() ?: return false
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    private fun delete() {
        val ic = currentInputConnection ?: return
        val u = undo
        if (u != null) {
            // Undo the autocorrect: put back exactly what was typed, and remember it.
            ic.deleteSurroundingText(u.corrected.length + u.separator.length, 0)
            ic.commitText(u.original, 1)
            undo = null
            if (learnOn) { userWords.record(u.original); userWords.record(u.original) }
            return
        }
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            if (composing.isEmpty()) { ic.commitText("", 1); strip.clear() }
            else { ic.setComposingText(composing, 1); refreshSuggestions() }
            return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoCaps()
    }

    private fun enter() {
        if (composing.isNotEmpty()) {
            val original = composing.toString()
            val final = fixFor(original) ?: original
            currentInputConnection?.commitText(final, 1)
            learn(final)
            composing.clear(); strip.clear()
        }
        undo = null
        if (useEnterAction) currentInputConnection?.performEditorAction(enterAction)
        else sendKeyChar('\n')
        updateAutoCaps()
    }

    private fun pick(word: String) {
        val ic = currentInputConnection ?: return
        val final = if (composing.isNotEmpty()) matchCase(composing.toString(), word) else word
        ic.commitText("$final ", 1)
        learn(final)
        composing.clear(); strip.clear(); undo = null
        updateAutoCaps()
    }

    private fun shiftTap() {
        val now = SystemClock.uptimeMillis()
        keyboard.shift = when (keyboard.shift) {
            Shift.OFF -> Shift.ONE
            Shift.ONE -> if (now - lastShiftTap < 350) Shift.LOCKED else Shift.OFF
            Shift.LOCKED -> Shift.OFF
        }
        lastShiftTap = now
    }

    private fun finishComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.clear()
        }
        if (::strip.isInitialized) strip.clear()
    }

    private fun updateAutoCaps() {
        if (!::keyboard.isInitialized || keyboard.shift == Shift.LOCKED || numeric) return
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        keyboard.shift = if (caps != 0) Shift.ONE else Shift.OFF
    }

    // ---- suggestions ----

    /**
     * Suggestions are worked out on a background thread so keys always respond instantly.
     * Results that arrive after Sam has typed on are dropped.
     */
    private fun refreshSuggestions() {
        val d = dict
        if (!suggestOn || d == null) { strip.clear(); return }
        val typed = composing.toString()
        val my = ++seq
        val learnNow = learnOn
        val correctNow = autocorrectOn
        worker.execute {
            val boost: (String) -> Int = { if (learnNow) userWords.boost(it) else 0 }
            val learned = if (learnNow) userWords.learned else emptyList()
            val known = { w: String -> d.isKnown(w) || (learnNow && userWords.isKnown(w)) }
            val pick = if (correctNow) d.autocorrect(typed, known, boost, learned)?.let { matchCase(typed, it) } else null
            val ranked = d.candidates(typed, boost, learned).map { matchCase(typed, it.word) }.distinct()
            val list = ArrayList<String>(3)
            val highlight: Int
            if (pick != null) {
                // Left: exactly what was typed (tap to keep it). Middle: the fix space will apply.
                list += typed; list += pick
                ranked.firstOrNull { it != pick && it != typed }?.let { list += it }
                highlight = 1
            } else {
                if (known(typed)) list += typed
                ranked.filter { it != typed }.take(3 - list.size).forEach { list += it }
                highlight = if (list.firstOrNull() == typed) 0 else -1
            }
            main.post {
                if (my != seq || composing.toString() != typed) return@post
                autocorrectPick = pick
                pickFor = typed
                strip.show(list, highlight)
            }
        }
    }

    /** The fix for a finished word: reuse the background result if it's for this exact word, else work it out now. */
    private fun fixFor(original: String): String? {
        if (!autocorrectOn) return null
        if (pickFor == original) return autocorrectPick
        val d = dict ?: return null
        val known = { w: String -> d.isKnown(w) || (learnOn && userWords.isKnown(w)) }
        return d.autocorrect(original, known, { if (learnOn) userWords.boost(it) else 0 }, if (learnOn) userWords.learned else emptyList())
            ?.let { matchCase(original, it) }
    }

    private fun learn(word: String) {
        if (learnOn) userWords.record(word)
    }

    /** Keep Sam's capitalisation: "Hello" -> "Hello", "HELLO" -> "HELLO". */
    private fun matchCase(typed: String, word: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    private fun isPassword(cls: Int, variation: Int): Boolean =
        (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

    override fun onDestroy() {
        if (::prefs.isInitialized && prefs.learn) userWords.save()
        worker.shutdown()
        super.onDestroy()
    }
}
