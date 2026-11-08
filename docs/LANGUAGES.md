# Languages and engines

What ClearVoice can transcribe on the phone, with which engine, how well it did in our test, and what we could not measure.
Generated from `tools/asr-bench` (see "Method"); the routing table in code is `LanguageSupport.kt`, generated from the same numbers.

**Read this first.** Every number below is from **FLEURS read speech** (people reading Wikipedia-style sentences into a good
microphone). Real recordings (noise, crosstalk, accents, dialect, phone calls) are harder: expect more mistakes than the
table shows. The test is 25 short clips (about 5 minutes) per language, so a single number carries several points of noise
(95% intervals in the table are from a bootstrap over clips). Nothing here was run on a phone; speed and memory are Mac
CPU numbers.


## Result

Languages the picker offers: **41** (10 before this change, 31 added). Measured and not offered: Hebrew, Urdu (see below).

Error % is word error rate, or character error rate (marked `*`) for Chinese, Japanese, Korean and Thai. "Fast" is the small Vosk model, scored on the same clips. The first ten languages keep their October 2026 numbers (tools/asr-bench/README.md); the rest are new. Languages with a big engine fall back to the smaller one on phones that report less than 5 GB of RAM.

| Language | Native name | Accurate engine | Error % [95% CI] | Status | Phones under 5 GB RAM | Fast (Vosk) error % | Licence |
|---|---|---|---|---|---|---|---|
| Bengali | বাংলা | Zipformer Bengali (streaming) (87 MB) | 17.1 [12.7-21.8] | Supported | same | none | Apache-2.0 |
| Bulgarian | Български | Omnilingual 300M CTC (293 MB) | 25.2 [21.6-29.5] | Supported | needs a bigger phone | none | Apache-2.0 |
| Catalan | Català | Vosk small (Fast model) | 17.3 [12.9-22.1] | Supported | same | 17.3 | Apache-2.0 |
| Chinese | 中文 | SenseVoice (237 MB) | 11.9* | Supported | same | 24.2 | see NOTICE / Apache-2.0 |
| Croatian | Hrvatski | Omnilingual 300M CTC (293 MB) | 19.6 [15.3-23.8] | Supported | needs a bigger phone | none | Apache-2.0 |
| Czech | Čeština | Omnilingual 300M CTC (293 MB) | 29.3 [24.2-34.9] | Supported | needs a bigger phone | - (60%, not offered) | Apache-2.0 |
| Dutch | Nederlands | Whisper small (375 MB) | 21.7 [17.2-26.5] | Supported | Vosk small 48.7% | 48.7 | MIT / Apache-2.0 |
| English | English | Whisper small (375 MB) | 8.9 | Supported | NeMo FastConformer en/de/es/fr 16.2% | 24.0 | MIT / CC-BY-4.0 / Apache-2.0 |
| Finnish | Suomi | Omnilingual 300M CTC (293 MB) | 28.4 [20.8-36.4] | Supported | needs a bigger phone | none | Apache-2.0 |
| French | Français | NeMo FastConformer en/de/es/fr (103 MB) | 11.5 | Supported | same | 19.4 | CC-BY-4.0 / Apache-2.0 |
| German | Deutsch | NeMo FastConformer en/de/es/fr (103 MB) | 9.1 | Supported | same | 23.8 | CC-BY-4.0 / Apache-2.0 |
| Hindi | हिन्दी | Vosk small (Fast model) | 16.4 | Supported | same | 16.4 | Apache-2.0 |
| Indonesian | Bahasa Indonesia | Starling multilingual (streaming Zipformer) (259 MB) | 12.2 [8.5-16.2] | Supported | Dolphin base CTC 19.1% | none | Apache-2.0 |
| Italian | Italiano | Vosk small (Fast model) | 9.0 | Supported | same | 9.0 | Apache-2.0 |
| Japanese | 日本語 | Whisper small (375 MB) | 20.0* | Supported | Vosk small 22.2% | 22.2 | MIT / Apache-2.0 |
| Kazakh | Қазақша | Vosk small (Fast model) | 19.0 [13.9-24.5] | Supported | same | 19.0 | Apache-2.0 |
| Korean | 한국어 | Whisper small (375 MB) | 22.0* | Supported | Vosk small 29.8% | 29.8 | MIT / Apache-2.0 |
| Malay | Bahasa Melayu | Whisper small (375 MB) | 23.0 [17.6-29.7] | Supported | Dolphin base CTC 32.1% | none | MIT / Apache-2.0 |
| Persian | فارسی | Vosk small (Fast model) | 16.9 [12.2-22.3] | Supported | same | 16.9 | Apache-2.0 |
| Polish | Polski | NeMo FastConformer 10-language (102 MB) | 14.7 [9.4-20.5] | Supported | same | 17.1 | NVIDIA NGC Terms of Use (see below) |
| Portuguese | Português | Whisper small (375 MB) | 6.3 | Supported | Vosk small 29.6% | 29.6 | MIT / Apache-2.0 |
| Romanian | Română | Omnilingual 300M CTC (293 MB) | 25.8 [19.2-33.6] | Supported | needs a bigger phone | none | Apache-2.0 |
| Russian | Русский | Zipformer Russian (60 MB) | 9.5 [5.3-14.2] | Supported | NeMo FastConformer 10-language 17.1% | 22.6 | Apache-2.0 / NVIDIA NGC Terms of Use (see below) |
| Slovak | Slovenčina | Omnilingual 300M CTC (293 MB) | 19.3 [14.2-24.9] | Supported | needs a bigger phone | none | Apache-2.0 |
| Spanish | Español | NeMo FastConformer en/de/es/fr (103 MB) | 6.9 | Supported | same | 11.5 | CC-BY-4.0 / Apache-2.0 |
| Swahili | Kiswahili | Omnilingual 300M CTC (293 MB) | 22.1 [17.6-26.3] | Supported | needs a bigger phone | none | Apache-2.0 |
| Swedish | Svenska | Whisper small (375 MB) | 24.6 [18.8-30.9] | Supported | Vosk small 38.8% | 38.8 | MIT |
| Tagalog | Tagalog | Omnilingual 300M CTC (293 MB) | 22.3 [18.7-26.4] | Supported | Dolphin base CTC 36.3% | none | Apache-2.0 |
| Thai | ไทย | Dolphin base CTC (81 MB) | 14.2 [10.8-18.8]* | Supported | same | none | Apache-2.0 |
| Turkish | Türkçe | Whisper small (375 MB) | 18.2 [14.4-23.3] | Supported | Vosk small 29.7% | 29.7 | MIT / Apache-2.0 |
| Ukrainian | Українська | NeMo FastConformer 10-language (102 MB) | 15.8 [10.3-23.4] | Supported | same | 18.6 | NVIDIA NGC Terms of Use (see below) |
| Uzbek | Oʻzbekcha | Vosk small (Fast model) | 29.1 [21.0-36.6] | Supported | same | 29.1 | Apache-2.0 |
| Vietnamese | Tiếng Việt | Zipformer Vietnamese (60 MB) | 8.5 [5.3-12.0] | Supported | Dolphin base CTC 22.2% | - (58%, not offered) | Apache-2.0 |
| Arabic | العربية | Starling multilingual (streaming Zipformer) (259 MB) | 18.4 [10.9-27.1] | Experimental (dialect) | Dolphin base CTC 39.0% | none | Apache-2.0 |
| Danish | Dansk | Whisper small (375 MB) | 34.8 [28.6-41.0] | Experimental | needs a bigger phone | none | MIT |
| Georgian | ქართული | Vosk small (Fast model) | 32.1 [23.2-43.5] | Experimental | same | 32.1 | Apache-2.0 |
| Greek | Ελληνικά | Omnilingual 300M CTC (293 MB) | 38.4 [32.4-43.9] | Experimental | needs a bigger phone | none | Apache-2.0 |
| Hungarian | Magyar | Omnilingual 300M CTC (293 MB) | 36.5 [29.4-44.1] | Experimental | needs a bigger phone | none | Apache-2.0 |
| Slovenian | Slovenščina | Omnilingual 300M CTC (293 MB) | 35.8 [29.1-42.8] | Experimental | needs a bigger phone | none | Apache-2.0 |
| Tamil | தமிழ் | Omnilingual 300M CTC (293 MB) | 45.7 [39.8-52.6] | Experimental | needs a bigger phone | none | Apache-2.0 |
| Telugu | తెలుగు | Omnilingual 300M CTC (293 MB) | 34.4 [29.6-39.2] | Experimental | needs a bigger phone | - (72%, not offered) | Apache-2.0 |

Notes on the table:
* **Status** follows the written rule: error at most 30% is Supported, 30 to 50% is Experimental (the app says so, with the
  number), above 50% is not offered. Uzbek (29.1, interval reaches 36.6), Georgian and Tamil sit on or near a line; treat
  their label as soft. **Arabic** earns "Supported" on standard Arabic read speech (18.4%) but is held at Experimental: the
  same engines scored 66 to 73% on Egyptian-dialect broadcast speech (below).
* **Phones under 5 GB RAM**: the engine that runs instead when the phone reports less than 5e9 bytes of RAM (a 6 GB phone
  reports about 5.7e9, a 4 GB phone about 3.8e9). "needs a bigger phone" means every engine for the language is a big one; the
  picker lists the language under "Needs a bigger phone" and transcription says so instead of running something that gets killed.
* **Fast (Vosk)** is the existing default engine. For languages where Vosk scored above 50% or has no model, Fast is not
  offered and the Accurate engine is always used (the settings screen says so). Vosk small models in the first ten languages
  are unpinned downloads as before; every Vosk model added here is pinned by size and SHA-256.
* The Vosk row of Catalan, Kazakh, Persian, Georgian and Uzbek is the only engine we shipped for them. Vosk lists its own
  FLEURS results for Persian, Kazakh and Georgian on its model page, so it is possible those models were tuned against FLEURS;
  we cannot tell, and the number may flatter them.

## How the app uses this

* **Picker** (Settings, Transcription Language): native name first, a Supported or Experimental badge, the Accurate download size and the
  Fast size before anything is downloaded, a note when this phone has less memory than the best model needs, and a section for
  languages that need a bigger phone. Languages that were measured and failed (Hebrew, Urdu) are listed greyed with the closest
  available language. Arabic carries a plain warning that dialect speech is much worse.
* **Quality setting.** Fast (Vosk) stays the default and is untouched for the first ten languages. For a new language with no Fast model,
  or whose Fast model scored above 50%, the Accurate engine is always used and the settings screen says so.
* **Memory.** Below 5e9 bytes of reported RAM an engine marked big is skipped for the next one in the language's list; if nothing
  is left, transcription stops with a message that the language needs a phone with 6 GB or more.
* **Word times.** Every engine except Whisper returns token times, used as they are (leading spaces mark word starts, so Arabic words
  and Thai pieces group correctly). Whisper has none; `distributeOverSpeech` spreads its words over the speech. Thai, Lao, Khmer and
  Burmese pieces are treated like Chinese characters: each piece is a timed word and pieces join with no spaces, so the phrase spaces
  an engine puts in Thai are not kept. The Vietnamese Zipformer writes capitals; the app lower-cases them.
* **Right-to-left.** The transcript view is one Compose text per paragraph, which takes its direction from the first strong letter.
  SRT and VTT cues that contain Arabic, Persian, Urdu or Hebrew letters get a right-to-left mark so other players lay them out right
  to left even when a cue starts with a digit or a Latin word. Search ignores Arabic and Hebrew vowel marks, folds alef/yeh/teh
  marbuta variants, and searches Thai character by character. Arabic and Urdu question marks and full stops end a sentence.
* **Not changed:** the live transcript (Vosk only, so it says it is unavailable for languages with no Fast model), "Mark uncertain
  words" (stays off for the new languages: it was only measured for seven), voice-data contribution (new languages upload with their
  language tag like the others; the raw transcript of an Experimental language is likely to contain errors).


## Engines considered

Sizes are what the user downloads (the release archive, or the int8 files for Whisper). RAM is the peak resident memory of a fresh Mac Python process decoding three ~28 s windows with 4 threads (about 50 MB of that is Python); it is not a phone number. The only phone measurements ever made are the old ones in tools/asr-bench/README.md (Whisper small about 1.3 GB, NeMo about 0.38 GB on a Pixel 9 Pro).

| Engine | Languages | Download | Mac peak RAM | Speed (x real time, Mac) | Licence | Word times | Verdict |
|---|---|---|---|---|---|---|---|
| Vosk small | about 20: ar, ca, cs, fa, hi, it, ka, kk, ky, nl, pl, ru, sv, te, tg, tr, uk, uz, vi + the first ten | 31-304 MB each | 225 | 0.04 | Apache-2.0 / MIT (Arabic small-ar-0.3: not stated) | yes | Shipped for 11 languages; not for Arabic (licence unstated) |
| Whisper tiny / base / small / medium (int8) | about 99 | 104 / 160 / 375 / 945 MB | base 880, small 1349 | base 0.06, small 0.18 | MIT | no (int8 export has no cross-attention; WordTiming spreads words over the speech) | Small shipped; tiny and base not integrated; medium over budget |
| SenseVoice (int8) | zh, yue, en, ja, ko | 237 MB | - | - | see NOTICE.md | yes | Existing (zh) |
| NeMo FastConformer CTC en/de/es/fr | en, de, es, fr | 103 MB | 509 (old run) | - | CC-BY-4.0 | yes | Existing |
| NeMo FastConformer CTC 10-language | be, de, en, es, fr, hr, it, pl, ru, uk | 102 MB | 529 | 0.01 | NVIDIA NGC Terms of Use. Checked 2026-11-07: the model card names no Creative Commons licence and says nothing on commercial use or redistribution, so the earlier CC-BY-4.0 assumption was wrong. Shipped; Vosk is the drop-in fallback for Polish and Ukrainian if the terms turn out not to allow it | yes | Shipped for pl, uk. Croatian scored 88%: the model lists it but FLEURS Croatian fails |
| Parakeet TDT 0.6B v3 (int8) | 25 European | 487 MB | 1379 | 0.03 | CC-BY-4.0 | yes (token and duration) | Not shipped: over the 400 MB budget and only Danish would need it (Whisper small already gives Danish 34.8%). Best Italian (3.4%), Dutch (12.1%), Polish (9.5%), Bulgarian (12.9%) of anything tested: a candidate for a later optional download |
| Starling streaming Zipformer | ar, en, id, ja, ru, th, vi, zh | 259 MB | 809 | 0.06 | Apache-2.0 (training data licences not audited) | yes | Shipped for ar, id (big-phone engine) |
| Zipformer Russian (Vosk project) | ru | 60 MB | 742 | 0.01 | Apache-2.0 | yes | Shipped |
| GigaAM v3 CTC Russian | ru | 163 MB | 397 (short clips) | 0.04 | MIT | yes | Not shipped: 8.8% vs Zipformer 9.5%, inside the 2 point tie rule, and 3x larger |
| Zipformer Vietnamese (zzasdf) | vi | 60 MB | 779 | 0.01 | Apache-2.0 | yes | Shipped (writes capitals, lower-cased by the app) |
| Zipformer Vietnamese 30M (hynt) | vi | 26 MB | 344 (short clips) | 0.01 | CC-BY-NC-ND-4.0 | yes | NOT shippable: non-commercial, no derivatives. 8.2% would have been the smallest model |
| Zipformer Thai (GigaSpeech2) | th | 696 MB archive (157 MB int8 files) | 670 (short clips) | - | not checked | yes | Not integrated: 11.0% vs Dolphin base 14.2% (within noise), HF repo gated, archive is mostly fp32 |
| Streaming Zipformer Bengali (Vosk project) | bn | 87 MB | 388 | 0.03 | Apache-2.0 | yes | Shipped |
| Dolphin base CTC (int8) | 40 Eastern languages (ar, bn, fa, hi, id, ms, th, tl, ur, vi, ru ...; no Turkish) | 81 MB | 543 | 0.03 | Apache-2.0 | yes | Shipped: Thai, and the small-phone engine for Arabic, Indonesian, Malay, Tagalog |
| Dolphin small CTC (int8) | same | 192 MB | 1080 | 0.04 | Apache-2.0 | yes | Not shipped: Arabic 20.4% vs Starling 18.4%, Malay 26.2% vs Whisper 23.0%; no language only it covers |
| Omnilingual ASR 300M CTC (int8) | 1,600 (measured on 30) | 293 MB | 1224 | 0.10 | Apache-2.0 | yes | Shipped for the languages nothing smaller covers: bg, hr, cs, fi, ro, sk, sl, sw, tl, hu, el, ta, te |
| Moonshine base (ar, uk, vi, es, ja, zh); tiny (ja, ko) | see left | 30-119 MB | about 500 | - | Moonshine Community License: NON-COMMERCIAL for non-English | n/a | NOT shippable. Also scored 83% (ar), 69% (uk), 90% (vi) in our run |
| Qwen3-ASR 0.6B (int8) | 52 | 879 MB | - | - | Apache-2.0 | - | Not benchmarked: over budget |
| Cohere Transcribe 14-language | 14 | 1.7 GB | - | - | - | - | Not benchmarked: over budget |
| Whisper turbo / distil-large / large | about 99 | 564 MB and up | - | - | MIT | no | Not benchmarked: over budget |
| FireRed ASR, FunASR-nano, TeleSpeech, Paraformer, WeNet | zh/en (and dialects) | - | - | - | - | - | Not relevant to the new languages |

Timestamps were checked directly (`lang_timestamps.py`): every engine above except Whisper returns one time per token; the streaming engines return them through the online recognizer.

## All measurements

Error % on the 25 FLEURS clips (CER for th). `-` = not run. Vosk columns include models we do not ship (Czech, Telugu, Vietnamese failed the bar). Whisper small is close to useless on bn (100.8), ta (80.6), sw (74.9) and hi (90.7); it was not run on Telugu.

| lang | vosk | whisper-small | whisper-base | whisper-medium | stream-zipformer-multi | dolphin-base | dolphin-small | omnilingual-300m | nemo10 | parakeet-v3 | zipformer-ru | zipformer-vi | zipformer-th | stream-zipformer-bn | gigaam-v3-ru |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ar | 38.2 | 27.6 | 48.4 | 15.4 | 18.4 | 39.0 | 20.4 | 18.2 | - | - | - | - | - | - | - |
| bg | - | 37.1 | - | - | - | - | - | 25.2 | - | 12.9 | - | - | - | - | - |
| bn | - | 100.8 | - | - | - | 38.2 | 30.4 | 35.5 | - | - | - | - | - | 17.1 | - |
| ca | 17.3 | 18.3 | - | - | - | - | - | 18.9 | - | - | - | - | - | - | - |
| cs | 60.0 | 44.3 | - | - | - | - | - | 29.3 | - | 25.9 | - | - | - | - | - |
| da | - | 34.8 | - | - | - | - | - | 39.3 | - | 25.8 | - | - | - | - | - |
| el | - | 50.3 | - | - | - | - | - | 38.4 | - | 45.7 | - | - | - | - | - |
| fa | 16.9 | 56.1 | - | - | - | 42.7 | 37.8 | 25.4 | - | - | - | - | - | - | - |
| fi | - | 31.5 | - | - | - | - | - | 28.4 | - | 21.1 | - | - | - | - | - |
| he | - | 58.2 | - | - | - | - | - | 50.9 | - | - | - | - | - | - | - |
| hi | 16.4 | 90.7 | - | - | - | 35.2 | 45.0 | 30.5 | - | - | - | - | - | - | - |
| hr | - | 37.7 | - | - | - | - | - | 19.6 | 88.0 | 27.7 | - | - | - | - | - |
| hu | - | 40.8 | - | - | - | - | - | 36.5 | - | 33.4 | - | - | - | - | - |
| id | - | 14.6 | 39.6 | - | 12.2 | 19.1 | 15.0 | 17.2 | - | - | - | - | - | - | - |
| it | 8.8 | 9.8 | - | - | - | - | - | 9.3 | 8.3 | 3.4 | - | - | - | - | - |
| ka | 32.1 | 100.9 | - | - | - | - | - | 38.6 | - | - | - | - | - | - | - |
| kk | 19.0 | 84.2 | - | - | - | 62.3 | 42.9 | 22.1 | - | - | - | - | - | - | - |
| ms | - | 23.0 | - | - | - | 32.1 | 26.2 | 30.4 | - | - | - | - | - | - | - |
| nl | 48.7 | 21.7 | 44.7 | - | - | - | - | 30.4 | - | 12.1 | - | - | - | - | - |
| pl | 17.1 | 19.8 | 45.0 | - | - | - | - | 22.7 | 14.7 | 9.5 | - | - | - | - | - |
| ro | - | 32.9 | - | - | - | - | - | 25.8 | - | 22.0 | - | - | - | - | - |
| ru | 22.6 | 13.0 | 25.9 | - | 16.9 | 53.6 | 39.6 | 30.5 | 17.1 | 9.7 | 9.5 | - | - | - | 8.8 |
| sk | - | 43.1 | - | - | - | - | - | 19.3 | - | 18.2 | - | - | - | - | - |
| sl | - | 56.9 | - | - | - | - | - | 35.8 | - | 56.3 | - | - | - | - | - |
| sv | 38.8 | 24.6 | - | - | - | - | - | 35.7 | - | 22.4 | - | - | - | - | - |
| sw | - | 74.9 | - | - | - | - | - | 22.1 | - | - | - | - | - | - | - |
| ta | - | 80.6 | - | - | - | 72.6 | 60.3 | 45.7 | - | - | - | - | - | - | - |
| te | 72.4 | - | - | - | - | 65.6 | 52.0 | 34.4 | - | - | - | - | - | - | - |
| th | - | 55.4 | 68.3 | - | 12.5 | 14.2 | 16.7 | 24.1 | - | - | - | - | 11.0 | - | - |
| tl | - | 31.5 | - | - | - | 36.3 | 30.5 | 22.3 | - | - | - | - | - | - | - |
| tr | 29.7 | 18.2 | 36.1 | - | - | - | - | 27.3 | - | - | - | - | - | - | - |
| uk | 18.6 | 19.2 | 46.8 | - | - | - | - | 32.1 | 15.8 | 12.0 | - | - | - | - | - |
| ur | - | 51.8 | - | - | - | 56.6 | 74.7 | 68.8 | - | - | - | - | - | - | - |
| uz | 29.1 | 99.8 | - | - | - | 59.0 | 48.2 | 41.5 | - | - | - | - | - | - | - |
| vi | 58.5 | 22.2 | 39.9 | - | 11.7 | 22.2 | 17.7 | 29.6 | - | - | - | 8.5 | - | - | - |

## Arabic, honestly

Standard Arabic (FLEURS ar_eg: formal sentences read aloud) and Egyptian dialect (MGB-3 test, 26 clips taken across the broadcast genres, 4-20 s, dialect spelling in the reference with tags removed):

| engine | standard Arabic read speech | Egyptian dialect broadcast |
|---|---|---|
| stream-zipformer-multi | 18.4 [11-27] | 66.5 [58-75] |
| omnilingual-300m | 18.2 [14-23] | 66.3 [61-72] |
| whisper-medium | 15.4 [10-21] | 61.5 [53-70] |
| dolphin-small | 20.4 [16-26] | 96.1 [94-98] |
| whisper-small | 27.6 [21-34] | 69.6 [63-76] |
| vosk | 38.2 [25-55] | 72.6 [66-79] |
| dolphin-base | 39.0 [32-46] | 91.2 [88-94] |
| vosk Tunisian (ar-tn) on standard Arabic | 75.2 | - |

* Standard Arabic is usable (about 1 word in 5 wrong). Everyday dialect is not: two thirds of the words wrong, for every engine.
  Part of that is spelling (MGB-3 writes dialect words its own way, so a recogniser that outputs the standard spelling is
  marked wrong), but not all of it: reading the output, the best engines give the gist of a sentence with many words wrong, merged or dropped, which is readable by someone who knows what was said and not much more.
* **Gulf, Levantine and Maghrebi dialects were not measured.** No open, ungated test set for them was reachable (Casablanca and
  the Saudi set are gated by hand approval; the commercial conversational sets list no files). A user in the UAE speaking
  Gulf Arabic should expect something between the two columns at best. This is why Arabic is labelled Experimental in the app.
* Whisper medium scored 15.4% on standard Arabic but is 945 MB and needs a much bigger phone; not offered.
* Vosk's only small Arabic model has no stated licence, and the Tunisian one scores 75% on standard Arabic; neither ships.
* Output has no diacritics and no punctuation from the streaming engine. Numbers come out as digits from Starling, as words
  from Dolphin.

## Measured and not offered

| Language | Best result | Why |
|---|---|---|
| Hebrew | 50.9% (Omnilingual); Whisper small 58.2% | Over the 50% bar. Nothing close to offer. |
| Urdu | 51.8% (Whisper small); Omnilingual 68.8%, Dolphin 56.6% | Over the bar. Closest available: Hindi (spoken Urdu and Hindi are close; the script differs, so the Hindi transcript is Devanagari) |

## Re-check of Hindi and Italian

Hindi: Vosk 16.4% is still the best (same as October). Whisper small 90.7% (identical to October), Omnilingual 30.5%, Dolphin base 35.2%.
Italian: Vosk 8.8%, NeMo 10-language 8.3%, Whisper small 9.8% (identical to October), Omnilingual 9.3%, and **Parakeet v3 3.4%**.
Italian and Hindi routing is unchanged in this change. Parakeet is the one real improvement found for an existing language; it needs
the 487 MB download and a 6 GB phone, so it is left for a decision (see the Parakeet row).

## Method

* **Data.** Google FLEURS test split, read through the Hugging Face parquet conversion; the first 25 utterances of 4 to 25 seconds per
  language (about 5 minutes). Egyptian Arabic: MGB-3 test, 2 clips from each of 13 row groups. Test sets were kept out of the repository.
* **Engines.** The exact files the app downloads (hashes checked byte for byte), run with sherpa-onnx 1.13.8 (the version of the
  AAR in the app) and 4 threads, or Vosk small. Whisper with `language=` set and token timestamps requested, as in the app.
  Streaming models were fed a whole window plus one second of silence.
* **Scoring.** NFKC, lower case, punctuation and symbols to spaces; Arabic, Persian, Urdu and Hebrew vowel marks and Arabic
  tatweel removed; Arabic-Indic and Indic digits mapped to ASCII; no other normalisation (no spelling-out of numbers, no alef
  folding in the headline number). Word error rate, or character error rate with spaces removed for zh, ja, ko, th. 95% interval:
  bootstrap over clips, 1,000 resamples. Folding alef/teh marbuta/yeh variants (the `--fold` option) moves Arabic scores by 0 to 5 points.
* **Choosing.** Per language: candidates are shippable engines (licence permits it, integrated, at most 50%). The lowest error
  wins, except that among engines within 2 points of the best (25 clips cannot separate them) the one needing the least memory,
  then the smaller download, wins. Engines over 400 MB are used only if nothing within budget reaches 30%. If the winner needs
  more than 5e9 bytes of RAM, the best small engine is listed as the fallback for smaller phones.

### Limits

* Read speech overstates real accuracy, for every language. The Mac is not a phone; the speeds in the engine table were
  measured with other jobs running on the machine (load average 7 to 15 on 10 cores) and are indicative only.
* 25 clips is small: intervals of plus or minus 5 to 10 points are normal, so rankings inside that are not real.
* Numbers: FLEURS references keep digits; some engines print numbers as words (Dolphin), which costs them points that real users may not mind.
* Contamination: Whisper, Omnilingual and several other models may have seen FLEURS-like text or audio in training. We cannot check.
* The old numbers for the first ten languages are from the October run (same data source, same scorer); they were not re-run except Hindi and Italian.

## What was not measured

* Anything on a phone: memory, speed, battery, and whether the Kotlin configuration of each new engine loads the model. The new engine
  code paths (offline transducer, streaming transducer, Dolphin, Omnilingual) compile against the pinned sherpa-onnx 1.13.8 API but have
  not been run on a device.
* Noisy, accented, telephone or multi-speaker speech in any new language; dialects other than Egyptian Arabic.
* Speaker labels, "mark uncertain words" and the summary for the new languages (mark-uncertain stays off for them; summaries have no
  stopword lists for them; the summariser's sentence splitting for Thai, which has no spaces, is untested).
* Whisper small on Telugu (it scored 81 to 101% on Bengali and Tamil), Whisper base/tiny beyond nine major languages, and Dolphin on languages it does not list; the matrix shows every cell that was run.
* Whisper medium/turbo/large, Qwen3-ASR, Cohere Transcribe (over the size budget).
* Hebrew and Urdu beyond the engines listed; Punjabi, Gujarati, Marathi, Nepali, Sinhala, Burmese, Khmer, Lao, Amharic and the other
  languages Omnilingual or Dolphin nominally cover (no time; no engine of ours was checked on them).
* Play listing text and screenshots for the new markets are a separate task and are not part of this change.
* Licences: the NeMo 10-language model is under the NVIDIA NGC Terms of Use, not CC-BY-4.0 (the NGC terms were not read for commercial redistribution), and the training-data licences behind Starling, Dolphin,
  Omnilingual and the Zipformer models were not audited; the model-card licences are what is recorded.

