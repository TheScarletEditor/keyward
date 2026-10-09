# Scarlet Keys dictionary

`keys/src/main/assets/en.dict` is built from AOSP LatinIME `dictionaries/en_wordlist.combined.gz` (Apache 2.0).
Format: `word<TAB>frequency(0-255)<TAB>o-if-possibly-offensive`, sorted case-insensitively.
Offensive-flagged words are known (never autocorrected away) but never offered as suggestions.

Rebuild: download `https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/en_wordlist.combined.gz?format=TEXT`,
base64-decode, gunzip, then filter as described in the 2026-10-07 commit.
