import json, glob, os, time, numpy as np, soundfile as sf, sherpa_onnx
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
M = "models"
SZ = os.environ.get("WSIZE", "base")
OUTF = "ml_results.json" if SZ == "base" else f"ml_results_{SZ}.json"
VOSK = {"en": "vosk-model-small-en-us-0.15", "es": "vosk-model-small-es-0.42", "fr": "vosk-model-small-fr-0.22",
        "de": "vosk-model-small-de-0.15", "pt": "vosk-model-small-pt-0.3", "zh": "vosk-model-small-cn-0.22",
        "it": "vosk-model-small-it-0.22", "hi": "vosk-model-small-hi-0.22", "ja": "vosk-model-small-ja-0.22",
        "ko": "vosk-model-small-ko-0.22"}
items = {}
for f in sorted(glob.glob("ml/*.json")):
    for x in json.load(open(f)): items.setdefault(x["lang"], []).append(x)
print({k: len(v) for k, v in items.items()}, flush=True)

wh = {}
def whisper(lang):
    if lang not in wh:
        wh[lang] = sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=f"{M}/whisper-{SZ}/{SZ}-encoder.int8.onnx", decoder=f"{M}/whisper-{SZ}/{SZ}-decoder.int8.onnx",
            tokens=f"{M}/whisper-{SZ}/{SZ}-tokens.txt", language=lang, task="transcribe", num_threads=4,
            enable_token_timestamps=True)
    return wh[lang]

out = {}
for lang, its in items.items():
    vm = Model(f"{M}/{VOSK[lang]}"); r = whisper(lang)
    res = []
    for it in its:
        y, _ = sf.read(it["wav"], dtype="float32")
        t = time.time()
        s = r.create_stream(); s.accept_waveform(16000, y); r.decode_stream(s)
        w_text, w_sec = s.result.text, time.time() - t
        toks, ts = list(s.result.tokens), list(s.result.timestamps)
        rec = KaldiRecognizer(vm, 16000)
        pcm = (np.clip(y, -1, 1) * 32767).astype("<i2").tobytes()
        t = time.time()
        for i in range(0, len(pcm), 8192): rec.AcceptWaveform(pcm[i:i + 8192])
        v_text, v_sec = json.loads(rec.FinalResult()).get("text", ""), time.time() - t
        res.append({"wav": it["wav"], "ref": it["ref"], "dur": it["dur"], "whisper": w_text, "w_sec": w_sec,
                    "vosk": v_text, "v_sec": v_sec, "tokens": toks, "ts": ts})
    out[lang] = res
    json.dump(out, open(OUTF, "w"))
    print(lang, "done", flush=True)
