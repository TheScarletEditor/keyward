# Scarlet Keys backlog

Owner-reported bugs and wants waiting for a version. Nothing here is scheduled until the owner picks it into a plan (then it moves into that version's plan and this entry points there). Mirrored in scarlet-hq `projects/scarlet-keys/IDEAS.md`.

## Bugs reported by the owner (2026-10-12)

Owner's words: "backspacing twice in a row removes the spaces between prior words. Adding a period sometimes erases the word it punctuates. The letter i doesnt autocorrect midsentence."

| # | Bug | First guess, before fixing (real causes and fixes: `KEYS-0.2.2-PLAN.md` slices 0b and 0c) |
|---|---|---|
| KB-1 | Two backspaces in a row remove the spaces between earlier words, not just characters. | The backspace-into-word re-edit and the one-backspace autocorrect undo: a second backspace may be read as "undo" again, or the re-edit may replace a range wider than the word. |
| KB-2 | Typing a period sometimes erases the word it ends. | The autocorrect commit on punctuation ("undo survives punctuation"): the composing word may be replaced by an empty or stale suggestion when the period commits it. "Sometimes" suggests it depends on whether a suggestion was showing. |
| KB-3 | A lone "i" is not corrected to "I" in the middle of a sentence. | Likely the same rule as D-020 ("im" to "I'm", already folded into 0.2.2): the always-capital I. Check whether lone "i" is skipped because it is a valid dictionary word or because mid-sentence words are never capitalized. |
| KB-4 | "Macbook" gets autocorrected (2026-10-12): newer brand names are missing from the word list. | Fixed the same day, slice 0c (names list, and capitals mid-sentence taken as names). |

Before fixing: reproduce each on an emulator and write a failing JVM test (SuggestTest or a new editing test). Never log typed text while debugging; the Recent fields diagnostic records field types only.

**Picked up 2026-10-12:** fixed as slice 0b of `KEYS-0.2.2-PLAN.md`, ships with 0.2.2. Causes and checks are there.

## From the 0.2.2 accessibility slice (proposed, 2026-10-12)

- KB-5: the accent row (long press on a vowel) has no TalkBack route; long press with TalkBack types the top-row digit. Proposed: offer the accents as TalkBack actions on the key.
- KB-6: the highlighted suggestion (the word space will put in) is spoken like the others. Proposed: "Suggestion: hello, will replace" (new wording, the owner's call).
- KB-7: on a phone the suggestion strip is 42 dp tall, under the 48 dp tap-area rule. Proposed: check on the phone whether 48 dp feels too tall; the owner's call.
