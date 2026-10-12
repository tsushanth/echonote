import io, json, collections, numpy as np, soundfile as sf
from datasets import load_dataset, Audio
ds = load_dataset("hf-audio/esb-datasets-test-only-sorted", "librispeech", split="test.clean", streaming=True).cast_column("audio", Audio(decode=False))
by = collections.defaultdict(list)
n = 0
for r in ds:
    if not (3.0 <= r["audio_length_s"] <= 14.0):
        continue
    spk = str(r["id"]).split("-")[0]
    if len(by[spk]) >= 3:
        continue
    y, sr = sf.read(io.BytesIO(r["audio"]["bytes"]), dtype="float32")
    if y.ndim > 1: y = y.mean(axis=1)
    assert sr == 16000, sr
    by[spk].append(y)
    n += 1
    if sum(1 for v in by.values() if len(v) >= 2) >= 24 and n > 90:
        break
import pickle
pickle.dump({k: v for k, v in by.items() if len(v) >= 2}, open("diar_utts.pkl", "wb"))
print("speakers with >=2 utterances:", sum(1 for v in by.values() if len(v) >= 2), "utterances:", n)
