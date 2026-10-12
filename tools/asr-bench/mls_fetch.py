import io, collections, pickle, soundfile as sf
from datasets import load_dataset, Audio
for lang, code in [("spanish","es"),("french","fr"),("german","de"),("italian","it"),("portuguese","pt")]:
    by = collections.defaultdict(list); n = 0
    try:
        ds = load_dataset("facebook/multilingual_librispeech", lang, split="test", streaming=True).cast_column("audio", Audio(decode=False))
        for r in ds:
            if not (3.0 <= r["audio_duration"] <= 14.0): continue
            sp = str(r["speaker_id"])
            if len(by[sp]) >= 3: continue
            y, sr = sf.read(io.BytesIO(r["audio"]["bytes"]), dtype="float32")
            if y.ndim > 1: y = y.mean(axis=1)
            if sr != 16000:
                import numpy as np
                m = int(len(y) * 16000 / sr); y = np.interp(np.linspace(0, len(y) - 1, m), np.arange(len(y)), y).astype("float32")
            by[sp].append(y); n += 1
            if sum(1 for v in by.values() if len(v) >= 2) >= 14 and n > 45: break
    except Exception as e:
        print(code, "FAILED", type(e).__name__, str(e)[:120], flush=True)
    pickle.dump({k: v for k, v in by.items() if len(v) >= 2}, open(f"mls_{code}.pkl", "wb"))
    print(code, "speakers>=2:", sum(1 for v in by.values() if len(v) >= 2), "utterances", n, flush=True)
