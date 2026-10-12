import json, time, soundfile as sf, sherpa_onnx
M = "models"
R = json.load(open("ml_results.json"))
def make_sv(lang):
    return sherpa_onnx.OfflineRecognizer.from_sense_voice(
        model=f"{M}/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/model.int8.onnx",
        tokens=f"{M}/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/tokens.txt", language=lang, use_itn=True, num_threads=4)
nm = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
    model=f"{M}/sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288-int8/model.int8.onnx",
    tokens=f"{M}/sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288-int8/tokens.txt", num_threads=4)
def run(r, y):
    s = r.create_stream(); s.accept_waveform(16000, y); t = time.time(); r.decode_stream(s); return s.result.text, time.time() - t
out = {}
for lang, rows in R.items():
    eng = "sensevoice" if lang in ("zh", "ja", "ko") else ("nemo" if lang in ("en", "de", "es", "fr") else None)
    if not eng: continue
    r = make_sv(lang) if eng == "sensevoice" else nm
    res = []
    for it in rows:
        y, _ = sf.read(it["wav"], dtype="float32")
        txt, sec = run(r, y)
        res.append({"wav": it["wav"], "text": txt, "sec": sec})
    out[lang] = {"engine": eng, "rows": res}
    json.dump(out, open("ml_extra.json", "w"))
    print(lang, eng, "done", round(sum(x["sec"] for x in res) / sum(x["dur"] for x in rows), 3), "RTF", flush=True)
