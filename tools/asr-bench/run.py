import json, sys, time, glob, os, numpy as np, soundfile as sf
import sherpa_onnx
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
M = "models"
engines = sys.argv[1:] or ["vosk", "whisper", "moonshine"]
items = []
for f in sorted(glob.glob("data/*.json")):
    items += json.load(open(f))
print(len(items), "utterances", flush=True)

def make(name):
    if name == "vosk":
        model = Model(f"{M}/vosk-model-small-en-us-0.15")
        def run(y):
            rec = KaldiRecognizer(model, 16000)
            pcm = (np.clip(y, -1, 1) * 32767).astype("<i2").tobytes()
            for i in range(0, len(pcm), 8192):
                rec.AcceptWaveform(pcm[i:i + 8192])
            return json.loads(rec.FinalResult()).get("text", "")
        return run
    if name == "whisper":
        r = sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=f"{M}/whisper-base/base-encoder.int8.onnx", decoder=f"{M}/whisper-base/base-decoder.int8.onnx",
            tokens=f"{M}/whisper-base/base-tokens.txt", language="en", task="transcribe", num_threads=4)
    elif name == "moonshine":
        d = f"{M}/sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27"
        r = sherpa_onnx.OfflineRecognizer.from_moonshine_v2(
            encoder=f"{d}/encoder_model.ort", decoder=f"{d}/decoder_model_merged.ort", tokens=f"{d}/tokens.txt", num_threads=4)
    else:
        raise SystemExit("unknown engine " + name)
    def decode(y):
        s = r.create_stream()
        s.accept_waveform(16000, y)
        r.decode_stream(s)
        return s.result.text
    if name != "moonshine":
        return decode
    # Moonshine's export fails above ~8-9 s, so cut at the quietest moment in 5-8 s windows.
    def run(y):
        parts, pos, frame = [], 0, 1600
        while len(y) - pos > 8 * 16000:
            seg = np.abs(y[pos + 5 * 16000: pos + 8 * 16000])
            n = len(seg) // frame
            e = seg[: n * frame].reshape(n, frame).sum(axis=1)
            cut = pos + 5 * 16000 + int(np.argmin(e)) * frame
            parts.append(decode(y[pos:cut])); pos = cut
        parts.append(decode(y[pos:]))
        return " ".join(p for p in parts if p)
    return run

os.makedirs("results", exist_ok=True)
for name in engines:
    run = make(name)
    out, total_audio, total_time = {}, 0.0, 0.0
    for i, it in enumerate(items):
        y, sr = sf.read(it["wav"], dtype="float32")
        t = time.time()
        try:
            hyp = run(y)
        except Exception as e:
            hyp = ""
            print(name, it["wav"], "ERR", str(e)[:100], flush=True)
        dt = time.time() - t
        out[it["wav"]] = {"hyp": hyp, "sec": dt}
        total_audio += it["dur"]; total_time += dt
        if i % 25 == 0:
            print(name, i, round(total_time / max(total_audio, 1e-9), 3), "RTF so far", flush=True)
    json.dump(out, open(f"results/{name}.json", "w"))
    print(name, "done RTF", round(total_time / total_audio, 3), flush=True)
