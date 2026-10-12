import io, json, sys, os, numpy as np, soundfile as sf
from datasets import load_dataset, Audio
SUBSETS = ["ami", "earnings22", "gigaspeech", "tedlium", "voxpopuli"]
N = 30
os.makedirs("data", exist_ok=True)
for cfg in SUBSETS:
    out = f"data/{cfg}.json"
    if os.path.exists(out):
        continue
    rows = []
    try:
        ds = load_dataset("hf-audio/esb-datasets-test-only-sorted", cfg, split="test", streaming=True)
        ds = ds.cast_column("audio", Audio(decode=False))
        for r in ds:
            d = r["audio_length_s"]
            if not (4.0 <= d <= 28.0):
                continue
            y, sr = sf.read(io.BytesIO(r["audio"]["bytes"]), dtype="float32")
            if y.ndim > 1:
                y = y.mean(axis=1)
            if sr != 16000:
                n = int(len(y) * 16000 / sr)
                y = np.interp(np.linspace(0, len(y) - 1, n), np.arange(len(y)), y).astype("float32")
            path = f"data/{cfg}_{len(rows):03d}.wav"
            sf.write(path, y, 16000)
            rows.append({"wav": path, "ref": r["text"], "dur": float(d)})
            if len(rows) >= N:
                break
    except Exception as e:
        print(cfg, "FAILED", type(e).__name__, str(e)[:200], flush=True)
    json.dump(rows, open(out, "w"))
    print(cfg, len(rows), "utterances", round(sum(x["dur"] for x in rows)), "s", flush=True)
