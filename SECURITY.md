# Security and privacy

Keyward is a keyboard, so it sees everything typed. It's designed so that nothing it holds can be used against the person typing.

## What Keyward does

- **No network.** The app has no INTERNET permission. The build fails if any dependency adds a permission, component, startup initializer, `<queries>` entry or network setting that isn't on an allowlist.
- **Single words only, and only if you turn learning on.** It never stores sentences or where something was typed. Nothing is learned from password fields, from fields that ask for no personalized learning (incognito tabs), or from its own search box.
- **Encrypted at rest.** Words are sealed with AES-256-GCM using an Android Keystore key created with `setUnlockedDeviceRequired(true)`. Old keys from earlier versions are replaced at the first save.
- **Minimal file.** Learned (3+ uses) and saved words are stored in alphabetical order. Words used once or twice are stored only as truncated SHA-256 fingerprints with a per-list 16-byte random salt kept inside the encrypted file.
- **Locked device.** While the device is locked, only the dictionary is used and nothing is learned. When the screen turns off, the word list is dropped from memory.
- **Out of sight.** The keyboard window uses `FLAG_SECURE` and is marked accessibility-data-sensitive. The word list in Settings asks for the device's fingerprint, face or PIN (when a screen lock is set), uses `FLAG_SECURE`, is blank in Recents, and is excluded from content capture and autofill.
- **No backups.** `allowBackup` is off and every domain is excluded from cloud backup and device transfer.
- **Supply chain.** Every dependency and build tool is pinned by SHA-256 in `gradle/verification-metadata.xml`, and the Gradle distribution is pinned by its official checksum.

## What Keyward can't protect against

- A device that is rooted or compromised, or code running as Keyward itself.
- The app you're typing into: it receives your text, as it would from any keyboard.
- Accessibility services marked as assistive tools (such as TalkBack), which need to read the keyboard to work.
- Someone holding both the decrypted word file and its salt could test common words against the fingerprints. The file can only be decrypted by code running as Keyward on an unlocked device.

## Reporting a problem

Please open a private security advisory on GitHub (Security tab, "Report a vulnerability") rather than a public issue.
