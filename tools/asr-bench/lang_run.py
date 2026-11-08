"""usage: lang_run.py ENGINE LANG [LANG...]  -> out/res/ENGINE__LANG.json  (hyp text per utterance, decode seconds)
Engines mirror what the app can run with sherpa-onnx 1.13.8 (4 threads) or Vosk small."""
import json, os, sys, time, glob, resource, numpy as np, soundfile as sf
import sherpa_onnx
M = os.path.abspath("models"); os.makedirs("out/res", exist_ok=True)
def p(*a): return os.path.join(M, *a)
G = lambda pat: glob.glob(p(pat))[0]
WHISPER_LANG = {"he": "he", "tl": "tl", "ms": "ms", "uz": "uz", "kk": "kk", "ka": "ka"}  # sherpa whisper language tags
def build(engine, lang):
    nt = 4
    if engine.startswith("whisper-"):
        s = engine.split("-")[1]
        return sherpa_onnx.OfflineRecognizer.from_whisper(encoder=p(engine, f"{s}-encoder.int8.onnx"), decoder=p(engine, f"{s}-decoder.int8.onnx"),
            tokens=p(engine, f"{s}-tokens.txt"), language=WHISPER_LANG.get(lang, lang[:2] if lang=="arEG" else lang), task="transcribe", num_threads=nt, enable_token_timestamps=True)
    if engine in ("dolphin-base", "dolphin-small"):
        d = G(f"sherpa-onnx-dolphin-{engine.split('-')[1]}-ctc-multi-lang-int8-*")
        return sherpa_onnx.OfflineRecognizer.from_dolphin_ctc(model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", num_threads=nt)
    if engine == "omnilingual-300m":
        d = G("sherpa-onnx-omnilingual-*300M-ctc-v2-int8*")
        return sherpa_onnx.OfflineRecognizer.from_omnilingual_asr_ctc(model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", num_threads=nt)
    if engine == "nemo10":
        d = G("sherpa-onnx-nemo-fast-conformer-ctc-be-*")
        return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", num_threads=nt)
    if engine == "gigaam-v3-ru":
        d = G("sherpa-onnx-nemo-ctc-giga-am-v3-*")
        return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", num_threads=nt)
    if engine == "parakeet-v3":
        d = G("sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8")
        return sherpa_onnx.OfflineRecognizer.from_transducer(encoder=f"{d}/encoder.int8.onnx", decoder=f"{d}/decoder.int8.onnx", joiner=f"{d}/joiner.int8.onnx",
            tokens=f"{d}/tokens.txt", num_threads=nt, model_type="nemo_transducer")
    if engine in ("zipformer-ru", "zipformer-vi-30m", "zipformer-vi", "zipformer-th"):
        d = G({"zipformer-ru": "sherpa-onnx-zipformer-ru-int8-*", "zipformer-vi-30m": "sherpa-onnx-zipformer-vi-30M-int8-*", "zipformer-vi": "sherpa-onnx-zipformer-vi-int8-*", "zipformer-th": "sherpa-onnx-zipformer-thai-*"}[engine])
        enc = glob.glob(f"{d}/encoder*.int8.onnx")[0]; dec = glob.glob(f"{d}/decoder*.onnx")[0]; jo = glob.glob(f"{d}/joiner*.int8.onnx")[0]
        return sherpa_onnx.OfflineRecognizer.from_transducer(encoder=enc, decoder=dec, joiner=jo, tokens=f"{d}/tokens.txt", num_threads=nt, decoding_method="greedy_search")
    if engine.startswith("moonshine-"):
        d = G(f"sherpa-onnx-moonshine-base-{engine.split('-')[1]}-quantized-*")
        return sherpa_onnx.OfflineRecognizer.from_moonshine_v2(encoder=f"{d}/encoder_model.ort", decoder=f"{d}/decoder_model_merged.ort", tokens=f"{d}/tokens.txt", num_threads=nt)
    if engine == "qwen3-asr":
        d = G("sherpa-onnx-qwen3-asr-0.6B-int8-*")
        return sherpa_onnx.OfflineRecognizer.from_qwen3_asr(conv_frontend=f"{d}/conv_frontend.onnx", encoder=f"{d}/encoder.int8.onnx", decoder=f"{d}/decoder.int8.onnx", tokenizer=f"{d}/tokenizer", num_threads=nt)
    if engine == "stream-zipformer-multi":
        d = G("sherpa-onnx-streaming-zipformer-ar_en_id*")
        return sherpa_onnx.OnlineRecognizer.from_transducer(encoder=glob.glob(f"{d}/encoder*.int8.onnx")[0], decoder=glob.glob(f"{d}/decoder*.onnx")[0],
            joiner=glob.glob(f"{d}/joiner*.int8.onnx")[0], tokens=f"{d}/tokens.txt", num_threads=nt, decoding_method="greedy_search", enable_endpoint_detection=False)
    if engine == "stream-zipformer-bn":
        d = G("sherpa-onnx-streaming-zipformer-bn-vosk-*")
        return sherpa_onnx.OnlineRecognizer.from_transducer(encoder=f"{d}/encoder.onnx", decoder=f"{d}/decoder.onnx", joiner=f"{d}/joiner.onnx", tokens=f"{d}/tokens.txt",
            num_threads=nt, decoding_method="greedy_search", enable_endpoint_detection=False)
    raise SystemExit("unknown engine " + engine)

VOSK = {"ar":"vosk-model-small-ar-0.3","ar-tn":"vosk-model-small-ar-tn-0.1-linto","ca":"vosk-model-small-ca-0.4","cs":"vosk-model-small-cs-0.4-rhasspy","fa":"vosk-model-small-fa-0.42",
 "hi":"vosk-model-small-hi-0.22","it":"vosk-model-small-it-0.22","ka":"vosk-model-small-ka-0.42","kk":"vosk-model-small-kz-0.42","nl":"vosk-model-small-nl-0.22","pl":"vosk-model-small-pl-0.22",
 "ru":"vosk-model-small-ru-0.22","sv":"vosk-model-small-sv-rhasspy-0.15","te":"vosk-model-small-te-0.42","tr":"vosk-model-small-tr-0.3","uk":"vosk-model-small-uk-v3-small",
 "uz":"vosk-model-small-uz-0.22","ky":"vosk-model-small-ky-0.42","tg":"vosk-model-small-tg-0.22","vi":"vosk-model-small-vn-0.4","gu":"vosk-model-small-gu-0.42"}

def run_one(rec, engine, y):
    t = time.time()
    if engine.startswith("stream-"):
        s = rec.create_stream(); s.accept_waveform(16000, y); s.accept_waveform(16000, np.zeros(16000, dtype="float32")); s.input_finished()
        while rec.is_ready(s): rec.decode_stream(s)
        return rec.get_result(s).strip() if isinstance(rec.get_result(s), str) else rec.get_result(s).text.strip(), time.time() - t
    s = rec.create_stream(); s.accept_waveform(16000, y); rec.decode_stream(s)
    return s.result.text.strip(), time.time() - t

def main():
    engine, langs = sys.argv[1], sys.argv[2:]
    rec = None if engine == "vosk" else None
    for lang in langs:
        out = f"out/res/{engine}__{lang}.json"
        if os.path.exists(out): continue
        items = json.load(open(f"data/{lang.split('-')[0] if lang!='ar-tn' else 'ar'}.json"))
        res = []; t0 = time.time()
        if engine == "vosk":
            from vosk import Model, KaldiRecognizer, SetLogLevel; SetLogLevel(-1)
            vk = "ar" if lang == "arEG" else lang
            if vk not in VOSK: print("no vosk model", lang); continue
            vm = Model(p(VOSK[vk]))
            load = time.time() - t0
            for it in items:
                y, _ = sf.read(it["wav"], dtype="float32"); pcm = (np.clip(y, -1, 1) * 32767).astype("<i2").tobytes()
                r = KaldiRecognizer(vm, 16000); t = time.time()
                for i in range(0, len(pcm), 8192): r.AcceptWaveform(pcm[i:i + 8192])
                res.append({"text": json.loads(r.FinalResult()).get("text", ""), "sec": time.time() - t, "dur": it["dur"], "ref": it["ref"]})
        else:
            if rec is None or engine.startswith("whisper-"): rec = build(engine, lang)
            load = time.time() - t0
            for it in items:
                y, _ = sf.read(it["wav"], dtype="float32")
                txt, sec = run_one(rec, engine, y)
                res.append({"text": txt, "sec": sec, "dur": it["dur"], "ref": it["ref"]})
        rss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1e6  # bytes on macOS -> MB
        json.dump({"engine": engine, "lang": lang, "load_sec": load, "peak_rss_mb": rss, "rows": res}, open(out, "w"), ensure_ascii=False)
        print(engine, lang, "rtf=%.3f" % (sum(r["sec"] for r in res) / sum(r["dur"] for r in res)), "peakMB=%.0f" % rss, flush=True)
main()
