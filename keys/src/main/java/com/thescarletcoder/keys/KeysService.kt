package com.thescarletcoder.keys

import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.ScarletTokens
import java.util.concurrent.Executors

/**
 * Scarlet Keys (docs/KEYS-SPEC.md). No network permission. Never logs or stores what's typed;
 * only optional learned single words, encrypted, and never from password or no-learning fields.
 */
class KeysService : InputMethodService(), KeyListener {
    private companion object {
        const val TAG = "ScarletKeys"
        /** Longest word Keys picks back up when the cursor returns to a word's end. */
        const val MAX_WORD = 48
    }

    private lateinit var prefs: KeysPrefs
    private lateinit var panel: KeyboardPanel
    private val keyboard get() = panel.keyboard
    private val strip get() = panel.strip
    private lateinit var userWords: UserWords
    private lateinit var feedback: Feedback
    private var soundOn = false
    private var vibrateOn = false
    private val insetLoc = IntArray(2)

    // Personal and debug builds: timing, logged (never anything typed).
    private var createdAt = 0L
    private var firstSuggestionLogged = false
    private val suggestNanos = LongArray(64)
    private var suggestCount = 0
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
    /** The word being typed was capitalised by the user mid-sentence: a name (MacBook, Priya), never autocorrected. */
    private var wordIsName = false
    /** Shift is on because the user tapped it, not because a sentence starts here. */
    private var shiftByUser = false
    /** The field this keyboard last set up, so a restart of the same field keeps undo and the words taken back. */
    private var fieldKey: Pair<String?, Int>? = null
    private var autocorrectPick: String? = null
    private var pickFor: String? = null
    private var seq = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastShiftTap = 0L

    /** Set right after an autocorrect; one backspace puts back what the user actually typed. */
    private var undo: Undo? = null
    /** [at]: where the cursor ends up after the fix, in the field (-1 when the editor doesn't say). */
    private class Undo(val original: String, val corrected: String, val separator: String, val at: Int)

    /**
     * Words the user took back from autocorrect in this field (lowercase): never corrected again here, even where the
     * field turns learning off. Held in memory only and cleared for each new field.
     */
    private val kept = HashSet<String>()

    /** Theme, key height or bubble changed in Settings: rebuild so it applies while the keyboard is up. */
    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("theme", "key_height", "bubble") && ::panel.isInitialized) rebuild()
    }

    override fun onCreate() {
        super.onCreate()
        createdAt = SystemClock.elapsedRealtime()
        prefs = KeysPrefs(this)
        KeysTuning.init(this)
        userWords = UserWords.get(this)
        feedback = Feedback(this)
        prefs.listen(prefsChanged)
        worker.execute {
            val t = SystemClock.elapsedRealtime()
            dict = runCatching { Dictionary.load(this) }.getOrNull()
            if (BuildConfig.TUNING) Log.d(TAG, "dictionary loaded in ${SystemClock.elapsedRealtime() - t} ms (${dict?.size ?: 0} words)")
            userWords.loadInBackground()
        }
    }

    override fun onCreateInputView(): View {
        panel = KeyboardPanel(this, this) { pick(it) }
        // The keyboard window reaches behind the navigation bar; keep the keys above it so Android's
        // hide-keyboard and switch-keyboard buttons never sit on top of ?123 and Enter.
        panel.keepAboveNavigation()
        applyLook()
        return panel
    }

    /**
     * Never Android's fullscreen ("extract") mode. By default it turns on in landscape on a short screen and covers
     * the app with a copy of the text field, so the keyboard looks like it takes the whole screen.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun applyLook() {
        val palette = ScarletTokens.resolve(prefs.theme)
        panel.apply(KeyColors(palette), prefs.keyHeight, prefs.bubble, Motion.scale(this))
    }

    /** New input view with the current look, set up again for the field that's open. */
    private fun rebuild() {
        setInputView(onCreateInputView())
        currentInputEditorInfo?.let { if (isInputViewShown) configureFor(it, restarting = true) }
    }

    private var lastNight = -1

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // System light/dark flipped: follow it without a restart (other changes, like rotation, Android handles).
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night != lastNight) { lastNight = night; if (::panel.isInitialized) rebuild() }
    }

    /**
     * With the letter bubble on, the panel has a transparent band above the keys. Tell Android where the
     * keyboard really starts, so the app underneath isn't pushed up and stays tappable through the band.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!::panel.isInitialized || panel.extraTopPx == 0 || !panel.isShown) return
        panel.getLocationInWindow(insetLoc)
        val top = insetLoc[1] + panel.extraTopPx
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, panel.width, insetLoc[1] + panel.height)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        configureFor(info, restarting)
    }

    private fun configureFor(info: EditorInfo, restarting: Boolean) {
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
        if (learnOn) userWords.loadInBackground()
        soundOn = prefs.sound
        vibrateOn = prefs.vibrate
        applyLook()

        enterAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val multiline = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        useEnterAction = !multiline && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
            enterAction != EditorInfo.IME_ACTION_NONE && enterAction != EditorInfo.IME_ACTION_UNSPECIFIED

        keyboard.rows = if (numeric) Layouts.numpad else Layouts.letters
        if (BuildConfig.TUNING) FieldLog.add(this, info.packageName, cls, variation, numeric)
        keyboard.enterKind = if (!useEnterAction) EnterKind.RETURN else when (enterAction) {
            EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
            EditorInfo.IME_ACTION_GO -> EnterKind.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS, EditorInfo.IME_ACTION_DONE -> EnterKind.WORD
            else -> EnterKind.RETURN
        }
        // Words, not icons, where an icon would be ambiguous.
        keyboard.enterWord = when (enterAction) {
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Back"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> ""
        }

        // A field that restarts mid-word (some messaging apps do, as text changes) keeps the word marked as being
        // typed; let it go, or the next space or period replaces that word (KB-2).
        finishComposing()
        // Some apps restart the field as text changes, and rotation restarts it too: the same field keeps the undo and
        // the words taken back (delete() checks the text still matches before undoing).
        // Compose and web fields in one app can share an id, so the undo also needs the cursor where it left it.
        val key = info.packageName to info.fieldId
        // A field without autocorrect (a password field sharing a Compose view's id) never keeps another field's undo (R9).
        val sameField = restarting && key == fieldKey && autocorrectOn
        if (!sameField) kept.clear()
        undo = undo?.takeIf { sameField && it.at >= 0 && info.initialSelStart == it.at && info.initialSelEnd == it.at }
        fieldKey = key
        strip.clear()
        // The strip is always there (the keyboard never changes height). Private fields say so.
        val noLearning = info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        strip.privateField = !numeric && !addressy && (password || noSuggestFlag || noLearning)
        keyboard.fieldHasText = info.initialSelEnd > 0
        updateAutoCaps()
        if (BuildConfig.TUNING) panel.post { Log.d(TAG, "input view height ${panel.height} px (class $cls, variation $variation)") }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        finishComposing()
        if (learnOn) userWords.saveInBackground()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The user tapped elsewhere in the text: stop working on the old word.
        if (composing.isNotEmpty() && (newSelStart != newSelEnd || candidatesEnd == -1 || newSelStart != candidatesEnd)) {
            composing.clear()
            currentInputConnection?.finishComposingText()
            strip.clear()
            undo = null
        }
        if (newSelStart != newSelEnd) undo = null
        // Text still marked as being typed while no word of ours is: let it go, or the next edit replaces it (KB-2).
        if (composing.isEmpty() && candidatesStart != -1) currentInputConnection?.finishComposingText()
        if (composing.isEmpty()) updateAutoCaps()
        // Cursor past the start means there's text: the space bar wordmark dims.
        if (::panel.isInitialized) keyboard.fieldHasText = newSelEnd > 0 || composing.isNotEmpty()
    }

    // ---- KeyListener ----

    override fun onPressFeedback(key: Key) {
        if (soundOn || vibrateOn) feedback.press(keyboard, key, soundOn, vibrateOn)
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
    override fun onDeleteRepeat() {
        feedback.deleteRepeat(keyboard, vibrateOn)
        delete()
    }
    override fun onShiftLock() {
        keyboard.shift = Shift.LOCKED
        shiftByUser = true
    }
    override fun onSpaceLongPress() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---- typing ----

    private fun typeChar(s: String) {
        val c = s[0]
        val wordChar = c.isLetter() || (c == '\'' && composing.isNotEmpty())
        when {
            wordChar && suggestOn -> {
                // Typing right after the end of a word (after backspacing into it) carries on that word, so
                // "exevute", back to "exe", then "cute" is one word again, not a new one starting at the cursor.
                val ic = currentInputConnection
                val look = if (composing.isEmpty()) lookBack() else null
                // One batch, so the editor reports a single change and never a moment with no word being typed.
                ic?.beginBatchEdit()
                if (composing.isEmpty()) {
                    if (look != null && look.word.isNotEmpty()) takeUp(look, look.word.length)
                    else wordIsName = keyboard.shift == Shift.ONE && shiftByUser && look?.sentenceStart != true
                }
                composing.append(s)
                ic?.setComposingText(composing, 1)
                ic?.endBatchEdit()
                refreshSuggestions()
                undo = null
            }
            c.isLetterOrDigit() -> { finishComposing(); currentInputConnection?.commitText(s, 1); undo = null }
            // Punctuation finishes the word and may autocorrect it; its undo must survive (it used to be wiped
            // right here, so backspace after "CINC?" deleted the "?" instead of putting "CINC" back).
            else -> separator(s)
        }
        if (keyboard.shift == Shift.ONE) { keyboard.shift = Shift.OFF; shiftByUser = false }
    }

    /** The word the cursor sits at the end of ("" when none) and where it starts in the field (-1 when unknown). */
    private class Look(val word: String, val start: Int, val sentenceStart: Boolean)

    /**
     * Read only, and always before any edit: some editors (Jetpack Compose) apply a batch's edits only when it ends and
     * answer reads from the old text, so reading after a write put the word over the wrong text (KB-1). Null when
     * there's a selection, the cursor is inside a word (a letter right after it: the user is editing inside a word,
     * left alone), or the editor can't say.
     */
    private fun lookBack(): Look? {
        val ic = currentInputConnection ?: return null
        if (!suggestOn) return null
        val st = ic.getSurroundingText(MAX_WORD, 1, 0) ?: return null
        val cur = st.selectionStart
        if (cur < 0 || cur != st.selectionEnd || cur > st.text.length) return null
        if (cur < st.text.length && Words.isWordChar(st.text[cur])) return null
        val before = st.text.subSequence(0, cur)
        val word = Words.wordAtEnd(before, MAX_WORD) ?: return null
        val start = if (st.offset >= 0) st.offset + cur - word.length else -1
        return Look(word, start, Words.startsSentence(before.subSequence(0, before.length - word.length)))
    }

    /**
     * Make the first [len] letters of [look]'s word the word being typed. Call inside a batch, after every read.
     * Marks the existing text where the editor gives positions (links and formatting stay), else retypes it.
     */
    private fun takeUp(look: Look, len: Int) {
        val ic = currentInputConnection ?: return
        val word = look.word.substring(0, len)
        // A stray mark would widen deleteSurroundingText and setComposingRegion's reach; drop it first.
        ic.finishComposingText()
        if (look.start >= 0) ic.setComposingRegion(look.start, look.start + len)
        else { ic.deleteSurroundingText(len, 0); ic.setComposingText(word, 1) }
        composing.clear()
        composing.append(word)
        // Only where the box doesn't capitalise every word itself (subject lines, names): there the capital says nothing (R11).
        val capsWords = (currentInputEditorInfo?.inputType ?: 0) and (InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0
        wordIsName = !capsWords && !look.sentenceStart && word.first().isUpperCase() && !(word.length > 1 && word.none { it.isLowerCase() })
    }

    /** Capitalised by the user mid-sentence: a name, never autocorrected. */
    private fun looksLikeName(word: String) = wordIsName && word.firstOrNull()?.isUpperCase() == true

    /** Space or punctuation: the word is finished, so this is where autocorrect happens. */
    private fun separator(sep: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            val original = composing.toString()
            // "i." stays as typed: "i.e." and list markers (the owner, 2026-10-12).
            val final = (if (sep == "." && original == "i") null else fixFor(original)) ?: original
            // Read before writing: where the word starts, so the undo knows where it applies.
            val at = if (final != original) cursorInField(ic)?.let { it - original.length + final.length + sep.length } ?: -1 else -1
            ic.commitText(final, 1)
            learn(final)
            composing.clear()
            strip.clear()
            undo = if (final != original) Undo(original, final, sep, at) else null
            ic.commitText(sep, 1)
        } else {
            // No word of ours is being typed, so nothing in the field should be marked as one (KB-2).
            ic.finishComposingText()
            // Double space after a word: ". " like most keyboards.
            if (sep == " " && lastWasSpaceAfterWord()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
            } else ic.commitText(sep, 1)
            undo = null
        }
        updateAutoCaps()
    }

    /** The cursor's position in the field, or null when there's a selection or the editor doesn't say. */
    private fun cursorInField(ic: android.view.inputmethod.InputConnection): Int? {
        val st = ic.getSurroundingText(0, 0, 0) ?: return null
        if (st.offset < 0 || st.selectionStart < 0 || st.selectionStart != st.selectionEnd) return null
        return st.offset + st.selectionStart
    }

    private fun lastWasSpaceAfterWord(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString() ?: return false
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    private fun delete() {
        val ic = currentInputConnection ?: return
        val u = undo
        undo = null
        // Only while the fix is still right before the cursor, at the same place (after a restart or a sent message
        // it may not be).
        if (u != null && ic.getTextBeforeCursor(u.corrected.length + u.separator.length, 0)?.toString() == u.corrected + u.separator &&
            (u.at < 0 || cursorInField(ic) == u.at)) {
            // Undo the autocorrect: put back exactly what was typed, separator included ("CINC?" stays "CINC?"),
            // and never correct that word again in this field.
            ic.deleteSurroundingText(u.corrected.length + u.separator.length, 0)
            ic.commitText(u.original + u.separator, 1)
            kept += u.original.lowercase()
            if (learnOn) { userWords.record(u.original); userWords.record(u.original) }
            strip.clear()
            updateAutoCaps()
            return
        }
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            if (composing.isEmpty()) { ic.commitText("", 1); strip.clear() }
            else { ic.setComposingText(composing, 1); refreshSuggestions() }
            return
        }
        // Backspacing into the end of a word: take it up again, so suggestions and autocorrect see the whole word.
        val look = lookBack()
        if (look != null && look.word.isNotEmpty() && look.word.last().isLetter()) {
            ic.beginBatchEdit()
            ic.finishComposingText()
            ic.deleteSurroundingText(1, 0)
            val resumed = look.word.length > 1
            if (resumed) takeUp(look, look.word.length - 1)
            ic.endBatchEdit()
            if (resumed) refreshSuggestions() else strip.clear()
            updateAutoCaps()
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
        shiftByUser = keyboard.shift != Shift.OFF
        lastShiftTap = now
    }

    private fun finishComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.clear()
        }
        if (::panel.isInitialized) strip.clear()
    }

    private fun updateAutoCaps() {
        if (!::panel.isInitialized || keyboard.shift == Shift.LOCKED || numeric) return
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        keyboard.shift = if (caps != 0) Shift.ONE else Shift.OFF
        shiftByUser = false
    }

    // ---- suggestions ----

    /**
     * Suggestions are worked out on a background thread so keys always respond instantly.
     * Results that arrive after the user has typed on are dropped.
     */
    private fun refreshSuggestions() {
        val d = dict
        if (!suggestOn || d == null) { strip.clear(); return }
        val typed = composing.toString()
        val my = ++seq
        val learnNow = learnOn
        val correctNow = autocorrectOn
        val keptNow = HashSet(kept)
        val nameNow = looksLikeName(typed)
        worker.execute {
            val started = System.nanoTime()
            val boost: (String) -> Int = { if (learnNow) userWords.boost(it) else 0 }
            val learned = if (learnNow) userWords.learned else emptyList()
            val known = { w: String -> d.isKnown(w) || (learnNow && userWords.isKnown(w)) }
            val cands = d.candidates(typed, boost, learned)
            val pick = if (correctNow && typed.lowercase() !in keptNow) d.autocorrect(typed, known, boost, learned, cands, nameNow)?.let { matchCase(typed, it) } else null
            val ranked = cands.map { matchCase(typed, it.word) }.distinct()
            val list = ArrayList<String>(3)
            val highlight: Int
            if (pick != null) {
                // Left: exactly what was typed (tap to keep it). Middle: the fix space will apply.
                list += typed; list += pick
                ranked.firstOrNull { it != pick && it != typed }?.let { list += it }
                // Not for a lone "i": a period keeps it lowercase (i.e.), so the highlight would promise too much (C1).
                highlight = if (typed.equals("i", ignoreCase = true)) -1 else 1
            } else {
                if (known(typed)) list += typed
                ranked.filter { it != typed }.take(3 - list.size).forEach { list += it }
                highlight = if (list.firstOrNull() == typed) 0 else -1
            }
            if (BuildConfig.TUNING) timeSuggestion(System.nanoTime() - started)
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
        if (!autocorrectOn || original.lowercase() in kept) return null
        if (pickFor == original) return autocorrectPick
        val d = dict ?: return null
        val known = { w: String -> d.isKnown(w) || (learnOn && userWords.isKnown(w)) }
        return d.autocorrect(
            original, known, { if (learnOn) userWords.boost(it) else 0 }, if (learnOn) userWords.learned else emptyList(),
            name = looksLikeName(original),
        )
            ?.let { matchCase(original, it) }
    }

    /** Personal/debug builds: first suggestion after a cold start, and p95 of the work per keystroke. */
    private fun timeSuggestion(nanos: Long) {
        if (!firstSuggestionLogged) {
            firstSuggestionLogged = true
            Log.d(TAG, "first suggestion ${SystemClock.elapsedRealtime() - createdAt} ms after the service started")
        }
        suggestNanos[suggestCount % suggestNanos.size] = nanos
        if (++suggestCount % suggestNanos.size == 0) {
            val sorted = suggestNanos.sortedArray()
            Log.d(TAG, "suggestion work p95 %.1f ms over the last %d keystrokes".format(sorted[(sorted.size * 95) / 100 - 1] / 1e6, sorted.size))
        }
    }

    private fun learn(word: String) {
        // "im", "ive" and the like are learned only by taking the fix back (the undo records them), never just by
        // typing them where autocorrect is off (R12).
        if (learnOn && (autocorrectOn || !Dictionary.fixesToI(word))) userWords.record(word)
    }

    /** Keep the user's capitalisation: "Hello" -> "Hello", "HELLO" -> "HELLO". */
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
        if (::prefs.isInitialized) prefs.unlisten(prefsChanged)
        if (::feedback.isInitialized) feedback.release()
        // On the worker, which finishes queued work after shutdown(): no file or Keystore work on the main thread (K5).
        if (::prefs.isInitialized && prefs.learn) userWords.saveInBackground()
        worker.shutdown()
        super.onDestroy()
    }
}
