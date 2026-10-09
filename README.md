# Scarlet Keys

An Android keyboard that never touches the internet. By [The Scarlet Coder](https://thescarletcoder.com).

- Standard QWERTY, sized for tablets and phones.
- Offline word suggestions and autocorrect. One backspace undoes an autocorrect.
- Optional: learns your own words so it stops correcting them. Single words only, encrypted on the device, never from password fields. Off until you turn it on.
- Key sound and vibration are built in, both off by default.
- No network access at all. The build fails if any dependency tries to add it.
- No ads, no tracking, no account.

## Build and install

Needs the Android SDK and JDK 17 (Android Studio's bundled JDK works).

```
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

Then turn it on in Android's keyboard settings.

## Credits

- Word list: derived from the Android Open Source Project LatinIME dictionaries (Apache 2.0). See `keys/src/main/assets/DICTIONARY-NOTICE.txt` and `tools/dict/README.md`.
- Font: Inter, SIL Open Font License 1.1 (`licenses/Inter-OFL.txt`).

## License

Scarlet Keys is free software: you can use it, study it, change it and share it under the [GNU General Public License v3.0](LICENSE). If you share a changed version, it has to stay open under the same license.

Copyright (C) 2026 The Scarlet Coder. Fonts and the word list keep their own licenses, listed under Credits.
