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

## From the owner's 0.2.2 device tests (2026-10-14)

- KB-8: The Scarlet Coder card in Settings shows the website address but can't be copied. Owner, Q63: "Leave it for 0.2.2". Later: long press the card to copy the address, with a "Copied" message (the owner's call which version).
  - Built 2026-11-05 for 0.2.3 (owner: "Add both"): a long press on the card copies "thescarletcoder.com"; TalkBack offers it as "Copy the website address". A tap still does nothing. Android 13 and later show their own "Copied" confirmation, so Keyward adds none (it would show twice). Not yet seen on a device.

## From the common-issues audit (2026-11-04)

Fixed in the audit's draft PR: autocorrect leaving numbers, handles, web addresses and names alone; spaces and periods after a picked suggestion; double space only for two quick taps in plain text; phone, date, email and web address keys; apostrophe from ?123 back to letters; fast typing never swapping letters.

The owner's picks from the open questions (2026-11-04: "Build all except 5"), built in the same draft PR:

- KB-9 (built): words typed in capitals are left as typed ("CINC" stays "CINC"); with caps lock on they're still corrected.
- KB-10 (built): after typing on ?123, space or Enter goes back to letters, like Android's own keyboard.
- KB-11 (built): holding backspace for about two seconds speeds up to whole words.
- KB-12 (built): no autocorrect from an Enter that sends, searches or goes; a new line still corrects.
- KB-13 (skipped by the owner): a space typed by hand, then "," or ".", still leaves "word ,".
- KB-14 (built): long press a learned or saved word on the strip to remove it from your words, with a one-line confirm.
- KB-16 (built, reported by the owner 2026-11-04): long suggestions were cut off at the end, so a misspelling offered with "+ Save" looked right and got saved. Now a long word takes room from the others, then shrinks a little, then is shortened in the middle so its ending shows; a "+ Save" word is always shown whole, or offered without "+ Save".
- KB-17 (built, reported by the owner 2026-11-04): a period hit instead of space ("something.thit") kept the period and fixed only the second half ("something.this"). Now, when both sides are real words (or the second is a near miss), space turns it into "something this", and one backspace puts back what was typed. Never for web addresses, domains, emails, handles, file names, abbreviations, numbers or a word in capitals; a capitalised word splits too ("end.The" is "end The"), per the owner. Applied on space , ? ! ; in plain text with autocorrect on, not on Enter.

Still open:

- KB-15: wants, not bugs: slide on the space bar to move the cursor; an emoji key; long press "." for more punctuation.
