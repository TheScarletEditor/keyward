# Finalize Scarlet Keys 0.2.2

Started 2026-10-12. Base: v0.2.1 (6a536fc). Diff: 38 files, +1992 -180 (before the version bump).
Mode: full. Public: yes.

Version 0.2.2 (versionCode 5) as decided in `docs/KEYS-0.2.2-PLAN.md` (slice 5, accepted with the plan, Q4). This run is slice 5 of that plan. Slices 0 to 4 each had a `/finalize --slice` check; their findings are in the plan.

## Stages

| # | Stage | Status | Notes |
|---|---|---|---|
| 0 | Preflight | done | tree clean, 8 commits ahead of origin; gh signed in; both devices connected; emulators scarlet_phone, scarlet_small, scarlet_tablet |
| 1 | Audit | done | release manifest permissions match 0.2.1 (VIBRATE, receiver permission); allowBackup false; four agents (spec, rules, manifest, data); 9 findings, 4 questions; the rotation answer had its own slice check |
| 2 | Code review | pending | |
| 3 | Cleanup | pending | |
| 4 | QA | pending | |
| 5 | Smoke test | pending | |
| 6 | Ship | pending | |
| 7 | Wrap-up | pending | |

## Findings

| Id | Stage | Label | Where | Finding | Done | Commit |
|---|---|---|---|---|---|---|
| A1 | 1 | question | scarlet-hq D-021 | Keys' public name is Keyward (decided today); 0.2.2 still says Scarlet Keys | Q1: rename in its own release | stage 1 |
| A2 | 1 | mechanical | docs/KEYS-DECISIONS.md Q15, plan slice 4 | Q15's shown list not recorded; plan named a "Numbers" label that doesn't exist | Q15 points at KeyLabels.kt at 38d0669; plan wording fixed | stage 1 |
| A3 | 1 | mechanical | docs/KEYS-0.2.2-PLAN.md:3,50 | status line and "Proposed:" out of date | updated | stage 1 |
| A4, A5 | 1 | mechanical | scarlet-hq keys IDEAS.md, DECISIONS.md D-019 | stale (KB-1 to KB-3 shipped, KB-4 to KB-7, Q5 to Q15 missing) | at hq-log (wrap-up) | |
| B1 | 1 | mechanical | docs/KEYS-BACKLOG.md:24 | named the owner's phone model | "the phone" | stage 1 |
| B2 | 1 | question | release.ps1:14-15 | keystore file name and alias in a public script (no password) | Q3: keep | |
| B3 | 1 | question | tools/dict/extra.dict | medicine brand names in a hand-picked public list | Q4: keep | |
| C1 | 1 | mechanical | keys/build.gradle.kts CopyLicenses | any file in licenses/ would ship | only `.txt` files | stage 1 |
| C2 | 1 | check | release APK | licenses in the release APK not yet seen | checked in QA (stage 4) | |
| D1 | 1 | mechanical + question | WordStore.save, UserWords | the key was replaced after any 3 failed saves, disk errors included, and a failed retry could strand the list | only seal failures count; Q2: safe rotation (key generations, old keys retired after a good save); slice check clean, one gap fixed (retire after every good save) | stage 1 |
| D2 | 1 | mechanical | WordStore.load | words from the file skipped the typing rule (a downgrade could leave a control character in a word) | cleaned and checked like typed words | stage 1 |
| D3 | 1 | mechanical | WordStore.clear | a folder that couldn't be listed counted as no set-aside files | Forget fails then | stage 1 |

## Questions

### Q1 (audit, A1)
D-021 makes Keys' public name Keyward; the brand notes keep the old name on the site and stores until the app ships the new one. Options: its own release (recommended), or rename in 0.2.2.
**Answer (2026-10-12):** "Its own release (Recommended)"
**Follow-up:** 0.2.2 ships as Scarlet Keys; the rename is a later release.

### Q2 (audit, D1)
How to replace a key that keeps failing. Options: safe rotation (recommended), or only through Forget with a Settings line.
**Answer (2026-10-12):** "Safe rotation (Recommended)"
**Follow-up:** key generations (keys_user_words_v1, then _g2, _g3); only seal failures count; reading tries every key; older keys go after a good save with the newest. Tests: aKeyThatFailsEverySaveIsRotatedAndNothingIsLost, diskFailuresNeverReplaceTheKey, aRotationWhoseSaveFailsStrandsNothing.

### Q3 (audit, B2)
release.ps1 names the keystore file and alias (no password). Options: keep (recommended), or read from environment variables.
**Answer (2026-10-12):** "Keep as is (Recommended)"

### Q4 (audit, B3)
Medicine brand names in tools/dict/extra.dict. Options: keep (recommended), or remove the six.
**Answer (2026-10-12):** "I dont actually use any one of those in my daily life, so if any makes an inference from it, they'll trip themselves up instead, which i like the sound of. Keep them"

## Ship

| Step | Offered | Choice | Result |
|---|---|---|---|
| Install on devices | | | |
| Signed public build | | | |
| GitHub release (draft) | | | |
| Website update | | | |

## Wrap-up

- Backups:
- STATUS-AUDIT updated:
- hq-log:
