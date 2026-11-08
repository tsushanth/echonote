# Third-party notices

The ClearVoice Recorder Android app (`com.kreativekoala.echonote`) bundles or downloads the third-party components below. The same credits are shown in the app (Settings, Open source licenses) from `android/app/src/main/res/raw/open_source_licenses.txt`, which also holds the full Apache 2.0, MIT, and SIL OFL 1.1 texts. This file makes no statement about the licence of this repository's own code.

| Component | Licence | What it does in the app |
|---|---|---|
| sherpa-onnx 1.13.8 (Android AAR) | Apache-2.0 | Runs Whisper and the speaker models on the phone |
| eSpeak NG (built into the sherpa-onnx native libraries; fork pinned at commit `ed530aa113046142eb5115cf2fc9157854d0ffe1`) | GPL-3.0-or-later | Phonemizer for sherpa-onnx text-to-speech; present in the binaries, not called by the app. Source: https://github.com/csukuangfj/espeak-ng/tree/ed530aa113046142eb5115cf2fc9157854d0ffe1 , project: https://github.com/espeak-ng/espeak-ng , licence text: https://www.gnu.org/licenses/gpl-3.0.txt |
| ONNX Runtime | MIT | Inference engine shipped inside the sherpa-onnx AAR |
| Whisper base (int8), OpenAI | MIT | Transcription model, converted by the sherpa-onnx project |
| pyannote segmentation 3.0 | MIT | Finds when someone is speaking |
| NVIDIA NeMo TitaNet-small | Apache-2.0 | Tells voices apart |
| NVIDIA NeMo FastConformer multilingual (en, de, es, fr) | CC BY 4.0 or Apache-2.0 (per NVIDIA) | Accurate transcription of German, Spanish, French |
| Vosk and its small language models, Alpha Cephei | Apache-2.0 (Swedish model: MIT) | Fast transcription |
| NVIDIA NeMo FastConformer 10-language (be, de, en, es, fr, hr, it, pl, ru, uk) | CC BY 4.0 (per NVIDIA; NGC page to be re-checked) | Accurate transcription of Polish and Ukrainian |
| PengCheng Starling streaming Zipformer (ar, id, ru, th, vi ...) | Apache-2.0 | Accurate transcription of Arabic and Indonesian |
| Dolphin base CTC, DataoceanAI and Tsinghua University | Apache-2.0 | Accurate transcription of Thai; smaller-phone engine for Arabic, Indonesian, Malay, Tagalog |
| Omnilingual ASR 300M CTC, Meta | Apache-2.0 | Accurate transcription of Bulgarian, Croatian, Czech, Finnish, Romanian, Slovak, Slovenian, Swahili, Tagalog, Hungarian, Greek, Tamil, Telugu |
| Zipformer Russian and Bengali (Vosk project), Zipformer Vietnamese (zzasdf) | Apache-2.0 | Accurate transcription of Russian, Bengali, Vietnamese |
| Apache Commons Compress | Apache-2.0 | Unpacks model downloads |
| Figtree font | SIL OFL 1.1 | UI typography |
| Source Serif 4 font | SIL OFL 1.1 | UI typography |
| Android Jetpack (Compose, Room, WorkManager, Glance), Media3, Dagger Hilt, OkHttp | Apache-2.0 | App framework |

Analytics and advertising SDKs are used under their own terms and are not listed here.
