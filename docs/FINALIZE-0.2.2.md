# Finalize Scarlet Keys 0.2.2

Started 2026-10-12. Base: v0.2.1 (6a536fc). Diff: 38 files, +1992 -180 (before the version bump).
Mode: full. Public: yes.

Version 0.2.2 (versionCode 5) as decided in `docs/KEYS-0.2.2-PLAN.md` (slice 5, accepted with the plan, Q4). This run is slice 5 of that plan. Slices 0 to 4 each had a `/finalize --slice` check; their findings are in the plan.

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | tree clean, 8 commits ahead of origin; gh signed in; both devices connected; emulators scarlet_phone, scarlet_small, scarlet_tablet |
| 1 | Audit | done | release manifest permissions match 0.2.1 (VIBRATE, receiver permission); allowBackup false; four agents (spec, rules, manifest, data); 9 findings, 4 questions; the rotation answer had its own slice check |
| 2 | Code review | done | five agents (typing, storage, UI, security, Android pass); 12 findings, 3 questions; the R10 answer had its own slice check (clean, two notes fixed); 49 unit tests, lint no errors |
| 3 | Cleanup | done | /simplify (reuse, simplification, efficiency, altitude agents); no TODOs, commented-out code or unused resources added; TUNING false in release; release notes drafted (Q8), README line (Q9), C1 answered (Q10); 49 unit tests, lint no errors |
| 4 | QA | done | pass 3 after S1 (stage 5): clean (49 tests, no lint errors or new warnings, three builds, release APK checks unchanged). Pass 1: QA1 fixed, QA2 asked (Q11); pass 2 clean: 49 release unit tests, lintRelease no errors and no new warnings, release/personal/smoke built, release APK 0.2.2 (5), not debuggable, permissions as 0.2.1, 4,524,313 bytes (0.2.1: 4,451,441, +1.6%), license texts in assets/licenses (C2 done) |
| 5 | Smoke test | running | Part 1 done (below); Part 2: the owner's checklist |
| 6 | Ship | pending | |
| 7 | Wrap-up | pending | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| A1 | 1 | question | scarlet-hq D-021 | Keys' public name is Keyward (decided today); 0.2.2 still says Scarlet Keys | Q1: rename in its own release | stage 1 |
| A2 | 1 | mechanical | docs/KEYS-DECISIONS.md Q15, plan slice 4 | Q15's shown list not recorded; plan named a "Numbers" label that doesn't exist | Q15 points at KeyLabels.kt at 38d0669; plan wording fixed | stage 1 |
| A3 | 1 | mechanical | docs/KEYS-0.2.2-PLAN.md:3,50 | status line and "Proposed:" out of date | updated | stage 1 |
| A4, A5 | 1 | mechanical | scarlet-hq keys IDEAS.md, DECISIONS.md D-019 | stale (KB-1 to KB-3 shipped, KB-4 to KB-7, Q5 to Q15 missing) | at hq-log (wrap-up) | |
| B1 | 1 | mechanical | docs/KEYS-BACKLOG.md:24 | named the owner's phone model | "the phone" | stage 1 |
| B2 | 1 | question | release.ps1:14-15 | keystore file name and alias in a public script (no password) | Q3: keep | |
| B3 | 1 | question | tools/dict/extra.dict | medicine brand names in a hand-picked public list | Q4: keep | |
| C1 | 1 | mechanical | keys/build.gradle.kts CopyLicenses | any file in licenses/ would ship | only `.txt` files | stage 1 |
| C2 | 1 | check | release APK | licenses in the release APK not yet seen | checked in QA (stage 4) | |
| D1 | 1 | mechanical + question | WordStore.save, UserWords | the key was replaced after any 3 failed saves, disk errors included, and a failed retry could strand the list | only seal failures count; Q2: safe rotation (key generations, old keys retired after a good save); slice check clean, one gap fixed (retire after every good save) | stage 1 |
| D2 | 1 | mechanical | WordStore.load | words from the file skipped the typing rule (a downgrade could leave a control character in a word) | cleaned and checked like typed words | stage 1 |
| D3 | 1 | mechanical | WordStore.clear | a folder that couldn't be listed counted as no set-aside files | Forget fails then | stage 1 |
| R1 | 2 | mechanical | KeyboardView a11y, KeysService | caps lock unreachable with TalkBack (two Shift taps within 350 ms) | TalkBack long press on Shift turns caps lock on | stage 2 |
| R2 | 2 | mechanical | KeyboardView rows setter | TalkBack focus dropped on every field restart, not only on a layout change | acts only on a real change | stage 2 |
| R3 | 2 | mechanical | SettingsActivity | Forget state lived inside the main page: opening Licenses or Tuning mid-Forget reset it | moved above the pages | stage 2 |
| R4 | 2 | mechanical | SettingsActivity Theme and Key height rows | at 200% text the pills squeezed and their labels broke | the rows wrap (FlowRow) | stage 2 |
| R5 | 2 | mechanical | SettingsActivity onCreate | every rotation showed a blank frame while preferences reloaded | after the first load in a process, it draws at once from memory | stage 2 |
| R6 | 2 | mechanical | WordStore.save | a disk failure (seal worked) didn't reset the seal-failure count | reset | stage 2 |
| R7 | 2 | mechanical | WordStore.load | no size limit before reading the list file | a file over 1 MB is set aside unread | stage 2 |
| R8 | 2 | mechanical | WordStore | use counts could overflow | capped at 1,000,000 | stage 2 |
| R9 | 2 | mechanical | KeysService.configureFor | undo could carry into a restarted field without autocorrect (Compose fields share an id) | dropped there | stage 2 |
| R10 | 2 | question | WordStore locks | typing could wait on the disk and Keystore (one lock) | Q5: two locks; slice check clean | stage 2 |
| R11 | 2 | question | KeysService.takeUp | a picked-up word was a name in boxes that capitalise every word | Q6: not in those boxes | stage 2 |
| R12 | 2 | question | Suggest, KeysService.learn | "im" typed twice where autocorrect is off stopped the I'm fix for good | Q7: only taking it back counts | stage 2 |
| R13 | 2 | mechanical | WordStore.save, UserWords | the Keystore was asked to retire keys after every save | only when more than one key exists | stage 2 |
| C1 | 3 | question | KeysService.refreshSuggestions | the strip highlighted "I" for "i" though "i." stays lowercase | Q10: no highlight for "i" | stage 3 |
| C2 | 3 | mechanical | WordStore, UserWords | saves and loads ran on the suggestion thread; trim sorted all 5,000 words; the save copy was built under the memory lock; the learned list was copied on every key; "newer" was a second flag | saves and loads on the store's own thread; linear trim; bytes built after the lock; learned list cached per change; one state | stage 3 |
| C3 | 3 | mechanical | SettingsActivity, LicensesScreen | the same card row three times; two states for one Forget result; aliases (open, rows) | NavCard; one result state; aliases gone | stage 3 |
| C4 | 3 | mechanical | KeysService, KeyboardView, Words, Suggest, Licenses, UserWords, LicensesTest | repeated rules and small expressions; Enter redrew on every field start; dead members; a regex compiled per line | shared isWordChar and fixesToI, finishComposing(), takeIf undo, one `at`, Enter redraws on change, dead members removed, regex compiled once | stage 3 |
| C5 | 3 | skipped | SettingsActivity scroll, Keystore key cache, backspace allocations, separator finishComposingText | possible further simplifications | left as is: tested logic or safety, small gain before a release; noted for later | |
| QA1 | 4 | mechanical | keys/build.gradle.kts | the new customview dependency wasn't in the version catalog (lint UseTomlInstead) | moved to gradle/libs.versions.toml | stage 4 |
| QA2 | 4 | question | gradle/libs.versions.toml | lint: customview 1.2.0 exists (1.1.0 used) | Q11: keep 1.1.0; warning accepted | |
| S1 | 5 | mechanical | KeyboardView (since 0.2.1) | on a small phone at 1.3x text, the space bar's "SCARLET KEYS" mark ran past the key in Settings' preview | shrunk to fit the space bar when needed; QA pass 3 clean | stage 5 |
| R14 | 2 | noted | SuggestTest.fastEnoughPerKeystroke | the 50 ms speed check failed once (60 ms) while two builds and lint ran alongside; alone: 21 ms and 20 ms | QA runs the tests on their own | |

## Questions

### Q1 (audit, A1)
D-021 makes Keys' public name Keyward; the brand notes keep the old name on the site and stores until the app ships the new one. Options: its own release (recommended), or rename in 0.2.2.
**Answer (2026-10-12):** "Its own release (Recommended)"
**Follow-up:** 0.2.2 ships as Scarlet Keys; the rename is a later release.

### Q2 (audit, D1)
How to replace a key that keeps failing. Options: safe rotation (recommended), or only through Forget with a Settings line.
**Answer (2026-10-12):** "Safe rotation (Recommended)"
**Follow-up:** key generations (keys_user_words_v1, then _g2, _g3); only seal failures count; reading tries every key; older keys go after a good save with the newest. Tests: aKeyThatFailsEverySaveIsRotatedAndNothingIsLost, diskFailuresNeverReplaceTheKey, aRotationWhoseSaveFailsStrandsNothing.

### Q3 (audit, B2)
release.ps1 names the keystore file and alias (no password). Options: keep (recommended), or read from environment variables.
**Answer (2026-10-12):** "Keep as is (Recommended)"

### Q4 (audit, B3)
Medicine brand names in tools/dict/extra.dict. Options: keep (recommended), or remove the six.
**Answer (2026-10-12):** "I dont actually use any one of those in my daily life, so if any makes an inference from it, they'll trip themselves up instead, which i like the sound of. Keep them"

### Q5 (review, R10)
Typing can wait while the learned-words file is saved or read (one lock). Options: fix now (recommended), or a later version.
**Answer (2026-10-12):** "Fix now (Recommended)"
**Follow-up:** two locks: memory (brief) and files/Keystore; saves copy the list, then seal and write without the memory lock; Forget still holds the file lock throughout. Tests: wordsTypedDuringASaveStayUnsaved and the existing Forget tests.

### Q6 (review, R11)
A picked-up capitalised word counted as a name even where the box capitalises every word. Options: not in Capitalise-words boxes (recommended), or never for picked-up words.
**Answer (2026-10-12):** "Not in Capitalise-words boxes (Recommended)"

### Q7 (review, R12)
"im" learned by typing it where autocorrect is off stops the I'm fix. Options: only taking it back counts (recommended), or leave as is.
**Answer (2026-10-12):** "Only taking it back counts (Recommended)"

### Q8 (cleanup, release notes)
The 0.2.2 release notes as drafted in `docs/release-0.2.2/RELEASE-NOTES.md`. Options: OK as written (recommended), or shorter.
**Answer (2026-10-12):** "OK as written (Recommended)"

### Q9 (cleanup, README)
Add "Works with TalkBack: every key, suggestion and setting is spoken." to the README feature list. Options: add it (recommended), or leave the README.
**Answer (2026-10-12):** "Add it (Recommended)"

### Q10 (cleanup, C1)
"I" highlighted for "i" though a period keeps it lowercase. Options: leave it (recommended), or no highlight for "i".
**Answer (2026-10-12):** "No highlight for \"i\""
**Follow-up:** "I" is still offered and space still fixes it; it's just not highlighted.

### Q11 (QA, QA2)
customview 1.2.0 exists; 1.1.0 is what the TalkBack checks ran on. Options: keep 1.1.0 (recommended), or upgrade.
**Answer (2026-10-12):** "Keep 1.1.0 (Recommended)"

## Smoke test, Part 1 (emulators, 2026-10-12)

Smoke copy (release-optimized, `com.thescarletcoder.keys.smoke`) on scarlet_phone, scarlet_small (text 1.3x) and scarlet_tablet, light and dark. Screenshots: The Scarlet Raven `screenshots/2026-10-12-keys-0.2.2/` (phone, small, tablet; settings, keyboard, licenses, license text; phone also 200% text).

- Upgrade (regular app id, personal builds): 0.2.1 built from its tag learned 3 words with Dark, Large keys, bubble off; 0.2.2 installed over it kept all of them.
- Typing in the smoke copy: two backspaces keep spaces; a period keeps its word; "i" to "I", "im" to "I'm", "i.e." left alone; "Macbook" with Shift left alone; "i" shows "I" without a highlight (Q10).
- Licenses list and text, Forget, 200% text (choice rows wrap, R4), killed from home and reopened on the license page (it comes back to the page, at the top of the text).
- No crashes on any emulator; the smoke copy logged nothing of its own (R8 strips logs).
- S1 found and fixed (the preview's space-bar mark at 1.3x text on the small phone); QA pass 3 clean after it.
- Not checked on screen: TalkBack caps lock (double-tap-and-hold can't be scripted with adb; tried, unreliable). Code-reviewed (R1); optional line in the owner's checklist.
- Seen, not a Keys bug: on emulators, replacing or force-stopping the selected keyboard's package makes Android fall back to Gboard; on the owner's devices Keys has stayed selected through every install today.

## Ship

| Step | Offered | Choice | Result |
|---|---|---|---|
| Install on devices | | | |
| Signed public build | | | |
| GitHub release (draft) | | | |
| Website update | | | |

## Wrap-up

- Backups:
- STATUS-AUDIT updated:
- hq-log:
