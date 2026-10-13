package com.thescarletcoder.keys

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thescarletcoder.scarlet.ScarletField
import com.thescarletcoder.scarlet.ScarletPalette

/** Rows drawn at once: search narrows the rest (a list can hold 5,000 words). */
private const val MAX_ROWS = 200

/**
 * The Your words page (Q27): search, the learned and saved words, remove one, save one, Forget all. Every delete takes
 * two taps, the second outlined in scarlet (Q30). The list is read and changed off the main thread.
 * [forgetting] and [forgetOk] belong to the main screen, so a Forget started here still reports after Back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordsPage(
    words: UserWords, status: UserWords.Status?, onStatus: (UserWords.Status) -> Unit,
    forgetting: Boolean, forgetOk: Boolean?, onForget: () -> Unit, learnOn: Boolean, p: ScarletPalette, font: FontFamily,
    /** A word was saved or removed: an earlier "Forgotten." no longer describes the list. */
    onChanged: () -> Unit = {},
) {
    var entries by remember { mutableStateOf<List<WordStore.Entry>?>(null) }
    // Read again after every change here, and when the count or notice changes (Forget, from the main screen).
    var changes by remember { mutableIntStateOf(0) }
    LaunchedEffect(status, changes) { words.entriesAsync { e, s -> entries = e; if (s != status) onStatus(s) } }
    // Not saved with the screen's state: a searched word must not outlive the page (it closes when Keyward leaves the screen).
    var query by remember { mutableStateOf("") }
    // The word whose Remove was tapped once (Q30: two taps to delete).
    var confirming by remember { mutableStateOf<String?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Text(
        "Words you save stay until you remove them. Words you use often are learned on their own, after three uses. " +
            "Single words only, encrypted on this device.",
        color = Color(p.soft), fontSize = 15.sp, fontFamily = font,
    )
    noticeText(status?.notice)?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = font) }
    if (!learnOn) Text("Learn my words is off: nothing new is learned, and your saved words still count.", color = Color(p.soft), fontSize = 14.sp, fontFamily = font)

    // No suggestions or learning in this box: searching must never teach the keyboard what was searched, whichever keyboard
    // is in use (the no-learning flag every keyboard should honour, plus Keyward's own marker).
    NoLearning { ScarletField(
        query, { query = it; message = null }, "Search or add a word", p, font,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            platformImeOptions = PlatformImeOptions(Words.NO_LEARN),
        ),
    ) }
    val q = query.trim().lowercase()
    // A list that couldn't be read just now (a passing error) says so, instead of looking empty.
    val unread = status?.loaded == false
    val list = entries?.takeUnless { unread }
    if (unread) Text("Couldn't read your words just now. Open this page again in a moment.", color = Color(p.text), fontSize = 15.sp, fontFamily = font)
    val shown = remember(list, q) { list?.filter { q.isEmpty() || it.word.contains(q) }.orEmpty() }

    // A word not on the list can be saved from here ("Search, remove or add").
    // Not while the list is refused (a newer version's list, or one that can't be read): a save would always fail.
    val listed = remember(list, q) { list?.any { it.word == q } ?: true }
    if (list != null && status?.canKeep != false && Words.isWord(q) && !listed) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("\"$q\" isn't on your list.", color = Color(p.text), fontSize = 16.sp, fontFamily = font, modifier = Modifier.weight(1f))
            Pill("Save", false, p, font, enabled = !busy) {
                busy = true
                words.keepAsync(q) { ok, s ->
                    busy = false
                    message = if (ok) "Saved \"$q\"." else "Couldn't save \"$q\"."
                    onStatus(s); changes++; if (ok) onChanged()
                }
            }
        }
    }
    Announce(message, p, font)

    when {
        list == null -> Unit
        list.isEmpty() -> Text("No words yet. Words you use often show up here, and words you save.", color = Color(p.soft), fontSize = 15.sp, fontFamily = font)
        shown.isEmpty() -> Text("No words match.", color = Color(p.soft), fontSize = 15.sp, fontFamily = font)
        else -> Column(
            Modifier.card(p).padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            shown.take(MAX_ROWS).forEach { e ->
                WordRow(e, confirming == e.word, busy, p, font,
                    onRemove = { confirming = e.word },
                    onKeep = { confirming = null },
                    onConfirm = {
                        busy = true; confirming = null
                        words.removeAsync(e.word) { ok, s ->
                            busy = false
                            message = if (ok) "Removed \"${e.word}\"." else "Couldn't remove \"${e.word}\". Try again."
                            onStatus(s); changes++; if (ok) onChanged()
                        }
                    },
                )
            }
        }
    }
    if (shown.size > MAX_ROWS) Text("Showing $MAX_ROWS of ${shown.size}. Search to find the rest.", color = Color(p.soft), fontSize = 14.sp, fontFamily = font)

    val count = status?.count ?: 0
    // Every word held counts here, listed or not: Forget all reaches words used once or twice too (S7-1).
    val held = status?.held ?: 0
    if (held > 0 || status?.notice?.let { it != WordStore.Notice.NONE } == true || forgetOk == false) {
        if (!confirmForget) {
            Pill("Forget all words", false, p, font, enabled = !forgetting, danger = true) { confirmForget = true }
        } else {
            Question(
                when {
                    held > count -> "Forget all your words, including ones used only once or twice? This can't be undone."
                    count == 1 -> "Forget your 1 word? This can't be undone."
                    count > 1 -> "Forget all $count words? This can't be undone."
                    else -> "Forget all your words? This can't be undone."
                },
                16, p, font,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Keep", false, p, font) { confirmForget = false }
                Pill("Forget all", false, p, font, enabled = !forgetting, danger = true) { confirmForget = false; onForget() }
            }
        }
    }
    Announce(forgetOk?.let { ok -> if (ok) "Forgotten." else "Couldn't forget your words. Try again." }, p, font)
}

/** One word: the word and what it is, then Remove; once tapped, the row asks (Keep / Remove). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordRow(
    e: WordStore.Entry, confirming: Boolean, busy: Boolean, p: ScarletPalette, font: FontFamily,
    onRemove: () -> Unit, onKeep: () -> Unit, onConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!confirming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(e.word, color = Color(p.text), fontSize = 18.sp, fontFamily = font)
                    Text(if (e.saved) "Saved by you" else "Used ${e.uses} times", color = Color(p.soft), fontSize = 14.sp, fontFamily = font)
                }
                Pill("Remove", false, p, font, enabled = !busy, danger = true, clickLabel = "Remove ${e.word}", onClick = onRemove)
            }
        } else {
            Question("Remove \"${e.word}\"?", 17, p, font)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Keep", false, p, font, onClick = onKeep)
                Pill("Remove", false, p, font, enabled = !busy, danger = true, clickLabel = "Remove ${e.word} for good", onClick = onConfirm)
            }
        }
    }
}

/** What Settings says about the list, if anything (Q2, Q12). */
internal fun noticeText(notice: WordStore.Notice?): String? = when (notice) {
    WordStore.Notice.SET_ASIDE -> "Your learned words couldn't be read on this device, so it started a new list."
    WordStore.Notice.NEWER -> "Your learned words were saved by a newer version of Keyward. This version leaves them as they are and won't learn new words."
    WordStore.Notice.NOT_LEARNING -> "Your learned words couldn't be read, so Keyward isn't learning new words for now."
    WordStore.Notice.NONE, null -> null
}

/** Text boxes inside ask every keyboard not to learn from them (IME_FLAG_NO_PERSONALIZED_LEARNING), not only Keyward. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoLearning(content: @Composable () -> Unit) = InterceptPlatformTextInput(
    interceptor = { request, next ->
        next.startInputMethod(object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
        })
    },
    content = content,
)

/** A two-tap question (Q30). TalkBack lands on it, not at the top of the page: the button it was on is gone. */
@Composable
private fun Question(text: String, sizeSp: Int, p: ScarletPalette, font: FontFamily) {
    val ask = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { ask.requestFocus() } }
    Text(text, color = Color(p.text), fontSize = sizeSp.sp, fontFamily = font,
        modifier = Modifier.focusRequester(ask).focusable().semantics { liveRegion = LiveRegionMode.Polite })
}

/** A result line that's always there, so TalkBack hears each new one (a live region announces changes, not new nodes). */
@Composable
private fun Announce(text: String?, p: ScarletPalette, font: FontFamily) {
    Box(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        text?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = font) }
    }
}
