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

## Low-RAM fallback: NeMo instead of Vosk for English (2026-10-12)

Question: phones under 5e9 bytes of RAM cannot run Whisper small (~1.3 GB peak on a Pixel 9 Pro) and used Vosk, which is
about 2.5x worse. Is NeMo FastConformer (103 MB, ~383 MB peak on the Pixel) a better fallback for English?
`lowram_en_eval.py` (all engines, one scorer, both clip sets), `lowram_nemo_short.py`, `lowram_base_fallback.py`.

WER %, Whisper `EnglishTextNormalizer` (the 120-clip scorer), lower is better:

| source | clips | Vosk small | Whisper small | NeMo FC |
|---|---|---|---|---|
| AMI meetings | 30 | 24.9 | 13.0 | 12.1 |
| Earnings22 | 30 | 35.3 | 10.8 | 13.2 |
| GigaSpeech | 30 | 9.1 | 4.4 | 8.3 |
| VoxPopuli | 30 | 19.0 | 7.6 | 6.5 |
| **ESB all 120** | 120 | 22.0 | **8.9** | 9.9 |
| **FLEURS en** | 25 | 24.9 | **11.0** | 17.8 |
| all 145 | 145 | 22.3 | 9.0 | 10.6 |

Same data with the plain normalizer `ml_score2.py` uses (NFKC, lowercase, punctuation to spaces): ESB 120 24.4 / 13.4 / 14.0,
FLEURS 24.0 / 8.9 / 16.2 (Vosk / Whisper small / NeMo). Paired bootstrap over clips, WER points, 95% CI:

| comparison | ESB 120 | FLEURS 25 |
|---|---|---|
| Vosk minus NeMo | +12.1 [10.0, 14.3] | +7.1 [2.3, 12.3] |
| Whisper small minus NeMo | -1.1 [-1.9, -0.3] | -6.9 [-12.2, -2.0] |

**Why the two NeMo numbers disagree.** Both are right; they measure different speech.
* NeMo is strong on conversational / parliamentary speech like its training data (VoxPopuli 6.5, better than Whisper small;
  AMI 12.1) and weaker on FLEURS read Wikipedia sentences full of rare words ("cabbage juice" heard as "capestries", "Dunlap
  broadsides" as "Dunmat Bobsite"), where it also drops or splits characters ("goingt", "tovie").
* The normalizer is not the explanation. Switching it moves every engine's FLEURS score by 1-2 points (NeMo 16.2 to 17.8,
  Whisper small 8.9 to 11.0, Vosk 24.0 to 24.9) and the NeMo-minus-Whisper-small gap stays 6.8-7.3 points. (FLEURS references
  keep digits, "150 and 200"; the Whisper normalizer folds spelled numbers either way.) The earlier "16.2 vs 9.9" mismatch
  was therefore two different kinds of speech, not two scorers.
* Clip length is not the cause, and does not exceed the app's 28 s windows (longest clip 27.9 s). By length (Whisper
  normalizer, Vosk / Whisper small / NeMo): <10 s (12 clips) 27.8 / 10.1 / 19.8; 10-20 s (62) 16.0 / 8.5 / 10.7; 20-30 s (71)
  26.2 / 9.3 / 9.9. NeMo is weakest on short clips, but padding the FLEURS clips with 1 s of silence (17.8 to 20.3) or joining
  them into ~20 s windows (22.0) made it worse, so short length alone is not the mechanism; the vocabulary is.
  Real recordings in the app are cut at quiet moments near 28 s, so the 20-30 s row is the more relevant one.

**Conclusion.** NeMo is clearly better than Vosk for English on both sets (both CIs exclude 0: +12 and +7 points), at ~0.4 GB
instead of ~1.3 GB, so `EnginePlan.forLanguage` now returns NeMo for English below 5e9 bytes. It is still worse than Whisper small
(1 point on ESB, 7 on FLEURS), so phones with enough RAM keep Whisper small. Confidence: high that NeMo beats Vosk; moderate on
the size of the gap to Whisper small (25 FLEURS clips is a small sample). Mac Python peak RSS for 12 clips, including the
Python/ONNX baseline: Vosk 268 MB, NeMo 509 MB, Whisper base 685 MB, Whisper small 1186 MB.

**pt, ja, ko stay on Vosk.** NeMo has none of them. Whisper base int8 (0.69 GB on the Mac, 58% of small) was measured on the
same FLEURS clips (`lowram_base_fallback.py`, CER for ja/ko, paired bootstrap of Vosk minus base): pt 29.6 vs 16.7, +12.9
[7.5, 18.5] so base is clearly better; ja 22.2 vs 27.9, -5.6 [-11.4, 1.5] so base is worse; ko 29.8 vs 25.0, +4.7 [-4.1, 15.4]
so not distinguishable. A pt-only (maybe ko) Whisper base fallback is possible but needs a new ~160 MB download and an
on-phone memory check, so it is only a proposal, not shipped.

Not measured: real-phone RAM and speed for the English-on-NeMo path on a 4 GB phone (including speaker labels, which load
the 46 MB diarization models while NeMo is still resident), and quality on noisy phone recordings.
