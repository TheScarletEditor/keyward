# Scarlet Keys 0.2.2 plan

Status: **slices 0 to 4 done (2026-10-12); slice 5 (the release) runs as /finalize 0.2.2, report in `FINALIZE-0.2.2.md`.** Prepared 2026-10-11 with /prep (resume mode) from the cross-app pattern check (Command `docs/BUILD-PATTERNS-2026-10.md`, "Keys hits"). Every finding below was checked against the code at `80aa720` before it went in.

Everything below was proposed by Claude and confirmed by the owner on 2026-10-11 (Q1 to Q4), so it is **decided**.

## Where things stand

- Released: 0.2.1 (versionCode 4), GitHub release v0.2.1, 2026-10-10. Owner devices run the personal build (D-018).
- `main` is 1 commit ahead of `origin/main`: `80aa720` "Smoke build type and smoke.ps1 for /finalize", made by another session for /finalize. Working tree clean otherwise. Pushing it is the owner's call (hub session).
- No open `docs/FINALIZE-*.md`, no other plan, no repo `*-DECISIONS.md` (Keys decisions live in scarlet-hq, last D-018).
- Repo guard: installed, matches the skill's copy.
- Backups: no verified two-drive backup yet (neither drive has passed verification). Release 0.2.2 is gated on it or on a recorded skip.
- Decisions since the last session (D-017, D-018): D-017's "one shared word store" holds (`UserWords.get`, single instance, used by `KeysService` and `SettingsActivity`). D-018 holds (personal build type still debug-signed).

## Baseline (as found, before any 0.2.2 work)

Run 2026-10-11 at `80aa720` (`:keys:assembleDebug :keys:testDebugUnitTest :keys:lintDebug`, JAVA_HOME set to Android Studio's runtime as the repo scripts do).

- Build: passes. One Kotlin warning: `LocalLifecycleOwner` deprecated (`SettingsActivity.kt:109`).
- Unit tests: 7 of 7 pass (`SuggestTest` 6, `BinaryDictTest` 1). Nothing covers `UserWords`, the undo logic or the field rules.
- Lint: 0 errors, 34 warnings, all older than this plan: UseKtx 13, GradleDependency 9, AndroidGradlePluginVersion 3, DrawAllocation 3 (`KeyboardView.kt:166,180,181`, all in `onLayout`, where keys are built by design; drawing and touch stay allocation-free), UseTomlInstead 2, StaticFieldLeak 1 (`UserWords.kt:105`, holds the application context, so not a real leak), ObsoleteSdkInt 1, RedundantLabel 1, UseOfNonLambdaOffsetOverload 1.
- None of this is fixed by 0.2.2 unless a slice touches the line anyway.

## Findings brought in (verified)

| # | Finding | Where (at 80aa720) | Verified |
| --- | --- | --- | --- |
| K1 | Learned words: `load()` and `save()` swallow every error. A damaged file or a lost key leaves an empty list, and the next `save()` writes a new file over the old one: the words are gone with no sign. `save()` ignores the result of `renameTo` and still clears `dirty`, so a failed rename drops the batch. | `UserWords.kt:47-76` | Yes. Also found: `loaded = true` is set **before** the read, so a passing error (key store busy right after unlock) has the same effect as a lost key: empty list, then the next save overwrites the real file. That makes K1 more than a key-loss edge case. |
| K3 | "Forget learned words" ignores the result of `delete()`, and the Settings pill sets the count to 0 either way. | `UserWords.kt:79-84`, `SettingsActivity.kt:138` | Yes. Also found: Forget never touches `words.tmp`, which still holds the last batch if a rename ever failed (Done checklist 4: "Forget reaches every copy"). |
| K4 | The keyboard has no accessibility semantics. | `KeyboardView.kt` (451 lines, canvas), `SuggestionStrip.kt` (canvas) | Yes: no `contentDescription`, delegate or `AccessibilityNodeInfo` in either view. Also found: the Theme and Key height pills in Settings don't say which one is on (`SettingsActivity.kt:300`, `Pill` has no selected state for TalkBack). Toggles are fine (`Role.Switch`). Not checked with TalkBack on a device. |
| K5 | File and Keystore work on the main thread in Settings. | `SettingsActivity.kt:78` (`UserWords.get(this).also { it.load() }` in `onCreate`) | Yes. The keyboard service loads and saves on its worker (`KeysService.kt:91,159,202`), except the save in `onDestroy` (`:500`), which runs on the main thread. |
| K6 | Private details in a public repo. | see list | Yes, and two more than the report: `keys/build.gradle.kts:41` (a pronoun for the owner) and `Compact.kt:6` (names the owner's phone model). The owner's first name is in 12 tracked files (13 counting the dictionary source, where it is an ordinary word list entry and stays): `CLAUDE.md:5`, `install.ps1:4`, `keys/build.gradle.kts:40`, `Feedback.kt:14`, `FieldLog.kt:9`, `KeyOverlay.kt:59`, `KeysPrefs.kt:30,53`, `KeysService.kt:61,66,208,280,417,484`, `Layouts.kt:13`, `SettingsActivity.kt:173`, `UserWords.kt:15`, `Tunables.kt:8`. `KeysPrefs.kt:30` also uses a pronoun for the owner. `CLAUDE.md:3,7,13` has local paths, both drive labels and the backup script path. "The tablet" in `UserWords.kt:17` and `KeysPrefs.kt:53` reads as the owner's device. Git history keeps the old text either way. |
| K7 | Apache 2.0 notices missing for Compose (ui, foundation, material3), AndroidX core-ktx, activity-compose and profileinstaller, all shipped in the APK. | `README.md` Credits, `licenses/` | Yes: Credits list only the word list and the two fonts; `licenses/` has only the two OFL files. Lyrics has the same gap (its C1 fix added SQLCipher and Bouncy Castle only): noted for Lyrics, not fixed here. |

Not brought in: K2 (fixed in 0.2.1) and K8 (speed and size; answered by this plan's small slices and the tests in each).

## What can go wrong (**decided**, Q2 and Q4)

For the learned-words store and Forget. Each line becomes a check in slice 2 or 3.

### Learned words (`UserWords`)

1. **The file can't be decrypted** (key gone after a reset of the key store, a restore onto a new device, or a damaged file). Decided: the unreadable file is never overwritten. It is moved aside as `words.unreadable`, learning starts fresh, and Settings shows one line under "Learn my words": "Your learned words couldn't be read on this device, so it started a new list." Forget also deletes the set-aside file. (Q2.)
2. **A passing error** (key store busy, the device not quite unlocked yet). Decided: the store stays "not loaded", nothing is saved, and the next field tries again. A save never runs while the old file is unread.
3. **The file is cut short or holds junk lines.** Decryption fails as a whole (AES-GCM checks the tag), so this is case 1. A line with a bad count is skipped, not read as 0.
4. **Killed mid-save.** The new list is written to `words.tmp`, then renamed over `words.enc`. Decided: the rename result is checked; on failure `dirty` stays set so the next save tries again, and the temp file is deleted. A leftover `words.tmp` at load time is deleted, never read.
5. **Settings and the keyboard at the same time.** One instance per process (D-017) and every method synchronized: kept as is, with a test that a Forget followed by a keyboard save writes nothing back.
6. **Very large list.** Words are capped at 30 letters already. Decided: cap the list at 5,000 words, dropping the least-used, so the file and the suggestion pass stay small.
7. **Format change later.** Decided (Q4): the file starts with a one-byte format version (inside the encrypted body), so a future 0.3 can read 0.2.2 files and an older app refuses a newer file instead of wiping it. Files from 0.2.1 (no version byte) are read as version 0.

### Forget learned words

1. **Delete fails** (file in use, storage error). Decided: `clear()` reports success only when `words.enc`, `words.tmp` and `words.unreadable` are all gone. Settings then shows "Forgotten." and hides the pill; on failure it shows "Couldn't forget your words. Try again." and keeps the count.
2. **The keyboard saves right after Forget.** Covered by the single store (D-017): the files and then memory are cleared under the same lock, so nothing old can be written back (the list file goes last, so a failed Forget leaves it on disk, L3-1). Test kept as a JVM test.
3. **Tapped twice.** The pill does one Forget at a time and is disabled while it runs.
4. **Settings closed mid-Forget.** The work runs off the main thread and is not tied to the screen, so it finishes; the next open reads the true count.
5. **What Forget doesn't touch:** the Keystore key stays (it holds no words); the "Learn my words" setting stays as it was. Decided: say so in nothing; the pill text is enough.

## Slices

Each slice is one commit (or a small series), passes the build, unit tests and lint with no new errors, and is checked with `/finalize --slice` before the next starts. Order decided (Q4); slice 1 has no behaviour change and could ship alone.

### Slice 0: landscape (L1, added 2026-10-12, built first)

Owner, 2026-10-12: "texting in landscape, the keyboard takes up the whole screen." Two causes, both fixed:

- Keys never turned off Android's fullscreen ("extract") mode, which by default turns on in landscape on a short screen and covers the app with a copy of the text field. `KeysService.onEvaluateFullscreenMode()` now returns false.
- `Compact` went by the screen's current width, so a phone in landscape got the tablet's sizes and 64 dp keys. It now goes by the short side (`smallestScreenWidthDp`), so a phone stays a phone (phone sizes, letter bubble) in both orientations.
- In landscape the keys take at most 40% of the screen's height and shrink to fit (tuning: Landscape, 0.25 to 0.6, personal and debug builds only).
- Checks: unit tests and the personal build pass; the owner texts in landscape on the phone and the app stays visible above the keyboard; portrait unchanged on both devices; tablet landscape unchanged (its keys already fit under 40%). Emulator walk (phone, small phone, tablet, landscape) with the 0.2.2 release check.

### Slice 0b: typing bugs (KB-1 to KB-3 and D-020, added 2026-10-12, built second)

Owner, 2026-10-12: "now fix the three typing bugs" (`docs/KEYS-BACKLOG.md`).

- KB-1, two quick backspaces eat the spaces before a word: picking a word back up used a remembered cursor position, and selection updates arrive late, so the word was written over the wrong stretch of text. `resumeWord` now works relative to the real cursor (delete the word, set it again as the word being typed, in one batch); the remembered `cursor` is gone.
- KB-2, a period sometimes erases the word: a field that restarts mid-word keeps the word marked as being typed while Keys forgets it, so the next space or period replaced it. Keys now lets go of the word on a restart, and a space or punctuation with no word in progress clears any leftover mark first.
- KB-3 and D-020: "i", "im", "i'm", "ive", "i've", "i'll", "i'd" always become "I", "I'm", "I've", "I'll", "I'd" (a rule ahead of the dictionary; "id" and "ill" stay words). One backspace still undoes it, and a word taken back is never corrected again in that field.
- Checks: JVM test `iIsAlwaysCapital`; lint no new errors; the owner types on both devices (two quick backspaces after a word, a period right after a word, "i" mid-sentence, "im"). KB-1 and KB-2 have no JVM test (the editing code needs a real text field); the emulator walk for 0.2.2 repeats them.

### Slice 0c: names (KB-4, added 2026-10-12, built third)

Owner, 2026-10-12: "Macbook tried to autocorrect. Does Keys not recognize brands?" The AOSP list knows older names (iPhone, Samsung, Netflix) but predates MacBook, AirPods, Venmo, TikTok and others, so they looked like typos. Owner's picks (Q7): both.

- `tools/dict/extra.dict`: a hand-kept list of newer names in the en.dict format, merged by `:keys:buildDict`. Modest frequencies (60 to 90) so a name never outranks a common word as a fix; names one slip from a common word (Ulta and "ultra") are left out. Typing "macbook" is left alone and "MacBook" is offered.
- A word the user capitalises mid-sentence is taken as a name and never autocorrected (suggestions still show). A capital at the start of a sentence or the field says nothing. The "I" words are still fixed.
- Checks: JVM tests `modernNamesAreKnown`, `capitalMidSentenceIsAName`, and `BinaryDictTest` with the merged list; the owner types "Macbook" mid-sentence and a capitalised made-up name.

### Slices 0 to 0c: mini-audit (2026-10-12)

`/finalize --slice` on `5054bd7..3a10263`, run after the slices were already on the owner's devices and pushed (they were built as quick fixes before the check). Checklist pass, three review rounds, build, unit tests (13) and lint clean. Findings:

- L0-1 (question): "i" then "." became "I.", so "i.e." became "I.e.". Owner: "Leave \"i.\" alone (Recommended)". "i" before a period is never fixed.
- L0-2 (mechanical, high): Jetpack Compose fields apply a batch's edits at its end and answer reads from the old text, so backspacing into a word read stale text and KB-1 came back there. All reads now happen before any edit (`lookBack`), and the word is marked in place with `setComposingRegion` where the editor gives positions (`getSurroundingText`), which also keeps links and formatting (L0-4); else it is retyped.
- L0-3 (mechanical): a stray "being typed" mark widened deletes. Cleared before taking a word up, and whenever an update shows one while no word of ours is in progress.
- L0-5 (mechanical): the name rule took autocaps after quotes or brackets (`He left." teh`) and every word in capitalise-words fields for names. A new word is a name only when the user tapped Shift, mid-sentence (quotes and brackets skipped); a word picked up again, when it starts with a capital mid-sentence and isn't all caps.
- L0-6 (mechanical): "IM" in caps became "I'M"; all-caps forms are left alone.
- L0-7 (mechanical): learned words now stop the "I" fix only for a form the user took back ("im"), never "i'm" learned from every fix.
- L0-8 (mechanical): "Shein" blocked the "shine" typo fix; removed (rule already in extra.dict's header).
- L0-9 (mechanical): a word list line with a Windows line ending silently got frequency 0; both loaders trim, and the build fails on a frequency that doesn't parse.
- L0-10 (mechanical): fields that restart as you type (and rotation) wiped the undo and the words taken back. Both survive a restart of the same field; the undo only applies where it was made (same text before the cursor, same position), since Compose and web fields in one app can share an id.
- L0-11 (mechanical): fewer round trips to the app per word (one `getSurroundingText` instead of up to four reads).
- Laptop tests added: `WordsTest` (word at the cursor, sentence starts), "IM", taken-back and learned "I" words. KB-1 and KB-2 still have no laptop test (they need a real text field); covered on the emulator below.
- Noted, no change: an opening quote mid-sentence (`said "Teh` with Shift) counts as mid-sentence; the landscape keys cap covers the keys only, the strip and bottom padding come on top (the owner checked landscape on the phone); the tablet's short side is 800 dp, well clear of the 600 dp phone line.
- Device checks, 2026-10-12, emulator `scarlet_phone` with `0.2.1+675efea` (same build as the owner's devices), in Keys' own "Try it" box (Jetpack Compose) and in Google Messages (a dummy number, nothing sent). All passed: two backspaces into a word ("say hello world " to "say hello worl", spaces kept); typing straight after a backspace continues the word; a long backspace run across words; a picked-up "hel" + "o" autocorrected to "hello"; "home." keeps its word; "i.e." stays, "so i went" gets "I", "im" gets "I'm"; "teh " to "the ", one backspace back to "teh ", "teh" again left alone; "Macbook" and "Zorvex" with Shift mid-sentence left alone; landscape in Messages leaves the conversation visible.
- Seen on the emulator, no change: in Messages, rotating rebuilds the text box, so the undo after an autocorrect is dropped (the safe direction; it was always dropped before). Messages also redraws an underline over the last words after rotating; Keys' clear-mark calls don't remove it, and the next space and period left the text intact, so it is Messages' own drawing, not a live "being typed" mark.

### Slice 1: generic text and notices (K6, K7)

- Replace the owner's name, pronouns, phone model and "the tablet" (where it means the owner's device) in comments, scripts and `CLAUDE.md` with neutral wording ("the owner", "the user", "a phone-width screen"), as Lyrics did on 2026-10-11. `CLAUDE.md` follows the Lyrics wording: folders "next to this one", "the backups it describes", no drive labels or script path.
- Add `licenses/Apache-2.0.txt` and a Credits line: "Jetpack Compose and AndroidX libraries (Apache 2.0)".
- Checks: every pattern in the repo guard's private denylist, run over all tracked files except `tools/dict`, finds nothing (the patterns stay out of this repo); no pronoun for the owner remains; the repo guard passes the commit; build unchanged (no code changes).

**Done 2026-10-12** (commit "0.2.2 slice 1: generic text, Apache 2.0 notice"). `/finalize --slice`: checklist pass, plan check (the private denylist over every tracked file except `tools/dict`: no hits; no pronoun for the owner, device model or em dash), one review agent, build, 10 unit tests and lint clean. Findings:

- L1-1 (mechanical): the edit turned `UserWords.kt` from CRLF to LF, a whole-file rewrite. Restored and folded into the slice commit before it was pushed.
- L1-2 (mechanical, text shown to the owner): Credits also name the Kotlin standard library and kotlinx.coroutines. Owner: "Use that wording (Recommended)".
- L1-3 (question): the Apache text is in the repo but not in the APK. Owner: "Add to 0.2.2 (Recommended)", now slice 1b; Lyrics has the same gap (filed in scarlet-hq).
- L1-4 (mechanical): `Layouts.kt` points at KEYS-SPEC.md in the Command repo instead of "the original spec".
- Noted, no change: Q5's verbatim answer has the phone model replaced by "[phone]"; CLAUDE.md's backup line leaves the how to BACKUP-SPEC, as Lyrics does.

### Slice 1b: licenses inside the app (L1-3, added 2026-10-12)

- The license texts that cover what ships in the APK (Apache 2.0, and the fonts' OFL) go into the APK's assets, built from `licenses/` so there is one copy.
- Settings gets an "Open source licenses" row that shows them (scrolling text, readable in light and dark, TalkBack reads it).
- Checks: a build step or test fails if a file in `licenses/` is missing from the APK's assets; the row opens on the emulator and on the owner's devices; README Credits and the screen name the same things.

**Done 2026-10-12.** `:keys:copyLicenses` copies the repo's LICENSE and `licenses/` into the APK under `assets/licenses/`; `Licenses.kt` lists the five parts (Scarlet Keys' own GPL included, owner OK); Settings has "Open source licenses" (list, then each license in full, read off the main thread, paragraphs reflowed for a phone). Wording shown to the owner word for word: "OK as written (Recommended)" (Q11). `LicensesTest`: every repo license is listed and in the APK, every listed text ships, keeps every word after reflowing, and is named in the README. 17 unit tests, lint no errors. Emulator: list and text in light and dark, font 1.3, landscape; the open page and scroll survive rotation; Back returns to the same spot on the main page.

`/finalize --slice`: two review rounds. Findings, all mechanical and fixed: L1b-1 a rotation while reading lost the scroll position (texts kept in memory once read); L1b-2 Back to the main page could stop short of the saved spot (waits for the page's height); L1b-3 headings ran into the next paragraph, and the last line of an all-caps paragraph split off ("SUCH DAMAGES."), and "PERMISSION & CONDITIONS" wasn't seen as a heading (headings only at a paragraph's start, judged by the words with letters); L1b-4 the word check skipped the GPL and the word list notice, and the README check passed for LICENSE by accident (every shipped text checked, the README must name the GPL); L1b-5 TalkBack: the row's action label and page titles marked as headings. Found on the emulator before review: hard line breaks wrapped twice (reflow), a huge title for the long part name (the license name is the title now), Back returned to the top of Settings, the open page was lost on rotation.

### Slice 2: learned words never vanish silently (K1)

- Split `UserWords` into the file format and the store logic (plain Kotlin, JVM-testable with a fake cipher) and the Keystore part.
- Implement "What can go wrong" 1 to 7 as confirmed.
- Tests (JVM): unreadable file is set aside and never overwritten; passing error leaves the file untouched and retries; failed rename keeps `dirty` and removes the temp file; leftover temp file is ignored; bad count line skipped; 0.2.1 file (no version byte) reads; newer version refused without writing; cap at 5,000.
- Device check: upgrade the personal build over 0.2.1 on the emulator with learned words in place; the count in Settings is unchanged.

**Done 2026-10-12.** `WordStore` (plain Kotlin: format, load, save, set aside, cap, Forget's files) and `UserWords` (the Keystore). What can go wrong 1 to 7 as decided, plus the owner's two answers in the mini-audit (Q12, Q13). 19 `WordStoreTest` tests (36 in all), lint no errors. Device check on the emulator: the real 0.2.1 (built from its tag) learned 3 words ("Forget learned words (3)"); this build installed over it read the same 3; after typing with it, saving in the new format and a restart, 4. No line shown in Settings.

`/finalize --slice`: three review rounds. Findings:
- L2-1 (mechanical, revised in round 2): Keystore errors were sorted too eagerly. Only a failed integrity check (wrong key, damaged file) and a permanently invalidated key mean "never readable"; anything else from the Keystore is passing, and 3 in a row while unlocked count as unreadable (Q13).
- L2-2 (question): two rare states (a list from a newer Keys; an unreadable list that couldn't be moved aside) stopped learning with nothing shown and no Forget. Owner: a line for each and the Forget pill (Q12).
- L2-3 (mechanical): the cap scanned 5,000 words on the typing path; now trimmed at save and load, the least used and among those the longest unused first.
- L2-4 (mechanical): the new file is synced to disk before the rename.
- L2-5 (noted): disk and Keystore work on the main thread in Settings and in the service's onDestroy save; slice 3 (K5).
- L2-6 (mechanical): a first byte of 0 is treated as unreadable, not as a 0.2.1 file.
- L2-7 (mechanical, found while building): when full, the word just typed could be the one dropped, so a new word could never be learned; the trim's order fixes it.
- L2-8 (mechanical): a key that fails every save left saving stuck with nothing shown; after 3 failed saves in a row the key is replaced once per run and the whole list (all in memory, since the file was read) is written with the new key in the same call.
- L2-9 (mechanical): words typed before the list loaded counted as the oldest; the file's words now come first.
- Also: every set-aside list keeps its own name (`words.unreadable`, `words.unreadable-2`), and Forget shows when only a set-aside list is left.

### Slice 3: Forget tells the truth, Settings off the main thread (K3, K5)

- `clear()` returns whether every copy is gone; Settings shows the result as in "What can go wrong" above.
- Settings loads the count off the main thread (shows nothing until it's known, then the pill); The `onDestroy` save (`KeysService.kt:500`) goes to the worker before it shuts down.
- Tests (JVM): Forget removes all three files; delete failure reports failure and keeps the count; Forget then save writes nothing back.
- Emulator: Forget with words, Forget pill gone, reopen Settings shows no pill; StrictMode (debug only) shows no disk read on the main thread in Settings.

**Done 2026-10-12.** `WordStore.clear()` true only when every copy is gone; Settings reads the store through `UserWords.loadAsync`/`forgetAsync` (its own thread, not the screen's, so a Forget finishes after Settings closes), shows nothing until the answer, disables the pill while a Forget runs, then "Forgotten." or "Couldn't forget your words. Try again." (Q4). The service's `onDestroy` save runs on its worker. StrictMode thread policy in debug builds. 22 `WordStoreTest` tests (39 in all), lint no errors. Emulator (debug build): Forget with 4 words showed "Forgotten." and hid the pill; reopened, no pill, no line. StrictMode: no learned-words or Keystore frames on the main thread; the preference files still are (slice 3b, Q14).

`/finalize --slice`: two review rounds. Findings, all mechanical and fixed: L3-1 a partly failed Forget could delete the list and still say it was kept (the list file is now deleted last, so a failure always leaves it on disk; test `aPartlyFailedForgetLeavesTheListOnDisk`); L3-2 a failed Forget before the list loaded dropped the "needs saving" mark; L3-3 rotation lost the Forget result line (kept across rotation); L3-4 asking Android for the store's folder touched the disk on the main thread (looked up on first file use, in the background); L3-5 a failed Forget could hide the pill and leave no way to try again. Noted: a rotation while a Forget runs shows the right state after, but not the "Forgotten." line.

### Slice 3b: preferences off the main thread too (Q14, added 2026-10-12)

Owner, on L3-4: "Move them off too". StrictMode in Settings (debug) still shows the preference files read on the main thread: `KeysPrefs` (the settings), `KeysTuning`/`Tunables` (tuning values) and `FieldLog` ("Recent fields", debug builds only).

- Read them on a background thread before Settings draws anything; until then the window shows its plain background, light or dark with the system (no flash of wrong values).
- Writes from Settings (toggles, theme, key height) go through `apply()` already (asynchronous); check none use `commit()`.
- The keyboard service reads the same preferences; it is out of this slice unless StrictMode shows its reads on the keyboard's main thread too (then a question).
- Checks: StrictMode (debug build) shows no disk read or write on the main thread when Settings opens, scrolls, toggles a setting and opens Tuning and the licenses; rotation keeps every value; the emulator walk in light and dark.

**Done 2026-10-12.** `SettingsActivity` reads the settings (`KeysPrefs.preload`), the tuning values and "Recent fields" on a background thread, then draws the page. Every write was already `apply()`. 39 unit tests, lint no errors. Emulator (debug build, StrictMode on): opening Settings, scrolling, flipping a switch twice, Tuning, the licenses, two rotations and typing with the keyboard in "Try it": no violation from Keys' process (the only ones logged came from YouTube Music, another app). A switch turned off stayed off through two rotations. The keyboard's own reads showed no violations, so nothing for the service (no question needed).

`/finalize --slice`: one review round, clean. Notes, no change: "Recent fields" (tuning builds only) is read once per opening of Settings, so it no longer refreshes on return from Tuning; each rotation redraws after a reload from memory (a frame or two of plain background).

### Slice 4: accessibility (K4)

Full scope (decided, Q3):

- `KeyboardView`: an `ExploreByTouchHelper` exposing each key as a virtual view, with spoken labels for icon keys (Shift and its state, Backspace, Enter with its action word, Space, Symbols and numbers, More symbols, Letters, Hide keyboard), so TalkBack's lift-to-type works.
- `SuggestionStrip`: each suggestion as a virtual view ("Suggestion: hello").
- Settings `Pill`: selected state for Theme and Key height choices (`Role.RadioButton`, selected), as Lyrics R21.
- No allocation added to drawing or touch handling (D-013): node info is built only when TalkBack asks.
- Checks: a JVM test that every key in every layout has a non-empty spoken label; emulator with TalkBack on: type a word by touch, pick a suggestion, change theme; tap areas already at least 48dp.

**Done 2026-10-12.** `ExploreByTouchHelper` (androidx.customview 1.1.0, Apache 2.0, under AndroidX in Credits) on the keyboard and the suggestion strip; spoken labels in `KeyLabels` (owner's wording, Q15: "Backspace" for the delete key); Settings switches are one item each (row toggleable, the inner switch's own semantics cleared); Theme and Key height pills are radio buttons in a group; Back and Forget are buttons. Nothing allocated in drawing or touch with TalkBack off (the helper checks first). `KeyLabelsTest` (every key in every layout and state has a label, every symbol a name). 42 unit tests, lint no errors. Emulator with TalkBack on: lift on "h" then "i" typed "hi"; lift on the middle suggestion picked "his"; focus then double tap on Dark switched the theme; switch rows are single items; Theme and Key height pills report checked or not.

`/finalize --slice`: one review round, clean. Fixed while here: L4-1 TalkBack focus is cleared when the layout swaps (letters, symbols, number pad), so it doesn't land on a different key; L4-2 Theme and Key height rows are a selectable group ("1 of 3"). Noted for later (backlog): the accent row has no TalkBack route (long press types the digit); the highlighted suggestion (the autocorrect pick) is spoken like the others; on a phone the suggestion strip is 42 dp tall, under 48 dp (as since 0.2.0).

### Slice 6: fold-ins and the name (added 2026-10-12, /prep resume)

The owner, during /finalize 0.2.2 Stage 5: "I would prefer to finish up this project ASAP. Lets run another /prep, fold in whatever's waiting, and then finalize and release." Resume found: KB-5 to KB-7 on the backlog, Keyward (scarlet-hq D-021) decided for its own release, the two-drive backup not run. Owner's answers Q16 to Q22 (`KEYS-DECISIONS.md`). Baseline: /finalize QA pass 3 at 9b1354a (49 unit tests, lintRelease no errors, release/personal/smoke build); no drift (audit agent A, today).

**6a. KB-5, accents with TalkBack.** A vowel key's node offers each accent as its own TalkBack action ("é" etc.), next to the long press that types the digit. Checks: a JVM test that every accent in `Layouts.accents` has a spoken action label; emulator: the actions appear on the node (`uiautomator` can't list IME actions, so by code review plus TalkBack's actions menu if it can be reached).

**6b. KB-6, the highlighted suggestion.** TalkBack says "Suggestion: hello, will replace" for the word space will put in (the strip's highlight); the others stay "Suggestion: hello". Check: KeyLabels test.

**6c. Keyward (D-021, Q18 to Q21).** Every visible "Scarlet Keys" becomes Keyward; package id, folder names and the dictionary's internal magic stay. App name and keyboard label "Keyward"; space bar "KEYWARD"; Settings header "Keyward" / "A keyboard by The Scarlet Coder. No internet access, nothing typed is ever logged or sent anywhere."; set-up lines, notices, licenses intro and list use Keyward; README title Keyward with "Formerly Scarlet Keys." under it; release notes start "Scarlet Keys is now Keyward: same keyboard, same promise, a name of its own."; release.ps1 writes `dist/keyward-<version>.apk`; scripts' messages say Keyward. At ship: the GitHub repo is renamed to `keyward` (with the owner's OK at that step), the git remote and scarlet-hq's repo map follow, and the site's links are updated in the site step. Checks: grep finds no "Scarlet Keys" in user-visible strings (code, res, README) except "Formerly Scarlet Keys." and the release-note line; screenshots of the header, the space bar and the keyboard picker label.

**Built 2026-10-12.** Slice check (a review agent and an audit agent, both on slice 6): clean apart from wording, settled in Q23 to Q26; "will replace" only on real fixes; the preview's sample words and the word list notice no longer read "Scarlet Keys". 51 unit tests, lint no errors.

Not folded in: KB-7 (taller strip; owner left it out), swipe typing (declined), custom key sounds (parked "later"), F-Droid (its own project), the skipped cleanups C5 (no behaviour change, not needed).

Backup: the owner's skip for 0.2.2 (Q22), recorded in Command STATUS-AUDIT.

After 6a to 6c: `/finalize --slice` on the slice, then /finalize 0.2.2 resumes: Stage 1 to 4 re-checks on the new diff (audit and review on slice 6 only, QA in full), Stage 5 again (emulator walk for the rename and the new TalkBack actions; the owner's checklist gains the name items), then ship.

### Slice 5: release 0.2.2

- versionName 0.2.2, versionCode 5; README and release notes match the code.
- Emulator check on a regular phone, a small phone and a large tablet, light and dark; owner tries it on the tablet and the phone (personal build) and OKs it.
- Two-drive backup passes, or the owner records a skip.
- The owner signs with `release.ps1`; GitHub release and website update each only after the owner confirms.

## Questions for the owner (one round)

Claude's recommendation first in each.

1. **Q1 Private details (K6).** The owner's first name in 12 files, pronouns for the owner in two places, the phone model, local paths and both drive labels in `CLAUDE.md`. Options: (a) make generic, same pass as Lyrics Q3 and Q4 on 2026-10-11, pushed history stays as it is (**recommended**); (b) leave as is.
2. **Q2 Learned words that can't be read (K1).** Options: (a) set the unreadable file aside, start a new list, and show one line in Settings saying so (**recommended**); (b) stop learning until the owner chooses "Start a new list" in Settings; (c) start fresh quietly, file set aside, no message.
3. **Q3 Accessibility scope (K4).** Options: (a) full TalkBack support in the keyboard, suggestions and Settings choices in 0.2.2 (**recommended**; the largest slice, about a day, tested on the emulator with TalkBack); (b) Settings choices and suggestion labels now, keyboard TalkBack on LATER.md; (c) all of it on LATER.md.
4. **Q4 The rest of the draft.** "What can go wrong" lines marked proposed above: the 5,000-word cap, the format version byte, Forget's messages ("Forgotten." / "Couldn't forget your words. Try again."), and the slice order. Options: (a) accept as drafted (**recommended**); (b) accept with changes (say which); (c) skip the cap and version byte, keep the rest.

## Owner's answers

2026-10-11, given in the hub session (the owner's picks, word for word). Also in `docs/KEYS-DECISIONS.md`.

- Q1: "Make generic (Recommended)"
- Q2: "Set aside + one line (Recommended)"
- Q3: "Full support in 0.2.2 (Recommended)"
- Q4: "Accept as drafted (Recommended)"
