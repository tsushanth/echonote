"""Would Whisper base int8 be a safe fallback for pt/ja/ko on low-RAM phones?
Part 1 (no args): paired bootstrap of Vosk minus Whisper base error (CER for ja/ko) on the FLEURS clips already
transcribed by ml_run.py (ml_results.json). Part 2 (`rss <small|base|nemo|vosk>`): decode ~12 clips of one engine in a
fresh process; wrap with `/usr/bin/time -l` to read its peak memory (Mac, includes the Python runtime baseline)."""
import sys, json, re, unicodedata, random, jiwer
def norm(s, ns):
    s = unicodedata.normalize("NFKC", s).lower(); s = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)
    s = re.sub(r"\s+", " ", s).strip(); return s.replace(" ", "") if ns else s
if len(sys.argv) == 1:
    R = json.load(open("ml_results.json")); random.seed(0)
    for lang in ("pt", "ja", "ko"):
        ns = lang in ("ja", "ko"); rows = [r for r in R[lang] if norm(r["ref"], ns)]
        f = (lambda a, b: jiwer.cer(a, b)) if ns else (lambda a, b: jiwer.wer(a, b))
        def e(r, k):
            a, b = norm(r["ref"], ns), norm(r[k], ns); o = jiwer.process_characters(a, b) if ns else jiwer.process_words(a, b)
            return o.substitutions + o.deletions + o.insertions, len(a) if ns else len(a.split())
        V, W = [e(r, "vosk") for r in rows], [e(r, "whisper") for r in rows]; d = []
        for _ in range(4000):
            ix = [random.randrange(len(V)) for _ in V]; d.append(100 * (sum(V[i][0] for i in ix) - sum(W[i][0] for i in ix)) / sum(V[i][1] for i in ix))
        d.sort(); print("%s vosk %.1f  whisper-base %.1f  vosk minus base %.1f pts  95%% CI [%.1f, %.1f]" % (
            lang, 100 * f([norm(r["ref"], ns) for r in rows], [norm(r["vosk"], ns) for r in rows]),
            100 * f([norm(r["ref"], ns) for r in rows], [norm(r["whisper"], ns) for r in rows]), sum(d) / len(d), d[100], d[3900]))
else:
    import soundfile as sf, sherpa_onnx
    M = "models"; items = json.load(open("ml/en.json"))[:12]; ys = [sf.read(i["wav"], dtype="float32")[0] for i in items]
    k = sys.argv[2]
    if k in ("small", "base"):
        r = sherpa_onnx.OfflineRecognizer.from_whisper(encoder=f"{M}/whisper-{k}/{k}-encoder.int8.onnx", decoder=f"{M}/whisper-{k}/{k}-decoder.int8.onnx",
            tokens=f"{M}/whisper-{k}/{k}-tokens.txt", language="en", task="transcribe", num_threads=4)
    elif k == "nemo":
        d = f"{M}/sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288-int8"; r = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", num_threads=4)
    else:
        from vosk import Model, KaldiRecognizer, SetLogLevel; import numpy as np; SetLogLevel(-1); m = Model(f"{M}/vosk-model-small-en-us-0.15")
        for y in ys: rc = KaldiRecognizer(m, 16000); rc.AcceptWaveform((np.clip(y, -1, 1) * 32767).astype("<i2").tobytes()); rc.FinalResult()
        sys.exit()
    for y in ys:
        s = r.create_stream(); s.accept_waveform(16000, y); r.decode_stream(s)
