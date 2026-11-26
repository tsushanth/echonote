# Deployed to Google Play

| Date | Version | versionCode | Track state | main SHA | AAB sha256 |
|---|---|---|---|---|---|
| 2026-11-25 | 1.15.2 | 51 | production inProgress 20% (1.15.1 / 50 stays completed) | fb3899a (tag play/1.15.2-51) | 6607efc63bd95dfd36807931be2f303cbc0af73da4c82bf972669d529a79a17f |
| 2026-11-23 | 1.15.1 | 50 | production inProgress 20% (1.15.0 / 49 stays completed) | ae48491 (tag play/1.15.1-50) | bf34cbeb313700602e52a5280a28b489772a51d9e83c8693d925bcc343404531 |
| 2026-11-16 | 1.15.0 | 49 | production inProgress 20% (1.14.0 / 48 stays completed) | d1a9c39 (tag play/1.15.0-49) | eed0e3264d169f70f89cfcf6fb8385125acbad9a6257db9b40b80494a9e66a87 |

1.15.0: app in 26 languages (new cs el fi hu ro sv uk), Listen/Find/Ask, Settings, Recycle Bin and consent translated, locale-aware default recording titles, ASCII digits in SRT/VTT.
Built from main with `bundleRelease`; 234 unit tests pass. `lintDebug` reports 4 errors that exist since 1.14.0 and were not changed here (NewApi in TranscriptionService.kt:254 and VoiceQueryInput.kt:47, a MediaCodec flag in AudioEditorService.kt:98, an opt-in marker in AudioPlayerService.kt:129). Rollback: halt the rollout, or re-upload versionCode 48 as the completed release.

1.15.1: Traditional Chinese (zh-TW, zh-HK, plus values-b+zh+Hant script fallback), listed in locales_config. Built from main ae48491 with `bundleRelease`; 235 unit tests pass; debug build of the same tree checked on a Pixel 9 Pro in zh-TW, zh-HK, zh-CN and en, no FATAL/ANR. Rollback: halt the rollout, or re-upload versionCode 49 as the completed release.

1.15.2 (2026-11-25): failure reports (#30). Built from `fb3899a` (#31, version bump and release note on top of `fd6c18a`) in a clean detached worktree with `bundleRelease`; the `ratingkit` and `crosspromokit` modules come from their local checkouts. targetSdk 36, Play Billing 8.0.0, upload-key signature, no permission added compared with the Play-generated APK of 50. 279 unit tests pass. `lintDebug` fails on the same 4 errors as before (lines unchanged since 1.15.1). Play read back: production 51 inProgress 0.2, 50 completed; beta, alpha and internal moved to 51 (they held 50, 50 and 41). Release note (en-US): "ClearVoice now sends us crash and failure reports, with no audio or transcripts, so we can find and fix problems."

- The build's `local.properties` held only `clearvoice.reporterKey` (registered in the failure reporter as `clearvoice`, app-failure-reporter #46; copy in Keychain `app-failure-reporter-clearvoice-key`; also added to the main checkout's `android/local.properties`).
- `clearvoice.ingestUrl` / `clearvoice.ingestKey` were deliberately NOT included, to match the live 1.15.1. Checked in the Play-generated APKs: 45, 48 and 49 contain the voice-contribution endpoint, 46, 47 and 50 do not, so "Contribute Voice Data" uploads nothing in 50 and 51. A build from the main checkout's `local.properties` would switch uploads back on; that needs the owner's decision.
- Data safety resubmitted through the API 2026-11-25 with "Crash logs" added (collected, not shared, not ephemeral, required, Analytics); CSV in ops-notes-private `play-data-safety/2026-11-25/`. Privacy page text for 1.15.2+ published the same day.
- NOT verified: any device run, a report arriving from a real phone, the Play-signed build.
- Rollback: halt the rollout; 1.15.1 (50) stays completed.

