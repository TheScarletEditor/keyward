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
| 2 | Code review | done (re-run 2) | the owner asked mid-stage for an About and privacy section and even Feel spacing (Q46 to Q50), built as its own slice with a slice check (4 findings: 3 fixed, 1 asked as Q50);  five agents (storage, typing, Settings, security, Android); R13 to R34, 18 fixed, 3 asked (Q42 to Q44), wording Q45; 72 tests, every build strict-verified, lint no errors. Earlier:  earlier:  five agents (typing, storage, UI, security, Android pass); 12 findings, 3 questions; the R10 answer had its own slice check (clean, two notes fixed); 49 unit tests, lint no errors |
| 3 | Cleanup | done (re-run 2) | /simplify (reuse, simplification, efficiency, altitude); C5 to C9 applied, two groups skipped (see table); no TODOs, commented-out code or em dashes; TUNING false in release; 72 tests, lint no errors. Earlier:  /simplify (reuse, simplification, efficiency, altitude agents); no TODOs, commented-out code or unused resources added; TUNING false in release; release notes drafted (Q8), README line (Q9), C1 answered (Q10); 49 unit tests, lint no errors |
| 4 | QA | done (re-run 2) | pass 2 after S2 (stage 5): clean (72 tests, lintRelease no errors, three builds, APK checks unchanged). Pass 1 clean: 72 release unit tests (--rerun-tasks, strict dependency verification), lintRelease no errors and no warnings on lines changed since the hold, release/personal/smoke built, release APK 0.2.2 (5), not debuggable, permissions 0.2.1 plus USE_BIOMETRIC (Stage 1), 4,557,265 bytes (0.2.1: 4,451,441, +2.4%), licenses, dictionary and baseline profile in the APK. Earlier:  pass 3 after S1 (stage 5): clean (49 tests, no lint errors or new warnings, three builds, release APK checks unchanged). Pass 1: QA1 fixed, QA2 asked (Q11); pass 2 clean: 49 release unit tests, lintRelease no errors and no new warnings, release/personal/smoke built, release APK 0.2.2 (5), not debuggable, permissions as 0.2.1, 4,524,313 bytes (0.2.1: 4,451,441, +1.6%), license texts in assets/licenses (C2 done) |
| 5 | Smoke test | done (re-run 2), 2026-10-14 | The owner is testing the final build (0.2.2+996420f, personal key) on the phone and tablet for a day or two before anything is announced; checklist: scarlet-hq projects/scarlet-keys/0.2.2-DEVICE-TESTS.md. Still to do after it: the phone emulator gets the personal-key build (it has a debug-key test build of the same code), then Stage 6 (push 23 commits, the owner signs, signature check against 0.2.1, GitHub release, old releases renamed (Q53), private vulnerability reporting if SECURITY.md is added, website in its own session) and Stage 7. Launch drafts (not in the repo): a private launch-kit page. | Part 1 (emulators, optimized build signed with the debug key for the emulator only): upgrade from 0.2.1 kept Dark, Large keys, the bubble, learning, a 3-use word, and a 2-use word as a fingerprint (learned at its next use, kept across a screen off, a restart and the move to the new key); 200% text on the new section; small phone at 1.3x and tablet, light and dark; accessibility order of the new section; S2 found and answered (Q51); not checked on an emulator: the letter bubble in password fields (the keyboard is blacked out in screenshots) and real biometrics, both on the owner list. Earlier:  Part 1 (emulators) and Part 2 (the owner: "tests pass"); the renamed build checked by the owner: "looks right" |
| 6 | Ship | done (2026-10-14) | pushed; repo renamed to keyward; the owner held signing for slice 7 (your words, saved words, teal Enter); Stages 1 to 5 re-run for it |
| 7 | Wrap-up | done (2026-10-14) | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| S2 | 5 (re-run 2) | question | SettingsActivity Your privacy | "on this phone" and "the phone is unlocked" on a tablet | Q51: "device" | stage 5 r2 |
| C5 | 3 (re-run 2) | mechanical | WordStore | fingerprint made a digest per call and its own hex loop; four copies of the wipe; startLoaded repeated listed(); entries() rescanned; save sorted under the memory lock; remove's flag chain; learnable() forwarder | one digest per salt and hex(); wipeWords(); listed(); entries() from the learned cache; sort after the lock; one expression; Words.isWord | stage 3 r2 |
| C6 | 3 (re-run 2) | mechanical | KeysService | bubble and accents set twice per field with an order dependency; UserLookup hand-written components; known() computed when unused; an unneeded local | decided in applyLook from passwordField; data class; lazy; removed | stage 3 r2 |
| C7 | 3 (re-run 2) | mechanical | SuggestionStrip | chip width measured on every draw; colour and baseline set twice | measured once; set once | stage 3 r2 |
| C8 | 3 (re-run 2) | mechanical | WordsScreen, LicensesScreen, SettingsActivity | two-tap question and live-region blocks written twice; card look in four places; search filtering on every recomposition; MakerText indentation; unused imports | Question, Announce, Modifier.card; remembered; fixed | stage 3 r2 |
| C9 | 3 (re-run 2) | mechanical | KeyTheme LOCK, Suggest comment, UserWords Status, install.ps1, release.ps1 | copied path; "without boxing" wrong; unused defaults; adb devices parsed twice; single-use flags | LOCK_SHACKLE reused; comment fixed; defaults removed; once; direct checks | stage 3 r2 |
| skipped | 3 (re-run 2) | | learned cache rebuilt per word (efficiency) | a narrower cache key would change the order of tied suggestions; not a cleanup | | |
| skipped | 3 (re-run 2) | | pickFor keyed by seq, one enum for the three word switches, saveSlot plumbing, the key-generation offset, shared script code (altitude) | restructures of correct, reviewed code this close to release; filed for later | | |
| R13 | 2 (re-run 2) | mechanical | KeyboardView.stopTouches, ACTION_UP | the letter bubble could stay up showing a password character after focus moved mid-touch | bubble always closed on lift and on hide; touches reset | stage 2 r2 |
| R14 | 2 (re-run 2) | mechanical | SuggestionStrip.show, KeysService.pick | "+ Save" could save a word that appeared under a finger already down | a press is dropped when its slot changes; save only for the word as typed | stage 2 r2 |
| R15 | 2 (re-run 2) | mechanical | KeysService pickFor | a stale fix from another field or before a save could apply | cleared on pick, undo, field start, screen off | stage 2 r2 |
| R16 | 2 (re-run 2) | mechanical | KeysService screen receiver | after a screen timeout without a lock, saved words stayed unloaded in the open field | reloaded on screen on and unlock; kept, undo and strip cleared on screen off | stage 2 r2 |
| R17 | 2 (re-run 2) | mechanical | SuggestionStrip chip | "+ Save" in sp overflowed its pill and squeezed the word at large text | sized in dp like the strip words; pill fits the text | stage 2 r2 |
| R18 | 2 (re-run 2) | mechanical | SettingsActivity.confirmItsYou | a rotation during the PIN prompt lost the unlock and held the old screen | prompt cancelled with the screen | stage 2 r2 |
| R19 | 2 (re-run 2) | mechanical | WordsScreen confirm rows | TalkBack focus lost when Remove or Forget all turned into the question | focus moves to the question, announced | stage 2 r2 |
| R20 | 2 (re-run 2) | mechanical | WordsScreen messages | first results possibly silent for TalkBack; "Forgotten." stayed after later changes | messages in always-present live regions; "Forgotten." cleared on the next save or remove | stage 2 r2 |
| R21 | 2 (re-run 2) | mechanical | UserWords remove/keep, WordsScreen | after a passing read error the page showed "No words yet." and reported saves and removes that didn't happen | Status.loaded; the page says it couldn't read; remove and save fail honestly | stage 2 r2 |
| R22 | 2 (re-run 2) | mechanical | WordStore.save | a locked-only key refusing every save while unlocked lost learning silently | counted like reading (A16): refused with the notice | stage 2 r2 |
| R23 | 2 (re-run 2) | mechanical | SettingsActivity | words stayed in memory after Your words when Keyward wasn't the active keyboard | dropped when the page closes | stage 2 r2 |
| R24 | 2 (re-run 2) | mechanical | KeysService FieldLog | prefs read and written on the keyboard's main thread (personal builds) | on the worker | stage 2 r2 |
| R25 | 2 (re-run 2) | mechanical | baseline-prof.txt | field-start classes missing; comment said every class | added (profile 3,701 to 3,780 bytes); comment fixed | stage 2 r2 |
| R26 | 2 (re-run 2) | mechanical | WordsScreen query | search text saved with the screen state | kept in memory only | stage 2 r2 |
| R27 | 2 (re-run 2) | mechanical | install.ps1, release.ps1 | signing passwords passed through an environment variable other same-user processes can read | apksigner asks for the password itself | stage 2 r2 |
| R28 | 2 (re-run 2) | mechanical | gradle-wrapper.properties, scripts | the Gradle download unchecked; verification could be turned off from outside the repo; build cache in releases | official sha256 pinned (matches the local copy); --dependency-verification=strict; release without the build cache | stage 2 r2 |
| R29 | 2 (re-run 2) | mechanical | build.gradle.kts guard | a startup initializer under any other name passed; testOnly not checked | exact initializer names; testOnly fails | stage 2 r2 |
| R30 | 2 (re-run 2) | mechanical | install.ps1 | a failed switch to Keyward wasn't reported | says so | stage 2 r2 |
| R31 | 2 (re-run 2) | question | WordsScreen, SuggestionStrip | accessibility apps that aren't screen readers (including AI agents) can read the words list and the strip | Q42: hide from them | stage 2 r2 |
| R32 | 2 (re-run 2) | question | RELEASE-NOTES.md:6 | "fingerprints that can't be read back" overstates it (common words can be guessed by whoever holds the decrypted file) | Q43: reworded | stage 2 r2 |
| R33 | 2 (re-run 2) | question | Pill danger, dark | scarlet outline 2.99:1 on dark (3:1 for controls) | Q44: #F0323E in dark | stage 2 r2 |
| R34 | 2 (re-run 2) | mechanical | WordsScreen search box | another keyboard (if active) could learn what's searched | the box asks every keyboard not to learn (IME_FLAG_NO_PERSONALIZED_LEARNING) | stage 2 r2 |
| dropped | 2 (re-run 2) | | first field right after unlock before the list loads | covered by R16 (reload on screen on and unlock) | | |
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

Re-run 2, final build 0.2.2+996420f (personal key) on both devices, 2026-10-14. The owner: "The rest pass". Screenshot line: the phone refuses the whole screenshot instead of a black keyboard (a manufacturer difference; protection works); Q62 kept it and fixed the release-notes wording (docs only, no QA re-run). Incognito line: the address bar and the Wikipedia search box ask for no suggestions, so Keyward gives none there (Q37, as decided); in an incognito text area the owner confirmed "yes it autocorrects". The card question became KB-8 (Q63). Optional lines (battery, TalkBack caps lock) not reported. Still to do from Part 1: the phone emulator moves from the debug-key test build to the personal-key build (same code), with the owner at the password prompt.

## Ship

| Step | Offered | Choice | Result |
|---|---|---|---|
| Install on devices | yes | the owner: "install the renamed build" | 0.2.2+aa2face on both devices; Keyward still the selected keyboard |
| Push | yes | the owner: "push and rename the repo" | main pushed (3a10263..17d62d8) |
| Repo rename | yes | same | TheScarletEditor/scarlet-keys renamed to TheScarletEditor/keyward; local remote updated |
| SECURITY.md | yes | the owner: "Commit as written (Recommended)" (Q61) | docs-only commit, no QA re-run |
| Push (2026-10-14) | yes | the owner: "Push (Recommended)" | main pushed (0a12989..7c8dfdd, 27 commits) |
| Phone emulator to the personal-key build | yes | the owner: "With the upgrade test (Recommended)" | scarlet_phone runs 0.2.2+996420f on the personal key, same signer as the owner's phone (install.ps1 -NoBuild -NewKey; three earlier tries had the wrong key's password) |
| Signed public build | yes | the owner ran release.ps1 (2026-10-14) | dist/keyward-0.2.2.apk: v3 signature valid, certificate SHA-256 b2388659...acacd matches 0.2.1; com.thescarletcoder.keys 0.2.2 (5), label Keyward, not debuggable; permissions VIBRATE, USE_BIOMETRIC and the app's receiver permission; SHA-256 297de1af...0a37d matches the .sha256 file; 4,570,616 bytes. Upgrade test on scarlet_phone: public 0.2.1 with Dark, Large keys, Learn my words and vibration on, then the signed 0.2.2 over it with adb install -r: every setting kept, Keyward still the selected keyboard, no crashes |
| GitHub release (draft) | yes | the owner: "Create the draft (Recommended)"; old titles: "When 0.2.2 goes live (Recommended)" | tag v0.2.2 on 7c8dfdd pushed; draft "Keyward 0.2.2" with keyward-0.2.2.apk and .sha256 |
| Publish | yes | the owner: "Publish the Keyward 0.2.2 release, mark it latest, and retitle the four old releases." | v0.2.2 published, Latest; v0.1.0 to v0.2.1 retitled "Keyward 0.1.0" to "Keyward 0.2.1" |
| Private vulnerability reporting | yes | the owner: "Turn it on (Recommended)" | enabled on the repo |
| Website update | yes | its own session, from the launch kit | not started |

## Wrap-up

- Backups: skipped for 0.2.2 by the owner on 2026-10-12 ("Skip for 0.2.2 (Recommended)"), recorded in the hub STATUS-AUDIT; no drive has been verified yet.
- STATUS-AUDIT updated: 2026-10-14 (Keys row, and a note that Keys now uses the personal key on the owner's devices).
- hq-log: 2026-10-14.
- Follow-ups: the website update; optional device lines (battery, TalkBack caps-lock hold) not reported.
