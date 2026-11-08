import io, json, re, numpy as np, soundfile as sf, pyarrow.parquet as pq
from huggingface_hub import HfFileSystem
# Egyptian-dialect Arabic, MGB-3 test split (broadcast/YouTube). 2 utterances from each row group => spread across genres. Private evaluation only.
fs = HfFileSystem(); rows = []
pf = pq.ParquetFile(fs.open("datasets/MohamedRashad/MGB-3-Arabic/data/test-00000-of-00002.parquet"))
for g in range(pf.metadata.num_row_groups):
    t = pf.read_row_group(g, columns=["id", "audio", "text"]).to_pylist(); got = 0
    for r in t[len(t)//3:]:                      # skip the very start of the group
        txt = re.sub(r"\([^)]*\)", " ", r["text"]).replace("#", " ").replace("+", " "); txt = re.sub(r"\s+", " ", txt).strip()
        y, sr = sf.read(io.BytesIO(r["audio"]["bytes"]), dtype="float32")
        if y.ndim > 1: y = y.mean(axis=1)
        if sr != 16000: continue
        d = len(y) / 16000
        if not (4 <= d <= 20) or len(txt.split()) < 6: continue
        p = f"data/arEG_{len(rows):03d}.wav"; sf.write(p, y, 16000)
        rows.append({"wav": p, "ref": txt, "dur": d, "lang": "arEG", "src": r["id"]}); got += 1
        if got == 2: break
    if len(rows) >= 25: break
json.dump(rows, open("data/arEG.json", "w"), ensure_ascii=False)
print(len(rows), "utts", round(sum(x["dur"] for x in rows)), "s"); print(rows[0]["ref"], "|", rows[5]["ref"])
