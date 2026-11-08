import io, json, os, sys, numpy as np, soundfile as sf, pyarrow.parquet as pq
from huggingface_hub import HfFileSystem
# FLEURS test split, first 25 utterances of 4-25 s, via ranged reads of the parquet conversion (no full download).
LANGS = {"ar":"ar_eg","ru":"ru_ru","tr":"tr_tr","id":"id_id","vi":"vi_vn","th":"th_th","pl":"pl_pl","nl":"nl_nl",
 "uk":"uk_ua","fa":"fa_ir","he":"he_il","sv":"sv_se","cs":"cs_cz","el":"el_gr","ro":"ro_ro","hu":"hu_hu","ms":"ms_my",
 "tl":"fil_ph","bn":"bn_in","ur":"ur_pk","ta":"ta_in","te":"te_in","sw":"sw_ke","hi":"hi_in","it":"it_it",
 "bg":"bg_bg","hr":"hr_hr","da":"da_dk","fi":"fi_fi","sk":"sk_sk","sl":"sl_si","ca":"ca_es","kk":"kk_kz","uz":"uz_uz","ka":"ka_ge","ko":"ko_kr","ja":"ja_jp","zh":"cmn_hans_cn","en":"en_us","de":"de_de","es":"es_419","fr":"fr_fr","pt":"pt_br"}
N = 25
D = "data"; os.makedirs(D, exist_ok=True)
fs = HfFileSystem()
only = sys.argv[1:] or list(LANGS)
for code in only:
    cfg = LANGS[code]; out = f"{D}/{code}.json"
    if os.path.exists(out): continue
    rows = []
    try:
        path = f"datasets/google/fleurs@refs%2Fconvert%2Fparquet/{cfg}/test/0000.parquet"
        pf = pq.ParquetFile(fs.open(path))
        for batch in pf.iter_batches(batch_size=8, columns=["audio","raw_transcription","transcription"]):
            for r in batch.to_pylist():
                y, sr = sf.read(io.BytesIO(r["audio"]["bytes"]), dtype="float32")
                if y.ndim > 1: y = y.mean(axis=1)
                if sr != 16000:
                    n = int(len(y)*16000/sr); y = np.interp(np.linspace(0,len(y)-1,n), np.arange(len(y)), y).astype("float32")
                d = len(y)/16000
                if not (4 <= d <= 25): continue
                p = f"{D}/{code}_{len(rows):03d}.wav"; sf.write(p, y, 16000)
                rows.append({"wav": p, "ref": r["raw_transcription"], "dur": d, "lang": code})
                if len(rows) >= N: break
            if len(rows) >= N: break
    except Exception as e:
        print(code, "FAILED", type(e).__name__, str(e)[:200], flush=True)
    json.dump(rows, open(out, "w"), ensure_ascii=False)
    print(code, len(rows), "utts", round(sum(x["dur"] for x in rows)), "s", flush=True)
