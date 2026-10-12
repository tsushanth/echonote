"""Is NeMo's FLEURS gap a short-clip effect? Decode the 25 FLEURS English clips (a) alone, (b) with 1 s of
silence each side, (c) concatenated in groups of K (argv[1], default 2) into ~20 s windows (like the app's 28 s windows).
Run from the bench dir. Scores with the Whisper English normalizer; (c) compares pooled text to pooled refs."""
import sys, json, numpy as np, soundfile as sf, sherpa_onnx, jiwer, time, resource
from whisper_normalizer.english import EnglishTextNormalizer
EN = EnglishTextNormalizer(); M = "models/sherpa-onnx-nemo-fast-conformer-ctc-en-de-es-fr-14288-int8"
r = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(model=f"{M}/model.int8.onnx", tokens=f"{M}/tokens.txt", num_threads=4)
def dec(y):
    s = r.create_stream(); s.accept_waveform(16000, y); r.decode_stream(s); return s.result.text
items = json.load(open("ml/en.json")); ys = [sf.read(i["wav"], dtype="float32")[0] for i in items]
refs = [i["ref"] for i in items]
def score(h, rf): return 100 * jiwer.wer([EN(x) for x in rf], [EN(x) for x in h])
print("alone      %.1f" % score([dec(y) for y in ys], refs))
K = int(sys.argv[1]) if len(sys.argv) > 1 else 2
sil = np.zeros(16000, dtype="float32")
print("pad 1s     %.1f" % score([dec(np.concatenate([sil, y, sil])) for y in ys], refs))
g = [(" ".join(refs[i:i+K]), dec(np.concatenate([np.concatenate([ys[j], np.zeros(8000, "float32")]) for j in range(i, min(i+K, len(ys)))]))) for i in range(0, len(ys), K)]
print("concat xK  %.1f  (%d windows, up to %.0f s)" % (score([h for _, h in g], [x for x, _ in g]), len(g), max(sum(len(ys[j]) + 8000 for j in range(i, min(i+K, len(ys)))) / 16000 for i in range(0, len(ys), K))))
print("peak RSS MB (python process, NeMo only):", resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1e6)
