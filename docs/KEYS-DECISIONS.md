# Scarlet Keys: decisions

Against `docs/KEYS-0.2.2-PLAN.md`. Newest first. scarlet-hq mirrors these (hq-log).

## 2026-10-14 (the owner, after the 0.2.2 release: an easy download)

The owner: "how do we get an easily downloadable version of this to people? My less tech savvy friends will not understand the build part of the github instructions". Picks, word for word.

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q64 | A link that always gets the newest version | "Stable download link (Recommended)" | release.ps1 also writes dist/keyward.apk and its .sha256; every release carries keyward.apk, so .../releases/latest/download/keyward.apk always downloads the newest. 0.2.2 has it attached. | The link redirects to the latest release and serves an APK. |
| Q65 | Download steps at the top of the README | "Download line in README (Recommended)" | A Download section before everything else: the link and four install steps; "Build and install" renamed "Build it yourself". | README. |
| Q66 | A foolproof install guide | "Install guide with screenshots (Recommended)" | The website guide shows the unknown-apps prompt, the unknown-app warning and the two setup steps, with emulator screenshots (site session). | The site page. |
| Q67 | Where the website's Download button gets the app ("Can we host the download on our website? Github is still very technical for people, and i see no download button, just links and lines of code") | "Host it on our site (Recommended)" | The APK is served from the website itself (/downloads/keyward.apk) behind a large Download button; each release copies the new keyward.apk into the site and updates the version and SHA-256 there (site session). The GitHub stable link stays for GitHub visitors. | The site serves the APK with the Android app type; its SHA-256 matches the release. |

## 2026-10-14 (the owner, /prep resume)

/finalize 0.2.2 is paused at the owner's device testing (Stage 5). These are the owner's picks, word for word. Q62 and Q63 came from the owner's device test results the same day.

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q55 | Device testing of the final build (0.2.2+996420f) | "Still testing" | Nothing is pushed or signed. /finalize resumes at Stage 6 only after the owner reports the tests done. | The device-test list in scarlet-hq is ticked, or the owner says so. |
| Q56 | When the local folder becomes Keyward (brand-system D-036) | "Before the release" | The folder rename runs before /finalize Stage 6; Stage 6 runs from the Keyward folder. | Stage 0 of the resumed run sees the new folder. |
| Q57 | Rename scope | "Keyward only (Recommended)" | Only this app's folder and what points at it (device-version script, install script in the hub repo, backup config, drift check, global instructions, finalize and prep skill docs, scarlet-hq repo map and brief). The other apps wait for their own pass. | `assembleDebug` passes in the new folder; the version script and drift check find it. |
| Q58 | The rename spec wants the repo pushed first; this repo is 24 commits ahead and the push belongs to the release | "Rename unpushed (Recommended)" | The rename runs with the commits unpushed (they move with the folder). The push stays in Stage 6. An exception to the spec's preflight, recorded here. | `git log` in the new folder shows `e95592d` or later, still ahead of origin. |
| Q59 | Backup gate for the rename (no drive verified yet) | "Skip, recorded (Recommended)" | Explicit skip for the folder rename, recorded in the hub's STATUS-AUDIT. | The STATUS-AUDIT skip line. |
| Q60 | When the rename runs | "Now, while you test (Recommended)" | It runs now, from a session rooted outside this folder; no device install. | No install in that session. |
| Q61 | SECURITY.md and private vulnerability reporting (proposed by Claude 2026-10-12) | "Add at the ship step (Recommended)" | At Stage 6: the drafted SECURITY.md lands as a docs-only commit before the push (no QA re-run, no app code), and GitHub private vulnerability reporting is turned on with the owner's OK. | The file in the repo; the setting on GitHub. |
| Q62 | Screenshots on the owner's phone: the whole screenshot is refused ("this app doesn't allow screenshots") instead of a black keyboard | "Keep it, fix the wording (Recommended)" | No code change. Release notes say "hidden from screenshots, screen recordings and screen sharing (some phones block the whole screenshot while the keyboard is up)"; README and Settings already say hidden. | grep: no "blacked out" in the release notes. |
| Q63 | Copying the website address from The Scarlet Coder card | "Leave it for 0.2.2 (Recommended)" | Card stays information only (D-026). Long press to copy is KB-8 on the backlog. | `KEYS-BACKLOG.md` KB-8. |

## 2026-10-12 (the owner, slice 8: locked down)

The owner, before the last build: "this will be the last build unless I find something to fix. Let's make sure its security, battery use, everything is optimized and the best version it can be. I want this thing locked down so no other app or ai or anything can use it against me somehow". A security review and a battery review ran; these are the owner's answers.

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| (slice 8) | No visible change, under the same request | (the owner's request above) | The EmojiCompat startup initializer removed; a manifest allowlist that fails the build on any other permission, component, startup initializer, `<queries>` or backup or network setting; Gradle dependency verification (sha256). | Build. |
| Q31 | The words file keeps every word typed, in last-used order | "Thats bad. It makes me not want to store words, but then the user can't delete it if they accidentally add a wrong word. So the solution is no custom words, but then what good is it? Can we encrypt it somehow or protect it better?" | Led to Q32. | |
| Q32 | Fingerprints for words used once or twice, A to Z for the rest | "Yes, fingerprints + A to Z (Recommended)" | Format 3: learned and saved words as text, A to Z; words used once or twice only as salted hashes; no order kept. | WordStoreTest. |
| Q33 | While the phone is locked | "Also lock the key itself" | Locked: dictionary only, nothing learned, the list dropped from memory; a new key generation with unlockedDeviceRequired, older keys retired after the first good save. | Emulator: lock, type, unlock. |
| Q34 | Password characters on screen | "Always hide from capture" | The keyboard window is FLAG_SECURE everywhere; no bubble or accent row in password fields. | Screenshot is black. |
| Q35 | Your words page exposure | "Also ask for your PIN" | Opening Your words asks for the device PIN, fingerprint or face (when the device has one), which needs the USE_BIOMETRIC permission; the page is FLAG_SECURE, blank in Recents, no content capture. | Emulator. |
| Q36 | Signing of the owner's builds | "Also a new personal key" | install.ps1 refuses debuggable builds on the owner's devices; a personal key the owner creates and types the password for; one uninstall per device. | Owner. |
| Q37 | Incognito and no-suggestion boxes | "Dictionary only there (Recommended)" | No-learning fields: dictionary only, never the user's words. NO_SUGGESTIONS fields: no suggestions. Refines S7-4. | Emulator. |
| Q54 | Release notes and Learn my words | "Add it (Recommended)" | The "Save a word" and "learned after three uses" lines say both need Learn my words on (off by default). | Text. |
| Q53 | The old "Scarlet Keys" GitHub releases | "Rename to Keyward" | At the ship step, with the 0.2.2 release: retitle v0.1.0 to v0.2.1 as "Keyward 0.1.0" and so on (assets keep their file names). The owner: "Scarlet Keys was always a working/code name and I guarantee no one but me has used it yet" (6 downloads in total). | GitHub. |
| Q52 | The old name in what ships | "Drop both lines (Recommended)" | RELEASE-NOTES.md no longer starts with the rename line; README no longer says "Formerly Scarlet Keys." | Text. |
| Q51 | "phone" on a tablet | "Use \"device\" (Recommended)" | "Your words are encrypted on this device, with a key kept in Android's protected key store that only works while the device is unlocked." | Screenshot. |
| Q50 | Privacy line about the key | "Reword (Recommended)" | "Your words are encrypted on this phone, with a key kept in Android's protected key store that only works while the phone is unlocked." Also from the slice check: version and tagline in the soft grey (contrast), icons drawn once, the raven above the text at large font sizes. | Screenshot. |
| Q49 | Link colour on the maker card | "Scarlet, like Sudoku" | thescarletcoder.com in scarlet, in shades that read as text (#C40411 light, #FF4D57 dark). | Screenshot. |
| Q48 | Tapping the maker card | "Nothing, just the card" | Information only; no link out of the app. | Code. |
| Q47 | About line | "Use that line (Recommended)" | About: Keyward, the version, "A private keyboard for Android. Free and open source." | Screenshot. |
| Q46 | Privacy section | "Add as shown (Recommended)" | "Your privacy" section with the five points as drafted; Settings doubles as the About page; The Scarlet Coder card at the bottom (the owner: "its also doubling as our About, so we should add a bit, which should be at the bottom also with my usual card we put in Sudoku"). Also the Feel spacing evened out ("the spacing under Feel in the settings is all different and looks wrong"). | Screenshot. |
| Q42 | Accessibility apps reading words | "Hide from them (Recommended)" | The keyboard panel, and Settings while Your words is open, are accessibility-data-sensitive: only accessibility tools (TalkBack, Switch Access) read them. | Device. |
| Q43 | Release notes fingerprint claim | "Reword (Recommended)" | "Your words file keeps no typing order, and words used only once or twice aren't stored as text, only as scrambled fingerprints." | Text. |
| Q44 | Dark scarlet outline | "Slightly brighter in dark (Recommended)" | #F0323E in dark (3.6:1), brand scarlet in light. | Screenshot. |
| Q45 | Stage 2 wording | "Use as written (Recommended)" | "Couldn't read your words just now. Open this page again in a moment." / "Couldn't remove "x". Try again." / the script messages as shown. | Text. |
| Q41 | Learn my words description | "Mention three uses (Recommended)" | "Learns words you use often (after three uses), so it stops correcting them. Single words only, encrypted on this device. Never from password fields or fields an app marks as private, like incognito tabs." Release notes and README text approved as shown ("Use as written (Recommended)"). | Screenshot. |
| Q40 | Keyward corrected to "keyword" | "I beg so many of your pardons, the app wants to correct Keyward to Keyword. Please fix, lol" | Keyward added to tools/dict/extra.dict, an exception to its one-letter rule. | SuggestTest. |
| Q39 | Locking before Keyward saves | "Drop it anyway (Recommended)" | When the screen goes off or the phone locks, the list always leaves memory; words used in the last seconds before an instant lock may not be remembered. | WordStoreTest. |
| Q38 | The personal build's recent-fields list | "Keep, but protect it" | Stored in credential-encrypted storage after unlock, skipping private and password fields; the old copy deleted. | Code. |

## 2026-10-12 (the owner, slice 7 check)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| S7-1 | Forget all and words used once or twice | "Always shown, clears all (Recommended)" | Forget all shows whenever anything is kept; its confirm names the quiet words when there are any. | Emulator. |
| S7-2 | Old counts after the 3-use rule | "Start fresh at 3 uses (Recommended)" | Format 0 and 1 counts carry over as they are; a word taken back before can be taken back once more and is then saved. | WordStoreTest. |
| S7-3 | Other scarlet outlines | "Text box teal, steps stay" | Focused text boxes outline in teal; the not-done set-up steps stay scarlet. | Screenshot. |
| S7-4 | Saved words with learning off | "Saved words still work (Recommended)" | Saved words are never corrected in any field with suggestions; nothing new is learned or offered; the page says so. | Emulator. |

## 2026-10-12 (the owner, slice 7: your words)

| # | Question | Answer (word for word) | Effect | Check |
|---|---|---|---|---|
| Q27 | Layout for the words list | "A: Your words page (Recommended)" | A "Your words" card in Settings opens a page with search, the list, remove and Forget all; adding a word from the search box, as Draft A showed ("Search, remove or add."). | Emulator walk. |
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
