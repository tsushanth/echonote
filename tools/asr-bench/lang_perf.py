"""usage: lang_perf.py ENGINE LANG -> one JSON line. Fresh process, one recognizer, ~28 s windows (clips joined, as the app cuts
audio), 4 threads. peak_rss_mb = ru_maxrss of this process (Mac, includes ~50 MB of Python and numpy)."""
import json, sys, time, resource, numpy as np, soundfile as sf
engine, lang = sys.argv[1], sys.argv[2]
sys.argv = ["x"]
exec(open("lang_run.py").read().split("def main():")[0])
items = json.load(open(f"data/{lang}.json"))
wins, cur = [], []
for it in items:
    y, _ = sf.read(it["wav"], dtype="float32"); cur.append(y)
    if sum(len(c) for c in cur) > 20 * 16000: wins.append(np.concatenate(cur)[: 28 * 16000]); cur = []
    if len(wins) >= 3: break
t0 = time.time()
if engine == "vosk":
    from vosk import Model, KaldiRecognizer, SetLogLevel; SetLogLevel(-1)
    m = Model(p(VOSK[lang])); load = time.time() - t0
    secs = 0
    for w in wins:
        r = KaldiRecognizer(m, 16000); pcm = (np.clip(w, -1, 1) * 32767).astype("<i2").tobytes(); t = time.time()
        for i in range(0, len(pcm), 8192): r.AcceptWaveform(pcm[i:i + 8192])
        r.FinalResult(); secs += time.time() - t
else:
    rec = build(engine, lang); load = time.time() - t0; secs = 0
    for w in wins:
        _, s = run_one(rec, engine, w); secs += s
audio = sum(len(w) for w in wins) / 16000
print(json.dumps({"engine": engine, "lang": lang, "load_sec": round(load, 1), "rtf": round(secs / audio, 3), "audio_sec": round(audio),
                  "peak_rss_mb": round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1e6)}))
