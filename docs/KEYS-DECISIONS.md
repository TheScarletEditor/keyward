# Scarlet Keys: decisions

Against `docs/KEYS-0.2.2-PLAN.md`. Newest first. scarlet-hq mirrors these (hq-log).

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
