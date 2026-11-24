# Deployed to Google Play

| Date | Version | versionCode | Track state | main SHA | AAB sha256 |
|---|---|---|---|---|---|
| 2026-11-23 | 1.15.1 | 50 | production inProgress 20% (1.15.0 / 49 stays completed) | ae48491 (tag play/1.15.1-50) | bf34cbeb313700602e52a5280a28b489772a51d9e83c8693d925bcc343404531 |
| 2026-11-16 | 1.15.0 | 49 | production inProgress 20% (1.14.0 / 48 stays completed) | d1a9c39 (tag play/1.15.0-49) | eed0e3264d169f70f89cfcf6fb8385125acbad9a6257db9b40b80494a9e66a87 |

1.15.0: app in 26 languages (new cs el fi hu ro sv uk), Listen/Find/Ask, Settings, Recycle Bin and consent translated, locale-aware default recording titles, ASCII digits in SRT/VTT.
Built from main with `bundleRelease`; 234 unit tests pass. `lintDebug` reports 4 errors that exist since 1.14.0 and were not changed here (NewApi in TranscriptionService.kt:254 and VoiceQueryInput.kt:47, a MediaCodec flag in AudioEditorService.kt:98, an opt-in marker in AudioPlayerService.kt:129). Rollback: halt the rollout, or re-upload versionCode 48 as the completed release.

1.15.1: Traditional Chinese (zh-TW, zh-HK, plus values-b+zh+Hant script fallback), listed in locales_config. Built from main ae48491 with `bundleRelease`; 235 unit tests pass; debug build of the same tree checked on a Pixel 9 Pro in zh-TW, zh-HK, zh-CN and en, no FATAL/ANR. Rollback: halt the rollout, or re-upload versionCode 49 as the completed release.
