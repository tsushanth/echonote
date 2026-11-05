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

### Listen, Find, Ask (1.12.0)

All on-device features. Params are whitelisted in `FunnelTracker`; anything outside the list is sent as `other`.

| Event | When | Params |
|---|---|---|
| `listen_started` | the voice actually starts reading | `source`: `summary`, `segment`, `transcript`, `passage` |
| `listen_completed` | the last sentence was read to the end (not stopped) | `source` |
| `find_query` | a search settled (typed: 1 s after the last keystroke, at least 2 characters; voice: when speech was recognised) | `input`: `typed` or `voice`; `results`: `0`, `1`, `2_5`, `6_20`, `gt_20` |
| `find_result_opened` | a Find result was tapped to jump into the recording | `scope`: `all` or `recording` |
| `ask_query` | a question was submitted in Ask mode | `input`; `results` (same buckets) |
| `ask_result_opened` | a passage from Ask was opened in the recording | `scope` |
| `premium_row_viewed` | the "AI answer (Premium)" row was shown (only when Google Play billing is available) | none |
| `premium_sheet_opened` | the "coming soon" sheet behind that row was opened | none |

Never sent for these events: the query or question text, the passages, recording titles or ids, timestamps, the voice
engine name, the language. Voice search audio is processed by the phone's on-device recognizer only and is not stored.

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
