# ClearVoice Android: activation funnel metrics

Transport: Firebase Analytics (already in the app and initialised in `EchoNoteApplication`). No new SDK, no
new network endpoint. The app is completely free (since 1.9.3), so there are no paywall or purchase events;
this funnel measures activation and sharing, the numbers any later monetisation decision needs.

## Events

Automatic (Firebase, no code): `first_open`, `session_start`, `app_open`, `screen_view`, `app_update`.

Custom, sent from `service/Funnel.kt`:

| Event | When | Params |
|---|---|---|
| `first_recording_started` | first time a recording starts, once per install | none |
| `recording_started` | every recording start | none |
| `first_recording_completed` | first time a recording is saved, once per install | `duration` bucket |
| `recording_completed` | every saved recording | `duration`: `lt_10s`, `10s_1m`, `1_5m`, `5_30m`, `gt_30m` |
| `transcription_started` | user starts transcribing | none |
| `transcription_completed` | transcript produced | none |
| `first_transcription_completed` | first transcript, once per install (the value moment) | none |
| `transcription_failed` | transcription error | `error_code`: `file_missing`, `no_speech`, `no_audio`, `low_memory`, `model_download`, `engine_unavailable`, `other` |
| `first_share` / `share` | audio or transcript sent through the share sheet or export | `what`: `recording` or `transcript` |

Funnel in the Firebase console: `first_open` -> `first_recording_started` -> `first_recording_completed` ->
`first_transcription_completed` -> `first_share`.

Not collected: audio, transcript text, titles, file names or paths, free-text error messages (messages are mapped
to the fixed codes above), locations, user ids. "First" events use a local flag, not an identifier.

## Play Data safety

No new data types: the events fall under "App activity > App interactions" and diagnostics that Firebase
Analytics already collects, and which must already be declared because the SDK ships in 1.11.1. The app also
contains the Meta (Facebook) and TikTok Business SDKs and declares `AD_ID`; the owner should confirm the Data
safety form discloses advertising-ID collection and sharing for them, since the app shows no ads and runs no ad
campaigns. Removing those SDKs would shrink the disclosure; that is a separate decision.

## Reading the data

Firebase console > Analytics > Events / Funnels (events appear after about a day; DebugView is immediate on a
device with `adb shell setprop debug.firebase.analytics.app <package>`).
