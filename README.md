# Keyward

An Android keyboard that never touches the internet. By [The Scarlet Coder](https://thescarletcoder.com).

## Download

**[Download Keyward from thescarletcoder.com](https://thescarletcoder.com/tools/keyward/)** (Android 14 or newer), with a step-by-step install guide with pictures.

Or download the file directly: [keyward.apk](https://github.com/TheScarletEditor/keyward/releases/latest/download/keyward.apk). This link always gets the newest version.

1. Open the downloaded file on your phone or tablet and tap Install.
2. If Android asks, allow your browser to install apps, then go back and tap Install again.
3. If you see a warning about an unknown app or developer, tap "Install anyway". Apps from outside the Play Store get this warning.
4. Open Keyward and follow its two setup steps to turn the keyboard on.

- Standard QWERTY, sized for tablets and phones, in light and dark (or following the system).
- Long-press for accents: é è ê ë, á à â ä, ó ò ô ö, ú ù û ü, í ì î ï, ñ, ç.
- On phones, the letter you're pressing shows above your thumb (can be turned off).
- Offline word suggestions and autocorrect. One backspace undoes an autocorrect (after a space or punctuation), and a word you take back stays as you typed it. Backspacing into a word picks the whole word back up.
- Works with TalkBack: every key, suggestion and setting is spoken.
- Optional: learns words you use often (after three uses), and keeps any word you save with "+ Save", so it stops correcting them. The Your words page in Settings lets you search, remove or add words, and asks for your PIN first. Single words only, encrypted on the device with a key that only works while the phone is unlocked; never from password fields or private boxes. Off until you turn it on.
- Hidden from screenshots and screen recording, with no letter bubble in password fields.
- Key sound and vibration are built in, both off by default.
- No network access at all. The build fails if any dependency tries to add it.
- No ads, no tracking, no account.

## Build it yourself

Needs the Android SDK and JDK 17 (Android Studio's bundled JDK works).
`install.ps1` builds the `personal` type: optimized like a release, and signed with your own personal key, whose password it asks for (create the key once with keytool; see the script's header). Debug builds only go on emulators (`-Debug`).

```
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

Then turn it on in Android's keyboard settings.

## Credits

- Word list: derived from the Android Open Source Project LatinIME dictionaries (Apache 2.0). See `keys/src/main/assets/DICTIONARY-NOTICE.txt` and `tools/dict/README.md`.
- Fonts: Inter and Fraunces, SIL Open Font License 1.1 (`licenses/Inter-OFL.txt`, `licenses/Fraunces-OFL.txt`).
- Jetpack Compose, AndroidX, the Kotlin standard library and kotlinx.coroutines (Apache 2.0, `licenses/Apache-2.0.txt`).

The same license texts ship inside the app: Settings, Open source licenses.

## License

Keyward is free software: you can use it, study it, change it and share it under the [GNU General Public License v3.0](LICENSE). If you share a changed version, it has to stay open under the same license.

Copyright (C) 2026 The Scarlet Coder. Fonts, libraries and the word list keep their own licenses, listed under Credits.
