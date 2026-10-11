# Scarlet Keys 0.2.2 plan

Status: **waiting for the owner's answers and go.** Nothing here is built. Prepared 2026-10-11 with /prep (resume mode) from the cross-app pattern check (Command `docs/BUILD-PATTERNS-2026-10.md`, "Keys hits"). Every finding below was checked against the code at `80aa720` before it went in.

Anything marked **proposed** is Claude's suggestion, not a decision, until the owner confirms it.

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

## What can go wrong (draft, **proposed**)

For the learned-words store and Forget. Each line becomes a check in slice 2 or 3 once the owner confirms it.

### Learned words (`UserWords`)

1. **The file can't be decrypted** (key gone after a reset of the key store, a restore onto a new device, or a damaged file). Proposed: the unreadable file is never overwritten. It is moved aside as `words.unreadable`, learning starts fresh, and Settings shows one line under "Learn my words": "Your learned words couldn't be read on this device, so it started a new list." Forget also deletes the set-aside file. (Question Q2.)
2. **A passing error** (key store busy, the device not quite unlocked yet). Proposed: the store stays "not loaded", nothing is saved, and the next field tries again. A save never runs while the old file is unread.
3. **The file is cut short or holds junk lines.** Decryption fails as a whole (AES-GCM checks the tag), so this is case 1. A line with a bad count is skipped, not read as 0.
4. **Killed mid-save.** The new list is written to `words.tmp`, then renamed over `words.enc`. Proposed: the rename result is checked; on failure `dirty` stays set so the next save tries again, and the temp file is deleted. A leftover `words.tmp` at load time is deleted, never read.
5. **Settings and the keyboard at the same time.** One instance per process (D-017) and every method synchronized: kept as is, with a test that a Forget followed by a keyboard save writes nothing back.
6. **Very large list.** Words are capped at 30 letters already. Proposed: cap the list at 5,000 words, dropping the least-used, so the file and the suggestion pass stay small.
7. **Format change later.** Proposed: the file starts with a one-byte format version (inside the encrypted body), so a future 0.3 can read 0.2.2 files and an older app refuses a newer file instead of wiping it. Files from 0.2.1 (no version byte) are read as version 0.

### Forget learned words

1. **Delete fails** (file in use, storage error). Proposed: `clear()` reports success only when `words.enc`, `words.tmp` and `words.unreadable` are all gone. Settings then shows "Forgotten." and hides the pill; on failure it shows "Couldn't forget your words. Try again." and keeps the count.
2. **The keyboard saves right after Forget.** Covered by the single store (D-017): memory is emptied first, under the same lock, so nothing old can be written back. Test kept as a JVM test.
3. **Tapped twice.** The pill does one Forget at a time and is disabled while it runs.
4. **Settings closed mid-Forget.** The work runs off the main thread and is not tied to the screen, so it finishes; the next open reads the true count.
5. **What Forget doesn't touch:** the Keystore key stays (it holds no words); the "Learn my words" setting stays as it was. Proposed: say so in nothing; the pill text is enough.

## Slices

Each slice is one commit (or a small series), passes the build, unit tests and lint with no new errors, and is checked against `DONE-CHECKLIST.md` before the next starts. Order is proposed; slice 1 has no behaviour change and could ship alone.

### Slice 1: generic text and notices (K6, K7)

- Replace the owner's name, pronouns, phone model and "the tablet" (where it means the owner's device) in comments, scripts and `CLAUDE.md` with neutral wording ("the owner", "the user", "a phone-width screen"), as Lyrics did on 2026-10-11. `CLAUDE.md` follows the Lyrics wording: folders "next to this one", "the backups it describes", no drive labels or script path.
- Add `licenses/Apache-2.0.txt` and a Credits line: "Jetpack Compose and AndroidX libraries (Apache 2.0)".
- Checks: every pattern in the repo guard's private denylist, run over all tracked files except `tools/dict`, finds nothing (the patterns stay out of this repo); no pronoun for the owner remains; the repo guard passes the commit; build unchanged (no code changes).

### Slice 2: learned words never vanish silently (K1)

- Split `UserWords` into the file format and the store logic (plain Kotlin, JVM-testable with a fake cipher) and the Keystore part.
- Implement "What can go wrong" 1 to 7 as confirmed.
- Tests (JVM): unreadable file is set aside and never overwritten; passing error leaves the file untouched and retries; failed rename keeps `dirty` and removes the temp file; leftover temp file is ignored; bad count line skipped; 0.2.1 file (no version byte) reads; newer version refused without writing; cap at 5,000.
- Device check: upgrade the personal build over 0.2.1 on the emulator with learned words in place; the count in Settings is unchanged.

### Slice 3: Forget tells the truth, Settings off the main thread (K3, K5)

- `clear()` returns whether every copy is gone; Settings shows the result as in "What can go wrong" above.
- Settings loads the count off the main thread (shows nothing until it's known, then the pill); The `onDestroy` save (`KeysService.kt:500`) goes to the worker before it shuts down.
- Tests (JVM): Forget removes all three files; delete failure reports failure and keeps the count; Forget then save writes nothing back.
- Emulator: Forget with words, Forget pill gone, reopen Settings shows no pill; StrictMode (debug only) shows no disk read on the main thread in Settings.

### Slice 4: accessibility (K4)

Scope depends on Q3. Proposed full scope:

- `KeyboardView`: an `ExploreByTouchHelper` exposing each key as a virtual view, with spoken labels for icon keys (Shift and its state, Backspace, Enter with its action word, Space, Symbols, Letters, Numbers, Hide keyboard), so TalkBack's lift-to-type works.
- `SuggestionStrip`: each suggestion as a virtual view ("Suggestion: hello").
- Settings `Pill`: selected state for Theme and Key height choices (`Role.RadioButton`, selected), as Lyrics R21.
- No allocation added to drawing or touch handling (D-013): node info is built only when TalkBack asks.
- Checks: a JVM test that every key in every layout has a non-empty spoken label; emulator with TalkBack on: type a word by touch, pick a suggestion, change theme; tap areas already at least 48dp.

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

Not yet asked. (Recorded here and in `docs/KEYS-DECISIONS.md`, newest first, dated, in the owner's words.)
