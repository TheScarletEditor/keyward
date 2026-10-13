# Finalize Scarlet Keys 0.2.2

Started 2026-10-12. Base: v0.2.1 (6a536fc). Diff: 38 files, +1992 -180 (before the version bump).
Mode: full. Public: yes.

Version 0.2.2 (versionCode 5) as decided in `docs/KEYS-0.2.2-PLAN.md` (slice 5, accepted with the plan, Q4). This run is slice 5 of that plan. Slices 0 to 4 each had a `/finalize --slice` check; their findings are in the plan.

## Re-run 2 (from Stage 1, 2026-10-12)

The owner held signing for more work, then asked for /finalize again. Re-run scope: everything since the hold, 0a12989..HEAD: slice 7 (Your words, saved words, two-tap deletes, teal Enter; Q27 to Q30, S7-1 to S7-4), slice 8 (locked down: words file format 3, locked-phone key, capture blocking, PIN for Your words, private boxes, personal signing key, faster suggestions, manifest allowlist, dependency verification; Q31 to Q39), and the Keyward dictionary fix. Each slice had its own slice check (plan notes). The owner's phone and tablet run 0.2.2+84ed148, signed with the owner's personal key (Q36 replaces the debug-key rule for the owner's devices: personal builds are now signed with a key only the owner holds).

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | tree clean, 8 commits ahead of origin; gh signed in; both devices connected; emulators scarlet_phone, scarlet_small, scarlet_tablet |
| 1 | Audit | done (re-run 2) | re-run 2: four agents; release manifest adds only USE_BIOMETRIC (Q35); A6 to A19, 13 fixed, 1 at hq-log, 2 dropped; Q41; locked-only key checked on an emulator with no screen lock (Android 16, as both devices); 71 tests, all build types pass the allowlist. Earlier:  release manifest permissions match 0.2.1 (VIBRATE, receiver permission); allowBackup false; four agents (spec, rules, manifest, data); 9 findings, 4 questions; the rotation answer had its own slice check |
| 2 | Code review | running (re-run 2) | earlier:  five agents (typing, storage, UI, security, Android pass); 12 findings, 3 questions; the R10 answer had its own slice check (clean, two notes fixed); 49 unit tests, lint no errors |
| 3 | Cleanup | pending (re-run 2) | earlier:  /simplify (reuse, simplification, efficiency, altitude agents); no TODOs, commented-out code or unused resources added; TUNING false in release; release notes drafted (Q8), README line (Q9), C1 answered (Q10); 49 unit tests, lint no errors |
| 4 | QA | pending (re-run 2) | earlier:  pass 3 after S1 (stage 5): clean (49 tests, no lint errors or new warnings, three builds, release APK checks unchanged). Pass 1: QA1 fixed, QA2 asked (Q11); pass 2 clean: 49 release unit tests, lintRelease no errors and no new warnings, release/personal/smoke built, release APK 0.2.2 (5), not debuggable, permissions as 0.2.1, 4,524,313 bytes (0.2.1: 4,451,441, +1.6%), license texts in assets/licenses (C2 done) |
| 5 | Smoke test | pending (re-run 2) | earlier:  Part 1 (emulators) and Part 2 (the owner: "tests pass"); the renamed build checked by the owner: "looks right" |
| 6 | Ship | on hold | pushed; repo renamed to keyward; the owner held signing for slice 7 (your words, saved words, teal Enter); Stages 1 to 5 re-run for it |
| 7 | Wrap-up | pending | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| A6 | 1 (re-run 2) | mechanical | docs/release-0.2.2/RELEASE-NOTES.md | names rule and "Forget learned words" now wrong; slices 7 and 8 missing | rewritten, shown to the owner word for word | stage 1 r2 |
| A7 | 1 (re-run 2) | mechanical | README.md:12,19 | learning line incomplete; install line says debug key | rewritten, shown to the owner word for word | stage 1 r2 |
| A8 | 1 (re-run 2) | question | SettingsActivity.kt Learn my words | description reads as if one use is enough (Q29: three) | Q41: "Mention three uses" | stage 1 r2 |
| A9 | 1 (re-run 2) | mechanical | WordsScreen.kt | Save shown while the list is refused (always fails) | hidden then (Status.canKeep) | stage 1 r2 |
| A10 | 1 (re-run 2) | mechanical | docs/KEYS-DECISIONS.md | Keyward dictionary fix, add-from-page and the slice 8 build guards had no row | Q40 and rows added | stage 1 r2 |
| A11 | 1 (re-run 2) | mechanical | SettingsActivity.kt Pill KDoc | said delete actions are the only scarlet outline | reworded | stage 1 r2 |
| A12 | 1 (re-run 2) | mechanical | scarlet-hq DECISIONS.md D-018 | superseded by Q36; Q27 to Q41 not mirrored | at hq-log (wrap-up) | |
| A13 | 1 (re-run 2) | mechanical | keys/build.gradle.kts guard | didn't catch new startup initializers or backup/network application settings | both checked | stage 1 r2 |
| A14 | 1 (re-run 2) | mechanical | install.ps1 -NoBuild | signed a missing or stale APK with a misleading error | checks it exists, shows its time | stage 1 r2 |
| A15 | 1 (re-run 2) | mechanical | WordStore.saved, UserWords | the upgrade retired the old key even while a set-aside list (Q2, Q13) might need it | old keys kept while a set-aside list exists; Forget lets them go | stage 1 r2 |
| A16 | 1 (re-run 2) | mechanical | WordStore.load | a locked-only key refusing for good while unlocked left learning silently stuck | after 10 in a row the list is refused this run, Settings shows the notice, the file stays | stage 1 r2 |
| A17 | 1 (re-run 2) | mechanical | WordStore.write | folder not synced after the rename, just before old keys are retired | folder fsync after the rename | stage 1 r2 |
| A18 | 1 (re-run 2) | mechanical | KeysService | the format 3 rewrite and key move only saved with learning on | saves whenever the user's words are in use | stage 1 r2 |
| A19 | 1 (re-run 2) | check | locked-only key on a phone with no screen lock | Android 14 had issues with such keys without a lock | emulator, Android 16, no lock: words load cold and after screen off and on | |
| dropped | 1 (re-run 2) | | count 2 words demoted on upgrade | answered by S7-2 ("Start fresh at 3 uses") | | |
| dropped | 1 (re-run 2) | | downgrade to 0.2.1 | a release can't be downgraded without an uninstall, which wipes the data anyway | | |
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

## Slice 6 and the rename (2026-10-12, after /prep resume)

The owner asked to fold in what was waiting and rename to Keyward in this release (KEYS-DECISIONS Q16 to Q26; Q17 supersedes this report's Q1). Slice 6 built with its own slice check (review and audit agents; wording settled in Q23 to Q26). Stages 1 and 2 for slice 6 are those two agents. QA pass 4 in full: 51 unit tests, lintRelease no errors and no new warnings, release/personal/smoke built, release APK label "Keyward", 0.2.2 (5), permissions as 0.2.1, licenses in assets. Emulator (scarlet_phone, smoke copy, light and dark): Keyward header with the publisher line, "Ready. Keyward is on and in use.", KEYWARD on the space bar (preview and keyboard), preview words Your / Keyward / keyboard, licenses list; no crashes. Screenshots `phone-*-keyward-*.png`. Not checked on screen: the TalkBack accent actions (an IME's actions menu can't be scripted with adb); code-reviewed and unit-tested.

## Smoke test, Part 2 (the owner's devices)

Checklist `scarlet-hq/projects/scarlet-keys/0.2.2-DEVICE-TESTS.md` on both devices with 0.2.2+2c21cbe. The owner, 2026-10-12: "tests pass". The renamed build 0.2.2+aa2face is installed on both devices (Keyward still the selected keyboard); its one name line is the owner's to check.

## Ship

| Step | Offered | Choice | Result |
|---|---|---|---|
| Install on devices | yes | the owner: "install the renamed build" | 0.2.2+aa2face on both devices; Keyward still the selected keyboard |
| Push | yes | the owner: "push and rename the repo" | main pushed (3a10263..17d62d8) |
| Repo rename | yes | same | TheScarletEditor/scarlet-keys renamed to TheScarletEditor/keyward; local remote updated |
| Signed public build | | | |
| GitHub release (draft) | | | |
| Website update | | | |

## Wrap-up

- Backups:
- STATUS-AUDIT updated:
- hq-log:
