# Scarlet Keys: decisions

Against `docs/KEYS-0.2.2-PLAN.md`. Newest first. scarlet-hq mirrors these (hq-log).

## 2026-10-12 (the owner, slice 7: your words)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q27 | Layout for the words list | "A: Your words page (Recommended)" | A "Your words" card in Settings opens a page with search, the list, remove and Forget all. | Emulator walk. |
| Q28 | Accidental capitals | "Correct unless saved (Recommended)" | The names rule (Q7) is replaced: capitalised words are corrected unless saved or known; "+ Save" on the strip. Supersedes Q7. | SuggestTest; emulator. |
| Q29 | What the words list holds | "Learned + saved, shown apart (Recommended)" | Learned at 3 uses; saved kept until removed; one-off words not counted or listed. | WordStoreTest. |
| Q30 | Scarlet outlines | "Delete actions (Recommended)" | Remove and Forget all (and their confirms) outlined in scarlet; Enter teal. | Screenshots. |

## 2026-10-12 (the owner, slice 6 wording)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q23 | Settings preview's sample suggestions | "Your \| Keyward \| keyboard (Recommended)" | Preview strip shows Your / Keyward / keyboard, Keyward highlighted. | Screenshot. |
| Q24 | The word list notice's credit | "\"Keyward, formerly Scarlet Keys\" (Recommended)" | "...by tools/dict (Keyward, formerly Scarlet Keys, 2026-10-07)". | File. |
| Q25 | New spoken text (accents, "will replace") | "OK, only on real fixes (Recommended)" | Accent actions named by the bare character (capital with Shift); "Suggestion: X, will replace" only when space will swap in a fix. | KeyLabelsTest; review. |
| Q26 | Release notes TalkBack line | "Add accents (Recommended)" | "Works with TalkBack: every key, suggestion and setting is spoken, you type by lifting your finger, and accents are in the actions menu." | Release notes. |

## 2026-10-12 (the owner, /prep resume before the release)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q16 | Which waiting backlog items go into 0.2.2 | "KB-5 TalkBack accents (Recommended),KB-6 \"will replace\" (Recommended)" | Slice 6a and 6b; KB-7 stays on the backlog. | KeyLabelsTest; review. |
| Q17 | Keyward: own release or 0.2.2 | "Rename in 0.2.2" | Slice 6c; supersedes the FINALIZE-0.2.2 Q1 answer ("Its own release"). | grep for visible "Scarlet Keys"; screenshots. |
| Q18 | The space bar's mark | "KEYWARD (Recommended)" | WORDMARK = "KEYWARD". | Screenshot. |
| Q19 | Settings header | "Add the publisher (Recommended)" | "Keyward" / "A keyboard by The Scarlet Coder. No internet access, nothing typed is ever logged or sent anywhere." | Screenshot. |
| Q20 | Repo and file names | "Rename the repo too" | APK `keyward-<version>.apk`, release "Keyward <version>", GitHub repo renamed to `keyward` at ship (confirmed at that step). | Ship stage. |
| Q21 | Announcing the change | "\"Scarlet Keys is now Keyward.\" (Recommended)" | Release notes start "Scarlet Keys is now Keyward: same keyboard, same promise, a name of its own."; README "Formerly Scarlet Keys." | Release notes, README. |
| Q22 | The two-drive backup for 0.2.2 | "Skip for 0.2.2 (Recommended)" | Release without it; recorded in Command STATUS-AUDIT. | STATUS-AUDIT line. |

## 2026-10-12 (the owner, slice 4 wording)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q15 | What TalkBack says for each key, suggestion and Settings item | "Say \"Backspace\"" | As shown (the full list is `keys/src/main/java/com/thescarletcoder/keys/KeyLabels.kt` at 38d0669), with "Backspace" for the delete key. | `KeyLabelsTest`; TalkBack walk on the emulator. |

## 2026-10-12 (the owner, slice 3 mini-audit)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q14 | L3-4: preference files still read on the main thread when Settings opens | "Move them off too" | Slice 3b: settings, tuning values and "Recent fields" read off the main thread. | StrictMode (debug) shows no main-thread disk work in Settings. |

## 2026-10-12 (the owner, slice 2 mini-audit)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q12 | L2-2: a list from a newer Keys, or an unreadable list that couldn't be moved aside, stops learning with nothing shown | "Show line + Forget (Recommended)" | Settings shows "Your learned words were saved by a newer version of Scarlet Keys. This version leaves them as they are and won't learn new words." or "Your learned words couldn't be read, so Scarlet Keys isn't learning new words for now.", with "Forget learned words", which starts a new list. | `noticesAndForgetStartsANewList`. |
| Q13 | L2-1b: reading keeps failing with errors that look passing | "Set aside after 3 (Recommended)" | After 3 failed reads in a row while unlocked, the file is moved aside (never deleted) and the usual line shows. | `threePassingErrorsInARowSetTheFileAside`. |

## 2026-10-12 (the owner, slice 1b wording)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q11 | Slice 1b's text (Settings row, list page, license page, missing-file line, README line) and the extra GPL entry | "OK as written (Recommended)" | Text as shown; Scarlet Keys' own GPL is listed with the third-party licenses. | LicensesTest; emulator walk. |

## 2026-10-12 (the owner, slices 0 to 0c mini-audit)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q10 | L0-1: "i" before a period ("i.e.") | "Leave \"i.\" alone (Recommended)" | "i" followed by "." is never fixed; elsewhere "i" still becomes "I". | Emulator: "i.e." and "so i went" typed. |

## 2026-10-12 (the owner, slice 1 mini-audit)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q8 | L1-3: license text in the repo but not in the APK | "Add to 0.2.2 (Recommended)" | Slice 1b: license texts in the APK, an "Open source licenses" row in Settings. Lyrics gets the same in its next version. | Build or test fails if a `licenses/` file is missing from the APK assets; row checked on emulator and devices. |
| Q9 | L1-2: Credits wording | "Use that wording (Recommended)" | README Credits: "Jetpack Compose, AndroidX, the Kotlin standard library and kotlinx.coroutines (Apache 2.0, `licenses/Apache-2.0.txt`)." | README matches the APK's bundled libraries. |

## 2026-10-12 (the owner, names)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q7 | Brand and product names Keys doesn't know (KB-4) | "Add a modern names list (Recommended),Trust capitals mid-sentence (Recommended)" | `tools/dict/extra.dict` merged at build time; a word capitalised mid-sentence is never autocorrected. Slice 0c. | `modernNamesAreKnown`, `capitalMidSentenceIsAName`, `BinaryDictTest`. |

## 2026-10-12 (the owner, landscape)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q5 | Test the landscape fix (L1) where? | "Install on the [phone] now (Recommended)" (phone model left out of the public repo) | Personal build on both devices before the emulator check (the emulator was in use by another session). | The owner texts in landscape on the phone. |
| Q6 | Where does the landscape fix ship? | "Fold into 0.2.2 (Recommended)" | Slice 0 of the 0.2.2 plan, ships with 0.2.2. | Emulator walk in landscape with the 0.2.2 release check. |

## 2026-10-11 (the owner, on the 0.2.2 plan)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q1 | Private details in the public repo (K6) | "Make generic (Recommended)" | Slice 1: the owner's name, pronouns, phone model, local paths and drive labels out of comments, scripts and CLAUDE.md, as Lyrics did. Pushed history stays. | The repo guard on every commit; the denylist run over all tracked files except `tools/dict` finds nothing. |
| Q2 | Learned words that can't be read (K1) | "Set aside + one line (Recommended)" | The unreadable file is moved aside, never overwritten; a new list starts; Settings shows one line saying so. | JVM test: unreadable file set aside and never overwritten. |
| Q3 | Accessibility scope (K4) | "Full support in 0.2.2 (Recommended)" | Slice 4: TalkBack for keys, suggestions and Settings choices. | JVM test: every key has a spoken label; emulator with TalkBack: type a word, pick a suggestion, change theme. |
| Q4 | The rest of the draft | "Accept as drafted (Recommended)" | 5,000-word cap, format version byte, Forget's messages ("Forgotten." / "Couldn't forget your words. Try again."), slice order 1 to 5. | Slice 2 and 3 tests as listed in the plan. |
