# On-device summaries: feasibility and status

Written 2026-10-12. Promise to keep: audio and transcripts never leave the phone.
Sources are named per claim. "From memory" means I did not re-check it in this task and it
must be confirmed before anyone relies on it.

## Verdict in one paragraph

Ship the extractive "Key points" now (works on every phone, every language, instantly,
tested here). Real written summaries are only practical today through Gemini Nano via
ML Kit GenAI, and only for English, Japanese and Korean on a short list of 2024+ flagship
phones. That path is compiled in but switched OFF until someone runs it on the Pixel 9 Pro.
A downloadable small LLM is possible but is the most expensive option and is not worth it
until the first two are in users' hands.

## (a) ML Kit GenAI Summarization (Gemini Nano through AICore)

Source: developers.google.com/ml-kit/genai/summarization/android and
developers.google.com/ml-kit/genai (read 2026-10-12), plus the published AAR inspected here.

- Artifact: `com.google.mlkit:genai-summarization:1.0.0-beta1` (beta, "no SLA or deprecation policy").
  AAR is 582 KB. Pulls `genai-common` (83 KB) and Google Data Transport (`transport-backend-cct`).
- minSdk 26 (same as ClearVoice). Needs the system AICore app (`com.google.android.aicore`);
  the manifest adds `<queries>` for it and a BIND_SERVICE permission.
- Devices (feature API list): Pixel 9 series through Pixel 11 series; Galaxy S25/S26, Z Fold7/8,
  Z Flip8; plus some Honor, iQOO, Lenovo, Motorola, OnePlus, OPPO, POCO, realme, Sharp, Sony,
  vivo, Xiaomi models. Not supported with an unlocked bootloader. So the test Pixel 9 Pro is
  supported, and a typical older or mid-range phone is not. Pixel 8 is not on the list read here.
- Languages: English, Japanese, Korean only (`SummarizerOptions.Language`). ClearVoice offers 10
  transcript languages, so 7 get nothing from this API.
- Input: under 4,000 tokens (about 3,000 English words, roughly 20 minutes of speech); article
  input must be over 400 characters. Longer input: truncate (`setLongInputAutoTruncationEnabled`),
  chunk, or use cloud. Output is 1 to 3 bullets. A 30 minute recording (about 4,500 words)
  would need chunking and a second pass, or only the first part gets summarized.
- Runtime check: `checkFeatureStatus()` returns UNAVAILABLE / DOWNLOADABLE / DOWNLOADING /
  AVAILABLE (constants 0..3 confirmed in the AAR). `downloadFeature()` exists; otherwise the first
  inference triggers the download. The system (AICore) owns the model download, not the app.
- Privacy caveat to review: the dependency graph includes Google Data Transport, which ML Kit uses
  for its own usage/telemetry logging. The summarization text itself is processed by AICore on
  the device, but I did not audit what ML Kit logs. The app already ships Firebase Analytics, so
  this is not new in kind, but it conflicts with "no new analytics" in the strict reading. That is
  one reason it is off.
- Not verified here: everything about real behavior (quality, latency, battery, background
  limits, what AICore does when the app is not in the foreground, download size, whether the
  3 bullets are usable on spoken, error-ridden transcripts).

## (b) Small local LLM as a downloadable model

- Gemma 3 1B int4 for MediaPipe/LiteRT: 529 MB file, "at least 4 GB memory" recommended, Pixel 8
  / Galaxy S23 or later is the documented target class (Google Developers blog, "Gemma 3 on mobile
  and web with Google AI Edge"; LLM Inference guide for Android). Model card (litert-community/
  Gemma3-1B-IT) lists about 47 to 50 tokens/s decode on CPU on a Galaxy S24 Ultra with a 1280-token
  context build, and the blog claims up to 2,585 tokens/s prefill. A 1280-token context cannot
  hold a 10+ minute transcript, so it needs map-reduce chunking (several model calls per recording).
- Qwen2.5 0.5B / 1.5B and SmolLM2: GGUF q4 files are roughly 0.4 GB / 1 GB (from memory), run via
  llama.cpp. llama.cpp adds its own native `.so` per ABI (a few MB each, from memory), on top of
  the ~32 MB of sherpa-onnx/onnxruntime already shipped, plus a JNI layer someone has to write and
  maintain. MediaPipe LLM Inference would add its own native libs instead.
- Licenses (from memory, verify): Qwen2.5 0.5B and 1.5B are Apache-2.0 (the 3B is not);
  SmolLM2 is Apache-2.0; Gemma is under the Gemma Terms of Use, which allow redistribution but
  require passing on the use restrictions and notices, and Hugging Face downloads are gated
  behind accepting them, so a pinned direct download URL like ModelDownloader uses may not work.
  The app already has a licenses screen that would need an entry.
- Quality: 0.5B to 1B models summarize clean English text passably and mangle everything else.
  Spoken, unpunctuated, mis-recognized transcripts in 10 languages, with names and numbers, are the
  hard case; expect omissions and invented details, and 0.5B to 1B models are weak outside English
  and Chinese. This is judgment, not something measured here.
- Cost to the user: a 0.5 to 1 GB extra download on top of Whisper/NeMo models of 100 to 375 MB,
  RAM pressure on the 4 GB phones the app already protects, battery, and minutes of compute for a
  long recording.
- Cost to us: a native inference path, model hosting/pinning, a quality eval harness (like
  tools/asr-bench) before claiming anything, and a larger privacy and licensing review.
- Verdict: not now. Revisit after (a) is measured on the Pixel, because the same eval harness
  will tell us whether a downloadable model beats extractive for the 7 languages (a) cannot cover.

## (c) Extractive summarization, no model

Implemented. Method: sentence units, term weights (frequency times inverse sentence frequency),
stopwords for en/es/fr/de/pt/it/hi, character bigrams for Chinese/Japanese, cosine similarity to
the whole-document vector, small lead bias, greedy selection with a redundancy penalty, output in
original order, 3 to 8 points and a character budget.

Limits (honest):
- It shows what the speaker said most centrally. It cannot compress, merge, name action items or
  write "Tom will finish payments by month end". Points are verbatim sentences, which include
  filler and recognition errors.
- Whisper/NeMo/SenseVoice output is punctuated, so sentence splitting works. Vosk (used for Hindi
  and Italian, and for English/Portuguese on phones under about 4 GB) is not. Then it uses
  pauses of 700 ms or more in word timings (needs the stored segments), else fixed word windows;
  those points end in "..." and are fragments, which is visibly worse.
- CJK: splits on 。！？；, treats the text as bigrams, so a topic word of one character is weak.
  Japanese and Korean have only a small character stopword list (Korean none). Chinese tested,
  Japanese and Korean only smoke-tested (no crash, non-null).
- Hindi has a stopword list but is untested beyond smoke because I cannot judge quality.
- Very long, meandering recordings give scattered points; centrality is not importance.
- Mixed-language text uses the recording's language for stopwords only.

## Plan

1. Now (this branch): extractive "Key points" card + menu action, sidecar JSON storage, honest
   labeling. Ship when the UI is checked on a phone.
2. Pixel check, then flip `OnDeviceAiSummarizer.ENABLED`:
   - `checkFeatureStatus()` on the Pixel 9 Pro: what does it return, with and without AICore updated?
   - Run an English 2 min, 10 min and 30 min transcript; time the call; look at the three bullets.
   - Confirm nothing triggers a download unexpectedly and that the call does not hang if the app
     is backgrounded (the code uses a 90 s timeout and falls back).
   - Check logcat / network for any traffic from ML Kit during use (privacy).
   - For text over about 3,000 words, add chunking (summarize chunks, then summarize the
     bullets) rather than relying on truncation, which only summarizes the beginning.
   - Add a "Written by on-device AI" test string review with a native speaker for ja/ko.
3. Only if users want written summaries in the other 7 languages: build an eval harness and test
   a downloadable 1B model against extractive. Do not ship it on vibes.
