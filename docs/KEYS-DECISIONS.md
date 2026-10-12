# Scarlet Keys: decisions

Against `docs/KEYS-0.2.2-PLAN.md`. Newest first. scarlet-hq mirrors these (hq-log).

## 2026-10-12 (the owner, names)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q7 | Brand and product names Keys doesn't know (KB-4) | "Add a modern names list (Recommended),Trust capitals mid-sentence (Recommended)" | `tools/dict/extra.dict` merged at build time; a word capitalised mid-sentence is never autocorrected. Slice 0c. | `modernNamesAreKnown`, `capitalMidSentenceIsAName`, `BinaryDictTest`. |

## 2026-10-12 (the owner, landscape)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q5 | Test the landscape fix (L1) where? | "Install on the Flip now (Recommended)" | Personal build on both devices before the emulator check (the emulator was in use by another session). | The owner texts in landscape on the phone. |
| Q6 | Where does the landscape fix ship? | "Fold into 0.2.2 (Recommended)" | Slice 0 of the 0.2.2 plan, ships with 0.2.2. | Emulator walk in landscape with the 0.2.2 release check. |

## 2026-10-11 (the owner, on the 0.2.2 plan)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q1 | Private details in the public repo (K6) | "Make generic (Recommended)" | Slice 1: the owner's name, pronouns, phone model, local paths and drive labels out of comments, scripts and CLAUDE.md, as Lyrics did. Pushed history stays. | The repo guard on every commit; the denylist run over all tracked files except `tools/dict` finds nothing. |
| Q2 | Learned words that can't be read (K1) | "Set aside + one line (Recommended)" | The unreadable file is moved aside, never overwritten; a new list starts; Settings shows one line saying so. | JVM test: unreadable file set aside and never overwritten. |
| Q3 | Accessibility scope (K4) | "Full support in 0.2.2 (Recommended)" | Slice 4: TalkBack for keys, suggestions and Settings choices. | JVM test: every key has a spoken label; emulator with TalkBack: type a word, pick a suggestion, change theme. |
| Q4 | The rest of the draft | "Accept as drafted (Recommended)" | 5,000-word cap, format version byte, Forget's messages ("Forgotten." / "Couldn't forget your words. Try again."), slice order 1 to 5. | Slice 2 and 3 tests as listed in the plan. |
