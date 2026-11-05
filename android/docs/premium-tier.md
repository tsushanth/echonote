# ClearVoice Android: premium tier assessment (2026-11-04)

Decision input from the owner: keep most things free; add a small premium tier only for REALTIME features that
need server-side processing (they cost real money per minute); everything that runs on the device stays free.

**Finding: today the app has no server-side realtime feature, so there is nothing that rule allows us to charge
for.** Live transcription is on-device (Vosk). No premium code was added in this change; this document is the
inventory, the cost math, and the design to use if a hosted realtime feature is built. Section 5 lists what
would have to exist first.

All statements below were checked against `origin/main` (f3a4b5e) on 2026-11-04.

## 1. Inventory: every feature, on-device or server-side

| Feature | Where it runs | Evidence | Tier |
|---|---|---|---|
| Recording (foreground service, pause, bookmarks, widget) | device | `AudioRecorderService`, `RecordingForegroundService` | free |
| Live transcript while recording | **device** (Vosk model already on disk, second `AudioRecord`) | `LiveTranscriber`, `LiveTranscriptSession`; no network call anywhere in the live path | free |
| Fast transcription (Vosk) | device | `TranscriptionService` | free |
| Accurate transcription (Whisper small, NeMo FastConformer, SenseVoice) | device (model downloaded once) | `EnginePlan`, `SherpaTranscriber` | free |
| Speaker labels (diarization) | device (sherpa-onnx + TitaNet) | `SpeakerDiarizer` | free |
| Summaries | device (extractive; ML Kit summarizer was dropped) | `service/summary/*` | free |
| Transcript editing, uncertain-word marks | device | `TranscriptEdit`, `UncertainWords` | free |
| Audio editor, playback, folders, favourites, recycle bin | device | `AudioEditorService`, `data/local` | free |
| Export and share (audio, text, SRT/VTT) | device | `ExportFormat`, `SubtitleCues` | free |
| Read aloud | device (system TTS; network voices are filtered out) | `tts/SystemTtsEngine` | free |
| Location tag | Google Play services location and the system geocoder | `LocationService` | free, not our cost |
| Model downloads (Vosk from alphacephei, Whisper/NeMo/SenseVoice/TitaNet from Hugging Face and GitHub releases) | third-party CDNs, one time per model | `ModelDownloader`, `Recording.modelUrl` | free, not our cost |
| Voice-data contribution (opt-in, Wi-Fi only, audio + transcript) | our Cloudflare worker `voxkey-ingest` | `ContributionUploadWorker` | free; storage only, no per-minute compute |
| Analytics | Firebase Analytics, Meta and TikTok SDKs | `Funnel.kt`, `docs/metrics.md` | n/a |

There is no ClearVoice backend. `flyctl apps list` has no ClearVoice or EchoNote app, no Modal app is called from
this code, and the only first-party endpoint in the client is the opt-in ingest worker. A grep of the app source
for `websocket`, `wss://`, `deepgram`, `modal`, `fly.dev` finds nothing, and `git log -S` finds no such code in
history. The Modal app `realtime-stt-worker` (batch Whisper, $0.11 per audio hour) and the streaming STT in
ListenAI's backend serve other products; neither knows this package name (internal-docs `MODAL_INVENTORY.md`:
`realtime-stt-worker` has "no caller found").

So "restore the realtime gate" and "enforce entitlement on the realtime backend" have no object. Putting a
paywall on the existing live transcript would charge for an on-device feature, which the owner's rule excludes,
and would take away something users have had free since 1.9.3.

## 2. What Play currently holds (read-only check with the app's service account, 2026-11-04)

Three ACTIVE auto-renewing subscriptions, all with `newSubscriberAvailability: true` and a 3-day free-trial offer:

| Product | Base plan | US price |
|---|---|---|
| `clearvoice_premium_weekly` | weekly (P1W) | $4.99 |
| `clearvoice_premium_monthly` | monthly (P1M) | $9.99 |
| `clearvoice_premium_yearly` | yearly (P1Y) | $49.99 |

The task brief named only the monthly product. All three exist, and the listing copy still reads "Unlimited
recordings, Audio editing & transcription", which the app no longer gates. `voidedpurchases` returned nothing.
Risks for the owner, not changed here:
- Versions older than 1.9.3 still in the wild show the old paywall and can still sell these products (and the
  3-day trial) for features that are now free. Check the Play Console "Releases > Statistics" for the share on
  versions below 1.9.3 (the uploaded 1.9.3 code removed the paywall, but old installs do not update themselves
  if auto-update is off).
- Any legacy subscriber is still being charged $4.99 a week, $9.99 a month or $49.99 a year for nothing. RevenueCat
  project projd89ab256 would show the count, but the tools' key cannot read it. Cancelling or refunding is the
  owner's call and was not touched.
- Grandfathering must cover all three product ids, not only the monthly one.

## 3. Cost math for a HOSTED realtime transcription feature (hypothetical)

Inputs, from `internal-docs/cost/cost-model.md` unless noted:

| Input | Value |
|---|---|
| Hosted streaming STT vendor (Deepgram Flux) | $0.0065 per minute promo, **$0.0077 regular** (use regular) |
| Self-hosted streaming STT (Fly, zipformer / NeMo-480) | about $0.002 per call scaled to zero, but WER 8.3% / 15.4% vs 4.0% and it **failed the accuracy gate**; nothing deployed |
| Relay and idle overhead (gateway, retries, silence streamed) | assumption: +10% |
| **Cost per realtime minute used for pricing** | **$0.0085 ($0.51 per hour)** |
| Play commission on subscriptions | 15% |
| Reference: on-device live transcript today | $0 (Vosk WER 24% on English FLEURS in `EnginePlan`, so a hosted engine would be a real accuracy upgrade) |

Net revenue per subscriber per month and the minutes it covers:

| Price | Net per month after 15% | Break-even minutes (cost = net) | Cap at 50% of net |
|---|---|---|---|
| $3.99 monthly | $3.39 | 399 | 199 |
| $4.99 monthly | $4.24 | 499 | 249 |
| $34.99 yearly | $2.48 | 292 | 146 |
| $39.99 yearly | $2.83 | 333 | 166 |

If the vendor cost were $0.0100 per minute (promo ends and overhead is higher) the $3.99 break-even is 339
minutes and the yearly 248, so a 180-minute cap stays profitable in both.

### Proposed price and cap

- **$3.99 per month, $34.99 per year** (about 27% off twelve months), cap **180 realtime minutes per month**
  (3 hours), same cap on both plans.
- Worst case (a subscriber uses the whole cap every month): cost $1.53, which is 45% of the monthly net ($3.39) and
  62% of the yearly net ($2.48). Margin positive on both. A subscriber who never hits the cap costs less.
- Fair-use rule: minutes are metered on the server as audio seconds actually streamed (silence included, so the
  client should pause streaming when paused); at the cap the session ends with a clear message and on-device
  live transcript keeps working. Optional "buy 60 more minutes" is not proposed.
- No free trial on the new products: a trial costs real minutes (up to 180 minutes of vendor cost with no
  revenue). If a trial is wanted, cap trial usage at 20 minutes.

### Reality check on revenue

About 530 active devices and 47 installs a week. At 1% to 3% paying that is 5 to 16 subscribers, so net revenue of
roughly $17 to $54 a month before vendor cost, plus a new vendor dependency, a privacy-policy change (microphone
audio would leave the device, while the app is marketed as on-device) and a Data safety form update. This is a
small number against that scope. The honest recommendation is to **not build the hosted tier for revenue
reasons alone**; build it only if hosted realtime also serves another purpose (a feature users ask for, accuracy,
or shared infrastructure with ListenAI or Calldesk).

## 4. Design, if the owner decides to build it

### Free vs premium
- Free: everything in section 1, including the on-device live transcript, forever.
- Premium: only "Cloud live transcript" (a new, separate toggle next to the on-device one; the on-device one stays
  the default). Nothing else is gated.

### Play products to create (not created; owner action)
| Product id | Base plan id | Price (US) | Period | Offer |
|---|---|---|---|---|
| `clearvoice_realtime_monthly` | `monthly` | $3.99 | P1M | none |
| `clearvoice_realtime_yearly` | `yearly` | $34.99 | P1Y | none |

Grace period 7 days and account hold on, like the existing plans. Copy: "Cloud live transcript, 3 hours a month.
Recording, on-device live transcript, transcription, editing and export stay free." No claims about unlimited use.

### Grandfathering
Holders of `clearvoice_premium_weekly`, `clearvoice_premium_monthly` or `clearvoice_premium_yearly` that are
ACTIVE or in grace period get the same entitlement and cap as the new products. Nothing is deactivated or
refunded by the code.

### Client (not implemented)
Restore the minimal billing layer (Play Billing 8 is already a dependency; the BILLING permission and
`billing-ktx` stay as they are). Pre-9252f03 `BillingService.kt` (271 lines) is a reasonable base, but not its
paywall copy. Requirements:
- Product ids and the feature itself behind a remote/BuildConfig flag that is OFF by default; if products fail to
  load, hide the premium entry (never show a dead button).
- The upsell appears only when the user turns on "Cloud live transcript", never before the first recording,
  never as a blocking screen. Restore purchases in Settings. Plain error messages.
- Funnel events through `Funnel.kt` with no PII, audio or text: `paywall_shown{placement}`,
  `purchase_started`, `purchase_completed`, `purchase_failed{code}`, `restore_tapped`.

### Server gate (not implemented; must live in the hosted realtime backend)
Without it, the client check is bypassable. Required pieces, each with tests:
1. `POST /v1/session`: body has `purchaseToken` and `productId`; the server calls
   `purchases.subscriptionsv2.get` with its own service account and accepts only `SUBSCRIPTION_STATE_ACTIVE` or
   `SUBSCRIPTION_STATE_IN_GRACE_PERIOD`; the line item product id must be in the allowed set (the two new ids plus
   the three legacy ids). Expired, cancelled-and-lapsed, on-hold, paused, pending and unknown tokens are refused.
2. Short-lived signed session token (HMAC-SHA256, 5-minute lifetime, bound to a hash of the purchase token, not
   the token itself) used to open the realtime websocket.
3. Usage meter keyed by the hash of the purchase token (so no account system is needed): seconds streamed per
   calendar month (UTC), cap 180 minutes, enforced at open and every 10 seconds during the stream.
4. Fail closed: Google API error, bad signature, missing secret or meter store unavailable means no session and a
   clear client message; the on-device path is unaffected.
5. `ENFORCE_ENTITLEMENT` defaults to off, so deploying the server changes nothing for existing app versions
   until the owner flips it after the new app is released. With it off the server still writes the meter
   (observe-only) so the cap can be sized from real data.
Secret names the owner would set on the backend: `PLAY_SERVICE_ACCOUNT_JSON` (its own service account with
"View financial data" in Play Console, not the summaryai key), `ENTITLEMENT_TOKEN_SECRET`,
`ENFORCE_ENTITLEMENT` (`0` by default), `REALTIME_MONTHLY_CAP_SECONDS` (`10800`).

## 5. What has to exist before this is worth building
1. A hosted realtime STT endpoint that ClearVoice can call (none exists; ListenAI's backend is the nearest, and
   would need its own entitlement path for this package).
2. A decision on the vendor (Deepgram at $0.0077 per minute, or a self-hosted engine that passes the accuracy gate).
3. A privacy-policy and Data safety update, and an explicit opt-in screen before any audio leaves the device.
4. The owner's call on the three legacy Play subscriptions listed in section 2.
