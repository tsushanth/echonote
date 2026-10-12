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

* Whisper base (what the app ships as Accurate) is worse than Vosk for fr, it, hi, zh and ja, so the app only
  routes en, es, de, pt and ko to it (`AccurateSupport`) and falls back to Fast elsewhere.
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
