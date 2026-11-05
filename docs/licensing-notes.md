# Licensing notes: eSpeak NG inside the sherpa-onnx AAR

Date: 2026-11-04. Scope: Android app `com.kreativekoala.echonote`. This records a technical finding and options. It is not legal advice and does not conclude what the app's own licence must be.

## Finding

The app ships `sherpa-onnx-1.13.8.aar` (pinned by SHA-256 in `android/app/build.gradle.kts`). The in-app licence screen credited sherpa-onnx (Apache-2.0) and ONNX Runtime but not eSpeak NG.

What was checked:

- sherpa-onnx v1.13.8 `cmake/espeak-ng-for-piper.cmake` downloads `https://github.com/csukuangfj/espeak-ng` at commit `ed530aa113046142eb5115cf2fc9157854d0ffe1` (a fork of the eSpeak NG project) and builds it with `add_subdirectory`, using static library settings. The top-level `CMakeLists.txt` includes it and has `option(SHERPA_ONNX_ENABLE_TTS ... ON)`, so it is on by default.
- That fork's `README.md` says eSpeak NG "is released under the GPL version 3 or later license"; its `COPYING` is the GPLv3 text; source headers read "Copyright (C) 2005 to 2013 by Jonathan Duddington" and "Copyright (C) 2013-2017 Reece H. Dunn", under GPL version 3 or (at your option) later.
- In the AAR itself (hash verified), `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so` and `libsherpa-onnx-cxx-api.so` for arm64-v8a, armeabi-v7a, x86 and x86_64 contain eSpeak strings. This includes eSpeak NG's own status message "The requested functionality has not been built into espeak-ng" plus sherpa-onnx's phonemizer messages, so eSpeak NG code is linked into these shared libraries, not just referenced. `classes.jar` contains the `OfflineTts*` Kotlin classes.
- The app does not use sherpa-onnx TTS. Grepping `android/app/src` finds no `OfflineTts`, `espeak` or TTS model use; `SherpaTranscriber.kt` and `SpeakerDiarizer.kt` use only recogniser and diarization classes, `service/` uses Vosk, and Read Aloud (`tts/SystemTtsEngine.kt`) uses Android's system `TextToSpeech`. So eSpeak ships in the binary but is dead code at runtime.

Not verified: whether sherpa-onnx's Apache-2.0 plus a GPL-3.0-or-later component linked into the same `.so` has consequences for how the app itself may be licensed or distributed. That is a legal question. The credit alone does not settle it.

## Done in this change

- Added an eSpeak NG entry (licence, copyright holders, role, project and pinned-source links, GPL text link) to `open_source_licenses.txt`; `LicensesTest` asserts it.
- Added `NOTICE.md` listing bundled components.

The GPL full text is linked, not embedded, in the in-app screen.

## Options for the owner

**A. Keep the AAR and the credit (done here).** Zero effort, no behaviour change. Residual risk: the binary still contains GPL-3.0-or-later code, so the GPL source and licence-text obligations for that code still apply to what you distribute, and a link plus pointer may not be judged sufficient. Decide whether that residual exposure is acceptable, ideally with a legal opinion.

**B. Build a custom sherpa-onnx AAR with TTS disabled.** Configure the Android build with `-DSHERPA_ONNX_ENABLE_TTS=OFF`. Then `espeak-ng` should not be fetched or linked, removing eSpeak NG from the `.so` files (confirm by running `strings` for "espeak" on the output, and diff Kotlin API use). Not implemented.
- Effort: roughly 1 to 2 days: NDK setup, run `build-android-*.sh` for 4 ABIs, assemble the AAR (`android/SherpaOnnxAar`), host it, update `sherpaVersion`/`sherpaSha256`/download URL in `build.gradle.kts`, regression-test Whisper, NeMo CTC, and diarization on a device. Smaller `.so` files as a side effect.
- Risks: we then own an unofficial build and must rebuild per sherpa-onnx upgrade; the Kotlin `OfflineTts` classes would still be in the AAR's `classes.jar` but unusable (unused here); the TTS-off build has not been tried here, so ASR and diarization must be re-verified; the AAR needs hosting (the stock URL is GitHub Releases); the JNI glue may not build cleanly with TTS off at 1.13.8 (untested); if so, fall back to A.

I recommend deciding between A and B once you have an opinion on the residual risk; B is the only option that removes the GPL code from the app.
