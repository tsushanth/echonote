# ASR bench: Vosk vs Whisper vs Moonshine, and whether combining them helps

Scores the engines ClearVoice ships (or might ship) on real speech from the ESB test sets
(`hf-audio/esb-datasets-test-only-sorted`: AMI meetings, Earnings22 calls, GigaSpeech, VoxPopuli).

    uv venv .venv && source .venv/bin/activate
    uv pip install sherpa-onnx vosk jiwer datasets soundfile numpy whisper-normalizer huggingface_hub
    # models into ./models: vosk-model-small-en-us-0.15, whisper-base (int8 encoder/decoder/tokens
    # from csukuangfj/sherpa-onnx-whisper-base), sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27
    python fetch.py && python run.py vosk whisper moonshine && python score.py

Same model files as the app, run on a Mac (speed numbers are Mac, not phone).

## Result, 2026-10-11 (120 utterances, 6,100 words, 15-28 s each, English only)

| system | AMI | Earnings22 | GigaSpeech | VoxPopuli | all |
|---|---|---|---|---|---|
| Vosk small en-us | 24.9 | 35.3 | 9.1 | 19.0 | 22.0 |
| Whisper base int8 | 15.4 | 14.6 | 6.9 | 8.7 | **11.3** |
| Moonshine tiny (chunked to <=8 s) | 19.8 | 30.4 | 17.6 | 20.9 | 22.2 |
| ROVER vote of all three (Whisper backbone) | 12.8 | 14.4 | 6.5 | 9.0 | 10.6 |

* Voting beats Whisper alone by 0.65 WER points (paired bootstrap 95% CI 0.08 to 1.31); it is worse
  on VoxPopuli. Cost: all three engines per recording.
* Disagreement is a good uncertainty signal. Whisper words that neither other engine agrees with are
  5.1% of words and 49.5% of them are wrong (3.7% for the rest), holding 22% of Whisper's errors.
  With Vosk as the only second engine: 14.5% of words flagged, 25% of them wrong, 31% of errors caught.
* Real-time factor on a Mac, 4 threads: Vosk 0.09, Whisper 0.148, Moonshine 0.009.

## Caveats
* English only; Whisper base is multilingual, Vosk/Moonshine models here are English.
* Moonshine's v2 ONNX export returns nothing above ~9 s, so it was chunked; that costs it accuracy.
* The ROVER here is a simple backbone-and-vote implementation. Whisper's training data may overlap
  some of these public sets, which would flatter it.
* Not measured on the phone. TedLium returned no rows and is not included.

## Speaker labels: which embedding model and threshold (2026-10-11)

`diar_fetch.py` + `diar_eval.py`: real human speakers from LibriSpeech test-clean (ids give the speaker)
stitched into 16 conversations (alternating 2 or 3 speakers, 0.5 s gaps). Scores the number of speakers
found and the share of speech given to the wrong speaker after the best speaker mapping.
Segmentation is pyannote-segmentation-3-0 in every row; only the embedding model and the clustering
threshold change.

| embedding model | best threshold tried | right speaker count | speech on wrong speaker |
|---|---|---|---|
| NeMo TitaNet-small (40 MB) | 0.8 to 0.9 | **16/16** | **7.8%** |
| WeSpeaker ResNet34 (26 MB) | 0.4 to 0.6 | 12/16 | 13-15% |
| 3D-Speaker ERes2Net base (zh) | 0.8 | 13/16 | 8.9% |
| 3D-Speaker CAM++ en (30 MB) | 0.8 | 4/16 | 38% |
| WeSpeaker CAM++ en (29 MB), first choice | 0.7 (30 conversations) | 12/30 | 41% |

TitaNet-small by threshold: 0.4 4/16, 0.6 12/16, 0.7 15/16, 0.8 16/16, 0.9 16/16, 1.0 13/16, 1.1 3/16.
The app uses 0.85.

Caveats: clean read speech, no overlapping talk, sequential turns, 16 conversations. Real meetings will be
worse and the usable threshold range is not wide. A synthetic two-voice test (macOS TTS) looked fine at
0.3 and merged both voices at 0.5, which is why that test alone is not enough. TitaNet-small's licence has
not been confirmed (the large model's card says CC-BY-4.0).

## Other languages (2026-10-11)

`ml_fetch.py`, `ml_run.py` (set `WSIZE=small` for Whisper small), `ml_extra.py`, `ml_score.py`: 25 FLEURS read-speech
utterances per language (about 5 minutes each), the app's own Vosk small models and language codes, Whisper int8.
Error rate % (WER; CER for zh, ja and ko), lower is better:

| lang | Vosk small | Whisper base | Whisper small | other family |
|---|---|---|---|---|
| en | 24.0 | 10.9 | **8.9** | NeMo FC 16.2 |
| de | 23.8 | 20.9 | **9.1** | NeMo FC **9.1** |
| es | 11.5 | 10.7 | **5.6** | NeMo FC 6.9 |
| fr | 19.4 | 32.2 | 12.8 | NeMo FC **11.5** |
| it | **9.0** | 21.2 | 9.8 | |
| pt | 29.6 | 16.7 | **6.3** | |
| hi | **16.4** | 115 (garbage loop) | 90.7 | |
| zh | 24.2 | 46.9 (Traditional script) | 39.3 | SenseVoice **11.9** |
| ja | 22.2 | 27.9 | **20.0** | SenseVoice 78 (outputs Chinese; unusable) |
| ko | 29.8 | 25.0 | **22.0** | SenseVoice 79 (unusable) |

* Whisper base (what the app first shipped as Accurate) is worse than Vosk for fr, it, hi, zh and ja. The app now
  routes by language (`EnginePlan`): Whisper small for en, pt, ja, ko; NeMo FastConformer for de, es, fr; SenseVoice for zh;
  Vosk (Fast) for it and hi. NeMo fast-conformer (en/de/es/fr) tied Whisper small for de, es and fr (paired bootstrap of WER difference,
  Whisper small minus NeMo: de +0.0 [-3.4, +3.3], es -1.3 [-5.1, +2.0], fr +1.3 [-2.3, +5.5]; English -7.4 [-12.5, -2.8]
  so Whisper small is clearly better there) and is 3.6x smaller, about 15x faster, with real word times, so de, es and
  fr use it. It is only published as a .tar.bz2 on GitHub (its Hugging Face repo is empty), so the app unpacks it with
  Apache Commons Compress, pinning the archive and each extracted file.
* Whisper small fixes most of that (de 21 to 9, fr 32 to 13, pt 17 to 6) but is about 3x slower than base
  (Mac real-time factor 0.15 vs 0.05) and a ~375 MB download (112 + 262 MB), and does not fix Hindi.
* Voting vosk + best Whisper + a third family (ROVER, languages with three engines): es 5.6 to 3.8, de 9.1 to 7.9,
  fr 11.5 to 10.4, en 8.9 to 8.7, zh 11.9 to 12.3 (worse), ja and ko no gain. No third family for hi, it or pt.
* Whisper's token timings for zh and ja come as one character per token with no spaces, so word-level
  highlighting would be clause-sized there. Not addressed because those languages do not use Whisper base.

`mls_fetch.py`, `mls_sweep.sh`, `diar_eval.py`: speaker labels on Multilingual LibriSpeech real speakers
(TitaNet-small, threshold 0.85, 10 mixtures of 2-3 speakers per language). Right speaker count / share of
speech on the wrong speaker: es 9/10 17.8%, fr 8/10 22.2%, de 10/10 20.5%, it 10/10 10.0%, pt 10/10 10.1%
(English was 16/16, 7.8%). 0.85 was best or tied-best in every language. zh, ja, ko and hi are untested: no
open speaker-labelled data was reachable.

## Word times (timing_eval.py)

Whisper's sherpa-onnx int8 exports return no token timestamps (empty array), so word times in the app were an
even spread over each 28 s window. NeMo and SenseVoice do return real times. Against Vosk word start times on
444 matched words of real English speech, mean absolute start error: even spread over the window 0.65 s (50% within
0.5 s); spread over the speech only, by word length, 0.48 s (66% within 0.5 s). The app now does the latter for
Whisper (`WordTiming.kt`). Still approximate: tap-to-seek and speaker alignment can be off by about half a second.

## Marking uncertain words with Vosk as a second opinion (2026-10-12)

`flag_eval.py` (run it from the bench folder, `python flag_eval.py <bench_dir>`). Primary engine = what `EnginePlan`
picks for Accurate; second opinion = the app's Vosk small model. A primary word is flagged when the Levenshtein alignment
of primary vs Vosk words pairs it with no equal Vosk word (NFKC, lower case, punctuation removed; zh and ja per character).
A primary word is wrong when the alignment against the reference pairs it with no equal word (substitutions and insertions;
words the engine dropped cannot be marked and are not counted). 25 FLEURS clips per language (the 120 ESB clips for en-esb).

| lang | primary | words | primary wrong % | flagged % | wrong among flagged % | wrong among unflagged % | ratio | primary errors caught % | offered |
|---|---|---|---|---|---|---|---|---|---|
| en | Whisper small | 552 | 8.3 | 19.2 | 28.3 | 3.6 | 7.9x | 65.2 | yes |
| en (120 ESB clips) | Whisper small | 5920 | 6.3 | 15.4 | 22.5 | 3.3 | 6.8x | 55.2 | yes |
| de | NeMo FC | 516 | 7.8 | 18.2 | 33.0 | 2.1 | 15.5x | 77.5 | yes |
| es | NeMo FC | 619 | 5.5 | 8.6 | 45.3 | 1.8 | 25.6x | 70.6 | yes |
| fr | NeMo FC | 684 | 10.2 | 20.3 | 38.1 | 3.1 | 12.2x | 75.7 | yes |
| pt | Whisper small | 663 | 5.6 | 25.3 | 17.9 | 1.4 | 12.6x | 81.1 | yes (flags 25.3%, right at the limit) |
| ja (per character) | Whisper small | 1113 | 10.2 | 22.3 | 35.5 | 2.9 | 12.3x | 77.9 | yes |
| zh (per character) | SenseVoice | 801 | 7.9 | 17.0 | 18.4 | 5.7 | 3.2x | 39.7 | yes (weakest ratio) |
| ko | Whisper small | 340 | 47.4 | 68.5 | 62.7 | 14.0 | 4.5x | 90.7 | NO: flags 68.5% of words, which marks nearly everything |
| it | Vosk | - | - | - | - | - | - | - | NO: primary is Vosk, no independent second opinion |
| hi | Vosk | - | - | - | - | - | - | - | NO: same |

Rule: offered when wrong-among-flagged is at least about 2x wrong-among-unflagged and no more than about 25% of words are
flagged. Korean fails the second test (its Whisper error rate is 47% and Vosk's Korean differs from it on most words);
Italian and Hindi have no second engine. When Accurate falls back to Vosk on a small-RAM phone there is also nothing to
compare against, so no flags. The first line of the earlier table above (5.1% flagged, 49.5% wrong) is for two second
opinions; with Vosk alone the numbers are those in this table.

Caveats: 25 read-speech clips per language (about 500 to 1100 words each), so the rates carry a few points of noise;
English on the 120 real clips is the more trustworthy row and is weaker than FLEURS. "Caught" counts only substitutions and
insertions. Not measured on a phone, and not on noisy or accented recordings in the other languages.
