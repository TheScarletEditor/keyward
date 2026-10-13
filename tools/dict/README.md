# Keyward dictionary

`en.dict` (this folder) is the source word list, built from AOSP LatinIME `dictionaries/en_wordlist.combined.gz` (Apache 2.0).
Format: `word<TAB>frequency(0-255)<TAB>o-if-possibly-offensive`, sorted case-insensitively.
Offensive-flagged words are known (never autocorrected away) but never offered as suggestions.

## What ships: `en.bin`

The app ships a binary form, made at build time by the `:keys:buildDict` Gradle task (in `keys/build.gradle.kts`).
Nothing to run by hand: every build regenerates it from `en.dict` plus `extra.dict`.

`extra.dict` (same format, hand-kept by this project, not from AOSP) adds names the AOSP list predates: brands, products and apps such as MacBook, AirPods and Venmo. Add a line when Keys tries to correct a name it should know. Rules are in the file's header.

Layout (big-endian):

- magic `SKD1` (4 ASCII bytes), then the entry count (int32)
- entries already in lookup order: sorted by lowercase word, and for the same lowercase word the most frequent first
- each entry: `u8` length + lowercase UTF-8, `u8` length + display form (`0` when it's the same as the lowercase),
  `u8` frequency, `u8` flags (bit 0 = possibly offensive)

The app reads it in one buffered pass with no sorting. `BinaryDictTest` checks it behaves exactly like the text source.

## Rebuilding en.dict

Download `https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/en_wordlist.combined.gz?format=TEXT`,
base64-decode, gunzip, then filter as described in the 2026-10-07 commit.
