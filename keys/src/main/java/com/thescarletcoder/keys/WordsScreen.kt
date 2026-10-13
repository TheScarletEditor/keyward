package com.thescarletcoder.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    forgetting: Boolean, forgetOk: Boolean?, onForget: () -> Unit, p: ScarletPalette, font: FontFamily,
) {
    var entries by remember { mutableStateOf<List<WordStore.Entry>?>(null) }
    // Read again after every change here, and when the count or notice changes (Forget, from the main screen).
    var changes by remember { mutableIntStateOf(0) }
    LaunchedEffect(status, changes) { words.entriesAsync { e, s -> entries = e; if (s != status) onStatus(s) } }
    var query by rememberSaveable { mutableStateOf("") }
    // The word whose Remove was tapped once (Q30: two taps to delete).
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Text(
        "Words you save stay until you remove them. Words you use often are learned on their own, after three uses. " +
            "Single words only, encrypted on this device.",
        color = Color(p.soft), fontSize = 15.sp, fontFamily = font,
    )
    noticeText(status?.notice)?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = font) }

    // No suggestions or learning in this box: searching must never teach the keyboard what was searched.
    ScarletField(
        query, { query = it; message = null }, "Search or add a word", p, font,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            platformImeOptions = PlatformImeOptions(Words.NO_LEARN),
        ),
    )
    val q = query.trim().lowercase()
    val list = entries
    val shown = list?.filter { q.isEmpty() || it.word.contains(q) }.orEmpty()

    // A word not on the list can be saved from here ("Search, remove or add").
    if (list != null && Words.isWord(q) && list.none { it.word == q }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("\"$q\" isn't on your list.", color = Color(p.text), fontSize = 16.sp, fontFamily = font, modifier = Modifier.weight(1f))
            Pill("Save", false, p, font, enabled = !busy) {
                busy = true
                words.keepAsync(q) { ok, s ->
                    busy = false
                    message = if (ok) "Saved \"$q\"." else "Couldn't save \"$q\"."
                    onStatus(s); changes++
                }
            }
        }
    }
    message?.let { Text(it, color = Color(p.soft), fontSize = 14.sp, fontFamily = font, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

    when {
        list == null -> Unit
        list.isEmpty() -> Text("No words yet. Words you use often show up here, and words you save.", color = Color(p.soft), fontSize = 15.sp, fontFamily = font)
        shown.isEmpty() -> Text("No words match.", color = Color(p.soft), fontSize = 15.sp, fontFamily = font)
        else -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(p.surface)).border(1.dp, Color(p.line), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            shown.take(MAX_ROWS).forEach { e ->
                WordRow(e, confirming == e.word, busy, p, font,
                    onRemove = { confirming = e.word },
                    onKeep = { confirming = null },
                    onConfirm = {
                        busy = true; confirming = null
                        words.removeAsync(e.word) { s -> busy = false; message = "Removed \"${e.word}\"."; onStatus(s); changes++ }
                    },
                )
            }
        }
    }
    if (shown.size > MAX_ROWS) Text("Showing $MAX_ROWS of ${shown.size}. Search to find the rest.", color = Color(p.soft), fontSize = 14.sp, fontFamily = font)

    val count = status?.count ?: 0
    if (count > 0 || status?.notice?.let { it != WordStore.Notice.NONE } == true || forgetOk == false) {
        if (!confirmForget) {
            Pill("Forget all words", false, p, font, enabled = !forgetting, danger = true) { confirmForget = true }
        } else {
            Text(
                when (count) {
                    0 -> "Forget all your words? This can't be undone."
                    1 -> "Forget your 1 word? This can't be undone."
                    else -> "Forget all $count words? This can't be undone."
                },
                color = Color(p.text), fontSize = 16.sp, fontFamily = font,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Keep", false, p, font) { confirmForget = false }
                Pill("Forget all", false, p, font, enabled = !forgetting, danger = true) { confirmForget = false; onForget() }
            }
        }
    }
    forgetOk?.let { ok ->
        Text(if (ok) "Forgotten." else "Couldn't forget your words. Try again.", color = Color(p.soft), fontSize = 14.sp, fontFamily = font,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
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
            Text("Remove \"${e.word}\"?", color = Color(p.text), fontSize = 17.sp, fontFamily = font)
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
